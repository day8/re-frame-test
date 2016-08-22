(ns day8.re-frame.test-reframe.macros
  #?(:cljs (:require-macros day8.re-frame.test-reframe.macros))
  #?(:cljs (:require [cljs.test])))


(def ^:dynamic *captured-test-output* nil)

(defn report [m]
  (swap! *captured-test-output* (fnil conj []) m))


#?(:cljs
   (do
     (defmethod cljs.test/report [::custom :begin-test-var] [m] (report m))
     (defmethod cljs.test/report [::custom :pass]           [m] (report m))
     (defmethod cljs.test/report [::custom :fail]           [m] (report m))
     (defmethod cljs.test/report [::custom :error]          [m] (report m))
     (defmethod cljs.test/report [::custom :end-test-var]   [m] (report m))))


#?(:clj
   (defmacro assert-captured-test-results--jvm
     [assert-fn & body]
     `(do
        (clojure.test/deftest captured-test#
          ~@body)

        (let [captured-test-output# (atom [])]
          (try
            (binding [*captured-test-output* captured-test-output#
                      clojure.test/report    report]
              (captured-test#))
            (finally
              (alter-meta! (var captured-test#) dissoc :test)
              (ns-unmap (symbol (namespace `captured-test#))
                        (symbol (name `captured-test#)))))

          (~assert-fn @captured-test-output#)))))


#?(:cljs
   (defn wrap-cljs-async-test-result [test-result run-after-complete]
     (reify
       cljs.test/IAsyncTest
       cljs.core/IFn
       (-invoke [_ done]
         ;; (done) calls `(cljs.test/report {:type :end-test-var, ...})`.  Then
         ;; if there are other tests being run in the same test 'block', they'll
         ;; continue after that.  Here, because we invoke the test (below) via
         ;; `(captured-test#)`, we know that there are no other tests in the
         ;; (captured) test's block, so we can safely execute `(done)` before
         ;; continuing with some other work, without worrying that `(done)`
         ;; might trigger a whole subsequent test or three.
         (test-result (fn []
                        (done)
                        (run-after-complete)))
         ::async))))


#?(:clj
   (defmacro assert-captured-test-results--js
     [assert-fn & body]
     `(do
        (let [captured-test-output#   (atom [])
              ;; If the captured test is an async test, then our capturing test
              ;; will also have to be async. The capturing test will have to
              ;; invoke its `done` function only when the captured test's `done`
              ;; function is invoked. The capturing test puts its `done`
              ;; function in this atom so that the captured test can be made to
              ;; call it.
              capturing-test-done-fn# (atom nil)
              previous-test-env#      cljs.test/*current-env*
              ;; The complete-fn# executes the actual assertion, after cleaning
              ;; back up to remove the temporary test reporting capture
              ;; mechanism.  This happens either just after the test (for a
              ;; non-async test), or in the `done` continuation (for an async
              ;; test).
              complete-fn#            (fn []
                                        (set! *captured-test-output* nil)
                                        (set! cljs.test/*current-env* previous-test-env#)
                                        (~assert-fn @captured-test-output#))]

          (cljs.test/deftest captured-test#
            (let [test-result# (do ~@body)]
              (if (cljs.test/async? test-result#)
                ;; The `done` continuation invoked here finishes the capturing
                ;; test, and will then continue on to execute all the other
                ;; tests that the user has asked for.  So anything we want to do
                ;; on completion of the captured test has to be done before the
                ;; capturing test's `done` is invoked.
                (wrap-cljs-async-test-result test-result# (fn []
                                                            (complete-fn#)
                                                            (@capturing-test-done-fn#)))
                ::not-async)))

          (try
            (set! cljs.test/*current-env* (assoc previous-test-env# :reporter ::custom))
            (set! *captured-test-output* captured-test-output#)

            (let [result# (captured-test#)]
              (if (= ::async result#)
                ;; If it's an async test, then the `complete-fn#` will get
                ;; called in the captured test's `done` continuation, thanks to
                ;; the use of `wrap-cljs-async-test-result` in the `deftest`
                ;; itself (just above), so we don't call it here.
                (cljs.test/async done#
                  (reset! capturing-test-done-fn# done#))
                (complete-fn#)))
            (finally
              ;; `ns-unmap` on ClojureScript is a macro which can only take quoted
              ;; symbols, so we can't do the same in JS as we do on the JVM.
              (alter-meta! captured-test# dissoc :test)))))))


#?(:clj
   (defmacro assert-captured-test-results
     "Execute `body` as a test within a context where `clojure.test` or `cljs.test`
     will use a reporting function that -- instead of reporting test success or
     failure as per normal -- will just capture (and subsequently return) the calls
     made to the report function, for subsequent inspection.

     Used in the tests with a `body` representing a test we might expect a user
     of this library to write, to assert that running that test produces the
     correct output from clojure.test.

     Note that test which captures an async test in JS will correctly
     macro-expand into a `cljs.test/async` test, but as a result, such a test will
     need to be in a tail position.

     Don't forget to say hi to the turtles on the way down..."
     [assert-fn & body]
     (let [cljs-env? (boolean (:ns &env))]
       (if cljs-env?
         `(assert-captured-test-results--js ~assert-fn ~@body)
         `(assert-captured-test-results--jvm ~assert-fn ~@body)))))
