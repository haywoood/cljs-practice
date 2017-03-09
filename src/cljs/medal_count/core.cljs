(ns medal-count.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [ajax.core :refer [GET]]
            [goog.dom :as dom]))


;;; Helpers

(defn get-codes [countries]
  (->> countries
       (mapv :code)
       sort
       (into [])))

(defn get-sprite-offset [country codes]
  (* (.indexOf codes country) -17))

(def get-country-data (juxt :code :gold :silver :bronze :total))


;;; Initial App State

(def init-db {:loading? true
              :error? false
              :medal-counts []
              :sort-by :gold})


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
  (fn [db _]
    (GET "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json"
         {:response-format :json
          :keywords? true
          :handler #(rf/dispatch [:process-medals-data %1])
          :error-handler #(rf/dispatch [:process-medals-data-error %1])})
    init-db))


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
  (let [codes @(rf/subscribe [:get-alphabetical-codes])
        y-offset (get-sprite-offset country codes)]
    [:div {:style {:backgroundPositionY y-offset} :className "Flag"}]))

(defn medal-icon [color selected-color handler]
  [:div {:onClick handler
         :className "Medal-icon"
         :style {:backgroundColor color}}])

(defn table-row [data i]
  (let [[code gold silver bronze total] (get-country-data data)]
    [:tr {:key code}
     [:td nil i]
     [:td {:style {:width 300}}
      [:div {:className "Country"}
        (flag code) code]]
     [:td nil gold]
     [:td nil silver]
     [:td nil bronze]
     [:td {:className "td-total"} total]]))

(defn table-heading []
  (let [sort-val @(rf/subscribe [:sort-by])]
    [:thead nil
      [:tr nil
        [:th {:colSpan 2}]
        [:th {:className (if (= sort-val :gold) "selected")}
             (medal-icon "gold" sort-val #(rf/dispatch [:sort-by :gold]))]
        [:th {:className (if (= sort-val :silver) "selected")}
             (medal-icon "silver" sort-val #(rf/dispatch [:sort-by :silver]))]
        [:th {:className (if (= sort-val :bronze) "selected")}
             (medal-icon "brown" sort-val #(rf/dispatch [:sort-by :bronze]))]
        [:th {:className (str "th-total" (if (= sort-val :total) " selected"))
              :onClick #(rf/dispatch [:sort-by :total])} "TOTAL"]]]))

(defn medal-count []
  (let [loading? @(rf/subscribe [:loading?])
        error? @(rf/subscribe [:error?])
        medal-counts @(rf/subscribe [:medal-counts])]
    (cond loading? [:div {:className "Loading"} "Loading..."]
          error? [:div {:className "Loading"} "The request has failed."]
          :else
            [:div {:className "App-container"}
              [:div {:className "App-title"} "MEDAL COUNT"]
              [:table nil
                (table-heading)
                [:tbody nil
                  (doall
                   (map (fn [count i] (table-row count (inc i)))
                        medal-counts
                        (range)))]]])))


;;; App Initialization

(do
  (rf/dispatch-sync [:initialize])
  (r/render [medal-count] (dom/getElement "app")))
