(ns dk.simongray.onionskin.geometry
  "Boxes, the interval relation on each axis, and the fine relation of two
  boxes.

  A box is a map of :left, :top, :right and :bottom in CSS pixels, like the
  ones that getBoundingClientRect returns. The y axis grows downwards. Two
  values within the tolerance of each other count as equal, so sub-pixel
  noise doesn't change a relation.")

(def default-tolerance
  "The tolerance in CSS pixels for checks that don't set their own. Quixote
  and Jetpack Compose use the same value."
  0.5)

(defn compare-values
  "The comparison of `a` with `b` that counts them as equal within
  `tolerance`: -1, 0 or 1."
  [tolerance a b]
  (cond
    (< a (- b tolerance)) -1
    (> a (+ b tolerance)) 1
    :else 0))

(def interval-relations
  "Allen's 13 relations of one interval to another (CACM, 1983)."
  #{:before :meets :overlaps :starts :during :finishes :equals
    :after :met-by :overlapped-by :started-by :contains :finished-by})

;; With a tolerance, the 13 relations stay exhaustive and disjoint as long as
;; both intervals are longer than twice the tolerance. For shorter intervals,
;; the first test that passes picks the relation.
(defn interval-relation
  "How the interval from `a1` to `a2` relates to the one from `b1` to `b2`:
  one of Allen's 13 relations, with ends compared within `tolerance`."
  [tolerance [a1 a2] [b1 b2]]
  (let [cmp (partial compare-values tolerance)]
    (cond
      (neg? (cmp a2 b1)) :before
      (zero? (cmp a2 b1)) :meets
      (pos? (cmp a1 b2)) :after
      (zero? (cmp a1 b2)) :met-by
      :else (get {[0 0]   :equals
                  [0 -1]  :starts
                  [0 1]   :started-by
                  [1 0]   :finishes
                  [-1 0]  :finished-by
                  [1 -1]  :during
                  [-1 1]  :contains
                  [-1 -1] :overlaps
                  [1 1]   :overlapped-by}
                 [(cmp a1 b1) (cmp a2 b2)]))))

(defn width
  "The width of `box`."
  [{:keys [left right]}]
  (- right left))

(defn height
  "The height of `box`."
  [{:keys [top bottom]}]
  (- bottom top))

(defn center-x
  "The horizontal centre of `box`."
  [{:keys [left right]}]
  (/ (+ left right) 2))

(defn center-y
  "The vertical centre of `box`."
  [{:keys [top bottom]}]
  (/ (+ top bottom) 2))

(defn relation
  "The fine relation of box `a` to box `b`, with values compared within
  `tolerance`.

  It's a map with the interval relation on each axis under :x and :y. It
  also compares their centres and sizes under :center-x, :center-y, :width
  and :height. Each of those is -1, 0 or 1 when `a`'s value is less, about
  the same or greater."
  ([a b]
   (relation default-tolerance a b))
  ([tolerance a b]
   (let [cmp (partial compare-values tolerance)]
     {:x        (interval-relation tolerance
                                   [(:left a) (:right a)]
                                   [(:left b) (:right b)])
      :y        (interval-relation tolerance
                                   [(:top a) (:bottom a)]
                                   [(:top b) (:bottom b)])
      :center-x (cmp (center-x a) (center-x b))
      :center-y (cmp (center-y a) (center-y b))
      :width    (cmp (width a) (width b))
      :height   (cmp (height a) (height b))})))

(defn in-range?
  "True if `value` is within `tolerance` of `expected`.

  The `expected` value is a number, or a [low high] pair in which either end
  may be nil for no limit."
  [tolerance expected value]
  (if (number? expected)
    (zero? (compare-values tolerance value expected))
    (let [[low high] expected]
      (and (or (nil? low) (>= value (- low tolerance)))
           (or (nil? high) (<= value (+ high tolerance)))))))
