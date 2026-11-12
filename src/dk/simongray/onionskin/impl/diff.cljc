(ns ^:no-doc dk.simongray.onionskin.impl.diff
  "The differences between two captures of a page, e.g. before and after a
  change to its CSS.

  A full capture lists every element of the page under :nodes. Each node has
  its :path in the document, its :box, its computed :style and the styles of
  its :pseudo elements. The diff pairs elements by path, so it suits a change
  that leaves the markup alone."
  (:require [clojure.set :as set]
            [dk.simongray.onionskin.impl.geometry :as geometry]
            [dk.simongray.onionskin.impl.relation :as relation]))

(defn- by-path
  "The nodes of `capture` by their path."
  [capture]
  (into {} (map (juxt :path identity)) (:nodes capture)))

(defn- style-changes
  "The properties that differ between the styles `a` and `b` of the element
  at `path`, or of its `pseudo` element when one is given."
  [path pseudo a b]
  ;; Most styles are the same in both captures, and comparing the two maps
  ;; first is much cheaper than going through their properties.
  (when (not= a b)
    (for [property (sort (into (set (keys a)) (keys b)))
          :let [before (get a property)
                after (get b property)]
          :when (not= before after)]
      (cond-> {:change   :style
               :path     path
               :property property
               :before   before
               :after    after}
        pseudo (assoc :pseudo pseudo)))))

(defn- node-changes
  "The changes from `a` to `b`, the same element in two captures.

  It compares the `levels` asked for, with box edges compared within
  `tolerance`."
  [levels tolerance {:keys [path] :as a} b]
  (concat
    (when (:styles levels)
      (concat (style-changes path nil (:style a) (:style b))
              (mapcat (fn [pseudo]
                        (style-changes path pseudo
                                       (get-in a [:pseudo pseudo])
                                       (get-in b [:pseudo pseudo])))
                      (sort (into (set (keys (:pseudo a))) (keys (:pseudo b)))))))
    (when (and (:boxes levels)
               (not (geometry/same-box? tolerance (:box a) (:box b))))
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
       :lost    (set/difference was is)
       :gained  (set/difference is was)})))

;; Neither clojure.data/diff nor a library such as editscript fits here.
;; They compare numbers exactly where boxes need a tolerance, and the report
;; needs the old and new value of a property side by side. Nor can they pair
;; up elements after a change to the markup, which is the hard part.
(defn diff
  "The differences between the captures `before` and `after`, with the
  `opts` of diff in the root namespace."
  [before after {:keys [levels tolerance]
                 :or   {levels    #{:styles :boxes}
                        tolerance geometry/default-tolerance}
                 :as   opts}]
  (let [a (by-path before)
        b (by-path after)]
    (vec
      (concat
        (for [{:keys [path]} (:nodes before) :when (not (contains? b path))]
          {:change :removed :path path})
        (for [{:keys [path]} (:nodes after) :when (not (contains? a path))]
          {:change :added :path path})
        (for [node (:nodes before)
              :let [other (get b (:path node))]
              :when other
              change (node-changes levels tolerance node other)]
          change)
        (when (:relations levels)
          (relation-changes tolerance before after))))))
