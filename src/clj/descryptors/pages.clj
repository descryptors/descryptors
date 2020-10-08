(ns descryptors.pages
  (:require
   [hiccup.core :refer [html]]
   [clojure.core.memoize :as memo]
   [roll.handler :refer [href]]
   [descryptors.schema :as schema :refer [version]]
   [descryptors.util :as util]
   [proto.charts.util :as pcu]
   [proto.charts.line :as pcl]
   [proto.descryptors.defaults :as pdd]
   [proto.descryptors.common :as common
    :refer [descryptors-grid descryptor-single
            logo navigation header footer]]))




(def templates
  {:index (slurp "templates/index.html")
   :status (slurp "templates/status.html")
   :descryptor (slurp "templates/descryptor.html")
   :static-index (slurp "templates/static-index.html")
   :static-descryptor (slurp "templates/static-descryptor.html")})




(defn get-cookie-theme [req]
  (some-> (get-in req [:cookies "descryptors" :value])
          util/decode-edn
          (get-in [:toolbar :toolbar/theme])))




(defn set-html-theme [theme html-string]
  (clojure.string/replace
   html-string "{{html-opts}}"
   (if-let [css-class (get pdd/theme-classes theme)]
     (str "class=" css-class)
     "")))



(defn index-page' [router opts coins]
  (->>
   (html
    [:div.wrapper
     (header
      (logo {:href (href router :index)})
      (navigation
       {:prev-opts {:class "disabled"}
        :next-opts {:href (href router :page {:page-number 2})}}))

     (descryptors-grid
      (assoc opts :name-opts-fn
             #(do {:href (href router :descryptor {:slug (:slug %)})}))
      coins)

     (footer {:logo-opts {:href (href router :index)}
              :version version
              :support-links common/support-links})])
   
   (clojure.string/replace
    (:index templates) "{{page-contents}}")

   (set-html-theme (:theme opts))))



(def index-page
  (memo/ttl index-page' {} :ttl/threshold schema/memoize-ttl))




(defn static-index-page' [router page-number more-coins? opts coins]
  (->> (html
        [:div.wrapper
         (header
          (logo {:href (href router :index)})
          (navigation
           {:prev-opts
            (cond
              (< 1 page-number) {:href (href router :page
                                             {:page-number (dec page-number)})}
              (= 1 page-number) {:href (href router :index)}
              :default {:class "disabled"})
            
            :next-opts
            (if more-coins?
              {:href (href router :page {:page-number (inc page-number)})}
              {:class "disabled"})}))

         (descryptors-grid
          (assoc opts :name-opts-fn
                 #(do {:href (href router :descryptor {:slug (:slug %)})}))
          coins)

         (footer {:logo-opts {:href (href router :index)}
                  :version version
                  :support-links common/support-links})])
       
       (clojure.string/replace
        (:static-index templates) "{{page-contents}}")

       (set-html-theme (:theme opts))))


(def static-index-page
  (memo/ttl static-index-page' {} :ttl/threshold schema/memoize-ttl))




(defn descryptor-page' [router opts coin]
  (->
   (->> (html
         [:div.wrapper
          (header (logo {:href (href router :index)}))

          (descryptor-single opts coin)])
       
        (clojure.string/replace
         (:descryptor templates) "{{page-contents}}")

        (set-html-theme (:theme opts)))
   
   (clojure.string/replace
    "{{page-title}}" (:slug coin))))


(def descryptor-page
  (memo/ttl descryptor-page' {} :ttl/threshold schema/memoize-ttl))


(defn static-descryptor-page' [router opts coin]
  (->
   (->> (html
         [:div.wrapper
          (header (logo {:href (href router :index)}))

          (descryptor-single opts coin)])
       
        (clojure.string/replace
         (:static-descryptor templates) "{{page-contents}}")

        (set-html-theme (:theme opts)))
   
   (clojure.string/replace
    "{{page-title}}" (:slug coin))))



(def static-descryptor-page
  (memo/ttl static-descryptor-page' {} :ttl/threshold schema/memoize-ttl))






(defn stats-chart [data]
  (when (not-empty data)
    (->> {:data data}
         (pcu/add-spec
          {:view :1w
           :line-width "1.2px"
           :grid-width "1px"
           :ymargin 50
           :font-size 12
           :size [600 200]
           :ydomain [0]
           :yformatter long})
         (pcl/add-ticks {:xticks? true :yticks? true :screen :sm})
         pcl/line-chart
         :data)))



(defn status-page [stats]
  (->> (html
        [:p "Connected: " [:b (:amount stats)]]
        [:div {:style {:width "600px" :padding-top "2em"}}
         (stats-chart (:connects stats))])
   
       (clojure.string/replace
        (:status templates) "{{page-contents}}")))
