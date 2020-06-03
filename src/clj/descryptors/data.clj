(ns descryptors.data
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [roll.util :as ru]
            [linked.core :as linked]
            [clojure.java.shell :as sh]
            [descryptors.db :as db]
            [taoensso.carmine :as car :refer [wcar]]
            [descryptors.schema :as schema :refer [lod]]))




(defn handshake-data-fn
  "Initial data sent to client."
  [{:as req {:keys [handshake-for client-id]} :params}]

  ;; get particular slug or default handshake
  (if handshake-for
    (do
      (db/update-clients
       update client-id
       #(-> % (assoc :descryptors/data #{handshake-for}
                     :descryptors/broadcast #{handshake-for}
                     :descryptors/slugs #{handshake-for})))
      
      [(db/descryptors2 [handshake-for] :descryptors/all)])
    
    (do
      (db/update-clients
       update client-id
       #(-> % (assoc :descryptors/broadcast (db/handshake-slugs)
                     :descryptors/slugs (db/handshake-slugs))))
      
      [(db/handshake-data)])))






(defmethod ig/init-key :descryptors.data/files [_ files]
  (info "starting descryptors.data/files:")
  ;;(info (ru/spp files))
  
  (db/init2!)

  ;; load files
  (doseq [path (keep identity files)]
    (when (ru/exists path)
      (info "loading" path)
      (->> (ru/lazy-read-edn-seq path)
           (map #(assoc % :lod :descryptors/all))
           (db/update-db2 {:mode :replace}))
      (info "[OK]" path "\n")))


  ;; load saved changes
  #_(let [db-changes-path "data/db.changes.edn"]
      (when (ru/exists? db-changes-path)
        (info "merging" db-changes-path)
        (->> (ru/lazy-read-edn-seq db-changes-path)
             (db/update-db2 {:mode :append}))
        (info "[OK] merging\n" )))
  
  nil)



(defmethod ig/halt-key! :descryptors.data/files [_ close-fn]
  (when close-fn
    (info "stopping descrypts.data/files...")
    (close-fn)))




;; Redis
;;

(defn init-redis [& [{:keys [conn]
                      :or {conn {:pool {} :spec {}}}}]]
  (try
    (car/with-new-pubsub-listener (:spec conn)
      {"update-db"
       (fn [[op _ data]]
         (when (= "message" op)
           ;; args -> [opts coins]
           ;; (info "redis updating" (count (second data)) "coins")
           (db/update-db2 (first data) (second data))))}
      
      (car/subscribe "update-db"))
    
    (catch java.net.ConnectException ex
      (info "Warning: Could not connect to Redis."))))




(defmethod ig/init-key :descryptors.data/redis [_ opts]
  (info "starting decryptors.data/redis")
  (init-redis opts))




(defmethod ig/halt-key! :descryptors.data/redis [_ listener]
  ;;(info "[halting]" listener)
  (when (not-empty listener)
    (info "unsubscribing and closing" :descryptors.data/redis)
    (car/with-open-listener listener
      (car/unsubscribe))
    (car/close-listener listener)))





;; new data
;;


(def default-new-data {:cmd  "cp"
                       :path "../destreams/data/"
                       :files []})

(def new-data-opts (atom default-new-data))



(defmethod ig/init-key :descryptors.data/new-data [_ opts]
  (info "starting descryptors.data/new-data:")
  (info (ru/spp opts))
  (reset! new-data-opts (merge default-new-data opts)))




(defmethod ig/halt-key! :descryptors.data/new-data [_ _]
  (reset! new-data-opts default-new-data))




(defn get-new-data [& args]
  (doseq [path (keep identity (:files @new-data-opts))]
    (let [[file opts] (if (string? path) [path] path)]
      (info "getting" file)
      (sh/sh (:cmd @new-data-opts)
             (str (:path @new-data-opts) file)
             file)
      
      (when (ru/exists file)
        (info "loading" file (symbol (str opts)))
        (->> (ru/lazy-read-edn-seq file)
             (db/update-db2 (merge {:mode :replace} opts)))
        (info "[OK]" file)))))










;; are we lazy now?
;; no -> pagination needs total number of coins

;; validate (conform?)

;; cleanup db for coins that haven't been opened in a while




(comment
  
  (car/with-open-listener listener
    ;;(car/unsubscribe)
    (car/subscribe "some-channel"))


  (load-sheet {:sheet-id "143IpjQD4Clz4s8KHr9LBDdaHz1shoJh7Zb7WynK9zTA"
               :creds "conf/google-creds.edn"
               :decode {"tags" :list}})



  (db/update-db2 [{:slug "bitcoin"
                   :similar (linked/set "ethereum" "lisk" "monero")}

                  {:slug "ethereum"
                   :similar (linked/set "bitcoin" "eos")}

                  {:slug "iota"
                   :similar (linked/set "monero")}]

                 {:broadcast? true})

  
  
  (->> [{:slug "ethereum"
         :indexes [{:github-index -1}]
         :name "ethurum"
         :tags (linked/set "hello" "my" "friend")}]
       (db/update-db2 {:mode :replace
                       :broadcast? true}))


  
  (->> [{:slug "bitcoin"}]
       (db/update-db2 {:mode :remove
                       :broadcast? true}))
  
  )
