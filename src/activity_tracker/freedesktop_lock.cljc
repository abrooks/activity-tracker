(ns activity-tracker.freedesktop-lock
  (:require
     [activity-tracker.cmds :as cmds]
     [clojure.core.async :as async]
     [clojure.spec.alpha :as s]
     [clojure.string :as str]))

(def freedesktop-login-state
   ["dbus-monitor"
    "--system"
    "type='signal',interface='org.freedesktop.login1.Session'"])

;; signal time=1576525715.201585 sender=:1.18 -> destination=(null destination) serial=5620 path=/org/freedesktop/login1/session/_329; interface=org.freedesktop.login1.Session; member=Lock
;; signal time=1576525730.573013 sender=:1.18 -> destination=(null destination) serial=5625 path=/org/freedesktop/login1/session/_329; interface=org.freedesktop.login1.Session; member=Unlock
(def fd-event-regex #"^signal time=([0-9.]*) .* member=(Lock|Unlock)$")

(defn parse-freedesktop-event [fd-event]
  (if-let [res (re-find (re-matcher fd-event-regex fd-event))]
    (let [[_ time event] res]
      {:type :lock :time (Double/parseDouble time) :event event})
    (with-meta {} {:type :error :source :lock :txt fd-event})))

(defn freedesktop-lock-signals [ch]
  (cmds/run-cmd-async freedesktop-login-state ch))

(defn freedesktop-event-chan
  "This is a temporary glue function"
  []
  (let [ch (async/chan 5 (map parse-freedesktop-event))]
   (freedesktop-lock-signals ch)
   ch))
