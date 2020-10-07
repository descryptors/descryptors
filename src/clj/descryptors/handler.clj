(ns descryptors.handler
  (:require [taoensso.timbre :refer [info]]
            [clojure.core.memoize :as memo]
            [ring.util.response :as resp]
            [re-frame.core :refer [dispatch subscribe]]
            [roll.sente :refer [event-msg-handler]]
            [roll.handler :refer [href]]
            [roll.state :as rs]
            [roll.util :as ru]
            [descryptors.comms :as comms]
            [descryptors.pages :as pages]
            [descryptors.db :as db]
            [descryptors.data :as data]
            [descryptors.mail :as mail]
            [descryptors.schema :refer [handshake-amount]]
            [proto.descryptors :refer [search-box]]
            [proto.descryptors.common :refer [inline-html]]
            [proto.descryptors.defaults :as defaults
             :refer [svg-loading visible-charts coins-on-page]]))



(defonce stats (atom {:connects (sorted-map)}))


(defonce day-millis (* 24 60 60 1000))
(defn day []
  (-> (System/currentTimeMillis)
      (quot day-millis)
      (* day-millis)))



;; WebSocket
;;

(defn watch-connected-clients [connected-uids]
  (add-watch
   connected-uids :connected-uids
   (fn [k a {any-old :any} {any-new :any}]
     (let [connects (count (clojure.set/difference any-new any-old))
           ;;handshake-slugs @(subscribe [::db/handshake-slugs])
           disconnected (clojure.set/difference any-old any-new)]

       ;; update stats
       (swap! stats update :connects update (day) (fnil + 0) connects)
       
       ;; keep track of what coins we send on handshake
       ;; (for now slugs are updated during actual handshake)
       #_(when-not (empty? connected)
           (dispatch [::db/update-clients
                      merge (zipmap connected (repeat handshake-slugs))]))

       (when-not (empty? disconnected)
         (db/update-clients (partial apply dissoc) disconnected)))))
  
  #(do
     (remove-watch connected-uids :connected-uids)
     (db/update-clients (constantly nil))))




(defmethod event-msg-handler
  :contact/send
  [{:as ev-msg :keys [client-id ?data ?reply-fn]}]
  (when (not-empty (:message ?data))
    (info "[MESSAGE]" client-id)
    (mail/send-message ?data)))



(defmethod event-msg-handler
  :descryptors/search
  [{:as ev-msg :keys [client-id ?data ?reply-fn]}]

  (let [tags ?data
        client-slugs (get-in (db/clients)
                             [client-id :descryptors/slugs])

        coin-slugs (set (db/search2 tags))
        
        new-slugs (clojure.set/difference coin-slugs client-slugs)]
    
    (when-not (empty? new-slugs)
      (db/update-clients update-in [client-id :descryptors/slugs]
                         (fnil into #{}) new-slugs)
      (?reply-fn
       (db/descryptors2 new-slugs :descryptors/slug+tags)))))




(defmethod event-msg-handler
  :descryptors/get
  [{:as ev-msg :keys [client-id ?data ?reply-fn]}]

  (let [[level slugs] (first ?data)]
    (?reply-fn
     (db/descryptors2 slugs level))

    ;;(info ">>> [GET]" level (vec slugs))
    
    ;; update watched coins
    (case level
      ;; send data
      (:descryptors/all
       :descryptors/data)
      ;; keeping track of all data coins (optimize)
      (db/update-clients update-in [client-id :descryptors/data]
                         (fnil into #{})
                         slugs)

      ;; keeping track of last viewed coin
      #_(dispatch [::db/update-clients
                   assoc-in [client-id :descryptors/data]
                   (set slugs)])
      nil)
    
    ;; broadcast updates for tracked coins
    (when (isa? :descryptors/broadcast level)
      (db/update-clients update-in [client-id :descryptors/broadcast]
                         (fnil into #{})
                         slugs))


    ;; need this for getting next coins (but we added it during search
    ;; and next)
    #_(db/update-clients update-in [client-id :descryptors/slugs]
                         (fnil into #{})
                         slugs)))




(defmethod event-msg-handler
  :descryptors/next
  [{:as ev-msg :keys [client-id ?data ?reply-fn]}]
  
  (let [{:keys [index amount]} ?data
        amount (or amount handshake-amount)
        
        indexed-slugs (db/index-slugs index)

        next-slugs (->> (get-in (db/clients)
                                [client-id :descryptors/slugs])
                        (clojure.set/difference indexed-slugs)
                        (take amount))]
    
    (when (not-empty next-slugs)
      ;; keep track of sent coins
      (db/update-clients update-in [client-id :descryptors/slugs]
                         (fnil into #{}) next-slugs)
      (?reply-fn
       (db/descryptors2 next-slugs :descryptors/slug+tags)))))






;; HTTP
;;


(defn handle-index [{:keys [reitit.core/router] :as req}]
  (some->> (db/sorted-default-db)
           (take coins-on-page)
           (pages/index-page
            router
            {:search-box (search-box
                          {:search-icon
                           [:div (inline-html svg-loading)]})
             :visible-charts visible-charts
             :theme (pages/get-cookie-theme req)})
           resp/response))





(defn handle-static-index
  "Static index with pagination."
  [{:as req {:keys [page-number]} :path-params :keys [reitit.core/router]}]
  
  (let [page-number (clojure.edn/read-string page-number)]
    (if (or (not (number? page-number)) (< page-number 2))
      (resp/redirect "/")
      
      (let [ ;; we don't show search-box
            coins-on-page (inc coins-on-page)
            [page-coins more-coins?]
            (some->> (db/sorted-default-db)
                     (drop (* (dec page-number) coins-on-page))
                     ((juxt (partial take coins-on-page)
                            (comp not empty? (partial drop coins-on-page)))))]

        (if (not-empty page-coins)
          (->> page-coins
               (pages/static-index-page
                router
                page-number  more-coins?
                {:theme (pages/get-cookie-theme req)
                 :visible-charts visible-charts})
               (resp/response))
      
          (resp/redirect "/"))))))




(defn handle-descryptor
  [{{slug :slug} :path-params :as req}]
  (or (some->> (db/descryptor (clojure.string/lower-case slug))
               (pages/descryptor-page
                (:reitit.core/router req)
                {:theme (pages/get-cookie-theme req)})
               (resp/response))
      {:status 404 :body (str "cannot find " slug)}))





(defn handle-static-descryptor
  [{{slug :slug} :path-params :as req}]
  (or (some->> (db/descryptor (clojure.string/lower-case slug))
               (pages/static-descryptor-page
                (:reitit.core/router req)
                {:theme (pages/get-cookie-theme req)})
               (resp/response))
      {:status 404 :body (str "cannot find " slug)}))





(defn handle-status [{:as req :keys [params]}]
  (let [req-pass (:pass params)
        conf-pass (get-in rs/config [:roll/handler ::pass])]
    (if (or (not conf-pass)
            (= req-pass conf-pass))
      (resp/response
       (->> (count (:any @(comms/connected-uids)))
            (assoc @stats :amount)
            (pages/status-page)))

      (resp/not-found ""))))
