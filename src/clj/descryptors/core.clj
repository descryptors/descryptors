(ns descryptors.core
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [roll.core :as roll]
            [roll.util :as u]
            [descryptors.handler :as handler]))




(defmethod ig/init-key :descryptors/core [_ {:as opts :keys [sente]}]
  (info "starting descryptors/core")
  (info (u/spp (cond-> opts
                 (:sente opts)
                 (assoc :sente true))))
  (cond-> {}
    sente (assoc :stop-watch-clients
                 (handler/watch-connected-clients))))



(defmethod ig/halt-key! :descryptors/core [_ {:keys [stop-watch-clients]}]
  (when stop-watch-clients
    (info "stopping descryptors/core...")
    (stop-watch-clients)))




(defn -main [& args]
  ;; start webserver, websocket, repl and others
  (roll/init "conf/config.edn")
  (info "[DONE]"))

