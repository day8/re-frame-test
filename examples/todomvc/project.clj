(defproject todomvc-re-frame "see :git-version below https://github.com/arrdem/lein-git-version"

  :git-version
  {:status-to-version
   (fn [{:keys [tag version branch ahead ahead? dirty?] :as git-status}]
     (if-not (string? tag)
       ;; If git-status is nil (i.e. IntelliJ reading project.clj) then return an empty version.
       "_"
       (if (and (not ahead?) (not dirty?))
         tag
         (let [[_ major minor patch suffix] (re-find #"v?(\d+)\.(\d+)\.(\d+)(-.+)?" tag)]
           (if (nil? major)
             ;; If tag is poorly formatted then return GIT-TAG-INVALID
             "GIT-TAG-INVALID"
             (let [patch' (try (Long/parseLong patch) (catch Throwable _ 0))
                   patch+ (inc patch')]
               (str major "." minor "." patch+ suffix "-" ahead "-SNAPSHOT")))))))}

  :dependencies [[org.clojure/clojure        "1.10.1"]
                 [org.clojure/clojurescript  "1.10.520"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs "2.8.61"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.9"]
                 [binaryage/devtools "0.9.10"]
                 [day8.re-frame/test "0.1.5"]
                 [secretary "1.2.3"]]

  :plugins [[me.arrdem/lein-git-version "2.0.3"]
            [lein-shadow "0.1.5"]
            [lein-shell "0.5.0"]]

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
