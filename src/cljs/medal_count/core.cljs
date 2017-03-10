(ns medal-count.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [ajax.core :refer [GET]]
            [goog.dom :as dom]))


;;; Helpers

(defn get-codes [countries]
  (->> countries
       (map :code)
       sort
       (into [])))

(defn get-sprite-offset [country codes]
  (* (.indexOf codes country) -17))


;;; Initial App State

(def init-db {:loading? true
              :error? false
              :medal-counts []
              :sort-by nil})


;;; Sorting Logic

(defn get-total-count [country]
  (apply + ((juxt :gold :silver :bronze) country)))

(defn add-total-count [country]
  (assoc country :total (get-total-count country)))

(defn get-medal-keys [medal]
  (get {:gold   [:gold :silver]
        :silver [:silver :bronze]
        :bronze [:bronze :gold]
        :total  [:total :gold]}
       medal))

(def descending-compare (comp - compare))

(defn sort-medals [medal countries]
  (let [countries-with-totals (mapv add-total-count countries)
        medal-keys (get-medal-keys medal)
        sort-key-fn (apply juxt medal-keys)]
    (into [] (sort-by sort-key-fn descending-compare countries-with-totals))))


;;; Actions

(rf/reg-event-db
 :sort-by
 (fn [db [_ sort-val]]
   (assoc db :sort-by sort-val)))

(rf/reg-event-db
 :process-medals-data
 (fn [db [key data]]
   (assoc db
          :medal-counts data
          :error? false
          :loading? false)))

(rf/reg-event-db
 :process-medals-data-error
 (fn [db [key data]]
   (assoc db
          :error? true
          :loading? false)))

(rf/reg-event-db
  :initialize
  (fn [db [_ sort-val]]
    (GET "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json"
         {:response-format :json
          :keywords? true
          :handler #(rf/dispatch [:process-medals-data %1])
          :error-handler #(rf/dispatch [:process-medals-data-error %1])})
    (assoc init-db :sort-by sort-val)))


;;; Subscriptions

(rf/reg-sub :loading? :loading?)
(rf/reg-sub :error? :error?)
(rf/reg-sub :sort-by :sort-by)

(rf/reg-sub
  :get-alphabetical-codes
  (fn [db _]
    (get-codes (:medal-counts db))))

(rf/reg-sub
  :medal-counts
  (fn [db _]
    (let [sort-key (:sort-by db)
          medal-counts (:medal-counts db)]
      (sort-medals sort-key medal-counts))))


;;; Components

(defn flag [country]
  (let [codes (rf/subscribe [:get-alphabetical-codes])
        y-offset (get-sprite-offset country @codes)]
    [:div.Flag {:style {:background-position-y y-offset}}]))

(defn medal-icon [color selected-color handler]
  [:div.Medal-icon {:on-click handler
                    :style {:background-color color}}])

(defn table-row [{:keys [medal-count position]}]
  (let [{:keys [code gold silver bronze total]} medal-count]
    [:tr nil
     [:td nil position]
     [:td [:div.Country [flag code] code]]
     [:td nil gold]
     [:td nil silver]
     [:td nil bronze]
     [:td.td-total total]]))

(defn table-heading []
  (let [sort-val @(rf/subscribe [:sort-by])]
    [:thead nil
      [:tr nil
        [:th {:col-span 2}]
        [:th {:class-name (if (= sort-val :gold) "selected")}
             [medal-icon "#fad033" sort-val #(rf/dispatch [:sort-by :gold])]]
        [:th {:class-name (if (= sort-val :silver) "selected")}
             [medal-icon "#9aa6ad" sort-val #(rf/dispatch [:sort-by :silver])]]
        [:th {:class-name (if (= sort-val :bronze) "selected")}
             [medal-icon "#875124" sort-val #(rf/dispatch [:sort-by :bronze])]]
        [:th {:class-name (str "th-total" (if (= sort-val :total) " selected"))
              :on-click #(rf/dispatch [:sort-by :total])} "TOTAL"]]]))

(defn medal-table []
  (let [medal-counts (rf/subscribe [:medal-counts])]
    [:div.App-container
      [:div.App-title "MEDAL COUNT"]
      [:table nil
        [table-heading]
        [:tbody nil
          (map (fn [medal-count i] [table-row {:key (:code medal-count)
                                                :medal-count medal-count
                                                :position (inc i)}])
               @medal-counts
               (range))]]]))

(defn medal-count []
  (let [loading? (rf/subscribe [:loading?])
        error? (rf/subscribe [:error?])]
    (cond @loading? [:div.Loading "Loading..."]
          @error? [:div.Error "The request has failed."]
          :else [medal-table])))


;;; App Initialization

(defn ^:export run
  ([mount-id] (run mount-id "gold"))
  ([mount-id sort-string]
   (let [sort-key (keyword sort-string)
         valid-key? (contains? #{:gold :silver :bronze :total} sort-key)]
     (if valid-key?
       (do
        (rf/dispatch-sync [:initialize (keyword sort-string)])
        (r/render [medal-count] (dom/getElement mount-id)))
       (throw (js/Error. (str "Sort option: " sort-string " is invalid, "
                              "please choose one of the following: "
                              "gold, silver, bronze, total")))))))

