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

(rf/reg-fx
  :fetch-data
  (fn [{:keys [success fail]}]
    (GET "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json"
         {:response-format :json
          :keywords? true
          :handler #(rf/dispatch (conj success %))
          :error-handler #(rf/dispatch (conj fail %))})))

(rf/reg-event-fx
  :initialize
  (fn [db [_ sort-val]]
    {:db (assoc init-db :sort-by sort-val)
     :fetch-data {:success [:process-medals-data]
                  :fail [:process-medals-data-error]}}))


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
    [:div.MedalCount-flag {:style {:background-position-y y-offset}}]))

(defn country-code [code]
  [:div.MedalCount-countryCode [flag code] code])

(defn medal-icon [color handler]
  [:div.MedalCount-icon {:on-click handler :style {:background-color color}}])

(defn table-row [{:keys [medal-count position]}]
  (let [{:keys [code gold silver bronze total]} medal-count]
    [:tr
     [:td.MedalCount-td position]
     [:td.MedalCount-td.--countryCode [country-code code]]
     [:td.MedalCount-td gold]
     [:td.MedalCount-td silver]
     [:td.MedalCount-td bronze]
     [:td.MedalCount-td.--total total]]))

(defn header-cell [sort-val children]
  (let [selected-sort-val @(rf/subscribe [:sort-by])
        is-selected (= selected-sort-val sort-val)]
    [:th.MedalCount-th {:class-name (if is-selected "--selected")}
      children]))

(defn table-heading []
  [:thead nil
    [:tr
      [:th.MedalCount-th {:col-span 2}]
      [header-cell :gold
        [medal-icon "#fad033" #(rf/dispatch [:sort-by :gold])]]
      [header-cell :silver
        [medal-icon "#9aa6ad" #(rf/dispatch [:sort-by :silver])]]
      [header-cell :bronze
        [medal-icon "#875124" #(rf/dispatch [:sort-by :bronze])]]
      [header-cell :total
        [:div.MedalCount-totalHeaderCell {:on-click #(rf/dispatch [:sort-by :total])} "TOTAL"]]]])

(defn medal-table []
  (let [medal-counts (rf/subscribe [:medal-counts])]
    [:table.MedalCount-table
      [table-heading]
      [:tbody
        (map (fn [medal-count i] [table-row {:key (:code medal-count)
                                              :medal-count medal-count
                                              :position (inc i)}])
             @medal-counts
             (range))]]))

(defn medal-count []
  (let [loading? (rf/subscribe [:loading?])
        error? (rf/subscribe [:error?])]
    [:div.MedalCount-wrap
      (cond @loading? [:div.MedalCount-loading "Loading..."]
            @error? [:div.MedalCount-error "The request has failed."]
            :else
              [:div.u-fullWidth
                [:div.MedalCount-title "MEDAL COUNT"]
                [medal-table]])]))


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
