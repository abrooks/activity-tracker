(ns activity-tracker.i3-msg
  (:require
     [activity-tracker.cmds :as cmds]
     [activity-tracker.freedesktop-lock :as fdl]
     [clojure.core.async :as async]
     [clojure.spec.alpha :as s]
     [clojure.walk :as walk]
     [pjson.core :as json]))

(def i3-msg-cmdline ["i3-msg" "-t" "subscribe" "-m" "[ \"window\" ]"])

(s/def ::change #{"new" "close" "focus" "title"})
(s/def ::container (s/keys))
(s/def ::name string?)
(s/def ::transient_for #{nil})
(s/def ::window number?)
(s/def ::window_properties (s/keys :req-un [::class ::instance ::window_role ::title ::transient_for]))

(defn i3-msg-format [msg]
  (let [{:keys [change title]} msg]))

(def i3-msg-pipeline
  (comp (map (comp walk/keywordize-keys json/read-str))))

(defn i3-msg-messages [ch]
  (cmds/run-cmd-async i3-msg-cmdline ch))


(defn i3-msg-chan
  "This is a temporary glue function"
  []
  (let [ch (async/chan 5 (map fdl/parse-freedesktop-event))]
   (i3-msg-messages ch)
   ch))
