(ns day8.re-frame.test-reframe.macros
  #?(:cljs (:require-macros day8.re-frame.test-reframe.macros))
  #?(:cljs (:require [cljs.test])))


(def ^:dynamic *test-results* nil)

(defn report [m]
  (swap! *test-results* conj m))


#?(:cljs
   (do
     (defmethod cljs.test/report [::custom :begin-test-var] [m] (report m))
     (defmethod cljs.test/report [::custom :pass]           [m] (report m))
     (defmethod cljs.test/report [::custom :fail]           [m] (report m))
     (defmethod cljs.test/report [::custom :error]          [m] (report m))
     (defmethod cljs.test/report [::custom :end-test-var]   [m] (report m))))


#?(:clj
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
     (let [cljs-env?            (boolean (:ns &env))
           captured-test-gensym (gensym "captured-test")]
       `(let [test-results-atom# (atom [])]
          (~(if cljs-env? 'cljs.test/deftest 'clojure.test/deftest) ~captured-test-gensym
           ~@body)

          (binding [*test-results* test-results-atom#]
            (binding ~(if cljs-env?
                        ['cljs.test/*current-env* (list 'assoc 'cljs.test/*current-env*
                                                        :reporter ::custom)]
                        ['clojure.test/report `report])
              (~(if cljs-env?
                  'cljs.test/test-var
                  'clojure.test/test-var)
               (var ~captured-test-gensym))))

          (alter-meta! ~captured-test-gensym dissoc :test)

          ;; `ns-unmap` on ClojureScript is a macro which can only take quoted
          ;; symbols.
          ~(when-not cljs-env?
             `(ns-unmap (symbol (namespace ~captured-test-gensym))
                        (symbol (name ~captured-test-gensym))))

          (deref test-results-atom#)))))
