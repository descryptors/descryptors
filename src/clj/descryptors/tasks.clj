(ns descryptors.tasks
  (:require [taoensso.timbre :refer [info]]
            [proto.descryptors.defaults :as pdd]
            [proto.charts.defaults :as pcd]
            [proto.charts.line :as line]
            [proto.charts.util :as pcu]
            [descryptors.db :as db]
            [descryptors.schema :as schema]
            [roll.util :as ru]))




(defn trickle-price-data [coin]
  (ru/deep-merge
   (pcu/minute->precision :price :hour coin)
   (pcu/minute->precision :price :day coin)))



(defn trim-price-data [coin]
  (ru/deep-merge
   ;;(pcu/trim-precision :price :minute :1d coin)
   (pcu/trim-precision :price :hour :1m coin)
   (pcu/trim-precision :price :day :1y coin)))



(defn update-price-charts [coin]
  (update-in coin [:data :price]
             line/price-charts
             pdd/static-charts))



(defn trickle-trim-price [coin]
  (->> (trickle-price-data coin)
       (ru/deep-merge-into coin)
       (trim-price-data)
       (update-price-charts)))



(defn update-price-minute [& args]
  (info "trimming minute price data...")
  (db/update-db2
   {:mode :replace}
   (fn [coin]
     (->> (pcu/trim-precision :price :minute :1d coin)
          (schema/lod2 :descryptors/price))))
  (info "[DONE] trimming"))



(defn update-price [& args]
  (info "trickle trimming price data...")
  (db/update-db2
   {:mode :replace}
   (fn [coin]
     (->> (trickle-trim-price coin)
          (schema/lod2 :descryptors/price))))
  (info "[DONE] trickle trimming"))





(comment

  (def bitcoin (get-in @db/DB [:lod :descryptors/all "bitcoin"]))

  (get-in bitcoin [:data :price :svg])

  )
