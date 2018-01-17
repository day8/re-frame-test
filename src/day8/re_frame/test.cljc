(ns day8.re-frame.test
  #?(:cljs (:require-macros day8.re-frame.test))
  (:require #?(:cljs [cljs.test :as test]
               :clj  [clojure.test :as test])
            [re-frame.core :as rf]
            [re-frame.router :as rf.router]
            [re-frame.db :as rf.db]
            [re-frame.interop :as rf.int])
  #?(:clj (:import [java.util.concurrent Executors TimeUnit])))


;;;;
;;;; General test utils
;;;;

(defn- dequeue!
  "Dequeue an item from a persistent queue which is stored as the value in
  queue-atom. Returns the item, and updates the atom with the new queue
  value. If the queue is empty, does not alter it and returns nil."
  [queue-atom]
  (let [queue @queue-atom]
    (when (seq queue)
      (if (compare-and-set! queue-atom queue (pop queue))
        (peek queue)
        (recur queue-atom)))))


#?(:clj
   (defmacro with-temp-re-frame-state
     "Run `body`, but discard whatever effects it may have on re-frame's internal
      state (by resetting `app-db` and re-frame's various different types of
      handlers after `body` has run).

      Note: you *can't* use this macro to clean up a JS async test, since the macro
      will perform the cleanup before your async code actually has a chance to run.
      `run-test-async` will automatically do this cleanup for you."
     [& body]
     `(let [restore-fn# (rf/make-restore-fn)]
        (try
          ~@body
          (finally
            (restore-fn#))))))


;;;;
;;;; Async tests
;;;;

(def ^:dynamic *test-timeout* 5000)

(def ^{:dynamic true, :private true} *test-context*
  "`*test-context*` is used to communicate internal details of the test between
  `run-test-async*` and `wait-for*`. It is dynamically bound so that it doesn't
  need to appear as a lexical argument to a `wait-for` block, since we don't
  want it to be visible when you're writing tests.  But care must be taken to
  pass it around lexically across callbacks, since ClojureScript doesn't have
  `bound-fn`."
  nil)


(defn run-test-async* [f]
  (let [test-context {:wait-for-depth     0
                      :max-wait-for-depth (atom 0)
                      :now-waiting-for    (atom nil)}]
    #?(:clj  (with-temp-re-frame-state
               (let [done-promise (promise)
                     executor     (Executors/newSingleThreadExecutor)
                     fail-ex      (atom nil)
                     test-context (assoc test-context :done #(deliver done-promise ::done))]
                 (with-redefs [rf.int/executor executor]
                   ;; Execute the test code itself on the same thread as the
                   ;; re-frame event handlers run, so that we accurately
                   ;; simulate the single-threaded JS environment and also so
                   ;; that we don't have to worry about making the JVM
                   ;; implementation of the re-frame EventQueue thread-safe.
                   (rf.int/next-tick #(try
                                        (binding [*test-context* test-context]
                                          (f))
                                        (catch Throwable t
                                          (reset! fail-ex t))))
                   (let [result (deref done-promise *test-timeout* ::timeout)]
                     (.shutdown executor)
                     (when-not (.awaitTermination executor 5 TimeUnit/SECONDS)
                       (throw (ex-info (str "Couldn't cleanly shut down the re-frame event queue's "
                                            "executor.  Possibly this could result in a polluted "
                                            "`app-db` for other tests.  Probably it means you're "
                                            "doing something very strange in an event handler.  "
                                            "(Catching InterruptedException, for a start.)")
                                       {})))
                     (if-let [ex @fail-ex]
                       (throw ex)
                       (test/is (not= ::timeout result)
                                (str "Test timed out after " *test-timeout* "ms"
                                     (when-let [ev @(:now-waiting-for test-context)]
                                       (str ", waiting for " (pr-str ev) ".")))))))))

       :cljs (test/async done
               (let [restore-fn (rf/make-restore-fn)]
                 (binding [*test-context* (assoc test-context :done (fn [] (restore-fn) (done)))]
                   (f)))))))


(defmacro run-test-async
  "Run `body` as an async re-frame test. The async nature means you'll need to
  use `wait-for` any time you want to make any assertions that should be true
  *after* an event has been handled.  It's assumed that there will be at least
  one `wait-for` in the body of your test (otherwise you don't need this macro
  at all).

  Note: unlike regular ClojureScript `cljs.test/async` tests, `wait-for` takes
  care of calling `(done)` for you: you don't need to do anything specific to
  handle the fact that your test is asynchronous, other than make sure that all
  your assertions happen with `wait-for` blocks.

  This macro will automatically clean up any changes to re-frame state made
  within the test body, as per `with-temp-re-frame-state` (except that the way
  it's done here *does* work for async tests, whereas that macro used by itself
  doesn't)."
  [& body]
  `(run-test-async* (fn [] ~@body)))


