(defproject day8.re-frame/test "0.1.1-SNAPSHOT"
  :description "re-frame testing tools"
  :url "https://github.com/Day8/re-frame-test"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.89"]
                 [re-frame "0.8.0-alpha11"]]
  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.3"]]

  :profiles {:dev {:dependencies   [[ch.qos.logback/logback-classic "1.1.7"]
                                    [karma-reporter "1.0.1"]]
                   :resource-paths ["test-resources"]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :jvm-opts ["-Xmx1g" "-XX:+UseConcMarkSweepGC"]

  :clean-targets [:target-path "run/compiled"]

  :npm {:dependencies [[karma "1.0.0"]
                       [karma-cljs-test "0.1.0"]
                       [karma-chrome-launcher "0.2.0"]
                       [karma-junit-reporter "0.3.8"]]}

  :cljsbuild
  {:builds [{:id           "test"
             :source-paths ["test" "src"]
             :compiler     {:output-to            "run/compiled/browser/test.js"
                            :source-map           true
                            :output-dir           "run/compiled/browser/test"
                            :optimizations        :none
                            :source-map-timestamp true
                            :pretty-print         true}}
            {:id           "karma"
             :source-paths ["test" "src"]
             :compiler     {:output-to     "run/compiled/karma/test.js"
                            :source-map    "run/compiled/karma/test.js.map"
                            :output-dir    "run/compiled/karma/test"
                            :optimizations :whitespace
                            :main          "re_frame_undo.test_runner"
                            :pretty-print  true}}]}

  :aliases {"test-once"  ["do" "clean," "cljsbuild" "once" "test," "shell" "open" "test/test.html"]
            "test-auto"  ["do" "clean," "cljsbuild" "auto" "test,"]
            "karma-once" ["do" "clean," "cljsbuild" "once" "karma,"]
            "karma-auto" ["do" "clean," "cljsbuild" "auto" "karma,"]})



