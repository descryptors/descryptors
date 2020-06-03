(ns descryptors.db
  (:require
   [linked.core :as linked]
   [taoensso.timbre :refer [info]]
   [me.tonsky.persistent-sorted-set :as pset]
   [descryptors.comms :as comms]
   [descryptors.schema :as schema :refer [lod handshake-amount]]
   [roll.util :as u]
   [re-frame.core :as rf :refer [reg-event-db reg-sub
                                 dispatch-sync dispatch
                                 subscribe path trim-v
                                 reg-event-fx reg-fx]]
   
   #?(:cljs [goog.array :as garray])
            
   #?@(:clj  [[descryptors.trace :refer [traced]]
              [clojure.core.memoize :as memo]]
       :cljs [[descryptors.trace :refer-macros [traced]]]))

  #?(:cljs (:require-macros [descryptors.db :refer [combine-cmp]])))


(defonce DB (atom {}))
(defonce CLIENTS (atom {}))


(def default-index :marketcap-index)

(def default-db-opts {:current-sort-idx default-index
                      :current-search-mode :intersection})


;; where to save data on update-db
(def alldata-path [:lod :descryptors/all])


;; save lods
(def lod-db-levels #{:descryptors/broadcast
                     :descryptors/slug+tags
                     :descryptors/svg+bare})


