(ns descryptors.index.events
  (:require [re-frame.core :as rf
             :refer [reg-event-db path trim-v reg-event-fx
                     dispatch dispatch-sync inject-cofx]]
            [vimsical.re-frame.fx.track :as track]
            [vimsical.re-frame.cofx.inject :as inject]
            [descryptors.index.db :refer [default-value]]
            [descryptors.index.subs :as sub]
            [descryptors.db  :as db]
            [descryptors.comms :as comms]
            [descryptors.schema :as schema]
            [taoensso.timbre :refer [info]]
            [roll.util :as u]
            #?@(:cljs [[com.smxemail.re-frame-cookie-fx]
                       [descryptors.util :as util]])))



(def index-interceptors      [(path :index) trim-v])
(def pagination-interceptors [(path :index :pagination) trim-v])
(def toolbar-interceptors    [(path :index :toolbar) trim-v])



#?(:clj
   (reg-event-db
    ::init
    index-interceptors
    (fn [old-db [new-db]]
      (merge default-value old-db new-db)))

   :cljs
   (reg-event-fx
    ::init
    [index-interceptors (inject-cofx :cookie/get [:descryptors])]
    (fn [{old-db :db cookie :cookie/get} [new-db]]
      (let [cookie (some-> cookie :descryptors util/decode)]
        {:db (u/deep-merge-into
              default-value old-db cookie
              {:cookie cookie}
              new-db)}))))



#?(:cljs
   (reg-event-fx
    ::set-cookie
    index-interceptors
    (fn [{db :db} [data]]
      (let [new-cookie (u/deep-merge-into (:cookie db) data)]
        {:db (assoc db :cookie new-cookie)
         :cookie/set {:name :descryptors
                      :value (util/encode new-cookie)}}))))



(reg-event-fx
 ::set-key
 index-interceptors
 (fn [{db :db} [k v]]
   {:db (assoc db k v)
    :dispatch [::set-cookie {k v}]}))


(reg-event-db
 ::update-key
 index-interceptors
 (fn [db [k f & args]]
   (apply update db k f args)))



(reg-event-db
 ::assoc-in
 index-interceptors
 (fn [db [& args]]
   (apply assoc-in db args)))



(reg-event-fx
 ::fetch-descryptors
 index-interceptors
 (fn [_ [level ids]]
   {::comms/send [:descryptors/get [level ids]
                  {:cb (fn [cb-reply]
                         (dispatch [::db/update-db cb-reply
                                    {:mode :replace}]))}]}))



(reg-event-fx
 ::expand-descryptors
 index-interceptors
 (fn [_ [level cards]]
   (let [expandees (->> cards (filter #(not (isa? level (:lod %)))))
         ;;expandees (->> cards (filter (comp empty? (partial schema/lod level))))
         ]
     (when-not (empty? expandees)
       {:dispatch [::fetch-descryptors [level (map :slug expandees)]]}))))




(reg-event-fx
 ::fetch-next
 index-interceptors
 (fn [_ [{:as opts :keys [amount index]}]]
   (when (and amount index)
     {::comms/send [:descryptors/next opts
                    {:cb (fn [cb-reply]
                           (dispatch [::db/update-db cb-reply]))}]})))




(reg-event-fx
 ::check-and-fetch
 [(inject-cofx ::inject/sub [::sub/paginated-coins])]
 (fn [{:as cofx ::sub/keys [paginated-coins]} _]
   (when (empty? (:search-tags (:db (:db cofx))))
     (let [{:keys [remaining-coins sort-idx page-coins]} paginated-coins]

       (when (or (< remaining-coins 30)
                 ;; check if there is a sort-idx gap
                 (loop [idxs (map (comp sort-idx
                                        first
                                        :indexes) page-coins)]
                   (let [[a & rst] idxs]
                     (if (or (empty? rst)
                             (nil? (first rst)))
                       false
                       (if (= (inc a) (first rst))
                         (recur rst)
                         true)))))
         
         {:dispatch [::fetch-next {:index sort-idx :amount 100}]})))))




(reg-event-fx
 ::next-page
 pagination-interceptors
 (fn [{{:as pagination :keys [page-step first-card-idx]} :db}
      [total-coins {:keys [fetch?]}]]

   (cond-> {:db (cond-> pagination
                  (< (+ page-step first-card-idx) total-coins)
                  (assoc :first-card-idx (+ first-card-idx page-step)))}

     fetch? (assoc :dispatch [::check-and-fetch]))))



(reg-event-db
 ::prev-page
 pagination-interceptors
 (fn [{:as pagination :keys [page-step first-card-idx]} _]
   (assoc pagination :first-card-idx
          (max (- first-card-idx page-step) 0))))



(reg-event-db
 ::reset-page
 pagination-interceptors
 (fn [pagination]
   (assoc pagination :first-card-idx 0)))



(reg-event-fx
 ::set-page-step
 pagination-interceptors
 (fn [{pagination :db} [new-step {:keys [fetch?]}]]
   (cond-> {:db (-> pagination (assoc :page-step new-step))}
     fetch? (merge {:dispatch [::check-and-fetch]}))))



(reg-event-db
 ::set-toolbar
 toolbar-interceptors
 (fn [toolbar [k & [v]]]
   (cond
     (fn? v) (update toolbar k v)
     (and (map? k) (nil? v)) (merge toolbar v)
     :else (assoc toolbar k v))))




(defn init! [& [db]]
  (dispatch-sync [::init db]))


