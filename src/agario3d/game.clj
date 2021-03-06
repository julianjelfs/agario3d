(ns agario3d.game
  (:require [clojure.core.async :refer [>! <! alts! chan close! go go-loop timeout]]
            [schema.core :as s]
            [clj-time.core :as t]
            [agario3d.config :refer [config]]
            [clojure.math.numeric-tower :refer [expt]]
            [agario3d.loop :refer [every]]))

(def Pos
  "Scheme for position vector"
  {:x s/Num :y s/Num :z s/Num})

(def Agent
  "Schema for an agent in the game"
  {:c s/Str
   (s/optional-key :n) s/Str
   :id s/Any  ;;this could be a string or a num
   :t s/Keyword
   :r s/Num
   :m s/Num
   :x s/Num
   :y s/Num
   :z s/Num})

(def next-id (atom 0))

(def pi (. Math PI))

(s/defn euclidean-distance :- s/Num
  [p1 :- Pos p2 :- Pos]
  (let [dx (expt (- (:x p2) (:x p1)) 2)
        dy (expt (- (:y p2) (:y p1)) 2)
        dz (expt (- (:z p2) (:z p1)) 2)]
    (-> (+ dx dy dz)
        (expt ,, (/ 1 2)))))

(s/defn contains? :- s/Bool 
  [a1 :- Agent a2 :- Agent]
  (let [d (euclidean-distance a1 a2)]
    (-> (+ d (:r a2))
        (< ,, (:r a1)))))

(s/defn mass->radius :- s/Num
  [mass :- s/Num]
  (-> mass
      (/ ,, (* pi (/ 4 3)))
      (expt ,, (/ 1 3))))

(s/defn radius->mass :- s/Num 
  [radius :- s/Num]
  (-> radius
      (expt ,,, 3)
      (* ,, pi)
      (* ,, (/ 4 3))))

(s/defn random-coord :- s/Num
  [limit]
  (-> (rand)
      (- ,, 0.5)
      (* ,, limit)))

(s/defn random-pos :- Pos
  []
  (let [[x y z] (:dimensions config)]
    {:x (random-coord x)
     :y (random-coord y)
     :z (random-coord z)}))

(s/defn create-agent :- Agent 
  ([radius type colour] (create-agent radius type colour "" (swap! next-id inc)))
  ([radius type colour name id]
   (merge  {:c colour
            :id id
            :n name
            :t type
            :r radius
            :m (radius->mass radius)} (random-pos))))

(s/defn create-food  :- Agent []
  (let [radius (get-in config [:food :radius])]
    (create-agent radius :food "0xffffff")))

(s/defn create-virus  :- Agent []
  (let [radius (get-in config [:viruses :radius])
        colour (get-in config [:viruses :colour])]
    (create-agent radius :virus colour)))

(s/defn create-bot  :- Agent []
  (let [radius (:startRadius config)
        colour (get-in config [:bots :colour])]
    (create-agent radius :bot colour)))

(s/defn create-player  :- Agent [player]
  (let [radius (:startRadius config)]
    (create-agent radius :player (:colour player) (:name player) (:id player))))

(s/defn create-agents :- [Agent] [n agentFn]
  (map (fn [n] (agentFn)) (range n)))

(def factories {:food create-food
                :virus create-virus
                :bot create-bot})

(s/defn things-of-type :- [Agent]
  [game :- s/Any t :- s/Keyword]
  (filter (fn [[k v]] (= t (:t v))) game))

(defn eat [eater eatee]
  (let [mass (+ (:m eater) (:m eatee))]
    (assoc eater :m mass :r (mass->radius mass))))

(defn get-collisions [game]
  (let [players (things-of-type game :player)
        bots (things-of-type game :bot)
        agents (concat players bots)
        food (things-of-type game :food)
        both (concat agents food)]
    (for [a agents
          b both
          :when (and (not= a b) (contains? a b))]
      [a b])))

(defn perform-collisions [game]
  (let [collisions (get-collisions game)]
    (reduce 
      (fn [g [eater eatee]]
        (prn "nom nom nom")
        (-> g
            (dissoc ,, (:id eatee))
            (assoc ,, (:id eater) (eat eater eatee)))) game collisions)))

(defn create-new-game
  "Seed the game with food, viruses and bots"
  []
  (prn "creating a brand new game")
  (atom  (let [foodNum (get-in config [:food :num])
               botNum (get-in config [:bots :num])
               virusNum (get-in config [:viruses :num])
               agents (concat (create-agents foodNum create-food)
                              (create-agents botNum create-bot)
                              (create-agents virusNum create-virus))]
           (reduce (fn [g a] (assoc g (:id a) a)) {:snapshot {}} agents))))

(defn create-snapshot [game]
  (->> (reduce (fn [snap [k v]]
                 (assoc snap k v)) {} game)
       (assoc game :snapshot ,,,)))

(defn create-diff [s game]
  (reduce (fn [diff [k v]]
            (let [snap-val (get k s)]
              (if (and (not= :snapshot k) 
                       (or (nil? snap-val) (not= snap-val v)))
                (assoc diff k v)
                diff))
            ) {} game))

(def min-date (t/date-time 1970 1 1))

(defn now []
  (t/in-millis (t/interval min-date (t/now))))

(defn move-things [game type speed]
  (let [things (things-of-type game type)
        timer (* speed (now))
        [x, y] (:dimensions config)]
    (->> (map (fn [t]
                (let [id (:id t)] 
                  (assoc t :x (-> (+ timer id)
                                  (Math/cos ,,,)
                                  (* ,,, (/ x 2)))
                         :y (-> (* 1.1 id)
                                (+ ,,, timer)
                                (Math/sin ,,,)
                                (* ,,, (/ y 2))))) 
                ) things)
         (reduce (fn [g b]
                   (assoc g (:id b) b)) game ,,,) )))

(defn move-bots [game]
  (move-things game :bot 0.00003))

(defn move-food [game]
  (move-things game :food 0.00001))

(defn replenish-things [game type]
  (let [things (things-of-type game type)
        min (get-in config [type :num])
        shortfall (- min (count things))]
    (if (= 0 shortfall)
      things
      (->> (map (fn [i] ((type factories))) (range shortfall))
           (reduce (fn [g t]
                     (assoc g (:id t) t)) game ,,,)))))

(defn replenish-bots [game]
  (replenish-things game :bot))

(defn replenish-food [game]
  (replenish-things game :food))

(defn update-game [game delta]
  (let [snap (:snapshot @game)]
    (->> (swap! game
                (fn [g]
                  (-> g
                      move-bots
                      move-food
                      replenish-bots
                      replenish-food
                      perform-collisions
                      create-snapshot)))
         (create-diff snap ,,,))))

(defn start-game [game]
  (go
    (let [tick (every (/ 1000 (:updatesPerSecond config)))]
      (loop [game game]
        (let [delta (<! tick)]
          (recur (update-game game delta)))))))

(defn update-player-position [game command]
  (if-let [player (get @game (:id command)) ]
    (let [updated (merge player command)]
      (swap! game assoc (:id command) updated)
      game)
    game))

(defn player-command [game command]
  (case (:type command)
    :position (update-player-position game command)
    game))

(defn add-player [game player]
  (let [p (create-player player)]
    (swap! game assoc (:id p) p)
    game))

(defn remove-player [game {id :id}]
  (swap! game dissoc id)
  game)

