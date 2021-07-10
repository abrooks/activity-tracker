(ns activity-tracker.core-test
  (:require
    [clojure.test :refer :all]
    [clojure.core.async :as async]
    [activity-tracker.core :refer :all]))

(defn window-events [ch interval total]
  (async/thread
    (loop [cnt 1]
      (Thread/sleep interval)
      (async/>!! ch {:etype :window :eid cnt})
      (if (< cnt total)
         (recur (inc cnt))))))

(defn lock-events [ch interval duration total]
  (async/thread
    (loop [cnt 1]
      (Thread/sleep interval)
      (async/>!! ch {:etype :lock :eid cnt})
      (Thread/sleep duration)
      (async/>!! ch {:etype :unlock :eid cnt})
      (if (< cnt total)
         (recur (inc cnt))))))

;; Alts
(defn myalts [ch win-chan lock-chan]
  (async/thread
    (loop [state :unlock]
      (let [[event c] (async/alts!! [win-chan lock-chan])
            {:keys [etype eid]} event]
        (if (not event)
          (do (async/close! win-chan) (async/close! lock-chan)))
        (if (or (not= :lock state) (= :unlock etype))
          (do
            (async/>!! ch event)
            (recur etype))
          (recur :lock))))))

;; Mix
(defn myxduce []
  (fn xduce-compose [xf]
    (let [state (volatile! :unlock)]
      (fn xduce-fn
        ([] (xf))
        ([result] (xf result))
        ([result input]
         (let [{:keys [etype eid]} input]
           (if (or (not= :lock @state) (= :unlock etype))
              (do (vreset! state etype)
                  (xf result input))
              result)))))))

(defn snoop [ch msec]
  (let [timeout (async/timeout msec)]
    (loop [[v c] (async/alts!! [timeout ch])]
      (if (not= c timeout)
        (do
          (prn :snoop v)
          (recur (async/alts!! [timeout ch])))
        (prn :done)))))
#_
(let [wc (async/chan (async/sliding-buffer 5))
      lc (async/chan (async/sliding-buffer 5))
      oc (async/chan (async/sliding-buffer 5))]
  (window-events wc 500 20)
  (lock-events lc 5000 2000 3)
  (myalts oc wc lc)
  (snoop oc (* 20 1000)))

#_
(let [wc (async/chan (async/sliding-buffer 5))
      lc (async/chan (async/sliding-buffer 5))
      oc (async/chan (async/sliding-buffer 5) (myxduce))
      m (async/mix oc)]
  (async/admix m wc)
  (async/admix m lc)
  (window-events wc 500 20)
  (lock-events lc 5000 2000 3)
  (snoop oc (* 30 1000)))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