(defn- as-callback-pred
  "Interprets the acceptable input values for `wait-for`'s `ok-ids` and
  `failure-ids` params to produce a predicate function on an event.  See
  `wait-for` for details."
  [callback-pred]
  (when callback-pred
    (cond (or (set? callback-pred)
              (vector? callback-pred)) (fn [event]
                                         (some (fn [pred] (pred event))
                                               (map as-callback-pred (seq callback-pred))))
          (fn? callback-pred)          callback-pred
          (keyword? callback-pred)     (fn [[event-id _]]
                                         (= callback-pred event-id))
          :else                        (throw
                                        (ex-info (str (pr-str callback-pred)
                                                      " isn't an event predicate")
                                                 {:callback-pred callback-pred})))))


(defn wait-for*
  "This function is an implementation detail: in your async tests (within a
  `run-test-async`), you should use the `wait-for` macro instead.  (For
  synchronous tests within `run-test-sync`, you don't need this capability at
  all.)

  Installs `callback` as a re-frame post-event callback handler, called as soon
  as any event matching `ok-ids` is handled.  Aborts the test as a failure if
  any event matching `failure-ids` is handled.

  Since this is intended for use in asynchronous tests: it will return
  immediately after installing the callback -- it doesn't *actually* wait.

  Note that `wait-for*` tracks whether, during your callback, you call
  `wait-for*` again.  If you *don't*, then, given the way asynchronous tests
  work, your test must necessarily be finished.  So `wait-for*` will
  call `(done)` for you."
  [ok-ids failure-ids callback]
  ;; `:wait-for-depth` and `:max-wait-for-depth` are used together to track how
  ;; "deep" we are in callback functions as the test progresses.  We increment
  ;; `:max-wait-for-depth` before installing a post-event callback handler, then
  ;; after the event later occurs and the callback handler subsequently runs, we
  ;; check whether it has been incremented further (indicating another
  ;; `wait-for*` callback handler has been installed).  If it *hasn't*, since
  ;; `wait-for*` only makes sense in a tail position, this means the test is
  ;; complete, and we can call `(done)`, saving the test author the trouble of
  ;; passing `done` through every single callback.
  (let [{:keys [done] :as test-context} (update *test-context* :wait-for-depth inc)]
    (swap! (:max-wait-for-depth test-context) inc)

    (let [ok-pred   (as-callback-pred ok-ids)
          fail-pred (as-callback-pred failure-ids)
          cb-id     (gensym "wait-for-cb-fn")]
      (rf/add-post-event-callback cb-id (#?(:cljs fn :clj bound-fn) [event _]
                                          (cond (and fail-pred
                                                     (not (test/is (not (fail-pred event))
                                                                   "Received failure event")))
                                                (do
                                                  (rf/remove-post-event-callback cb-id)
                                                  (reset! (:now-waiting-for test-context) nil)
                                                  (done))

                                                (ok-pred event)
                                                (do
                                                  (rf/remove-post-event-callback cb-id)
                                                  (reset! (:now-waiting-for test-context) nil)
                                                  (binding [*test-context* test-context]
                                                    (callback event))
                                                  (when (= (:wait-for-depth test-context)
                                                           @(:max-wait-for-depth test-context))
                                                    ;; `callback` has completed with no `wait-for*`
                                                    ;; calls, so we're not waiting for anything
                                                    ;; further.  Given that `wait-for*` calls are
                                                    ;; only valid in tail position, the test must
                                                    ;; now be finished.
                                                    (done)))

                                                ;; Test is not interested this event, but we still
                                                ;; need to wait for the one we *are* interested in.
                                                :else
                                                nil)))
      (reset! (:now-waiting-for test-context) ok-ids))))


