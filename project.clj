(defproject day8.re-frame/test "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.89"]
                 [org.clojure/core.async "0.2.385"]
                 [re-frame "0.8.0-alpha9"]]
  :profiles {:dev {:dependencies [[ch.qos.logback/logback-classic "1.1.7"]]
                   :resource-paths ["test-resources"]}})
