(ns activity-tracker.desktop-test
  "A desktop has window focus and, potentially, a screensaver/locker which will suspend that focus."
  (:require
     [activity-tracker.desktop :as dt]
     [activity-tracker.freedesktop-lock :as fdt]
     [activity-tracker.i3-msg :as i3m]
     [clojure.core.async :as async]
     [clojure.spec.alpha :as s]))

(deftest desktop-test
  (testing "lock event tests"
    (let [wch (async/chan 10 i3m/i3-msg-pipeline)
          dch (async/chan 10 (map fdt/parse-freedesktop-event))
          och (async/chan 10 (dt/state-filter dt/locked?))
          m (async/mix och)]
      (async/admix m wch)
      (async/admix m dch)
      (async/>!! wch {:event})
      (is (= (meta res) (meta out))))))
