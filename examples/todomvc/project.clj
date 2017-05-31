(defproject todomvc-re-frame "0.9.0"
  :dependencies [[org.clojure/clojure        "1.9.0-alpha10"]
                 [org.clojure/clojurescript  "1.9.89"]
                 [reagent "0.6.0-rc"]
                 [re-frame "0.9.3"]
                 [binaryage/devtools "0.8.1"]
                 [day8.re-frame/test "0.1.3"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.6"]
            [lein-npm "0.6.2"]]

            :hooks [leiningen.cljsbuild]

  :profiles {:dev  {:dependencies [[karma-reporter "1.0.1"]
                                   [binaryage/devtools "0.8.1"]]
                    :cljsbuild    {:builds
                                   {:client {:compiler {:asset-path           "js"
                                                        :optimizations        :none
                                                        :source-map           true
                                                        :source-map-timestamp true
                                                        :main                 "todomvc.core"}
                                             :figwheel {:on-jsload "todomvc.core/main"}}}}}

             :prod {:cljsbuild
                    {:builds
                     {:client
                      {:compiler {:asset-path    "js"
                                  :optimizations :advanced
                                  :elide-asserts true
                                  :pretty-print  false}}}}}}

            :figwheel {:server-port 3450
             :repl        true}

  :jvm-opts ^:replace ["-Xms256m" "-Xmx2g"]

  :clean-targets ^{:protect false} ["resources/public/js" "target" "resources/public/karma"]

  :npm {:devDependencies [[karma "1.0.0"]
                          [karma-cljs-test "0.1.0"]
                          [karma-chrome-launcher "0.2.0"]
                          [karma-junit-reporter "0.3.8"]]}

  :cljsbuild {:builds
              {:client
               {:source-paths ["src"]
                :compiler     {:output-dir "resources/public/js"
                               :output-to  "resources/public/js/client.js"}}
               :karma
               {:source-paths ["test" "src"]
                :compiler     {:output-to     "resources/public/karma/test.js"
                               :source-map    "resources/public/karma/test.js.map"
                               :output-dir    "resources/public/karma/test"
                               :optimizations :whitespace
                               :main          "todomvc.test.runner"
                               :pretty-print  true}}}}

            :aliases
            {"karma-once" ["do" "clean," "cljsbuild" "once" "karma,"]
             "karma-auto" ["do" "clean," "cljsbuild" "auto" "karma,"]
             "prod-once"  ["with-profile" "prod" "do" "clean," "cljsbuild" "once" "client,"]})
