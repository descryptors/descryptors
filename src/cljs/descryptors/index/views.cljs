(ns descryptors.index.views
  (:require [dommy.core      :as d]
            [reagent.core    :as r]
            [re-frame.core   :refer [subscribe dispatch]]
            [taoensso.timbre :refer [info]]
            [linked.core :as linked]
            [clojure.string :as string]
            [descryptors.index.events :as evt]
            [descryptors.index.subs   :as sub]
            [descryptors.db           :as db]
            [descryptors.schema :refer [version]]
            [descryptors.routes :as routes :refer [href goto]]
            [roll.sente :as sente]
            [proto.mixins :as mixins]
            [proto.util :as u]
            [proto.descryptors.defaults :as defaults :refer [svg-search]]
            
            [proto.descryptors :as des
             :refer [toolbar mobile-toolbar search-box
                     amount-of-cards-in-rows toggle-info!
                     contact-form about highlighter search-modes]]
            
            [proto.descryptors.common :as common
             :refer [descryptors-grid navigation logo
                     descryptor-single header footer]]))





(defonce mobile? (r/atom false))

(defonce mobile-show (r/atom nil))
(defn toggle-mobile-show [path]
  (->> path
       (swap! mobile-show
              (fn [current new]
                (when-not (= current new) new)))))


(defonce throttle300 (u/throttler 300))
(defonce on-grid-resize
  (fn []
    (throttle300
     (fn []
       (when-let [new-step (des/amount-of-cards-in-rows
                            (:toolbar/rows-per-page
                             @(subscribe [::sub/toolbar])))]

         (cond-> new-step
           (and @mobile? (not= @mobile-show :toolbar/search))
           inc
           :default (->> (conj [::evt/set-page-step])
                         (dispatch))))))))



;; save which tags we clicked, keep track of that and
;; highlight them
(defonce highlight-tags (r/atom (linked/set)))
(defonce highlight-fn (highlighter))
(defonce highlight #(highlight-fn @highlight-tags))


(defonce reset-page #(dispatch [::evt/reset-page]))

(defn set-title [& [coin]]
  (set! (. js/document -title)
        (str "Descryptors"
             (some->> (:name coin)
                      (str " - ")))))


(defonce index-scroll (atom 0))

(defn descryptors-grid* [props coins]
  (let [expand-coins
        #(dispatch
          [::evt/expand-descryptors :descryptors/svg+bare
           (first (r/children %))])]
    
    (r/create-class
     {:reagent-render
      (fn [props coins]
        [mixins/window-event-listener
         {:on-resize on-grid-resize
          :on-mount on-grid-resize}
         [descryptors-grid props coins]])

      :component-did-update
      (fn [this]
        (expand-coins this)
        (when-let [on-update (:on-update props)]
          (on-update)))
    
      :component-did-mount
      (fn [this]
        (set-title)
        (window.scroll 0 @index-scroll)
        (expand-coins this)

        ;; we might need to inc card number when it's mobile and
        ;; search-card is not visible
        (add-watch mobile? :mobile
                   (fn [_ _ old new]
                     (when (not= old new)
                       (on-grid-resize))))
        
        (when-let [on-mount (:on-mount props)]
          (on-mount)))

      :component-will-unmount
      (fn [_]
        (reset! index-scroll js/pageYOffset)
        (remove-watch mobile? :mobile?))})))




(defn descryptor-single* [props coin]
  (let [throttle (u/throttler 42)
        on-resize #(some-> js/alignDots throttle)
        expand-coin #(dispatch
                      [::evt/expand-descryptors :descryptors/all
                       (r/children %)])]
    
    (r/create-class
     {:reagent-render
      (fn [props coin]
        [mixins/window-event-listener
         {:on-resize on-resize
          :on-update js/alignDots
          :on-mount js/alignDots}
         
         [descryptor-single props coin]])

      :component-did-mount
      (fn [this]
        (window.scroll 0 0)
        (expand-coin this)
        (set-title (first (r/children this))))

      :component-did-update
      (fn [this]
        (expand-coin this)
        (set-title (first (r/children this))))})))



(defn toggle-theme [path]
  (dispatch
   [::evt/set-toolbar path
    (fn [prev]
      (let [curr (first (disj defaults/themes prev))]
        (des/transition-theme! prev curr)
        (dispatch [::evt/set-cookie {:toolbar {path curr}}])
        curr))]))



