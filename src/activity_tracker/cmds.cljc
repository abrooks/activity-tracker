(ns activity-tracker.cmds
  (:require
     [clojure.core.async :as async]
     [clojure.java.io :as io]
     [clojure.java.shell :as shell]
     [clojure.spec.alpha :as s]
     [datascript.core :as d]
     [pjson.core :as json]
     [clojure.walk :as walk])
  (:import
     [java.io File]
     [java.lang ProcessBuilder]))

(defn shell-files
  "Helper to map Java.io.File objects to paths for shell commandlines"
  [cmdline]
  (map #(if (= File (class %))
          (.getPath ^File %)
          %)
       cmdline))

;; Based on: https://stackoverflow.com/questions/45292625/how-to-perform-non-blocking-reading-stdout-from-a-subprocess-in-clojure
(defn run-cmd-async
  [cmdline ch]
  (async/thread
    (let [pbuilder (ProcessBuilder. (into-array String (shell-files cmdline)))
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
      (let [res (try (apply shell/sh (shell-files cmdline))
                  (catch Exception e
                    (async/>!! ch e)
                    (async/close! ch)))]
        (if (and res (async/>!! ch res))
          (recur)
          (async/close! ch))))))

(comment
 (def d (async/chan 1000))
 (async/go-loop [] (Thread/sleep 10000) (when (async/>! d (apply shell/sh ["date"])) (recur)))
 (poll-cmd-async ["date"] d 10000)

 (def c (async/chan 1000 (map (comp walk/keywordize json/read-str))))
 (run-cmd-async i3-msg c))