(defmacro wait-for
  "Execute `body` once an event identified by the predicate(s) `ids` has been handled.

  `ids` and `failure-ids` are means to identify an event. Normally, each would
  be a simple keyword or a set of keywords.  If an event with event-id of (or
  in) `ids` is handled, the test will continue by executing the body. If an
  event with an event-id of (or in) `failure-ids` is handled, the test will
  abort and fail.

  IMPORTANT NOTE: due to the way async tests in re-frame work, code you want
  executed after the event you're waiting for has to happen in the `body` of the
  `wait-for` (in an implicit callback), not just lexically after the the
  `wait-for` call. In practice, this means `wait-for` must always be in a tail
  position.

  Eg:
      (run-test-async
        (dispatch [:get-user 2])
        (wait-for [#{:got-user} #{:no-such-user :system-unavailable} event]
          (is (= (:username @(subscribe [:user])) \"johnny\")))
        ;; Don't put code here, it will run *before* the event you're waiting
        ;; for.
        )

  Acceptable inputs for `ids` and `failure-ids` are:
    - `:some-event-id` => matches an event with that ID

    - `#{:some-event-id :other-event-id}` => matches an event with any of the
                                             given IDs

    - `[:some-event-id :other-event-id]` => ditto (checks in order)

    - `(fn [event] ,,,) => uses the function as a predicate

    - `[(fn [event] ,,,) (fn [event] ,,,)]` => tries each predicate in turn,
                                               matching an event which matches
                                               at least one predicate

    - `#{:some-event-id (fn [event] ,,,)}` => tries each

  Note that because we're liberal about whether you supply `failure-ids` and/or
  `event-sym`, if you do choose to supply only one, and you want that one to be
  `event-sym`, you can't supply it as a destructuring form (because we can't
  disambiguate that from a vector of `failure-ids`).  You can just supply `nil`
  as `failure-ids` in this case, and then you'll be able to destructure."
  [[ids failure-ids event-sym :as argv] & body]
  (let [[failure-ids event-sym] (case (count argv)
                                  3 [failure-ids event-sym]
                                  2 (if (symbol? (second argv))
                                      [nil (second argv)]
                                      [(second argv) (gensym "event")])
                                  1 [nil (gensym "event")]
                                  0 (throw (ex-info "wait-for needs to know what to wait for!"
                                                    {})))]
   `(wait-for* ~ids ~failure-ids (fn [~event-sym] ~@body))))



;;;;
;;;; Sync tests
;;;;

(def ^{:dynamic true, :private true} *handling* false)

(defn run-test-sync* [f]
  (day8.re-frame.test/with-temp-re-frame-state
    ;; Bypass the actual re-frame EventQueue and use a local alternative over
    ;; which we have full control.
    (let [my-queue     (atom rf.int/empty-queue)
          new-dispatch (fn [argv]
                         (swap! my-queue conj argv)
                         (when-not *handling*
                           (binding [*handling* true]
                             (loop []
                               (when-let [queue-head (dequeue! my-queue)]
                                 (rf.router/dispatch-sync queue-head)
                                 (recur))))))]
      (with-redefs [rf/dispatch        new-dispatch
                    rf.router/dispatch new-dispatch]
        (f)))))


(defmacro run-test-sync
  "Execute `body` as a test, where each `dispatch` call is executed
  synchronously (via `dispatch-sync`), and any subsequent dispatches which are
  caused by that dispatch are also fully handled/executed prior to control flow
  returning to your test.

  Think of it kind of as though every `dispatch` in your app had been magically
  turned into `dispatch-sync`, and re-frame had lifted the restriction that says
  you can't call `dispatch-sync` from within an event handler.

  Note that this is *not* achieved with blocking.  It relies on you not doing
  anything asynchronous (such as an actual AJAX call or `js/setTimeout`)
  directly in your event handlers.  In a real app running in the real browser,
  of course that won't apply, so this might seem useless at first.  But if
  you're a well-behaved re-framer, all of your asynchronous stuff (which is by
  definition side-effecty) will happen in effectful event handlers installed
  with `reg-fx`.  Which works very nicely: in your tests, install an alternative
  version of those effectful event handlers which behaves synchronously.  For
  maximum coolness, you might want to consider running your tests on the JVM and
  installing a `reg-fx` handler which actually invokes your JVM Clojure
  server-side Ring handler where your in-browser code would make an AJAX call."
  [& body]
  `(run-test-sync* (fn [] ~@body)))