(def index-keys #{:marketcap-index
                  :github-index
                  #_:price-index})



;; searchable fields
(def coin-sbls
  [[[:slug]]
   [[:symbol] clojure.string/lower-case]
   [[:name] clojure.string/lower-case]
   [[:tags] seq]])

(def sbls-keys (set (map ffirst coin-sbls)))


(defn get-sbls
  "Aggregate searchable data into a set."
  [sbls coin]
  (->> (for [[path f] sbls]
         (when-let [data (get-in coin path)]
           (cond-> data
             f (f))))
       flatten
       (remove nil?)
       set))



(def db-interceptors
  [(path :db) trim-v])



(reg-event-db
 ::init
 db-interceptors
 (traced
  [old-db [new-db]]
  (merge default-db-opts #_old-db new-db)))



(reg-fx
 ::run-fn
 (fn [f] (when f (f))))



(reg-sub
 ::db
 (fn [app-db _]
   (:db app-db)))



(reg-sub
 ::alldata
 :<- [::db]
 (fn [db _]
   (get-in db alldata-path)))


(reg-sub
 ::lod
 :<- [::db]
 (fn [db _]
   (:lod db)))



(reg-sub
 ::sbls
 :<- [::db]
 (fn [db _]
   (:sbls db)))




(reg-event-db
 ::update-key
 db-interceptors
 (traced
  [db [k f & args]]
  (apply update db k f args)))




;; Indexing
;;


(defrecord Tuple #?(:clj  [^String s ^int i]
                    :cljs [s ^number i]))


#?(:clj
   (defmacro combine-cmp [& comps]
     (loop [comps (reverse comps)
            res   (num 0)]
       (if (not-empty comps)
         (recur
          (next comps)
          `(let [c# ~(first comps)]
             (if (== 0 c#)
               ~res
               c#)))
         res))))



(defn cmp-str [a1 a2]
  #?(:cljs (garray/defaultCompare a1 a2)
     :clj  (.compareTo ^Comparable a1 a2)))



(defn cmp-tuple [^Tuple t1 ^Tuple t2]
  (combine-cmp
   (compare (:i t1) (:i t2))
   (cmp-str (:s t1) (:s t2))))




(defn append-index [index tuples]
  (reduce
   #(pset/conj %1 %2 cmp-tuple)
   (or (not-empty index) (pset/sorted-set-by cmp-tuple))
   tuples))




(defn remove-index [index tuples]
  (when index
    (reduce
     #(pset/disj %1 %2 cmp-tuple)
     index tuples)))




(defn keep-idx-tuples [idx-key coins]
  (keep #(some->> (get-in % [:indexes 0 idx-key])
                  (Tuple. (:slug %)))
        coins))




(defn update-index-tuples [indexes update-fn new-data]
  (reduce
   #(update-in %1 [%2 :tuples]
               update-fn (keep-idx-tuples %2 new-data))
   indexes index-keys))




(defn update-index-slugs [indexes]
  (reduce
   #(->> (keep :s (get-in %1 [%2 :tuples]))
         #?(:clj (into (linked/set)))
         (assoc-in %1 [%2 :slugs]))
   indexes index-keys))




(defn update-index-all-slugs [indexes update-fn new-data]
  (update-in
   indexes [:all :slugs]
   (fnil (partial apply update-fn) #{})
   (map :slug new-data)))




(defn update-indexes [db new-data & [{:keys [mode]}]]
  (let [[update-fn update-all-fn]
        (case mode
          :remove [remove-index disj]
          [append-index conj])]
    (update
     db :indexes
     #(-> (update-index-tuples % update-fn new-data)
          (update-index-slugs)
          #?(:clj (update-index-all-slugs update-all-fn new-data))))))





(reg-event-db
 ::update-indexes
 db-interceptors
 (fn [db [new-data & [opts]]]
   (cond-> db
     (not-empty new-data)
     (update-indexes new-data opts))))




#?(:clj
   (defn update-handshake [db]
     (let [slugs (->> #_(vals (select-keys (:indexes db) index-keys))
                      (get-in db [:indexes default-index :slugs])
                      #_(map :slugs)
                      #_(mapcat (partial take handshake-amount))
                      (take handshake-amount)
                      (into #{} #_(linked/set)))]
       (-> db
           (assoc :handshake-slugs slugs)
           (assoc :handshake-data
                  (->> (vals (select-keys (get-in db alldata-path) slugs))
                       (lod :descryptors/svg+bare)))))))




#?(:clj
   (reg-event-db
    ::update-handshake
    db-interceptors
    (fn [db _]
      (update-handshake db))))





(defn update-entities [data new-data
                       & [{:keys [mode] :or {mode :append}}]]
  
  #_(let [new-data (if (sequential? new-data) new-data [new-data])])

  (case mode
    (:append :replace)
    (let [merge-fn (case mode
                     :append  u/deep-merge-into
                     :replace u/deep-merge)]
      (reduce
       #(update %1 (:slug %2) merge-fn %2)
       data new-data))

    
    :remove (reduce
             #(dissoc %1 (:slug %2))
             data new-data)

    ;; no matching mode
    nil))





(defn filter-sbls-slugs
  "Filter slugs that have searchable data."
  [coins]
  (->> coins
       (filter #(some-> (set (keys %))
                        (disj :slug)
                        (clojure.set/intersection sbls-keys)
                        not-empty))
       (map :slug)))




(defn update-sbls [db slugs]
  (update
   db :sbls
   (fn [sbls]
     (reduce
      (fn [m slug]
        (->> (get-in db (conj alldata-path slug))
             (get-sbls coin-sbls)
             (assoc m slug)))
      sbls slugs))))




(reg-event-db
 ::update-sbls
 db-interceptors
 (fn [db [slugs]]
   (update-sbls db slugs)))



(defn update-lod-db [db new-data & [opts]]
  (reduce
   (fn [db lod-level]
     (update-in db [:lod lod-level] update-entities
                (lod lod-level new-data) opts))
   db lod-db-levels))



(reg-event-db
 ::update-lod-db
 db-interceptors
 (fn [db [new-data & [opts]]]
   (update-lod-db db new-data opts)))




;; todo: ExecutorService

#?(:clj
   (defn broadcast [level clients new-data
                    & [{:as opts :keys [mode]}]]
     (let [new-slugs (set (map :slug new-data))]
       (doseq [client clients]
         ;; send only coins that the client already has
         (when-let [send-slugs (->> (get (val client) level)
                                    (clojure.set/intersection new-slugs)
                                    not-empty)]
           ;;(info ">>> [BROAD]" (key client))
           (comms/send-msg (key client)
                           [:descryptors/update-db
                            [(filter (comp send-slugs :slug) new-data)
                             {:mode mode}]]))))))



#?(:clj
   (defn broadcast-data
     [level clients new-data & [opts]]
     (broadcast level clients
                (schema/lod2 level new-data)
                opts)))




#?(:clj
   (reg-event-fx
    ::broadcast-data
    db-interceptors
    (fn [{:keys [db]}
         [new-data & [{:as opts :keys [level]
                       :or {level :descryptors/broadcast}}]]]
      {::broadcast [level
                    (:clients db)
                    (schema/lod2 level new-data)
                    opts]})))





(defn update-db-event-fx
  [{:keys [db]} new-data & [{:as opts :keys [mode broadcast? data-path]
                             :or {mode :append data-path alldata-path}}]]
  (let [new-slugs (map :slug new-data)]
    (merge
     (case mode
       (:append :replace)
       (let [ ;; remove changed index keys already present in db
             old-index-data
             (->> new-data
                  (filter :indexes)
                  (keep (fn [coin]
                          ;; get old indexes
                          (when-let [indexes (->> (into data-path [(:slug coin) :indexes])
                                                  (get-in db)
                                                  not-empty)]
                            (when (not= indexes (:indexes coin))
                              {:slug (:slug coin)
                               :indexes indexes})))))]

         ;; update index and entities
         {:db (-> (update-indexes db old-index-data {:mode :remove})
                  (update-in data-path update-entities new-data {:mode mode}))

          :dispatch-n [[::update-indexes new-data {:mode :append}]
                       [::update-sbls (filter-sbls-slugs new-data)]]})
       

       ;; removes all coin data from path
       :remove
       (let [ ;; select all existing index keys to remove
             all-old-index-data
             (->> new-slugs
                  (keep (fn [slug]
                          ;; get old indexes
                          (when-let [indexes (->> (into data-path [slug :indexes])
                                                  (get-in db)
                                                  not-empty)]
                            {:slug slug
                             :indexes indexes}))))]
         
         {:db (-> (update-indexes db all-old-index-data {:mode :remove})
                  (update-in data-path update-entities new-data {:mode :remove}))
          
          :dispatch [::update-sbls new-slugs]})

       ;; no matching clause
       nil))))




#?(:clj
   (defn update-db-event-fx2
     [db new-data & [{:as opts :keys [mode broadcast? data-path]
                      :or {mode :append data-path alldata-path}}]]
     (let [new-slugs (map :slug new-data)]
       (->
        (case mode
          (:append :replace)
          (let [ ;; select only changed index keys already present in db
                old-index-data
                (->> new-data
                     (filter :indexes)
                     (keep (fn [coin]
                             ;; get old indexes
                             (when-let [indexes (->> (into data-path [(:slug coin) :indexes])
                                                     (get-in db)
                                                     not-empty)]
                               (when (not= (:indexes coin) indexes)
                                 {:slug (:slug coin)
                                  :indexes indexes})))))]

            ;; remove old index data and update entities
            (-> db
                (update-indexes old-index-data {:mode :remove})
                (update-in data-path update-entities new-data {:mode mode})
                (update-indexes new-data {:mode :append})
                (update-sbls (filter-sbls-slugs new-data))
                (update-lod-db new-data {:mode mode})
                (update-handshake)))
      

          ;; removes all coin data from path
          :remove
          (let [ ;; select all existing index keys to remove
                all-old-index-data
                (->> new-slugs
                     (keep (fn [slug]
                             ;; get old indexes
                             (when-let [indexes (->> (into data-path [slug :indexes])
                                                     (get-in db)
                                                     not-empty)]
                               {:slug slug
                                :indexes indexes}))))]
        
            (-> db
                (update-indexes db all-old-index-data {:mode :remove})
                (update-in data-path update-entities new-data {:mode :remove})
                (update-sbls new-slugs)
                (update-lod-db new-data {:mode :remove})
                (update-handshake)))

          ;; no matching clause
          db)))))





(reg-event-fx
 ::update-db
 db-interceptors
 (fn [cofx [new-data & [opts]]]
   (update-db-event-fx cofx new-data opts)))




(reg-sub
 ::db-loaded?
 :<- [::db]
 (fn [db _]
   (:db-loaded? db)))



(reg-sub
 ::search-tags
 :<- [::db]
 (fn [db _]
   (:search-tags db)))



(reg-event-fx
 ::set-search-tags
 db-interceptors
 (traced
  [{{:as db :keys [known-tags]} :db} [tags]]
  (let [tags (->> (map clojure.string/lower-case tags)
                  (into (empty tags)))
        new-tags (clojure.set/difference tags known-tags)]
    (cond->
        {:db (-> db
                 (assoc :search-tags tags)
                 (update :known-tags (fnil into #{}) new-tags))}
    
      ;; don't request backend search twice for same tag
        (not (empty? new-tags))
        (assoc ::comms/send
               [:descryptors/search (vec new-tags)
                {:cb (fn [cb-reply]
                       (dispatch [::update-db cb-reply
                                  {:mode :replace}]))}])))))




(reg-sub
 ::descryptors
 :<- [::lod]
 (fn [lod-db [_ slugs level]]
   (-> (get lod-db (or level :descryptors/all))
       (select-keys slugs)
       vals)))



(reg-sub
 ::current-sort-idx
 :<- [::db]
 (fn [db _]
   (:current-sort-idx db)))


(reg-event-db
 ::current-sort-idx
 db-interceptors
 (traced
  [db [idx]]
  (assoc db :current-sort-idx idx)))



(reg-sub
 ::current-search-mode
 :<- [::db]
 (fn [db _]
   (:current-search-mode db)))



(reg-event-db
 ::current-search-mode
 db-interceptors
 (traced
  [db [search-mode]]
  (assoc db :current-search-mode search-mode)))




(defn expand-similar
  "'~ethereum ~bitcoin currency' --> {:tags #{currency} :similar [#{...} #{...}]"
  [data tags]
  (->> tags
       (reduce
        (fn [m tag]
          (if (not= \~ (first tag))
            (update m :tags conj tag)
            (let [slug (clojure.string/lower-case (subs tag 1))]
              (update m :similar conj
                      (->> (get data slug)
                           :similar
                           (#(conj % slug)) ;;add itself
                           set)))))
        {:tags #{} :similar []})))




;; custom sorting for similar
;; fixme: how to reuse existing indexes?
(defn sort-slugs [data idx-key slugs]
  (->>
   (filter #(get-in data [%1 :indexes 0 idx-key]) slugs)
   (sort-by (fn [slug] (get-in data [slug :indexes 0 idx-key])))))



(defn search-union
  [data tags & [{:as opts :keys [sbls index-slugs index-key]}]]

  (let [{:keys [tags similar]} (expand-similar data (set tags))
        all-similar (some->> (not-empty similar)
                             (apply clojure.set/union))
        index (if (empty? similar) ;; no similar search tags
                index-slugs
                (if index-key
                  (sort-slugs data index-key all-similar)
                  all-similar))]

    ;; we got some similar slugs but no tags
    (if (and (empty? tags) (not-empty all-similar))
      index
      ;;(keep data index)
      ;; filter all similar + at least one tag
      (keep (fn [slug]
              (when (not-empty
                     (clojure.set/intersection tags (sbls slug)))
                slug
                ;;(get data slug)
                ))
            index))))



(defn search-intersection
  [data tags & [{:as opts :keys [sbls index-slugs index-key]}]]

  (let [{:keys [tags similar]} (expand-similar data (set tags))
        common-similar (some->> (not-empty similar)
                                (apply clojure.set/intersection))
        index (if (empty? similar)
                index-slugs
                (if index-key
                  (sort-slugs data index-key common-similar)
                  common-similar))]

    (if (and (empty? tags) (not-empty common-similar))
      index
      ;;(keep data index)
      ;; filter common similar + all tags
      (keep (fn [slug]
              (when (= tags
                       (clojure.set/intersection tags (sbls slug)))
                slug
                ;;(get data slug)
                ))
            index))))



(reg-sub
 ::indexes
 :<- [::db]
 (fn [db _]
   (:indexes db)))


(defn current-index []
  (:current-sort-idx @DB)
  
  #_(let [{current :current-sort-idx
           :keys [indexes]} @DB]
      [current (:slugs (get indexes current))]))


(defn index-slugs [index]
  (get-in @DB [:indexes index :slugs]))


(reg-sub
 ::current-index
 :<- [::indexes]
 :<- [::current-sort-idx]
 (fn [[indexes current] _]
   [current (:slugs (get indexes current))]))



(reg-sub
 ::sorted-search-results
 :<- [::alldata]
 :<- [::search-tags]
 :<- [::sbls]
 :<- [::current-index]
 :<- [::current-search-mode]
 (fn [[data tags sbls [index-key index-slugs] search-mode] _]
   (when (not (empty? tags))
     (->>
      ((case search-mode
         :union search-union
         :intersection search-intersection)
       data tags {:sbls sbls
                  :index-slugs index-slugs
                  :index-key  index-key})
      (keep data)))))




#?(:clj
   (do
     (defn sorted-default-db []
       (let [index-key (current-index)]
         (keep (get-in @DB alldata-path)
               (index-slugs index-key))))

     #_(def sorted-default-db
         (memo/ttl sorted-default-db' {} :ttl/threshold schema/memoize-ttl))))




(reg-sub
 ::sorted-default-db
 :<- [::alldata]
 :<- [::current-index]
 (fn [[data [index-key index-slugs]] _]
   (keep data index-slugs)))



(reg-sub
 ::default-db
 :<- [::alldata]
 (fn [data [_ index-slugs]]
   (keep data index-slugs)))



(reg-sub
 ::filtered-descryptors
 :<- [::sorted-default-db]
 :<- [::sorted-search-results]
 :<- [::search-tags]
 (fn [[sorted-default-db sorted-search-results search-tags] _]
   (vec (if (empty? search-tags)
          sorted-default-db
          sorted-search-results))))



(defn descryptor [slug]
  (get-in @DB (conj alldata-path slug)))


(reg-sub
 ::descryptor
 :<- [::alldata]
 (fn [data [_ slug]]
   (get data slug)))



#_(reg-event-fx
 ::load
 db-interceptors
 (traced
  [{:keys [db]} [data]]
  {:db (update db assoc-in alldata-path data)}))




#?(:cljs
   (do

     (reg-event-fx
      ::load-handshake
      db-interceptors
      (traced
       [{:keys [db]} _]
       (when (:on-db-load db) {::run-fn (:on-db-load db)})))

     
     (defn load-handshake! [handshake]
       (dispatch-sync [::update-db handshake {:mode :replace}])
       (dispatch [::load-handshake]))

     ))




#?(:clj
   (do

     ;; unordered
     (reg-sub
      ::search
      :<- [::alldata]
      :<- [::sbls]
      :<- [::indexes]
      (fn [[data sbls indexes] [_ tags]]
        (when (not-empty tags)
          (search-union data tags {:sbls sbls
                                   :index-slugs (:slugs (:all indexes))}))))


     (defn search [tags]
       @(subscribe [::search tags]))


     (defn search2' [tags]
       (when (not-empty tags)
         (let [{:keys [sbls indexes]} @DB]
           (search-union (get-in @DB alldata-path)
                         tags {:sbls sbls
                               :index-slugs (:slugs (:all indexes))}))))

     (def search2
       (memo/ttl search2' {} :ttl/threshold schema/memoize-ttl))


     ;; {"bitcoin" #{:uids ,,,} ,,,}
     (defn make-slugs->clients [level clients]
       (->> clients
            (reduce-kv
             (fn [m client-id watch-slugs]
               (->> (get watch-slugs level)
                    (reduce #(update %1 %2 (fnil conj []) client-id)
                            m)))
             {})))
     

     
     #_(reg-fx
        ::broadcast-level
        (fn [[level clients new-data & [{:as opts :keys [mode]}]]]
          (let [slugs->clients (make-slugs->clients level clients)]
            (doseq [coin (schema/lod2 level new-data)]
              (when-let [client-ids (get slugs->clients (:slug coin))]
                (doseq [uid client-ids]
                  ;; (info ">>> [BROAD/ALL]" uid)
                  (comms/send-msg uid [:descryptors/update-db
                                       [[coin] {:mode mode}]])))))))
     


     #_(reg-fx
        ::broadcast
        (fn [[level clients new-data & [{:as opts :keys [mode]}]]]
          (let [new-slugs (set (map :slug new-data))]
            (doseq [client clients]
              ;; send only coins that the client already has
              (when-let [send-slugs (->> (get (val client) level)
                                         (clojure.set/intersection new-slugs)
                                         not-empty)]
                ;; (info ">>> [BROAD]" (key client))
                (comms/send-msg (key client)
                                [:descryptors/update-db
                                 [(filter (comp send-slugs :slug) new-data)
                                  {:mode mode}]]))))))
     
     

     (defn update-clients [& args]
       (apply swap! CLIENTS args))
     
     
     #_(reg-event-db
        ::update-clients
        db-interceptors
        (traced
         [db [& args]]
         (apply update db :clients args)))

     

     #_{:clients
        {:client-uuid {:descryptors/data #{,,,}
                       :descryptors/broadcast #{,,,}}
         ,,,}}


     (defn clients []
       @CLIENTS)
     
     #_(reg-sub
        ::clients
        :<- [::db]
        (fn [db _]
          (:clients db)))


     ;; todo: unify with slugs
     
     (defn handshake-data' []
       (:handshake-data @DB))

     (def handshake-data
       (memo/ttl handshake-data' {} :ttl/threshold schema/memoize-ttl))
     

     #_(reg-sub
        ::handshake-data
        :<- [::db]
        (fn [db _]
          (:handshake-data db)))


     (defn handshake-slugs' []
       (:handshake-slugs @DB))

     (def handshake-slugs
       (memo/ttl handshake-slugs' {} :ttl/threshold schema/memoize-ttl))
     
     
     #_(reg-sub
        ::handshake-slugs
        :<- [::db]
        (fn [db _]
          (:handshake-slugs db)))

     ))





(defn init! [& [{:as db :keys [data] :or {data {}}}]]
  (dispatch-sync [::init (assoc-in db alldata-path data)]))


#?(:clj
   (defn init2! [& [{:as db :keys [data] :or {data {}}}]]
     (reset! DB (merge default-db-opts
                       (assoc-in db alldata-path data)))))




(defn descryptors [ids & [lod-level]]
  @(subscribe [::descryptors ids lod-level]))



#?(:clj
   (defn descryptors2 [slugs & [level]]
     (-> (get (:lod @DB) (or level :descryptors/all))
         (select-keys slugs)
         vals)))




#_(defn load! [data]
  (dispatch [::load data]))




(defn update-db
  ([data] (update-db {} data))
  
  ([{:as opts :keys [mode broadcast? data-path]}
    data]
   (dispatch [::update-db data opts])))



#?(:clj
   (defn update-db2
     ([coins-or-fn] (update-db2 {} coins-or-fn))
     ([{:as opts :keys [lod mode broadcast? data-path merge-fn]
        :or {mode :append data-path alldata-path}}
       coins-or-fn]

      (if (fn? coins-or-fn)
        (->>
         (keys (get-in @DB alldata-path))
         (run!
          (fn [slug]
            (some->>
             (coins-or-fn (-> (get-in @DB alldata-path)
                              (get slug)))
             (swap! DB update-in alldata-path
                    #(update-entities %1 %2 opts))))))
        
        #_(doseq [slug (keys (get-in @DB alldata-path))])


        (let [coins-or-fn (cond->> coins-or-fn
                            (map? coins-or-fn) vector
                            lod (schema/lod lod))]
          
          (swap! DB update-db-event-fx2 coins-or-fn opts)
          
          (when broadcast?
            (case mode
              (:append :replace)
              (do (broadcast-data :descryptors/broadcast
                                  @CLIENTS coins-or-fn {:mode mode})
                  (broadcast-data :descryptors/data
                                  @CLIENTS coins-or-fn {:mode mode}))

              :remove
              (broadcast :descryptors/broadcast
                         @CLIENTS coins-or-fn {:mode mode})

              nil)))))))
