(ns day8.re-frame.test-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [go <!! <! >! chan]]
            [day8.re-frame.test :as t]
            [re-frame.core :as r]
            [clojure.tools.logging :as log]))

;; async

(deftest async-success-test
  (t/async done
    (future (Thread/sleep 1000)
            (is (= 1 1) "Should pass")
            (done))))

(deftest async-fail-test
  (t/async done
    (future (Thread/sleep 1000)
            (is (= 1 2) "Should fail")
            (done))))

(deftest async-error-test
  (t/async done
    (future (Thread/sleep 1000)
            (is (throw (ex-info "Test error" {})) "Should error")
            (done))))

;; wait-for

(deftest wait-for-test
  (t/async done
    (t/wait-for :success :failure
                (fn [ev]
                  (is (= {:user-id 1} @(r/subscribe [:user])) "Should pass")
                  (done))
                done)
    (r/dispatch [:get-user 1])))

(deftest wait-for-test-failure
  (binding [t/fail-message "Should fail"]
    (t/async done
      (t/wait-for :success :failure
                  (fn [ev]
                    (is false "Shouldn't get here")
                    (done))
                  done)
      (r/dispatch [:get-user-fail 1]))))


(deftest wait-for-test-ch
  (t/async done
    (go
      (r/dispatch [:get-user 5])
      (let [[ev & rest] (<! (t/wait-for-ch [:success :failure]))]
        (if (= :success ev)
          (is (= {:user-id 5} @(r/subscribe [:user])) "Should pass")
          (is false "Should pass")))
      (done))))

;; testing setup

(use-fixtures :once (fn [f]
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
                                   (get db :user)))
                      (f)))

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
(defn test-ns-hook []
  (binding [original-report report
            report custom-report]
    (test-all-vars (find-ns 'day8.re-frame.test-test))))
