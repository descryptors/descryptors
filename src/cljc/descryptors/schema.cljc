(ns descryptors.schema
  (:require [proto.descryptors.defaults :as defaults]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform select multi-path]]))


(def version "build 80ae006 (Hmeli-Suneli)")

(def handshake-amount 60)


;; 5 minutes memoize
(def five-min (* 5 60 1000))
(def memoize-ttl five-min)


(derive :descryptors/broadcast :descryptors/bare)

(derive :descryptors/svg  :descryptors/svg+bare)
(derive :descryptors/bare :descryptors/svg+bare)

(derive :descryptors/price-data  :descryptors/data)
(derive :descryptors/github-data :descryptors/data)
(derive :descryptors/exchanges   :descryptors/data)
(derive :descryptors/social      :descryptors/data)
(derive :descryptors/info        :descryptors/data)

;;(derive :descryptors/data :descryptors/data+bare)
;;(derive :descryptors/bare :descryptors/data+bare)

(derive :descryptors/data :descryptors/all)
(derive :descryptors/svg+bare :descryptors/all)


(def lod-config
  (->> {:descryptors/slug
        [:slug]

        :descryptors/slug+tags
        [:tags :indexes :name]

        :descryptors/broadcast
        [:name :symbol :tags :similar
         :indexes :links :metrics
         [:data :github :total]]

        ;; just default svgs
        :descryptors/svg
        [[:data :github :svg defaults/chart-period]
         [:data :price  :svg defaults/chart-period]]

        :descryptors/price
        [:slug [:data :price]]
        
        :descryptors/price-svg
        [:slug [:data :price :svg]]
        
        :descryptors/price-data
        [[:data :price :minute]
         [:data :price :hour]
         [:data :price :day]]

        :descryptors/price-data-minute
        [[:data :price :minute]]

        :descryptors/price-data-hour
        [[:data :price :hour]]

        :descryptors/price-data-day
        [[:data :price :day]]

        :descryptors/new-data-price
        [[:data :price :svg]
         [:data :price :day]
         [:data :price :hour]]

        :descryptors/github-data
        [[:data :github defaults/github-precision]
         [:data :github :repos]]

        :descryptors/social
        [[:data :twitter]
         [:data :reddit]]

        :descryptors/exchanges
        [[:data :exchanges]]

        :descryptors/info
        [:people :used-by :wallets]}

       (transform [MAP-VALS ALL] #(if (coll? %) % [%]))))


;; compute paths
(def lod-paths
  (->> (keys lod-config)
       (mapcat parents)
       ((juxt identity (partial mapcat parents)))
       (sequence cat)
       set
       (into (keys lod-config))
       (reduce
        (fn [m level]
          (assoc m level
                 (or (get lod-config level)
                     ;; abstract lod, so load descendants
                     (->> (descendants level)
                          (mapcat #(get lod-config %))
                          set))))
        {})))



(defn select-fields [paths m]
  (-> (reduce
       (fn [res path]
         (if-let [v (get-in m path)]
           (assoc-in res path v)
           res))
       {}
       paths)))



(defn lod
  "Select paths and strip all other data. Sets lod level."
  [level coins & [opts]]
  (let [paths (get lod-paths level)]
    (keep #(some-> (select-fields paths %)
                   not-empty
                   (assoc :slug (:slug %) :lod level))
          (if (sequential? coins) coins [coins]))))


(defn lod2
  "Select paths and strip all other data."
  [levels coins & [opts]]
  (let [paths (mapcat lod-paths (if (sequential? levels)
                                  levels [levels]))]
    (keep #(some-> (select-fields paths %)
                   not-empty
                   (assoc :slug (:slug %)))
          (if (sequential? coins) coins [coins]))))
