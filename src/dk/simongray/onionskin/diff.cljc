(ns dk.simongray.onionskin.diff
  "The differences between two captures of a page, e.g. before and after a
  change to its CSS.

  A full capture lists every element of the page under :nodes, each with its
  :path in the document, its :box, its computed :style and the styles of its
  :pseudo elements. Elements are paired by path, so the comparison suits a
  change that leaves the markup alone."
  (:require [dk.simongray.onionskin.geometry :as geometry]
            [dk.simongray.onionskin.relation :as relation]))

(defn- by-path
  "The nodes of `capture` by their path."
  [capture]
  (into {} (map (juxt :path identity)) (:nodes capture)))

(defn- style-changes
  "The changes of the properties of the styles `a` and `b` of the element at
  `path`, or of its `pseudo` element."
  [path pseudo a b]
  (for [property (sort (into (set (keys a)) (keys b)))
        :let [before (get a property)
              after (get b property)]
        :when (not= before after)]
    (cond-> {:change :style :path path :property property :before before :after after}
      pseudo (assoc :pseudo pseudo))))

(defn- node-changes
  "The changes between `a` and `b`, two captures of the element at `path`,
  at the `levels`, comparing boxes with `tolerance`."
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

(defn- relation-changes
  "The changes of the named relations between the first elements that each
  pair of the selectors matched in `before` and `after`, with `tolerance`."
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

(defn diff
  "The differences between `before` and `after`, two captures of the same
  page, as a vector of maps in document order, which is empty when nothing
  changed.

  Each difference has a :change: :added or :removed for an element that only
  one capture has, :style for a property of an element or of its :pseudo
  element, :box for an element that moved or changed size, and :relation
  for a pair of selected elements whose named relations changed. The options
  are:

      :levels     what to compare, a set of :styles, :boxes and :relations
                  (default #{:styles :boxes})
      :tolerance  CSS pixels within which edges count as equal (default 0.5)"
  ([before after]
   (diff before after {}))
  ([before after {:keys [levels tolerance]
                  :or   {levels    #{:styles :boxes}
                         tolerance geometry/default-tolerance}}]
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
