(ns day8.re-frame.test-reframe.test-test
  (:require #?(:cljs [cljs.test :refer-macros [deftest is]]
               :clj  [clojure.test :refer [deftest is]])
            [day8.re-frame.test :as rf-test]
            [re-frame.core :as rf]
            re-frame.db
            re-frame.registrar))

#?(:cljs (enable-console-print!))


(deftest temp-re-frame-state
  ;; If only there were some sort of macro we could use to handle this cleanup
  ;; for us and prevent our various tests from interfering with one another via
  ;; their side effects...
  (letfn [(cleanup! []
            (reset! re-frame.db/app-db {})
            (re-frame.subs/clear-all-handlers!)
            (rf/clear-fx :do-ajax)
            (rf/clear-event :inc)
            (rf/clear-event :ajax))]
    (cleanup!)
    (try
      (let [ajax (atom 0)]
        ;; Set up common handlers for the whole test.
        (rf/reg-event-db :inc (fn do-inc [db _] (update db :counter (fnil inc 0))))
        (rf/reg-fx :do-ajax (fn [_] (swap! ajax inc)))
        (rf/reg-event-fx :ajax (fn [_ _] {:do-ajax nil}))

        (rf/reg-sub :counter (fn [db _] (:counter db)))
        (rf/reg-sub :dead (fn [db _] (or (:dead db) false)))

        (let [counter (rf/subscribe [:counter])
              dead    (rf/subscribe [:dead])]
          ;; Do some work prior to snapshotting the state, so we have something
          ;; other than {} to restore.
          (is (= nil @counter))
          (rf/dispatch-sync [:inc])
          (is (= 1 @counter))

          (rf-test/with-temp-re-frame-state ; BEGIN TRANSACTION
            (rf/dispatch-sync [:inc])
            (is (= 2 @counter))

            (rf/dispatch-sync [:ajax])
            (is (= 1 @ajax))

            (rf/reg-event-db :die (fn [db _] (assoc db :dead true)))
            (rf/dispatch-sync [:die])
            (is (= true @dead)))        ; ROLLBACK TRANSACTION

          (is (= 1 @counter))
          (rf/dispatch-sync [:inc])
          (is (= 2 @counter))

          (rf/dispatch-sync [:ajax])
          (is (= 2 @ajax))

          (is (= false @dead))
          ;; This event handler no longer exists, so this should be a no-op.
          (rf/dispatch-sync [:die])
          (is (= false @dead))))
      (finally
        (cleanup!)))))



