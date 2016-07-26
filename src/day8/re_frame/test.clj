(ns day8.re-frame.test)

(defmacro async
  [done & body]
  `(let [p# (promise)
         ~done #(deliver p# nil)]
     ~@body
     (deref p#)))
