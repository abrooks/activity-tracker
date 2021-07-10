(ns activity-tracker.core
  (:require
     [activity-tracker.cmds :as cmds]
     [activity-tracker.i3-msg :as i3]
     [clojure.core.async :as async]
     [clojure.spec.alpha :as s]
     [clojure.string :as str]
     [datascript.core :as d]
     [pjson.core :as json]
     [clojure.walk :as walk]))

;; TODO
;; Consider dockerized monitors for portability.
;; Mount in X11 and DBUS sockets.

(s/def ::event-id number?)
(s/def ::event (s/keys :req [::event-id] :opt []))

(def db-schema {::event-id {:db/unique :db.unique/identity}})


;; Maybe add system level information here?
(def main-xfrm (map identity))

(defn ex-handler [ex]
  (with-meta {} {:type :error :exception ex}))

(defonce main-ch (async/chan (async/sliding-buffer 10)
                             #'main-xfrm
                             #'ex-handler))

(defonce nexus {:inputs  (async/mix  main-ch)
                :outputs (async/mult main-ch)})

(def snoop (atom false))


#_
(def layout "
 i3    screenlock  phone   tracker
  |        |         |        |
[FIXi3] [FIXSS]   [FIXph]  [FIXtrk]
  |        |         |        |
  v        v         v        v
  +--------+---------+--------+
           |
         [MAIN]
           |
           v
   +-------+--------+
   |       |        |
[FILTER] [FILTER] [IDENT]
   |       |        |
   v       v        v
  file    db      snoop
")


(defn -main [cmd & args]
  (case cmd
    "record" (do (println "recording...")
               (loop []
                (prn (meta (async/<!! (i3/i3-msg-chan))))
                (recur)))))

(comment
 (use '[cemerick.pomegranate :only (add-dependencies)])
 (add-dependencies :coordinates '[[pjson "0.5.2"]]
                   :repositories (merge cemerick.pomegranate.aether/maven-central
                                        {"clojars" "https://clojars.org/repo"})))
