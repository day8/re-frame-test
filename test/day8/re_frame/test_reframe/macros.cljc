(ns day8.re-frame.test-reframe.macros
  #?(:cljs (:require-macros day8.re-frame.test-reframe.macros))
  #?(:cljs (:require [cljs.test])))


(def ^:dynamic *test-output* nil)

(defn report [m]
  (swap! *test-output* conj m))


#?(:cljs
   (do
     (defmethod cljs.test/report [::custom :begin-test-var] [m] (report m))
     (defmethod cljs.test/report [::custom :pass]           [m] (report m))
     (defmethod cljs.test/report [::custom :fail]           [m] (report m))
     (defmethod cljs.test/report [::custom :error]          [m] (report m))
     (defmethod cljs.test/report [::custom :end-test-var]   [m] (report m))))


#?(:clj
   (defmacro assert-captured-test-results--jvm
     [assert-fn & body]
     `(do
        (clojure.test/deftest captured-test#
          ~@body)

        (let [test-output# (atom [])]
          (try
            (binding [*test-output*       test-output#
                      clojure.test/report report]
              (captured-test#))
            (finally
              (alter-meta! (var captured-test#) dissoc :test)
              (ns-unmap (symbol (namespace `captured-test#))
                        (symbol (name `captured-test#)))))

          (~assert-fn @test-output#)))))


#?(:cljs
   (defn wrap-cljs-async-test-result [test-result run-when-complete]
     (reify
       cljs.test/IAsyncTest
       cljs.core/IFn
       (-invoke [_ done]
         (test-result (fn []
                        (run-when-complete)
                        (done)))
         ::async))))


#?(:clj
   (defmacro assert-captured-test-results--js
     [assert-fn & body]
     `(do
        (let [test-output# (atom [])
              ;; The complete-fn# executes the actual assertion, after cleaning
              ;; back up to remove the temporary test reporting capture
              ;; mechanism.  This happens either just after the test (for a
              ;; non-async test), or in the `(done)` callback (for an async
              ;; test).
              complete-fn# (fn []
                             (set! *test-output* nil)
                             (set! cljs.test/*current-env* previous-env#)
                             (~assert-fn @test-output#))]

          (cljs.test/deftest captured-test#
            (let [test-result# (do ~@body)]
              (if (cljs.test/async? test-result#)
                (wrap-cljs-async-test-result test-result# complete-fn#)
                ::not-async)))

          (try
            (let [previous-env# cljs.test/*current-env*]
              (set! cljs.test/*current-env* (assoc previous-env# :reporter ::custom))
              (set! *test-output* test-output#)

              (let [result# (captured-test#)]
                ;; If it's an async test, then the `complete-fn#` will get
                ;; called in `(done)` thanks to `wrap-cljs-async-test-result`,
                ;; so we don't call it here.
                (when-not (= ::async result#)
                  (complete-fn#))))
            (finally
              ;; `ns-unmap` on ClojureScript is a macro which can only take quoted
              ;; symbols, so we can't do the same in JS as we do on the JVM.
              (alter-meta! captured-test# dissoc :test)))))))


#?(:clj
   (defmacro assert-captured-test-results
     "Execute `body` as a test within a context where `clojure.test` or `cljs.test`
     will use a reporting function that -- instead of reporting test success or
     failure as per normal -- will just capture (and subsequently return) the calls
     made to the report function, for subsequent inspection.

     Used in the tests below with a `body` representing a test we might expect a
     user of this library to write, to assert that running that test produces the
     correct output from clojure.test.

     Don't forget to say hi to the turtles on the way down..."
     [assert-fn & body]
     (let [cljs-env? (boolean (:ns &env))]
       (if cljs-env?
         `(assert-captured-test-results--js ~assert-fn ~@body)
         `(assert-captured-test-results--jvm ~assert-fn ~@body)))))
