(ns ^:no-doc dk.simongray.onionskin.impl.diff
  "The differences between two captures of a page, e.g. before and after a
  change to its CSS.

  A full capture lists every element of the page under :nodes. Each node has
  its :path in the document, its :box, its computed :style and the styles of
  its :pseudo elements. The match namespace pairs the elements of the two
  captures, also when the markup changed between them."
  (:require [clojure.set :as set]
            [clojure.string :as str]
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

;; The computed style of an element has used values for these properties,
;; so they change with the layout around the element and not only with its
;; own declarations (CSSOM, resolved values). The list is the one that
;; Chromium flags in its table of properties.
(def layout-properties
  "The style properties whose values in a capture can restate the layout."
  (let [physical ["top" "right" "bottom" "left"]
        logical  ["block-start" "block-end" "inline-start" "inline-end"]]
    (-> #{"width" "height" "inline-size" "block-size"
          "grid-template-columns" "grid-template-rows"
          "transform" "transform-origin" "perspective-origin" "translate"}
        (into physical)
        (into (map #(str "inset-" %)) logical)
        (into (for [edge ["margin" "padding"]
                    side (concat physical logical)]
                (str edge "-" side))))))

;; StyleProof folds the properties that follow color in the same way. No
;; tool was found that reports one line per cause, so each fold here is a
;; plain rule over the changes themselves.
(defn- explained
  "The `changes` of the elements that two captures share, where each style
  change that another change explains says what it :follows:

      \"color\"  the element's color, for a colour that changed with it
      :box     the element's box, for a value that restates the layout of
               the element or of one of its pseudo elements
      :parent  the same change on the parent, or on the element itself for
               one of its pseudo elements"
  [changes]
  (let [styles (into {}
                     (comp (filter #(= :style (:change %)))
                           (map (juxt (juxt :path :pseudo :property) identity)))
                     changes)
        boxes  (into #{} (comp (filter #(= :box (:change %))) (map :path)) changes)
        same?  (fn [a b]
                 (and b (= (:before a) (:before b)) (= (:after a) (:after b))))
        parent (fn [path]
                 (some->> (str/last-index-of path ">") (subs path 0)))
        cause  (fn [{:keys [path pseudo property] :as change}]
                 (cond
                   (and (not= "color" property)
                        (str/ends-with? property "color")
                        (same? change (styles [path pseudo "color"])))
                   "color"

                   (and (layout-properties property) (boxes path))
                   :box

                   (same? change (styles [(if pseudo path (parent path)) nil property]))
                   :parent))]
    (mapv (fn [change]
            (if-let [cause (when (= :style (:change change)) (cause change))]
              (assoc change :follows cause)
              change))
          changes)))

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
        (explained (mapcat #(pair-changes levels tolerance %) pairs))
        (when (:relations levels)
          (relation-changes tolerance before after))))))