(defn accept-terms []
  (dispatch [::evt/set-key :accepted-terms true]))



(defn handle-toolbar-change [& [path value event]]
  (condp = path
    :toolbar/sort-idx
    (do (reset-page)
        (dispatch [::evt/set-toolbar path value])
        ;; fixme: how to remove this duplication?
        (dispatch [::db/current-sort-idx value])
        ;; make sure we have enough coins
        (dispatch [::evt/check-and-fetch]))

    :toolbar/charts
    (dispatch [::evt/set-toolbar path #(u/toggle-set-item % value)])

    :toolbar/search
    (dispatch [::evt/set-toolbar path #(not %)])

    :toolbar/rows-per-page
    (when-let [new-step (des/amount-of-cards-in-rows value)]
      (dispatch [::evt/set-page-step new-step
                 {:fetch? true}])
      (dispatch [::evt/set-toolbar path value]))

    :toolbar/theme
    (toggle-theme path)

    :toolbar/info
    (do (des/toggle-info!)
        (dispatch [::evt/set-toolbar path #(not %)]))
    
    nil))





(defn handle-mobile-toolbar-change [& [path value event]]
  (condp = path
    :toolbar/charts
    (toggle-mobile-show :toolbar/charts)

    :toolbar/sort-idx
    (toggle-mobile-show :toolbar/sort-idx)
    
    :toolbar/theme
    (toggle-theme path)

    :toolbar/info
    (do (des/toggle-info!)
        (dispatch [::evt/set-toolbar path #(not %)]))

    :toolbar/search
    (do (if-let [bottom (some-> (d/sel1 :.search-card)
                                (d/bounding-client-rect)
                                :bottom)]
          ;; visible
          (if (neg? bottom)
            (window.scroll 0 0)
            (toggle-mobile-show :toolbar/search))
      
          ;; hidden
          (do
            (window.scroll 0 0)
            (toggle-mobile-show :toolbar/search)))
        
        (on-grid-resize))
    
    nil))





(defn index []
  (let [href-about (href ::routes/about)
        href-terms (href ::routes/terms)
        ;;href-code  (href ::routes/code)
        href-descryptor (href ::routes/descryptor {:slug ""})
        
        wrapper-classes {::routes/descryptor "wrapper--single"}
        current-route  (subscribe [::routes/current-route])
        tags   (subscribe [::db/search-tags])
        paginated-coins (subscribe [::sub/paginated-coins])
        filtered-slugs   (subscribe [::sub/filtered-slugs])
        toolbar-state   (subscribe [::sub/toolbar])
     
        pagination (subscribe [::sub/pagination])
        
        current-sort-idx (subscribe [::db/current-sort-idx])

        logo-on-click (fn [e]
                        (case (:name (:data @current-route))
                          ::routes/index (reset-page)
                          ::routes/descryptor (goto ::routes/index)
                          nil))

        
        on-tag-click
        (fn [evt]
          (let [tag (subs (d/text (.. evt -target)) 1)]
            (swap! highlight-tags #((if (get % tag) disj conj) % tag))))

        on-tag-click-add
        (fn [evt]
          (let [tag (subs (d/text (.. evt -target)) 1)]
            ;; toggle tag highlight
            (swap! highlight-tags #(if (get % tag) % (conj % tag)))
            (goto ::routes/index)))
        

        more-on-click
        (fn [slug evt]
          (reset! highlight-tags
                  (linked/set (str "~" slug)))
          (goto ::routes/index))

        
        current-search-mode (subscribe [::db/current-search-mode])
        search-modes-opts
        (let [on-clicker
              (fn [id]
                (fn [_]
                  (do (when-not (empty? @tags) (reset-page))
                      (dispatch [::db/current-search-mode id]))))]
          (-> search-modes
              (assoc-in [:intersection :on-click] (on-clicker :intersection))
              (assoc-in [:union :on-click] (on-clicker :union))))


        on-click-next-page
        #(do (dispatch [::evt/next-page (:total-coins @paginated-coins)
                        {:fetch? true}])
             (.preventDefault %))

        on-click-prev-page
        #(do (dispatch [::evt/prev-page])
             (.preventDefault %))
        
        name-opts-fn
        (fn [{slug :slug}] {:href (str href-descryptor slug)})

        next-card-opts-fn
        (fn []
          (let [current-slug (-> @current-route
                                 :parameters :path :slug
                                 string/lower-case)
                slugs @filtered-slugs
                total (count slugs)
                idx (u/find-item current-slug slugs)]
            (if (and idx (< idx (dec total)))
              {:href (str href-descryptor (nth slugs (inc idx)))
               :on-click #(when (and (empty? @tags) (< (- total idx) 30))
                            (dispatch [::evt/fetch-next
                                       {:index @current-sort-idx :amount 30}]))}
              {:class "disabled"})))

        prev-card-opts-fn
        (fn []
          (let [current-slug (-> @current-route
                                 :parameters :path :slug
                                 string/lower-case)
                slugs @filtered-slugs
                idx (u/find-item current-slug slugs)]
            (if (and idx (< 0 idx))
              {:href (str href-descryptor (nth slugs (dec idx)))}
              {:class "disabled"})))

        
        index-navigation-fn
        #(let [{:keys [remaining-coins first-card-idx]} @paginated-coins]
           [navigation
            {:prev-opts
             (if (< 0 first-card-idx)
               {:target :self
                :on-click on-click-prev-page}
               {:class "disabled"})
                             
             :next-opts
             (if (< 0 remaining-coins)
               {:target :self
                :on-click on-click-next-page}
               {:class "disabled"})}])

        
        single-navigation-fn
        #(do [navigation
              {:prev-opts (prev-card-opts-fn)
               :next-opts (next-card-opts-fn)}])


        card-opts-fn
        (fn [coin]
          {:tabIndex 1
           :on-mouse-down #(.preventDefault %)
           :on-key-down
           (fn [e]
             (condp = (.. e -key)
               "Enter" (goto ::routes/descryptor {:slug (:slug coin)})
               false))})

        
        
        throttle (u/throttler 200)
        mobile-track-fn (u/mobile-tracker mobile? 750)
        check-mobile #(throttle mobile-track-fn)]

    

    (r/create-class
     {:component-did-mount
      (fn [_]
        (add-watch highlight-tags :tags
                   (fn [_ _ _ tags]
                     (highlight-fn tags)
                     (dispatch [::db/set-search-tags tags])
                     (reset-page))))

      :component-will-unmount
      (fn [_]
        (remove-watch highlight-tags :tags))

      
      :reagent-render
      (fn []
        (let [rp (-> @current-route :parameters :path)
              cp (-> @current-route :data :name)
              {:keys [remaining-coins first-card-idx
                      total-coins]} @paginated-coins]
          
          [mixins/window-event-listener
           {:on-resize check-mobile
            :on-mount check-mobile}


           (case cp
             ;; About
             ::routes/about
             [des/about
              {:on-close #(goto ::routes/index)
               :on-contact-click #(dispatch [::evt/set-toolbar :show-contact true])}]

             ;; Code
             ::routes/code
             [des/code-page
              {:on-close #(goto ::routes/index)}]

             ;; Terms and Conditions
             ::routes/terms
             [des/terms-and-conditions
              {:on-close #(goto ::routes/index)
               :on-contact-click #(dispatch [::evt/set-toolbar :show-contact true])}]

             
             ;; Default view
             
             [:div.wrapper
              {:class (str (get wrapper-classes cp)
                           (when (:show-contact @toolbar-state) " contact-visible"))}

              ;; Overlay
              [:div.overlay {:on-click #(handle-toolbar-change :toolbar/info)}
               (when @mobile?
                 (->> (repeat 5 [:div.overlay__item])
                      (into [:div.overlay__mobile])))]


              ;; COOKIE NOTICE
              ;;
              (when-not @(subscribe [::sub/accepted-terms])
                [des/cookie-notice
                 {:terms-opts {:href href-terms}
                  :on-close accept-terms}])
              
              
              ;; HEADER
              (if (nil? @mobile?)
                ;; we don't know if we're on mobile yet, so display only
                ;; the logo
                [common/header
                 [logo {:target :self
                        :on-click logo-on-click}]]
                
                (if @mobile?
                  
                  ;; MOBILE HEADER
                  ;;
                  (case cp

                    ;; INDEX

                    ::routes/index
                    [:<>
                     [des/sticky-header
                      [logo {:target :self
                             :on-click logo-on-click}]
                      (index-navigation-fn)]
                     
                     [des/sticky-mobile-toolbar
                      {:class "toolbar--mobile"
                       :show #{:mobile-toolbar/all}
                       :active-icon @mobile-show
                       :on-change handle-mobile-toolbar-change}]

                     [toolbar {:state @toolbar-state
                               :class "toolbar--mobile-grid"
                               :show #{@mobile-show}
                               :on-change handle-toolbar-change}]]


                    ;; SINGLE
                    
                    ::routes/descryptor
                    [common/header
                     [logo {:target :self
                            :on-click logo-on-click}]
                     
                     [mobile-toolbar
                      {:class "toolbar--mobile toolbar--mobile-single"
                       :show #{:toolbar/theme :toolbar/info}
                       :active-icon @mobile-show
                       :on-change handle-mobile-toolbar-change}]
                     
                     (single-navigation-fn)]

                    nil)


                  ;; DESKTOP HEADER

                  (case cp

                    ;; INDEX

                    ::routes/index
                    [des/sticky-header
                     [logo {:target :self
                            :on-click logo-on-click}]
                     
                     [toolbar {:state @toolbar-state
                               :class "toolbar toolbar--grid"
                               :show #{:toolbar/all}
                               :on-change handle-toolbar-change}]
                     
                     (index-navigation-fn)]

                    
                    ;; SINGLE
                    
                    ::routes/descryptor
                    [common/header
                     [logo {:target :self
                            :on-click logo-on-click}]
                     
                     [toolbar {:state @toolbar-state
                               :class "toolbar toolbar--single"
                               :show #{:toolbar/theme :toolbar/info}
                               :on-change handle-toolbar-change}]
                     
                     (single-navigation-fn)]

                    nil)))


              ;; BODY
              ;;
              (case cp
                
                ;; INDEX
                
                ::routes/index
                [descryptors-grid*
                 {:on-mount  highlight
                  :on-update highlight
                  :name-opts-fn name-opts-fn
                  :card-opts-fn card-opts-fn
                  :tag-opts {:on-click on-tag-click}
                  :visible-charts (:toolbar/charts @toolbar-state)
                  :search-box
                  (if @mobile?
                    (when (or (= @mobile-show :toolbar/search)
                              (:toolbar/info @toolbar-state))
                      [search-box
                       {:state highlight-tags
                        :props {:class "active"}
                        :toolbar-opts {:search-modes-opts search-modes-opts
                                       :search-mode @current-search-mode}}])

                    [search-box
                     {:state highlight-tags
                      :toolbar-opts {:search-modes-opts search-modes-opts
                                     :search-mode @current-search-mode}
                      :search-icon [:div (u/inline-html defaults/svg-search)]}])}
                 
                 (:page-coins @paginated-coins)]


                ;; SINGLE
                
                ::routes/descryptor
                [:<>
                 [:div.clickback {:on-click logo-on-click
                                  :style {:position :fixed
                                          :top 0
                                          :left 0
                                          :height "100%"
                                          :width "100%"}}]
                 
                 (let [slug (string/lower-case (:slug rp))
                       coin (-> @(subscribe [::db/descryptor slug])
                                ;; make sure we have slug at least
                                (assoc :slug slug))]
                   [descryptor-single*
                    {:tag-opts {:on-click on-tag-click-add}
                     :more-on-click (partial more-on-click slug)
                     :mobile? @mobile?}
                    coin])]

                nil)
              

              ;; FOOTER TOOLBAR
              
              (when (= cp ::routes/index)
                [toolbar {:state @toolbar-state
                          :class "toolbar toolbar--footer"
                          :show #{:footer-toolbar/all}
                          :on-change handle-toolbar-change}])

              
              ;; FOOTER
              
              (when (= cp ::routes/index)
                [footer {:version version
                         :support-links common/support-links
                         :logo-opts  {:target :self
                                      :on-click logo-on-click}
                         :about-opts {:href href-about}
                         :code-opts {:href "https://github.com/descryptors"
                                     :target :blank}
                         :reddit-opts {:target :blank
                                       :href "https://reddit.com/r/descryptors"}
                         :terms-opts {:href href-terms}
                         :contact-opts {:target :self
                                        :on-click #(dispatch [::evt/set-toolbar :show-contact true])}}])


              ;; CONTACT
              
              (when (:show-contact @toolbar-state)
                [contact-form {:on-close #(dispatch [::evt/set-toolbar :show-contact false])
                               :on-submit #(sente/send-msg [:contact/send %])}])])]))})))
