(ns descryptors.comms
  (:require [re-frame.core :as rf :refer [reg-fx]]
            [roll.sente :as sente]
            ;;[taoensso.timbre :refer [info]]
            [taoensso.sente :refer [cb-success?]]))


(def send-msg sente/send-msg)


(reg-fx
 ::send
 (fn [[evt data & [{:keys [cb timeout-ms] :as opts}]]]
   ;;(info "SENDING" evt data)
   (sente/send-msg
    [evt data]
    (cond-> {}
      ;; wrap callback in a timeout and success check
      cb (assoc
          :timeout-ms (or timeout-ms 10000)
          :cb (fn [cb-reply]
                (when (cb-success? cb-reply)
                  (cb cb-reply))))))))

