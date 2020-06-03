(ns ^:figwheel-hooks descryptors.core
  (:require [dommy.core :as d]
            [taoensso.timbre :as timbre :refer [info]]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [re-frame.core :refer [subscribe]]
            [linked.transit]
            [roll.sente :as sente]
            [descryptors.index.views :refer [index]]
            [descryptors.index.events :as idx-evts]
            [descryptors.routes  :as routes]
            [descryptors.db :as db]
            [descryptors.util :as du]
            [descryptors.handler]))




(defn ^:after-load reload []
  ;; stop loading animation
  (some-> (d/sel1 "#loading-icon")
          (d/toggle-class! "freeze"))
  
  (some->> (d/sel1 :#app)
           (rd/render [index])))




(defn ^:export init [& args]
  ;;(timbre/set-level! :error)
  
  ;;init app-db
  (idx-evts/init!)
  
  ;; mount app after db loaded
  (db/init! {:on-db-load reload})
  
  ;;Websockets
  (sente/start-router!
   {:path "/ws/chsk"
    :packer {:write-handlers [linked.transit/write-handlers]
             :read-handlers [linked.transit/read-handlers]}})
  
  ;;client-side page routing
  (routes/init-routes!))




(defn ^:export init-single [& args]
  ;;(timbre/set-level! :error)

  ;; init app-db
  (idx-evts/init!)
  
  ;; mount app after db loaded
  (db/init! {:on-db-load reload})
  
  ;; websockets
  (sente/start-router!
   (merge
    {:path "/ws/chsk"
     :packer {:write-handlers [linked.transit/write-handlers]
              :read-handlers [linked.transit/read-handlers]}}
    (when-let [slug (du/location->sym)]
      {:opts {:params {:handshake-for [slug]}}})))
  
  (routes/init-routes!))
