(ns dk.simongray.onionskin.relation
  "Named relations of one box to another, and the distances they measure.

  Each named relation is a predicate that reads only the fine relation from
  geometry/relation. A coarse relation therefore holds exactly when the fine
  relation is one that it allows, so relations at different levels of
  detail can't disagree."
  (:require [dk.simongray.onionskin.geometry :as geometry]))

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
  (reduce disj geometry/interval-relations (concat ends-before starts-after)))

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
(def vocabulary
  "The named relations, each a predicate on a fine relation.

  The directions allow touching: a box is left of another when its right
  edge is at the other's left edge or before it. A box overlaps another when
  their insides meet, which includes one lying inside the other."
  {:left-of        (fn [{:keys [x]}] (contains? ends-before x))
   :right-of       (fn [{:keys [x]}] (contains? starts-after x))
   :above          (fn [{:keys [y]}] (contains? ends-before y))
   :below          (fn [{:keys [y]}] (contains? starts-after y))
   :inside         (fn [{:keys [x y]}] (and (contains? within x) (contains? within y)))
   :contains       (fn [{:keys [x y]}] (and (contains? around x) (contains? around y)))
   :overlaps       (fn [{:keys [x y]}]
                     (and (contains? overlapping x) (contains? overlapping y)))
   :touches        (fn [{:keys [x y]}]
                     (or (and (contains? touching x) (not (contains? apart y)))
                         (and (contains? touching y) (not (contains? apart x)))))
   :apart          (fn [{:keys [x y]}] (or (contains? apart x) (contains? apart y)))
   :left-aligned   (fn [{:keys [x]}] (contains? same-start x))
   :right-aligned  (fn [{:keys [x]}] (contains? same-end x))
   :top-aligned    (fn [{:keys [y]}] (contains? same-start y))
   :bottom-aligned (fn [{:keys [y]}] (contains? same-end y))
   :center-aligned (fn [{:keys [center-x]}] (zero? center-x))
   :middle-aligned (fn [{:keys [center-y]}] (zero? center-y))
   :centered-in    (fn [{:keys [x y center-x center-y]}]
                     (and (contains? within x) (contains? within y)
                          (zero? center-x) (zero? center-y)))
   :same-width-as  (fn [{:keys [width]}] (zero? width))
   :same-height-as (fn [{:keys [height]}] (zero? height))
   :same-size-as   (fn [{:keys [width height]}] (and (zero? width) (zero? height)))
   :wider-than     (fn [{:keys [width]}] (pos? width))
   :narrower-than  (fn [{:keys [width]}] (neg? width))
   :taller-than    (fn [{:keys [height]}] (pos? height))
   :shorter-than   (fn [{:keys [height]}] (neg? height))})

;; As in IFix (Mahajan et al., ICST 2018), a failure says how many pixels a
;; relation is off by.
(def measures
  "The distance that a named relation measures, if it has one: a pair of the
  distance's name and a function of two boxes.

  A gap is negative when the boxes overlap. An offset or a difference is
  positive when the first box's edge, centre or size is further right,
  further down or greater."
  {:left-of        [:gap (fn [a b] (- (:left b) (:right a)))]
   :right-of       [:gap (fn [a b] (- (:left a) (:right b)))]
   :above          [:gap (fn [a b] (- (:top b) (:bottom a)))]
   :below          [:gap (fn [a b] (- (:top a) (:bottom b)))]
   :left-aligned   [:offset (fn [a b] (- (:left a) (:left b)))]
   :right-aligned  [:offset (fn [a b] (- (:right a) (:right b)))]
   :top-aligned    [:offset (fn [a b] (- (:top a) (:top b)))]
   :bottom-aligned [:offset (fn [a b] (- (:bottom a) (:bottom b)))]
   :center-aligned [:offset (fn [a b] (- (geometry/center-x a) (geometry/center-x b)))]
   :middle-aligned [:offset (fn [a b] (- (geometry/center-y a) (geometry/center-y b)))]
   :same-width-as  [:difference (fn [a b] (- (geometry/width a) (geometry/width b)))]
   :same-height-as [:difference (fn [a b] (- (geometry/height a) (geometry/height b)))]
   :wider-than     [:difference (fn [a b] (- (geometry/width a) (geometry/width b)))]
   :narrower-than  [:difference (fn [a b] (- (geometry/width a) (geometry/width b)))]
   :taller-than    [:difference (fn [a b] (- (geometry/height a) (geometry/height b)))]
   :shorter-than   [:difference (fn [a b] (- (geometry/height a) (geometry/height b)))]})

(defn holding
  "The names of the relations in the vocabulary that hold for `fine`, a fine
  relation."
  [fine]
  (into (sorted-set)
        (keep (fn [[k holds?]] (when (holds? fine) k)))
        vocabulary))
