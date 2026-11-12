(ns ^:no-doc dk.simongray.onionskin.impl.relation
  "Named relations of one box to another, and the distances they measure.

  Each named relation reads only the fine relation from geometry/relation.
  A coarse relation therefore holds exactly when the fine relation is one
  that it allows, so relations at different levels of detail can't
  disagree."
  (:require [clojure.set :as set]
            [dk.simongray.onionskin.impl.geometry :as geometry]))

(def ends-before
  "The interval relations in which the first interval ends where the other
  starts, or before."
  #{:before :meets})

(def starts-after
  "The interval relations in which the first interval starts where the other
  ends, or after."
  #{:after :met-by})

(def apart
  "The interval relations with a gap between the intervals."
  #{:before :after})

(def touching
  "The interval relations in which the intervals share an end and nothing
  more."
  #{:meets :met-by})

(def overlapping
  "The interval relations in which the intervals share more than an end."
  (set/difference geometry/interval-relations ends-before starts-after))

(def within
  "The interval relations in which the first interval lies within the
  other."
  #{:starts :during :finishes :equals})

(def around
  "The interval relations in which the other interval lies within the
  first."
  #{:started-by :contains :finished-by :equals})

(def same-start
  "The interval relations in which the intervals start together."
  #{:starts :started-by :equals})

(def same-end
  "The interval relations in which the intervals end together."
  #{:finishes :finished-by :equals})

;; Freksa, Temporal reasoning based on semi-intervals (Artificial
;; Intelligence, 1992): coarse relations like these each come down to one or
;; two comparisons of edges.
(defn- both
  "A function that tests whether the interval relations on both axes of a
  fine relation are in the set `s`."
  [s]
  (every-pred (comp s :x) (comp s :y)))

(def vocabulary
  "The named relations, each a function of a fine relation that returns
  something truthy when the relation holds.

  The directions allow touching: a box is left of another when its right
  edge is at the other's left edge or before it. A box overlaps another when
  their insides meet, which includes one lying inside the other."
  {:left-of        (comp ends-before :x)
   :right-of       (comp starts-after :x)
   :above          (comp ends-before :y)
   :below          (comp starts-after :y)
   :inside         (both within)
   :contains       (both around)
   :overlaps       (both overlapping)
   :touches        (some-fn (every-pred (comp touching :x) (comp not apart :y))
                            (every-pred (comp touching :y) (comp not apart :x)))
   :apart          (some-fn (comp apart :x) (comp apart :y))
   :left-aligned   (comp same-start :x)
   :right-aligned  (comp same-end :x)
   :top-aligned    (comp same-start :y)
   :bottom-aligned (comp same-end :y)
   :center-aligned (comp zero? :center-x)
   :middle-aligned (comp zero? :center-y)
   :centered-in    (every-pred (both within)
                               (comp zero? :center-x)
                               (comp zero? :center-y))
   :same-width-as  (comp zero? :width)
   :same-height-as (comp zero? :height)
   :same-size-as   (every-pred (comp zero? :width) (comp zero? :height))
   :wider-than     (comp pos? :width)
   :narrower-than  (comp neg? :width)
   :taller-than    (comp pos? :height)
   :shorter-than   (comp neg? :height)})

(defn holds?
  "True if the relation named `k` holds for `fine`, a fine relation."
  [k fine]
  (boolean ((vocabulary k) fine)))

;; As in IFix (Mahajan et al., ICST 2018), a failure says how many pixels a
;; relation is off by.
(def measures
  "The distance that a named relation measures, if it has one: a pair of the
  distance's name and a function of two boxes.

  A gap is negative when the boxes overlap. An offset or a difference is
  positive when the first box's edge, centre or size is further right,
  further down or greater."
  (let [minus  (fn [f] (fn [a b] (- (f a) (f b))))
        width  [:difference (minus geometry/width)]
        height [:difference (minus geometry/height)]]
    {:left-of        [:gap (fn [a b] (- (:left b) (:right a)))]
     :right-of       [:gap (fn [a b] (- (:left a) (:right b)))]
     :above          [:gap (fn [a b] (- (:top b) (:bottom a)))]
     :below          [:gap (fn [a b] (- (:top a) (:bottom b)))]
     :left-aligned   [:offset (minus :left)]
     :right-aligned  [:offset (minus :right)]
     :top-aligned    [:offset (minus :top)]
     :bottom-aligned [:offset (minus :bottom)]
     :center-aligned [:offset (minus geometry/center-x)]
     :middle-aligned [:offset (minus geometry/center-y)]
     :same-width-as  width
     :same-height-as height
     :wider-than     width
     :narrower-than  width
     :taller-than    height
     :shorter-than   height}))

(defn holding
  "The names of the relations in the vocabulary that hold for `fine`, a fine
  relation."
  [fine]
  (into (sorted-set) (filter #(holds? % fine)) (keys vocabulary)))
