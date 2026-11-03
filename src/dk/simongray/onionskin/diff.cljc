(ns dk.simongray.onionskin.diff
  "The differences between two captures of a page, e.g. before and after a
  change to its CSS.

  A full capture lists every element of the page under :nodes. Each node has
  its :path in the document, its :box, its computed :style and the styles of
  its :pseudo elements. The diff pairs elements by path, so it suits a change
  that leaves the markup alone."
  (:require [dk.simongray.onionskin.geometry :as geometry]
            [dk.simongray.onionskin.relation :as relation]))

(defn- by-path
  "The nodes of `capture` by their path."
  [capture]
  (into {} (map (juxt :path identity)) (:nodes capture)))

(defn- style-changes
  "The properties that differ between the styles `a` and `b` of the element
  at `path`, or of its `pseudo` element when one is given."
  [path pseudo a b]
  (for [property (sort (into (set (keys a)) (keys b)))
        :let [before (get a property)
              after (get b property)]
        :when (not= before after)]
    (cond-> {:change :style :path path :property property :before before :after after}
      pseudo (assoc :pseudo pseudo))))

(defn- node-changes
  "The changes between `a` and `b`, the element at `path` in two captures.

  It compares the `levels` asked for, with box edges compared within
  `tolerance`."
  [levels tolerance path a b]
  (concat
    (when (:styles levels)
      (concat (style-changes path nil (:style a) (:style b))
              (mapcat (fn [pseudo]
                        (style-changes path pseudo
                                       (get-in a [:pseudo pseudo])
                                       (get-in b [:pseudo pseudo])))
                      (sort (into (set (keys (:pseudo a))) (keys (:pseudo b)))))))
    (when (and (:boxes levels)
               (some #(not (zero? (geometry/compare-values tolerance
                                                           (get-in a [:box %])
                                                           (get-in b [:box %]))))
                     [:left :top :right :bottom]))
      [{:change :box :path path :before (:box a) :after (:box b)}])))

;; Comparing which relations hold, rather than coordinates, follows X-PERT
;; (Roy Choudhary et al., ICSE 2013) and ReDeCheck (Walsh et al., STVR 2020).
(defn- relation-changes
  "The named relations that changed between `before` and `after` for each
  pair of selectors, with edges compared within `tolerance`.

  Each selector stands for the first element that it matched."
  [tolerance before after]
  (let [first-box (fn [capture selector]
                    (:box (first (get-in capture [:elements selector :matches]))))
        selectors (sort (filter #(and (first-box before %) (first-box after %))
                                (keys (:elements before))))]
    (for [[i a] (map-indexed vector selectors)
          b (drop (inc i) selectors)
          :let [holding (fn [capture]
                          (relation/holding (geometry/relation tolerance
                                                               (first-box capture a)
                                                               (first-box capture b))))
                was (holding before)
                is (holding after)]
          :when (not= was is)]
      {:change  :relation
       :subject a
       :target  b
       :lost    (apply sorted-set (remove is was))
       :gained  (apply sorted-set (remove was is))})))

;; Neither clojure.data/diff nor a library such as editscript fits here.
;; They compare numbers exactly where boxes need a tolerance, and the report
;; needs the old and new value of a property side by side. Nor can they pair
;; up elements after a change to the markup, which is the hard part.
(defn diff
  "The differences between `before` and `after`, two captures of the same
  page, with the `opts` below: a vector of maps in document order that's
  empty when nothing changed.

  Each difference has one of these values under :change:

      :added, :removed  an element that only one of the captures has
      :style            a property of an element or of its :pseudo element
      :box              an element that moved or changed size
      :relation         a pair of selected elements whose named relations
                        changed

  The options are:

      :levels     what to compare, a set of :styles, :boxes and :relations
                  (default #{:styles :boxes})
      :tolerance  the CSS pixels within which edges count as equal
                  (default 0.5)"
  ([before after]
   (diff before after {}))
  ([before after {:keys [levels tolerance]
                  :or   {levels    #{:styles :boxes}
                         tolerance geometry/default-tolerance}
                  :as   opts}]
   (let [a     (by-path before)
         b     (by-path after)
         paths (fn [capture] (map :path (:nodes capture)))]
     (vec
       (concat
         (for [path (paths before) :when (not (contains? b path))]
           {:change :removed :path path})
         (for [path (paths after) :when (not (contains? a path))]
           {:change :added :path path})
         (mapcat (fn [path]
                   (when-let [node (get b path)]
                     (node-changes levels tolerance path (get a path) node)))
                 (paths before))
         (when (:relations levels)
           (relation-changes tolerance before after)))))))
