(ns day8.chrome-shadow-test-runner
  (:require [babashka.process :refer [shell]]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [org.httpkit.server :as http]
            [clojure.java.io :as io])
  (:import [java.net ServerSocket]
           [org.jsoup Jsoup]))

(def chrome-args
  ["--headless"
   "--disable-gpu"
   "--no-sandbox"
   "--virtual-time-budget=60000"
   "--dump-dom"])

(defn find-free-port []
  (let [socket (ServerSocket. 0)
        port (.getLocalPort socket)]
    (.close socket)
    port))

(defn file-handler [test-dir]
  (fn [{:keys [uri]}]
    (let [file-path (if (= uri "/") "/index.html" uri)
          full-path (str test-dir file-path)
          file (io/file full-path)]
      (if (.exists file)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (slurp file)}
        {:status 404
         :headers {"Content-Type" "text/plain"}
         :body "Not found"}))))

(defn start-test-server [test-dir port]
  (http/run-server (file-handler test-dir) {:port port}))

(defn run-chrome-tests [chrome-path port]
  (let [url (str "http://localhost:" port "/")
        args (concat [chrome-path] chrome-args [url])]
    (apply shell {:out :string :continue true} args)))

(defn parse-single-test [test-var]
  (let [has-failures (.hasClass test-var "has-failures")
        header-el (.selectFirst test-var ".var-header")
        test-name (when header-el (str/trim (.text header-el)))]
    (when test-name
      {:name test-name
       :failed? has-failures
       :element test-var})))

(defn categorize-tests [tests]
  (reduce (fn [acc test]
            (if (:failed? test)
              (-> acc
                  (update :failed inc)
                  (update :failed-tests conj test))
              (update acc :passed inc)))
          {:passed 0 :failed 0 :failed-tests []}
          tests))

(defn parse-test-results [html]
  (let [doc (Jsoup/parse html)
        report-el (.selectFirst doc ".report-number")
        test-vars (.select doc ".test-var")]
    (when-not report-el
      (println "No test results found")
      (System/exit 1))

    (println (.text report-el))

    (let [tests (->> test-vars
                     (map parse-single-test)
                     (filter some?))
          results (categorize-tests tests)]

      ;; Print individual test results
      (doseq [test tests]
        (println (str (if (:failed? test) "  ✗ " "  ✓ ") (:name test))))

      results)))

(defn extract-failure-details [element]
  (let [message-el (.selectFirst element ".test-message")
        code-elements (.select element "pre code")]
    {:message (when message-el (.text message-el))
     :expected (when (seq code-elements) (.text (first code-elements)))
     :actual (when (> (count code-elements) 1) (.text (second code-elements)))}))

(defn show-failures [failed-tests]
  (when (seq failed-tests)
    (println "\nFailure details:")
    (doseq [{:keys [name element]} failed-tests]
      (println (str "\n  " name ":"))
      (let [{:keys [message expected actual]} (extract-failure-details element)]
        (when message (println (str "    " message)))
        (when expected (println (str "    Expected: " expected)))
        (when actual (println (str "    Actual: " actual)))))))

(defn validate-test-path [test-path]
  (when-not (fs/exists? test-path)
    (println (str "Test directory not found: " test-path))
    (System/exit 1)))

(defn run
  "Run ClojureScript browser tests using Chrome headless.

  Options:
  - :test-dir    Path to compiled test directory (default: run/resources/public/compiled_test)
  - :chrome-path Path to Chrome binary (default: chromium)"
  [{:keys [chrome-path test-dir] :as opts}]
  (let [chrome-bin (or chrome-path "chromium")
        test-path (or test-dir "run/resources/public/compiled_test")
        port (find-free-port)]

    (validate-test-path test-path)
    (println "Running ClojureScript tests...")

    (let [stop-server (start-test-server test-path port)]
      (Thread/sleep 1000) ; Give server time to start
      (try
        (let [result (run-chrome-tests chrome-bin port)
              exit-code (:exit result)]
          (println (str "Chrome exit code: " exit-code))

          ;; Analyze Chrome exit code
          (cond
            (zero? exit-code)
            (println "Chrome completed successfully within virtual time budget")

            (= exit-code 1)
            (println "Chrome encountered errors or tests failed")

            :else
            (do
              (println (str "Chrome exited unexpectedly with code: " exit-code))
              (when (:err result)
                (println "Chrome stderr:" (:err result)))
              (System/exit 1)))

          (let [{:keys [passed failed failed-tests]} (parse-test-results (:out result))]
            (println (str "\nResults: " passed " passed, " failed " failed"))
            (show-failures failed-tests)
            (System/exit (if (> failed 0) 1 0))))
        (finally (stop-server))))))

(defn -main [& args]
  "Main entry point for command line usage"
  (let [arg-map (apply hash-map (map keyword args))]
    (run arg-map)))
