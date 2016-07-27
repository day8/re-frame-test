(ns day8.re-frame.test-test
  (:require [day8.re-frame.test :as t]
            [re-frame.core :as r]
            [clojure.test :refer :all]))

(use-fixtures :once (fn [f]
                      (r/reg-event :get-user
                        (fn [db event]
                          (future
                            (Thread/sleep 500)
                            (r/dispatch [:success {:user-id 1}]))
                          db))
                      (r/reg-event :success
                        (fn [db [_ user-details]]
                          (assoc db :user user-details)))
                      (r/reg-sub :user
                        (fn [db _]
                          (get db :user)))
                      (f)))

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
    (t/wait-for :success (fn [ev]
                           (println @(r/subscribe [:user]))
                           (is (= {:user-id 1} @(r/subscribe [:user])))
                           (done)))
    (r/dispatch [:get-user])))


;; testing setup

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