(defmacro with-captured-test-report
  "Execute `body` as a test within a context where `clojure.test` or `cljs.test`
  will use a reporting function that -- instead of reporting test success or
  failure as per normal -- will just capture (and subsequently return) the calls
  made to the report function, for subsequent inspection.

  Used in the tests below with a `body` representing a test we might expect a
  user of this library to write, to assert that running that test produces the
  correct output from clojure.test.

  Don't forget to say hi to the turtles on the way down..."
  [& body]
  (let [cljs-env?        (boolean (:ns &env))
        report-fn-gensym (gensym "report-fn")]
    `(let [test-results-atom# (atom [])
           ~report-fn-gensym  (fn [m#] (swap! test-results-atom# conj m#))]
       (deftest captured-test#
         ~@body)

       (binding ~(if cljs-env?
                   ['cljs.test/*current-env* (list 'assoc 'cljs.test/*current-env*
                                                   :report report-fn-gensym)]
                   ['clojure.test/report report-fn-gensym])
         (~(if cljs-env?
             'cljs.test/test-var
             'clojure.test/test-var)
          (var captured-test#)))

       (alter-meta! #'captured-test# dissoc :test)
       (ns-unmap *ns* (symbol (name 'captured-test#)))

       (deref test-results-atom#))))



;; To read the tests below: imagine that the contents of the
;; `with-captured-test-report` macro is the body of a `deftest` in a re-frame
;; application's test code.  Our tests here are asserting against the expected
;; test reports that the user would see from their tests.


;;;;
;;;; Sync tests
;;;;

(deftest run-test-sync--basic-flow
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-db :initialise-db (fn [_ _] {:hello "world"}))
                  (rf/reg-event-db :update-db (fn [db [_ arg]] (assoc db :goodbye arg)))
                  (rf/reg-sub :hello-sub (fn [db _] (:hello db)))
                  (rf/reg-sub :db-sub (fn [db _] db))

                  (let [hello-reaction (rf/subscribe [:hello-sub])
                        db-reaction    (rf/subscribe [:db-sub])]

                    (rf-test/run-test-sync
                     (rf/dispatch [:initialise-db])
                     (is (= "world" @hello-reaction))
                     (is (= "nope" (:goodbye @db-reaction))) ; Not true, reports failure.

                     (rf/dispatch [:update-db "bugs"])
                     (is (= "world" @hello-reaction))
                     (is (= {:hello "world", :goodbye "bugs"} @db-reaction))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(= "world" @hello-reaction)}
          {:type :fail, :expected '(= "nope" (:goodbye @db-reaction))} ; Failure noted above.
          {:type :pass, :expected '(= "world" @hello-reaction)}
          {:type :pass, :expected '(= {:hello "world", :goodbye "bugs"} @db-reaction)}
          {:type :end-test-var}])))


(deftest run-test-sync--event-handler-dispatches-event
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-ctx :do-stuff
                    (fn [ctx]
                      ;; In the real world, you should use effectful event
                      ;; handlers to `dispatch` as a result of an event, but the
                      ;; net result here is the same.
                      (rf/dispatch [:do-more-stuff])
                      ctx))
                  (rf/reg-event-db :do-more-stuff
                    (fn [db _] (assoc db :success true)))

                  (rf/reg-sub :success (fn [db _] (:success db)))

                  (let [success (rf/subscribe [:success])]
                    (rf-test/run-test-sync
                     (rf/dispatch [:do-stuff])
                     (is (= true @success))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(= true @success)}
          {:type :end-test-var}])))


(deftest run-test-sync--error-in-event-handler
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-ctx :do-stuff
                    (fn [ctx]
                      (rf/dispatch [:do-more-stuff])
                      ctx))
                  (rf/reg-event-db :do-more-stuff
                    (fn [db _]
                      (throw (ex-info "Whoops!" {:expected true}))))

                  (rf-test/run-test-sync
                   (is (= true (boolean "Did get here.")))
                   (rf/dispatch [:do-stuff]) ; Synchronously explodes.
                   (is (= true (boolean "Not gonna get here..."))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(= true (boolean "Did get here."))}
          {:type :error, :expected nil}
          {:type :end-test-var}])))


;;;;
;;;; Async tests
;;;;

(deftest run-test-async--basic-flow
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-db :initialise-db (fn [_ _] {:hello "world"}))
                  (rf/reg-event-db :update-db (fn [db [_ arg]] (assoc db :goodbye arg)))
                  (rf/reg-sub :hello-sub (fn [db _] (:hello db)))
                  (rf/reg-sub :db-sub (fn [db _] db))

                  (let [hello-reaction (rf/subscribe [:hello-sub])
                        db-reaction    (rf/subscribe [:db-sub])]

                    (rf-test/run-test-async
                     (rf/dispatch [:initialise-db])
                     (rf-test/wait-for [:initialise-db]
                       (is (= "world" @hello-reaction))
                       (is (= "nope" (:goodbye @db-reaction))) ; Not true, reports failure.

                       (rf/dispatch [:update-db "bugs"])
                       (rf-test/wait-for [:update-db]
                         (is (= "world" @hello-reaction))
                         (is (= {:hello "world", :goodbye "bugs"} @db-reaction))))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(= "world" @hello-reaction)}
          {:type :fail, :expected '(= "nope" (:goodbye @db-reaction))} ; Failure noted above.
          {:type :pass, :expected '(= "world" @hello-reaction)}
          {:type :pass, :expected '(= {:hello "world", :goodbye "bugs"} @db-reaction)}
          ;; This "not timeout" is always reported for passing async tests.
          {:type :pass, :expected '(not= ::rf-test/timeout result)}
          {:type :end-test-var}])))


(deftest run-test-async--with-actual-asynchrony
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-ctx :async
                    (fn [ctx]
                      #?(:cljs (js/setTimeout 50 #(rf/dispatch [:continue "event-arg"]))
                         :clj  (.start (Thread. #(do
                                                   (Thread/sleep 50)
                                                   (rf/dispatch [:continue "event-arg"])))))
                      ctx))
                  (rf/reg-event-db :continue (fn [db _]
                                               (assoc db :success true)))

                  (rf/reg-sub :success (fn [db _] (:success db)))

                  (let [success (rf/subscribe [:success])]
                    (rf-test/run-test-async
                     (rf/dispatch [:async])
                     ;; Additionally tests the event binding form.
                     (rf-test/wait-for [:continue nil [event-id event-arg]]
                       (is (= :continue event-id))
                       (is (= "event-arg" event-arg))
                       (is (= true @success)))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(= :continue event-id)}
          {:type :pass, :expected '(= "event-arg" event-arg)}
          {:type :pass, :expected '(= true @success)}
          {:type :pass, :expected '(not= ::rf-test/timeout result)}
          {:type :end-test-var}])))


(deftest run-test-async--test-times-out
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  ;; As per previous test, but :async event doesn't actually
                  ;; ever cause :continue to be dispatched, so we time out
                  ;; waiting.
                  (rf/reg-event-ctx :async (fn [ctx] ctx))
                  (rf/reg-event-db :continue (fn [db _]
                                               (assoc db :success true)))

                  (binding [rf-test/*test-timeout* 100]
                    (rf-test/run-test-async
                     (rf/dispatch [:async])
                     (is (= true (boolean "Did get here.")))
                     (rf-test/wait-for [:continue]
                       (is (= true (boolean "Not gonna get here..."))))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(= true (boolean "Did get here."))}
          {:type :fail, :expected '(not= ::rf-test/timeout result)}
          {:type :end-test-var}])))


(deftest run-test-async--test-dispatches-failure-event
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-ctx :async
                    (fn [ctx]
                      #?(:cljs (js/setTimeout 50 #(rf/dispatch [:stop]))
                         :clj  (.start (Thread. #(do
                                                   (Thread/sleep 50)
                                                   (rf/dispatch [:stop])))))
                      ctx))

                  (rf-test/run-test-async
                   (rf/dispatch [:async])
                   (rf-test/wait-for [:continue :stop]
                     (is (= true "Not gonna get here..."))))))
              (map #(select-keys % [:type :expected])))

         [{:type :begin-test-var}
          {:type :pass, :expected '(not (fail-pred event))} ; The :async event.
          {:type :fail, :expected '(not (fail-pred event))} ; The :stop event.
          {:type :pass, :expected '(not= ::rf-test/timeout result)}
          {:type :end-test-var}])))


(deftest run-test-async--error-in-event-handler
  (is (= (->> (with-captured-test-report
                (rf-test/with-temp-re-frame-state
                  (rf/reg-event-ctx :async
                    (fn [ctx]
                      #?(:cljs (js/setTimeout 50 #(rf/dispatch [:continue]))
                         :clj  (.start (Thread. #(do
                                                   (Thread/sleep 50)
                                                   (rf/dispatch [:continue])))))
                      ctx))
                  (rf/reg-event-db :continue
                    (fn [db _]
                      (throw (ex-info (str "Whoops!  (Not really, we threw this exception "
                                           "deliberately for testing purposes, and the fact that "
                                           "you're seeing it here on the console doesn't actually "
                                           "indicate a test failure.)")
                                      {:foo :bar}))))

                  (binding [rf-test/*test-timeout* 100]
                    (rf-test/run-test-async
                     (rf/dispatch [:async])
                     (rf-test/wait-for [:continue nil event]
                       (is (= [:continue] event)))))))
              (map #(select-keys % [:type :expected])))

         ;; Note you don't actually get the error from the event handler here,
         ;; even though it threw an exception. It'll get printed to the console,
         ;; but since it happens in a different thread, from the test's point of
         ;; view, all you see is a timeout.  This is one advantage of
         ;; synchronous tests.
         [{:type :begin-test-var}
          {:type :fail, :expected '(not= ::rf-test/timeout result)}
          {:type :end-test-var}])))
