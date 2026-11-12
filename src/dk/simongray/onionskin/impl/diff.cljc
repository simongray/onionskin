(ns ^:no-doc dk.simongray.onionskin.impl.diff
  "The differences between two captures of a page, e.g. before and after a
  change to its CSS.

  A full capture lists every element of the page under :nodes. Each node has
  its :path in the document, its :box, its computed :style and the styles of
  its :pseudo elements. The match namespace pairs the elements of the two
  captures, also when the markup changed between them."
  (:require [clojure.set :as set]
            [dk.simongray.onionskin.impl.geometry :as geometry]
            [dk.simongray.onionskin.impl.match :as match]
            [dk.simongray.onionskin.impl.relation :as relation]))

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
  "The changes from `a` to `b`, the same element in two captures, under the
  path of `b`.

  It compares the `levels` asked for, with box edges compared within
  `tolerance`."
  [levels tolerance a {:keys [path] :as b}]
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

(defn- pair-changes
  "The changes between the two nodes of `pair`, an element that both
  captures have, for the `levels` asked for and within `tolerance`.

  When the element has another path than before, each change also has its
  :old-path and the rule that it was :paired-by."
  [levels tolerance {a :before b :after :keys [rule moved?] :as pair}]
  (let [origin (when (not= (:path a) (:path b))
                 {:old-path (:path a) :paired-by rule})]
    (map #(merge % origin)
         (concat (when moved?
                   [{:change :moved :path (:path b)}])
                 (node-changes levels tolerance a b)))))

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
                 ks    :keys
                 :or   {levels    #{:styles :boxes}
                        tolerance geometry/default-tolerance
                        ks        ["data-testid" "id"]}
                 :as   opts}]
  (let [pairs (match/pairs (:nodes before) (:nodes after) ks)
        kept  (into #{} (map (comp :path :before)) pairs)
        found (into #{} (map (comp :path :after)) pairs)]
    (vec
      (concat
        (for [{:keys [path]} (:nodes before) :when (not (contains? kept path))]
          {:change :removed :path path})
        (for [{:keys [path]} (:nodes after) :when (not (contains? found path))]
          {:change :added :path path})
        (mapcat #(pair-changes levels tolerance %) pairs)
        (when (:relations levels)
          (relation-changes tolerance before after))))))
