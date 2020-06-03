(ns descryptors.index.subs
  (:require [re-frame.core :refer [reg-sub subscribe reg-event-fx]]
            [descryptors.db :as db]))


(reg-sub
 ::index
 (fn [db _]
   (:index db)))


(reg-sub
 ::pagination
 :<- [::index]
 (fn [db _]
   (:pagination db)))


(reg-sub
 ::toolbar
 :<- [::index]
 (fn [db _]
   (:toolbar db)))


(reg-sub
 ::accepted-terms
 :<- [::index]
 (fn [db _]
   (:accepted-terms db)))


;; fixme: not lazy

(reg-sub
 ::paginated-coins
 :<- [::pagination]
 :<- [::db/filtered-descryptors]
 :<- [::db/current-sort-idx]
 
 (fn [[{:keys [first-card-idx page-step]} coins sort-idx] _]
   (let [total-coins (count coins)
         start first-card-idx
         end (+ start (min page-step (- total-coins start)))
         remaining (- total-coins end)]
     
     {:sort-idx        sort-idx
      :page-coins      (subvec coins start end)
      :total-coins     total-coins
      :first-card-idx  first-card-idx
      :remaining-coins remaining})))



(reg-sub
 ::filtered-slugs
 :<- [::db/filtered-descryptors]
 (fn [coins _]
   (mapv :slug coins)))
