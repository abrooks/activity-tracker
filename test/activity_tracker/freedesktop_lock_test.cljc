(ns activity-tracker.freedesktop-lock-test
  (:require
    [activity-tracker.freedesktop-lock :as fd-l]
    [clojure.core.async :as async]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.test :refer :all]))

(def test-cases {:lock   ["signal time=1576525715.201585 sender=:1.18 -> destination=(null destination) serial=5620 path=/org/freedesktop/login1/session/_329; interface=org.freedesktop.login1.Session; member=Lock"
                          {:type :lock :event "Lock" :time 1576525715.201585}]
                 :unlock ["signal time=1576525730.573013 sender=:1.18 -> destination=(null destination) serial=5625 path=/org/freedesktop/login1/session/_329; interface=org.freedesktop.login1.Session; member=Unlock"
                          {:type :lock :event "Unlock" :time 1576525730.573013}]
                 :error  ["signal time=1577679633.348175 sender=org.freedesktop.DBus -> destination=:1.912 serial=2 path=/org/freedesktop/DBus; interface=org.freedesktop.DBus; member=NameAcquired"
                          ^{:type :error :source :lock :txt "signal time=1577679633.348175 sender=org.freedesktop.DBus -> destination=:1.912 serial=2 path=/org/freedesktop/DBus; interface=org.freedesktop.DBus; member=NameAcquired"} {}]})

(deftest lock-events
  (testing "lock event tests"
    ;; Really wishing clojure.test was more programmatic, data driven and less macro-y
    (let [[in out] (:lock test-cases)
          res (fd-l/parse-freedesktop-event in)]
      (is (= res out))
      (is (= (meta res) (meta out))))
    (let [[in out] (:unlock test-cases)
          res (fd-l/parse-freedesktop-event in)]
      (is (= res out))
      (is (= (meta res) (meta out))))
    (let [[in out] (:error test-cases)
          res (fd-l/parse-freedesktop-event in)]
      (is (= res out))
      (is (= (meta res) (meta out))))))
