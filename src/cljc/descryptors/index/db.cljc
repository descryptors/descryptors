(ns descryptors.index.db
  (:require [proto.descryptors.defaults :as defaults]))

(def default-value
  {:pagination {:first-card-idx 0 :page-step 24}
   :accepted-terms false
   :toolbar {:toolbar/charts   defaults/visible-charts
             :toolbar/theme    :day-theme
             :toolbar/info     false
             :toolbar/search   false
             :toolbar/sort-idx :marketcap-index
             :toolbar/rows-per-page 5}})
