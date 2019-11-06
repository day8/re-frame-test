(defproject    day8.re-frame/test "see :git-version below https://github.com/arrdem/lein-git-version"
  :description "re-frame testing tools"
  :url         "https://github.com/day8/re-frame-test"
  :license     {:name "MIT"
                :url "https://opensource.org/licenses/MIT"}

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

  :dependencies [[org.clojure/clojure       "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs      "2.8.69" :scope "provided"]
                 [re-frame                  "0.10.9"]]
  
  :plugins [[me.arrdem/lein-git-version "2.0.3"]
            [lein-shadow                "0.1.6"]
            [lein-shell                 "0.5.0"]]

  :profiles {:dev {:dependencies   [[ch.qos.logback/logback-classic "1.2.3"]]
                   :resource-paths ["test-resources"]}}

  :release-tasks [["deploy" "clojars"]]

  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_PASSWORD}]]

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

  :aliases {"dev-auto"    ["with-profile" "dev" "do"
                           ["clean"]
                           ["shadow" "watch" "browser-test"]]
            "karma-once"  ["do"
                           ["clean"]
                           ["shadow" "compile" "karma-test"]
                           ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]})


