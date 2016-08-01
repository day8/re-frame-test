(ns day8.re-frame.test
  (:require [re-frame.core :as r]
            #?(:clj
            [clojure.core.async :as async]
               :cljs [cljs.core.async :as async])
    #?(:clj
            [clojure.test :as t]
               :cljs
               [cljs.test :as t])))

(defmacro async
  [done & body]
  `(let [p# (promise)
         ~done #(deliver p# nil)]
     ~@body
     (deref p#)))

(def ^:dynamic default-wait-timeout-ms 5000)

(defn normalize-ids
  "Convert an id or a collection of ids into a set of ids"
  [ids]
  (if (coll? ids)
    (set ids)
    (hash-set ids)))

(def ^:dynamic fail-message "wait-for-event: didn't get expected event.")

(defn wait-for
  ([ids failure-ids cb done]
   (let [ok-set (if (coll? ids) (set ids) (hash-set ids))
         fail-set (if (coll? failure-ids) (set failure-ids) (hash-set failure-ids))
         cb-id (gensym "wait-for-cb-fn")]
     (r/add-post-event-callback
       cb-id
       ;; Need this to pass test report bindings into new thread.
       (#?(:clj bound-fn :cljs fn)
         [new-event _]
         (let [new-id (first new-event)]
           (when (get fail-set new-id)
             (r/remove-post-event-callback cb-id)
             (t/do-report
               {:type     :fail
                :message  fail-message
                :expected ok-set
                :actual   new-event})
             (done))

           (when (get ok-set new-id)
             (r/remove-post-event-callback cb-id)
             (cb new-event))))))))

(defn wait-for-ch
  [ids]
  (let [id-set (normalize-ids ids)
        ch (async/chan)
        cb-id (gensym "wait-for-ch-cb-fn")]
    (r/add-post-event-callback
      cb-id
      (fn listener [new-event _]
        (let [new-id (first new-event)]
          (when (get id-set new-id)
            (async/put! ch new-event)
            (async/close! ch)
            (r/remove-post-event-callback cb-id)))))
    ch))
