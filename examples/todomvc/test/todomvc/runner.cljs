(ns todomvc.test.runner
    (:require [jx.reporter.karma :as karma :include-macros true]
      [todomvc.core-test]))

(enable-console-print!)

(defn ^:export run-karma [karma]
      (karma/run-tests
        karma
        'todomvc.core-test))
