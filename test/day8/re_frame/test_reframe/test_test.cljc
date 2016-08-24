(ns day8.re-frame.test-reframe.test-test
  (:require #?(:cljs [cljs.test :refer-macros [deftest is]]
               :clj  [clojure.test :refer [deftest is]])
            #?(:cljs [day8.re-frame.test-reframe.macros :refer-macros [assert-captured-test-results]]
               :clj  [day8.re-frame.test-reframe.macros :refer [assert-captured-test-results]])
            [day8.re-frame.test :as rf-test]
            [re-frame.core :as rf]
            re-frame.db
            re-frame.registrar))


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



;; In the tests below, the `assert-captured-test-results` macro takes the first
;; argument (a function) and calls it with the captured test results of running
;; the remainder of the macro call as the body of a `deftest` in a re-frame
;; application's test code.  Our tests here are asserting against the expected
;; test reports that the user would see from their tests.


;;;;
;;;; Sync tests
;;;;

(deftest run-test-sync--basic-flow
  (assert-captured-test-results
   (fn [results]
     (is (= (map #(select-keys % [:type :expected]) results)
            [{:type :begin-test-var}
             {:type :pass, :expected '(= "world" @hello-reaction)}
             {:type :fail, :expected '(= "nope" (:goodbye @db-reaction))} ; Failure noted below.
             {:type :pass, :expected '(= "world" @hello-reaction)}
             {:type :pass, :expected '(= {:hello "world", :goodbye "bugs"} @db-reaction)}
             {:type :end-test-var}])))

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
        (is (= {:hello "world", :goodbye "bugs"} @db-reaction)))))))


(deftest run-test-sync--event-handler-dispatches-event
  (assert-captured-test-results
   (fn [results]
     (is (= (map #(select-keys % [:type :expected]) results)
            [{:type :begin-test-var}
             {:type :pass, :expected '(= true @success)}
             {:type :end-test-var}])))

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
        (is (= true @success)))))))


(deftest run-test-sync--error-in-event-handler
  (assert-captured-test-results
   (fn [results]
     (is (= (map #(select-keys % [:type :expected]) results)
            [{:type :begin-test-var}
             {:type :pass, :expected '(= true (boolean "Did get here."))}
             {:type :error, :expected nil}
             {:type :end-test-var}])))

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
      (rf/dispatch [:do-stuff])         ; Synchronously explodes.
      (is (= true (boolean "Not gonna get here...")))))))


;;;;
;;;; Async tests
;;;;

(deftest run-test-async--basic-flow
  (assert-captured-test-results
   (fn [results]
     (is (= (map #(select-keys % [:type :expected]) results)
            [{:type :begin-test-var}
             {:type :pass, :expected '(= "world" @hello-reaction)}
             {:type :fail, :expected '(= "nope" (:goodbye @db-reaction))} ; Failure noted above.
             {:type :pass, :expected '(= "world" @hello-reaction)}
             {:type :pass, :expected '(= {:hello "world", :goodbye "bugs"} @db-reaction)}
             ;; This "not timeout" is always reported on the JVM for passing
             ;; async tests.
             #?(:clj {:type :pass, :expected '(not= ::rf-test/timeout result)})
             {:type :end-test-var}])))

   (rf-test/run-test-async
    (rf/reg-event-db :initialise-db (fn [_ _] {:hello "world"}))
    (rf/reg-event-db :update-db (fn [db [_ arg]] (assoc db :goodbye arg)))
    (rf/reg-sub :hello-sub (fn [db _] (:hello db)))
    (rf/reg-sub :db-sub (fn [db _] db))

    (let [hello-reaction (rf/subscribe [:hello-sub])
          db-reaction    (rf/subscribe [:db-sub])]
      (rf/dispatch [:initialise-db])
      (rf-test/wait-for [:initialise-db]
        (is (= "world" @hello-reaction))
        (is (= "nope" (:goodbye @db-reaction))) ; Not true, reports failure.

        (rf/dispatch [:update-db "bugs"])
        (rf-test/wait-for [:update-db]
          (is (= "world" @hello-reaction))
          (is (= {:hello "world", :goodbye "bugs"} @db-reaction))))))))


(deftest run-test-async--with-actual-asynchrony
  (assert-captured-test-results
   (fn [results]
     (is (= (map #(select-keys % [:type :expected]) results)
            [{:type :begin-test-var}
             {:type :pass, :expected '(= :continue event-id)}
             {:type :pass, :expected '(= "event-arg" event-arg)}
             {:type :pass, :expected '(= true @success)}
             #?(:clj {:type :pass, :expected '(not= ::rf-test/timeout result)})
             {:type :end-test-var}])))

   (rf-test/run-test-async
    (rf/reg-event-ctx :async
      (fn [ctx]
        #?(:cljs (js/setTimeout #(rf/dispatch [:continue "event-arg"])
                                50)
           :clj  (.start (Thread. #(do
                                     (Thread/sleep 50)
                                     (rf/dispatch [:continue "event-arg"])))))
        ctx))
    (rf/reg-event-db :continue (fn [db _]
                                 (assoc db :success true)))

    (rf/reg-sub :success (fn [db _] (:success db)))

    (let [success (rf/subscribe [:success])]
      (rf/dispatch [:async])
      ;; Additionally tests the event binding form.
      (rf-test/wait-for [:continue nil [event-id event-arg]]
        (is (= :continue event-id))
        (is (= "event-arg" event-arg))
        (is (= true @success)))))))


;; JVM-only because `assert-captured-test-results` is complicated enough as it
;; is, without additionally handling async test timeouts in JS!
#?(:clj
   (deftest run-test-async--test-times-out
     (assert-captured-test-results
      (fn [results]
        (is (= (map #(select-keys % [:type :expected]) results)
               [{:type :begin-test-var}
                {:type :pass, :expected '(= true (boolean "Did get here."))}
                #?(:clj {:type :fail, :expected '(not= ::rf-test/timeout result)})
                {:type :end-test-var}])))

      (binding [rf-test/*test-timeout* 100]
        (rf-test/run-test-async
         ;; As per previous test, but :async event doesn't actually
         ;; ever cause :continue to be dispatched, so we time out
         ;; waiting.
         (rf/reg-event-ctx :async (fn [ctx] ctx))
         (rf/reg-event-db :continue (fn [db _]
                                      (assoc db :success true)))

         (rf/dispatch [:async])
         (is (= true (boolean "Did get here.")))
         (rf-test/wait-for [:continue]
           (is (= true (boolean "Not gonna get here...")))))))))


(deftest run-test-async--test-dispatches-failure-event
  (assert-captured-test-results
   (fn [results]
     (is (= (map #(select-keys % [:type :expected]) results)
            [{:type :begin-test-var}
             {:type :pass, :expected '(not (fail-pred event))} ; The :async event.
             {:type :fail, :expected '(not (fail-pred event))} ; The :stop event.
             #?(:clj {:type :pass, :expected '(not= ::rf-test/timeout result)})
             {:type :end-test-var}])))

   (rf-test/run-test-async
    (rf/reg-event-ctx :async
      (fn [ctx]
        #?(:cljs (js/setTimeout #(rf/dispatch [:stop]) 50)
           :clj  (.start (Thread. #(do
                                     (Thread/sleep 50)
                                     (rf/dispatch [:stop])))))
        ctx))

    (rf/dispatch [:async])
    (rf-test/wait-for [:continue :stop]
      (is (= true "Not gonna get here..."))))))


;; JVM-only because in JS, the error will not be captured and will terminate the
;; test run.
#?(:clj
   (deftest run-test-async--error-in-event-handler
     (assert-captured-test-results
      (fn [results]
        (is (= (map #(select-keys % [:type :expected]) results)
               ;; Note you don't actually get the error from the event handler here,
               ;; even though it threw an exception. It'll get printed to the console,
               ;; but since it happens in a different thread, from the test's point of
               ;; view, all you see is a timeout.  This is one advantage of
               ;; synchronous tests.
               [{:type :begin-test-var}
                {:type :fail, :expected '(not= ::rf-test/timeout result)}
                {:type :end-test-var}])))

      (binding [rf-test/*test-timeout* 100]
        (rf-test/run-test-async
         (rf/reg-event-ctx :async
           (fn [ctx]
             #?(:cljs (js/setTimeout #(rf/dispatch [:continue]) 50)
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

         (rf/dispatch [:async])
         (rf-test/wait-for [:continue nil event]
           (is (= [:continue] event))))))))
