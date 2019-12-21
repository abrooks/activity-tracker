(ns activity-tracker.core
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.spec.alpha :as s]
            [datascript.core :as d]
            [pjson.core :as json]
            [clojure.walk :as walk])
  (:import [java.io File]
           [java.lang ProcessBuilder]))

(defonce config (atom {}))

(def i3-msg ["i3-msg" "-t" "subscribe" "-m" "[ \"window\" ]"])

;; signal time=1576525715.201585 sender=:1.18 -> destination=(null destination) serial=5620 path=/org/freedesktop/login1/session/_329; interface=org.freedesktop.login1.Session; member=Lock
;; signal time=1576525730.573013 sender=:1.18 -> destination=(null destination) serial=5625 path=/org/freedesktop/login1/session/_329; interface=org.freedesktop.login1.Session; member=Unlock
(def freedesktop-login-state ["dbus-monitor" "--system" "type='signal',interface='org.freedesktop.login1.Session'"])

;; Based on: https://stackoverflow.com/questions/45292625/how-to-perform-non-blocking-reading-stdout-from-a-subprocess-in-clojure
(defn run-cmd-async
  [cmdline ch]
  (async/thread
    (let [pbuilder (ProcessBuilder. (into-array String cmdline))
          process (.start pbuilder)]
      (with-open [reader (clojure.java.io/reader (.getInputStream process))]
        (loop []
          (let [line (.readLine ^java.io.BufferedReader reader)]
            (if (and line (async/>!! ch line))
              (recur)
              (async/close! ch))))))))

(defn poll-cmd-async
  [cmdline ch msec]
  (async/thread
    (loop []
      (Thread/sleep msec)
      (let [res (try (apply shell/sh cmdline)
                 (catch Exception e
                   (async/>!! ch e)
                   (async/close! ch)))]
        (if (and res (async/>!! ch res))
          (recur)
          (async/close! ch))))))


(defn sh
  [& cmdline]
  ;; (prn :sh cmdline)
  (apply shell/sh (map #(if (= File (class %))
                          (.getPath ^File %)
                          %)
                       cmdline)))


(comment
 (def d (async/chan 1000))
 (async/go-loop [] (Thread/sleep 10000) (when (async/>! d (apply shell/sh ["date"])) (recur)))
 (poll-cmd-async ["date"] d 10000)

 (def c (async/chan 1000 (map (comp walk/keywordize json/read-str))))
 (run-cmd-async i3-msg c)


 (def s)
 (run-cmd-async)

 (use '[cemerick.pomegranate :only (add-dependencies)])
 (add-dependencies :coordinates '[[pjson "0.5.2"]]
                   :repositories (merge cemerick.pomegranate.aether/maven-central
                                        {"clojars" "https://clojars.org/repo"})))
