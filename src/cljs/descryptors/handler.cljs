(ns descryptors.handler
  (:require [taoensso.timbre :refer [info]]
            [descryptors.db :as db]
            [roll.sente :refer [event-msg-handler]]))



(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg [msg-id data opts] :?data}]
  (case msg-id
    :descryptors/test       (info data)
    ;; :descryptors/load!      (db/load! data)
    :descryptors/update-db  (let [[data opts] data]
                              (db/update-db opts data))
    nil))



(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[_ _ [db]] ?data]
    (db/load-handshake! db)))
