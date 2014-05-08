(ns times-up.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [timeout chan >! put! <!]]))

(enable-console-print!)

(def states {:init :playing
              :playing :switch-team
              :switch-team :playing})

(def app-state (atom {:state :init
                      :round 1
                      :timeout 10000
                      :team 1
                      :nb-teams 2
                      :person-id 0
                      :score {1 0 2 0}
                      :persons [{:person "Henry Ford", :id 0} {:person "Bruce Spreegsteen", :id 1} {:person "Jean Cocteau", :id 2} {:person "Capitaine Crochet", :id 3}]}))


;######################### Business Functions #########################



(defn inc-score
  "Increment the score of current team"
  [app]
  (let [team (:team app)]
   (update-in app [:score team] inc)))

(defn inc-round
  "Increment the round"
  [app]
  (update-in app [:round] inc))

(defn end-of-round?
  [app]
  (let [nb-cards (count (:persons @app))
        current (:person-id @app)]
    (= (inc current) nb-cards)))


(defn next-card
  "Next card to guess.
  If this was the last card of the deck then,restart à first card, shuffle and switch team."
  [app]
 (update-in app [:person-id] inc))


(defn shuffle-deck
  [app]
  (-> app
      (update-in [:persons] shuffle)
      (assoc-in [:person-id] 0)))

(defn switch-team
  [app]
  (let [{nb :nb-teams} app]
   (update-in app [:team] #(if (zero? (mod % nb))
                             1
                             (inc %)))))


(defn step
  "Decide what is the next state."
  [app]
  (update-in app [:state] states))

;################# Event handlers #################


(defmulti handle-event (fn [[cmd param] app owner] cmd))


(defmethod handle-event :OK
 [_ app owner]
  (if (end-of-round? app)
    (do
      (om/transact! app inc-score)
      (put! (om/get-state owner [:comm]) [:end-of-round]))
    (om/transact! app  (comp inc-score next-card))))


(defmethod handle-event :KO
  [_ app owner]
  (om/transact! app next-card))


(defmethod handle-event :start
  [_ app owner]
  (do
    (om/transact! app [:state] states)
    (let [comm (om/get-state owner [:comm])
          time-out (:timeout @app)]
      (go (<! (timeout time-out))
          (>! comm [:times-up (select-keys @app [:round :team :state])])))))

(defmethod handle-event :times-up
  [[_ s] app owner]
  (let [_ (prn s)]
   (when (= :playing (:state s))(om/transact! app  (comp switch-team step)))))

(defmethod handle-event :end-of-round
  [_ app owner]
  (om/transact! app (comp shuffle-deck switch-team inc-round step)))


;###################### View Components #################

(defn init-view
  [app owner opts]
  (reify
    om/IRenderState
    (render-state
     [this {:as state :keys [comm]}]
     (dom/div #js {:className "init"}
              (dom/h1 #js {:className "message"} (:title opts))
              (dom/button #js {:className "action"
                               :onClick #(put! comm [:start])} "Start")))))

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


(defn round-view
  [app owner]
  (dom/div #js {:className "current-round"} (str "Round : " app)))

(defn team-view
  [app owner]
  (dom/div #js {:className "current-team"} (str "Team : " app)))

(defn score-view
  [app owner]
  (reify
    om/IRenderState
    (render-state
     [this state]
     (dom/div #js {:className "information"}
            (om/build round-view (:round app))
            (om/build team-view (:team app))
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
                  (dom/div #js {:className "game "}
                           (om/build score-view app)
                   (condp = (:state app)
                    :init (om/build init-view app {:state state
                                                   :opts {:title "Welcome to Time's up"}})
                    :switch-team (om/build init-view app {:state state
                                                   :opts {:title "Changement d'équipe"}})
                    :playing (dom/div nil

                                      (dom/div #js {:className "flex"}
                                               (om/build card-view ((:persons app) (:person-id app)))
                                               (when (< 1 (:round app))(om/build navigation-view app {:state state
                                                                              :opts {:label "PASS"
                                                                                     :cmd :KO}}))
                                               (om/build navigation-view app {:state state
                                                                              :opts {:label "OK"
                                                                                     :cmd :OK}}))))))))

(om/root
  app-view
  app-state
  {:target (. js/document (getElementById "app"))})
