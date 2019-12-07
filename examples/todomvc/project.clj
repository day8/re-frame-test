(defproject todomvc-re-frame "lein-git-inject/version"

  :dependencies [[org.clojure/clojure        "1.10.1"]
                 [org.clojure/clojurescript  "1.10.520"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs "2.8.81"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.9"]
                 [binaryage/devtools "0.9.10"]
                 [day8.re-frame/test "0.1.5"]
                 [secretary "1.2.3"]]

  :plugins      [[day8/lein-git-inject "0.0.2"]
                 [lein-shadow          "0.1.6"]
                 [lein-shell           "0.5.0"]]

  :middleware   [leiningen.git-inject/middleware]

  :shadow-cljs {:nrepl  {:port 8777}

                :builds {:client {:target     :browser
                                  :output-dir "resources/public/js"
                                  :modules    {:client {:init-fn  todomvc.core/main}}
                                  :dev        {:compiler-options {:external-config  {:devtools/config {:features-to-install [:formatters :hints]}}}}
                                  :devtools   {:http-root "resources/public"
                                               :http-port 8280}}
                         :karma-test
                                 {:target    :karma
                                  :ns-regexp "-test$"
                                  :output-to "target/karma-test.js"}}}

  :aliases {"dev-auto" ["with-profile" "dev" "shadow" "watch" "client"]
            "test-once"  ["do"
                          ["shadow" "compile" "karma-test"]
                          ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]}

  :jvm-opts ^:replace ["-Xms256m" "-Xmx2g"]

  :clean-targets ^{:protect false} ["resources/public/js" "target"])
