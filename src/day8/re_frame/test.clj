(ns day8.re-frame.test
  (:require [re-frame.core :as r]
            [clojure.test :as t]))

(defmacro async
  [done & body]
  `(let [p# (promise)
         ~done #(deliver p# nil)]
     ~@body
     (deref p#)))

(def ^:dynamic default-wait-timeout-ms 5000)

(defn wait-for
  ([ids cb]
   (wait-for ids #{} cb default-wait-timeout-ms))
  ([ids failure-ids cb]
   (wait-for ids failure-ids cb default-wait-timeout-ms))
  ([ids failure-ids cb timeout-ms]
   (let [ok-set (if (coll? ids) (set ids) (hash-set ids))
         fail-set (if (coll? failure-ids) (set failure-ids) (hash-set failure-ids))]
     (r/add-post-event-callback
       (fn listener [new-event _]
         (let [new-id (first new-event)]
           (when (get fail-set new-id)
             (r/remove-post-event-callback listener)
             (t/do-report
               {:type     :fail
                :message  "wait-for-event: didn't get expected event."
                :expected ok-set
                :actual   new-event}))

           (when (get ok-set new-id)
             (r/remove-post-event-callback listener)
             (cb new-event))))))))

(defn wait-for-ch
  [])
