(ns day8.re-frame.test-test
  (:require [day8.re-frame.test :as t]
            [clojure.test :refer :all]))

(deftest async-success-test
  (t/async done
    (.run (Thread. (fn [] (println "Starting thread")
                       (Thread/sleep 2000)
                       (println "Finished thread")
                       (is (= 1 1)))))
    (done)))

(deftest async-failure-test
  (t/async done
    (.run (Thread. (fn [] (println "Starting thread")
                       (Thread/sleep 2000)
                       (println "Finished thread")
                       (is (= 1 2)))))
    (done)))
