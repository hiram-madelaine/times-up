(ns times-up.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [timeout chan >! put! <!]]))

(enable-console-print!)

(def states {:init :playing
              :playing :switch-team
              :switch-team :playing})

(def app-state (atom {:state :playing
                      :round 1
                      :timeout 5000
                      :team 0
                      :nb-teams 2
                      :person-id 0
                      :score {0 0 1 0}
                      :persons [{:person "Henry Ford", :id 0} {:person "Bruce Spreegsteen", :id 1} {:person "Jean Cocteau", :id 2} {:person "Capitaine Crochet", :id 3}]}))

;;;;;;;;;;;;;;;;;;; Event handlers ;;;;;;;;;;;;;;;;;;;;;


(defmulti handle-event (fn [[cmd param] app owner] cmd))


(defmethod handle-event :OK
 [_ app owner]
  (let [team (:team @app)]
    (om/transact! app [:score team] inc)
    (om/transact! app [:person-id] inc)))

(defmethod handle-event :KO
  [_ app owner]
  )

(defmethod handle-event :flow
  [[_ from] app owner]
  (condp = from
   :start (do
            (om/transact! app [:state] states)
            (let [comm (om/get-state owner [:comm])]
             (go (<! (timeout 10000))
                (put! comm [:flow :times-up]))))
    :times-up (om/transact! app [:state] states)))

(defn init-view
  [app owner opts]
  (reify
    om/IRenderState
    (render-state
     [this {:as state :keys [comm]}]
     (dom/div #js {:className "init"}
              (dom/h1 #js {:className "message"} (:title opts))
              (dom/button #js {:className "action"
                               :onClick #(put! comm [:flow :start])} "Start")))))

(defn card-view
  [card owner]
  (om/component
   (dom/div #js {:className "card"}
                     (:person card))))

(defn navigation-view
  [app owner {:keys [label cmd]}]
  (reify
    om/IRenderState
    (render-state [this {:keys [comm] :as state}]
                  (dom/button #js {:onClick #(put! comm [cmd])} label))))

(defn team-score
  [app view opts]
  (om/component
   (dom/div #js {:className "team-score"} (str "Team "(key app) " : " (val app)))))


(defn score-view
  [app owner]
  (reify
    om/IRenderState
    (render-state
     [this state]
     (dom/div #js {:className "information"}
            (dom/div #js {:className "current-team"} (str "Team : " (:team app)))
            (apply dom/div #js {:className "score"}
             (om/build-all team-score (:score app)))))))


(defn app-view
  [app owner]
  (reify
    om/IInitState
    (init-state [this]
                {:comm (chan)})
    om/IWillMount
    (will-mount [this]
                (let [comm (om/get-state owner [:comm])]
                  (go
                   (loop []
                     (let [[cmd param] (<! comm)]
                       (handle-event [cmd param] app owner)
                       (recur))))))
    om/IRenderState
    (render-state [this state]
                  (condp = (:state app)
                    :init (om/build init-view app {:state state
                                                   :opts {:title "Welcome to Time's up"}})
                    :switch-team (om/build init-view app {:state state
                                                   :opts {:title "Changement d'équipe"}})
                    :playing (dom/div nil
                                      (om/build score-view app)
                                      (dom/div #js {:className "flex"}
                                               (om/build card-view ((:persons app) (:person-id app)))
                                               (when (< 1 (:round app))(om/build navigation-view app {:state state
                                                                              :opts {:label "PASS"
                                                                                     :cmd :KO}}))
                                               (om/build navigation-view app {:state state
                                                                              :opts {:label "OK"
                                                                                     :cmd :OK}})))))))

(om/root
  app-view
  app-state
  {:target (. js/document (getElementById "app"))})
