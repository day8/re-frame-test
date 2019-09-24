(defproject day8.re-frame/test "0.1.6-SNAPSHOT"
  :description "re-frame testing tools"
  :url "https://github.com/Day8/re-frame-test"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs "2.8.57" :scope "provided"]
                 [re-frame "0.10.9"]]
  
  :plugins [[lein-shadow "0.1.5"]
            [lein-shell "0.5.0"]]

  :profiles {:dev {:dependencies   [[ch.qos.logback/logback-classic "1.2.3"]]
                   :resource-paths ["test-resources"]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :deploy-repositories [["releases" {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]

  :jvm-opts ["-Xmx1g"]

  :clean-targets [:target-path "run/compiled"]

  :shadow-cljs {:nrepl  {:port 8777}

                :builds {:browser-test
                         {:target    :browser-test
                          :ns-regexp "-test$"
                          :test-dir  "resources/public/js/test"
                          :devtools  {:http-root "resources/public/js/test"
                                      :http-port 8290}}

                         :karma-test
                         {:target    :karma
                          :ns-regexp "-test$"
                          :output-to "target/karma-test.js"}}}

  :aliases {"dev-auto" ["with-profile" "dev" "shadow" "watch" "browser-test"]
            "test-once"  ["do"
                          ["shadow" "compile" "karma-test"]
                          ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]})


