(ns descryptors.comms
  (:require [re-frame.core :as rf :refer [reg-fx]]
            [taoensso.timbre :refer [info]]
            [roll.sente :as sente]))


;; try using delay or promise 

(def connected-uids sente/connected-uids)
(def send-msg       sente/send-msg)


(reg-fx
 ::send
 (fn [[uid evt]]
   (send-msg uid evt)))
