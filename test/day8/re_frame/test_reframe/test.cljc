(ns day8.re-frame.test-reframe.test
  #?(:clj
     (:require [clojure.test :refer [deftest is use-fixtures]]
               [clojure.core.async :refer [go <! >! chan]]
               [day8.re-frame.test :as t :refer [async]]
               [re-frame.core :as r])
     :cljs
     (:require [cljs.test :refer-macros [async deftest is use-fixtures]]
       [cljs.core.async :refer [<! >! chan]]
       [day8.re-frame.test :as t]
       [re-frame.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

;; async

#?(:cljs (enable-console-print!))

(deftest async-success-test
  (async done
    #?(:clj  (future (Thread/sleep 1000)
                     (is (= 1 1) "Should pass")
                     (done))
       :cljs (js/setTimeout (fn []
                              (is (= 1 1) "Should pass")
                              (done))
                            200))))

(deftest async-fail-test
  (async done
    #?(:clj  (future (Thread/sleep 1000)
                     (is (= 1 2) "Should fail")
                     (done))

       ;; Not sure how to test failing tests in CLJS
       :cljs (done) #_(js/setTimeout (fn []
                                  (is (= 1 2) "Should fail")
                                  (done))))))

;; Not sure how to test failing tests in CLJS
#?(:clj (deftest async-error-test
          (async done
            (future (Thread/sleep 1000)
                    (is (throw (ex-info "Test error" {})) "Should error")
                    (done)))))

;; wait-for

(deftest wait-for-test
  (async done
    (t/wait-for :success :failure
                (fn [ev]
                  (is (= {:user-id 1} @(r/subscribe [:user])) "Should pass")
                  (done))
                done)
    (r/dispatch [:get-user 1])))

#?(:clj
   (deftest wait-for-test-failure
     (binding [t/fail-message "Should fail"]
       (async done
         (t/wait-for :success :failure
                     (fn [ev]
                       (is false "Shouldn't get here")
                       (done))
                     done)
         (r/dispatch [:get-user-fail 1])))))


(deftest wait-for-test-ch
  (async done
    (go
      (r/dispatch [:get-user 5])
      (let [[ev & rest] (<! (t/wait-for-ch [:success :failure]))]
        (if (= :success ev)
          (is (= {:user-id 5} @(r/subscribe [:user])) "Should pass")
          (is false "Should pass")))
      (done))))

;; testing setup

(do
  (r/reg-event :get-user
               (fn [db [_ user-id]]
                 (r/dispatch [:success {:user-id user-id}])
                 #_(future
                     (Thread/sleep 500)
                     (r/dispatch [:success {:user-id user-id}]))
                 db))
  (r/reg-event :get-user-fail
               (fn [db [_ user-id]]
                 (r/dispatch [:failure nil])
                 #_(future
                     (Thread/sleep 500)
                     (r/dispatch [:success {:user-id user-id}]))
                 db))
  (r/reg-event :success
               (fn [db [_ user-details]]
                 (assoc db :user user-details)))
  (r/reg-event :failure
               (fn [db _]
                 (assoc db :failure true)))
  (r/reg-sub :user
             (fn [db _]
               (get db :user))))

(defn setup-reframe []
  (r/reg-event :get-user
               (fn [db [_ user-id]]
                 (r/dispatch [:success {:user-id user-id}])
                 #_(future
                     (Thread/sleep 500)
                     (r/dispatch [:success {:user-id user-id}]))
                 db))
  (r/reg-event :get-user-fail
               (fn [db [_ user-id]]
                 (r/dispatch [:failure nil])
                 #_(future
                     (Thread/sleep 500)
                     (r/dispatch [:success {:user-id user-id}]))
                 db))
  (r/reg-event :success
               (fn [db [_ user-details]]
                 (assoc db :user user-details)))
  (r/reg-event :failure
               (fn [db _]
                 (assoc db :failure true)))
  (r/reg-sub :user
             (fn [db _]
               (get db :user))))

(use-fixtures :once #?(:clj  (fn [f]
                               (setup-reframe)
                               (f))
                       :cljs {:before setup-reframe}))

(declare ^:dynamic original-report)

(defn custom-report [data]
  (let [event (:type data)
        msg (:message data)
        expected (:expected data)
        actual (:actual data)
        passed (cond
                 (= event :fail) (= msg "Should fail")
                 (= event :pass) (= msg "Should pass")
                 (= event :error) (= msg "Should error")
                 :else true)]
    (if passed
      (original-report {:type     :pass, :message msg,
                        :expected expected, :actual actual})
      (original-report {:type     :fail, :message (str msg " but got " event)
                        :expected expected, :actual actual}))))

;; test-ns-hook will be used by test/test-ns to run tests in this
;; namespace.
#?(:clj (defn test-ns-hook []
          (binding [original-report clojure.test/report
                    clojure.test/report custom-report]
            (clojure.test/test-all-vars (find-ns 'day8.re-frame.test-reframe.test)))))

#_(:cljs (defn test-ns-hook []
           (binding [original-report cljs.test/report
                     cljs.test/report custom-report]
             (cljs.test/test-all-vars 'day8.re-frame.test-reframe.test))))
