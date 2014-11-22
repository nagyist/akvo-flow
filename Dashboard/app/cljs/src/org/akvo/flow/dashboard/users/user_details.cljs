(ns org.akvo.flow.dashboard.users.user-details
  (:require [org.akvo.flow.dashboard.components.bootstrap :as b]
            [org.akvo.flow.dashboard.dispatcher :refer (dispatch)]
            [org.akvo.flow.dashboard.ajax-helpers :refer (default-ajax-config)]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros (html)]
            [ajax.core :refer (ajax-request GET POST PUT DELETE)]))

(defn panel-header-section [{:keys [user]} owner]
  (om/component
   (html
    [:div.row.panelHeader
     [:div.col-xs-9.text-left.panelTitle
      [:h4
       (b/icon :pencil) " Edit " (get user "userName")]]
     [:div.col-xs-3.text-right
      [:button.btn.btn-primary
       (b/icon :circle-arrow-left) " Go back"]]])))

(defn update-input! [owner key]
  (fn [event]
    (om/set-state! owner key (-> event .-target .-value))))

(defn user-edit-section [{:keys [on-save user]} owner]
  (reify
    om/IInitState
    (init-state [this] user)

    om/IWillReceiveProps
    (will-receive-props [this {:keys [user]}]
      (om/set-state! owner user))

    om/IRenderState
    (render-state [this {:strs [userName emailAddress] :as state}]
      (html
       [:div.userEditSection.topMargin
        [:h2 "User info:"]
        [:form
         [:div.form-group
          [:label.control-label.text-left {:for "username"} "Name"]
          [:input.form-control {:value userName
                                :placeholder "Enter full name"
                                :on-change (update-input! owner "userName")}]]
         [:div.form-group
          [:label.control-label.text-left {:for "email"} "Email"]
          [:input.form-control {:value emailAddress
                                :placeholder "example@gmail.com"
                                :on-change (update-input! owner "emailAddress")}]]
         [:div.form-group
          (b/btn-primary {:class (when (= state user) "disabled")
                          :on-click #(do (.preventDefault %)
                                         (on-save state))}
                         (b/icon :floppy-disk) " Save user info")]]]))))

(defn generate-apikeys [owner user]
  (POST (str "/rest/users/" (get user "keyId") "/apikeys")
        (merge default-ajax-config
               {:handler (fn [response]
                           (let [access-key (get-in response ["apikeys" "accessKey"])
                                 secret (get-in response ["apikeys" "secret"])]
                             (om/set-state! owner {:access-key access-key
                                                   :secret secret})
                             (dispatch :new-access-key {:access-key access-key
                                                        :user user})))})))

(defn revoke-apikeys [owner user]
  (DELETE (str "/rest/users/" (get user "keyId") "/apikeys")
          (merge default-ajax-config
                 {:handler (fn [response]
                             (om/set-state! owner {:access-key nil :secret nil})
                             (dispatch :new-access-key {:access-key nil :user user}))})))

(defn api-keys-section [{:keys [user]} owner]
  (reify
    om/IInitState
    (init-state [this]
      {:access-key (get user "accessKey")
       :secret nil})
    om/IWillReceiveProps
    (will-receive-props [this {:keys [user]}]
      (om/set-state! owner :access-key (get user "accessKey")))
    om/IRenderState
    (render-state [this {:keys [access-key secret]}]
      (html
       [:div.apiKeySection.topMargin
        [:h2 "Manage API key:"]
        [:p "You can (re)generate or revoke an api key for this user"]
        [:form
         [:div.form-group
          [:label.control-label.text-left "Access key"]
          [:input.form-control {:type "text"
                                :value access-key}]]
         [:div.btn-group
          [:button.btn.btn-default {:on-click #(do (.preventDefault %)
                                                   (generate-apikeys owner user))}
           (b/icon :refresh) " (Re)generate"]
          [:button.btn.btn-default {:on-click #(do (.preventDefault %)
                                                   (revoke-apikeys owner user))}
           (b/icon :ban-circle) " Revoke"]]]]))))

(defn user-details [{:keys [user projects]} owner]
  (om/component
   (html
    [:div
     (om/build panel-header-section {:user user})
     (om/build user-edit-section {:user user
                                  :on-save #(dispatch :edit-user %)})
     #_(om/build roles-and-permissions-section {:user user :projects projects})
     (om/build api-keys-section {:user user})])))
