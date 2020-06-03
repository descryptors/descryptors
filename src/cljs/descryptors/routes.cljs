(ns descryptors.routes
  (:require [re-frame.core :as re-frame]
            [reitit.core :as rc]
            [reitit.frontend :as rf]
            [reitit.frontend.controllers :as rfc]
            [reitit.frontend.easy :as rfe]
            [descryptors.index.events :as evt]))



(def routes
  (rf/router
   [["/"
     {:name ::index
      ;; enable when single -> js -> index
      :controllers
      [{:start (fn [_] (re-frame/dispatch [::evt/check-and-fetch]))}]}]

    ["/me/about"
     {:name ::about}]

    ["/me/code"
     {:name ::code}]

    ["/me/terms-and-conditions"
     {:name ::terms}]
    
    ["/:slug"
     {:name ::descryptor
      :controllers
      [{:parameters {:path [:slug]}
        ;; we need current :lod. moved to component.
        #_:start
        #_(fn [{:keys [path]}]
            (re-frame/dispatch
             [::evt/expand-descryptors :descryptors/data+bare
              [{:slug (:slug path)}]]))}]}]]
   
   #_{:conflicts nil}))

;; :controllers [{:start (fn [& params] ...) :stop ...}]



(def app-db-path ::routes)

(def routes-interceptors
  [(re-frame/path app-db-path) re-frame/trim-v])


(re-frame/reg-event-db
 ::initialize-db
 routes-interceptors
 (fn [_ _]
   {:current-route nil}))


(re-frame/reg-event-fx
 ::navigate
 routes-interceptors
 (fn [{db :db} [& route]]
   {::navigate! route}))


(re-frame/reg-fx
 ::navigate!
 (fn [route]
   (apply rfe/push-state route)))


(re-frame/reg-event-db
 ::navigated
 routes-interceptors
 (fn [db [new-match]]
   (let [old-match (:current-route db)
         controllers (rfc/apply-controllers (:controllers old-match) new-match)]
     (assoc db :current-route (assoc new-match :controllers controllers)))))


(re-frame/reg-sub
 ::db
 (fn [db]
   (get db app-db-path)))


(re-frame/reg-sub
 ::current-route
 :<- [::db]
 (fn [db]
   (:current-route db)))



(defn on-navigate [new-match]
  (when (not-empty (:path new-match))
    (re-frame/dispatch [::navigated new-match])))



(defn href [& params]
  (apply rfe/href params))



(defn goto [& [k params query]]
  (re-frame/dispatch [::navigate k params query]))



(defn init-routes! [& route]
  (re-frame/dispatch [::initialize-db])
  (rfe/start!
   routes
   on-navigate
   {:use-fragment false})
  (when (not-empty route)
    (apply goto route)))
