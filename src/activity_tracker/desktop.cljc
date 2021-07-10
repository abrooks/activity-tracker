(ns activity-tracker.desktop
  "A desktop has window focus and, potentially, a screensaver/locker which will suspend that focus."
  (:require
     [clojure.core.async :as async]
     [clojure.spec.alpha :as s]))

;; Linux / Mac / Windows where?

;; Linux boot-id /sys/kernel/random/boot_id

(s/def ::window-id int?)
(s/def ::window-class string?)
(s/def ::window-title string?)
(s/def ::desktop-event (s/keys :req [::event]))

(defn locked? [state input]
  (let [{:keys [etype eid]} input]
     (when (or (not= :lock state) (= :unlock etype))
        etype)))

(defn state-filter [init pred]
  (fn state-compose [xf]
    (let [state (volatile! init)]
      (fn state-fn
        ([] (xf))
        ([result] (xf result))
        ([result input
           (if-let [ret (pred @state input)]
              (do (vreset! state ret)
                  (xf result input))
              result)])))))
