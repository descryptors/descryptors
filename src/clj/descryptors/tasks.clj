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
  (update-in coin [:data :price]
             #(->> (pcu/minute->precision :hour %)
                   (pcu/minute->precision :day))))



(defn trim-price-data [coin]
  (-> coin
      (update-in [:data :price :hour]
                 (partial pcu/trim-precision2 :1m))
      (update-in [:data :price :day]
                 (partial pcu/trim-precision2 :1y))))



(defn update-price-charts [coin]
  (update-in coin [:data :price]
             line/price-charts pdd/static-price-charts))



(defn trickle-trim-price [coin]
  (->> (trickle-price-data coin)
       (trim-price-data)
       (update-price-charts)))



(defn update-price-minute [& args]
  (info "trimming minute price data...")
  (db/update-db2
   {:mode :replace}
   (fn [coin]
     (->> (update-in coin [:data :price :minute]
                     (partial pcu/trim-precision2 :1d))
          (schema/lod2 :descryptors/price-data-minute))))
  (info "[DONE] trimming"))



(defn update-price [& args]
  (info "trickle trimming price data...")
  (db/update-db2
   {:mode :replace}
   (fn [coin]
     (->> (trickle-trim-price coin)
          (schema/lod2 [:descryptors/price-data-hour
                        :descryptors/price-data-day
                        :descryptors/price-svg]))))
  (info "[DONE] trickle trimming"))





(comment

  (def bitcoin (get-in @db/DB [:lod :descryptors/all "bitcoin"]))

  (get-in bitcoin [:data :price :svg])

  )
