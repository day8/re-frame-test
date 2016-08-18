(ns day8.re-frame.test-reframe.runner
  (:require [jx.reporter.karma :as karma :include-macros true]
            [day8.re-frame.test-reframe.test-test]))

(defn ^:export run-karma [karma]
  (karma/run-tests
    karma
    'day8.re-frame.test-reframe.test-test))
