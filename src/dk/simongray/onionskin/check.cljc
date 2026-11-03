(ns dk.simongray.onionskin.check
  "Checking a capture of a page against a spec.

  Under :elements, a capture maps each selector of the spec to the elements
  it matched. Each element has its :path in the document and its :box. It
  also has the :style properties that the spec reads. Under :resolved, it
  has what the browser computed from the spec's CSS values for that
  element."
  (:require [dk.simongray.onionskin.geometry :as geometry]
            [dk.simongray.onionskin.relation :as relation]
            [dk.simongray.onionskin.spec :as spec]))

(defn- unmatched
  "The failure of `check` when `selector` matched the wrong number of
  elements in `capture`, or else nil. A subject must match at least one
  element and a target exactly one."
  [check selector capture]
  (let [{:keys [error matches]} (get-in capture [:elements selector])
        n (count matches)]
    (cond
      error {:problem :invalid-selector :selector selector :error error :check check}
      (zero? n) {:problem :no-match :selector selector :check check}
      (and (= selector (:target check)) (> n 1))
      {:problem :ambiguous-target :selector selector :count n :check check})))

(defn- relation-failure
  "The failure of the relation `check` for `match`, an element of its
  subject in `capture`, or nil. Values are compared within `tolerance`."
  [capture tolerance {:keys [relation target by] :as check} match]
  (let [a           (:box match)
        b           (:box (first (get-in capture [:elements target :matches])))
        fine        (geometry/relation tolerance a b)
        [measure f] (relation/measures relation)
        value       (when f (f a b))]
    (when-not (and ((relation/vocabulary relation) fine)
                   (or (nil? by) (geometry/in-range? tolerance by value)))
      (cond-> {:problem :relation :check check :fine fine :boxes [a b]}
        measure (assoc :measure measure :value value)))))

(defn- size-failure
  "The failure of the size `check` for `match`, an element of its subject,
  or nil. Values are compared within `tolerance`."
  [tolerance {:keys [size expected] :as check} match]
  (let [value ((if (= size :width) geometry/width geometry/height) (:box match))]
    (when-not (geometry/in-range? tolerance expected value)
      {:problem :size :check check :value value})))

(defn- style-failure
  "The failure of the style `check` for `match`, an element of its subject,
  or nil."
  [{:keys [style expected] :as check} match]
  (let [actual   (get-in match [:style style])
        resolved (get-in match [:resolved [style expected]] expected)]
    (when (not= actual resolved)
      {:problem :style :check check :value actual :resolved resolved})))

(defn- element-failures
  "The failures of the elements that the subject of `check` matched in
  `capture`, with `failure` as the function of one element.

  Each failure gets the element's :path and its :index among the :count
  elements."
  [capture {:keys [subject] :as check} failure]
  (let [matches (get-in capture [:elements subject :matches])]
    (keep-indexed (fn [i match]
                    (some-> (failure match)
                            (assoc :index i :count (count matches) :path (:path match))))
                  matches)))

(defn check
  "The failures of `spec` in `capture` with the `opts` below: a vector of
  maps that's empty when the page matches the spec.

  Each failure has a :problem and the :check that failed. A failure of an
  element also has its :path and its :index among the :count elements that
  the selector matched. The options are:

      :tolerance  the CSS pixels within which edges, centres and sizes count
                  as equal, unless a check sets its own (default 0.5)"
  ([capture spec]
   (check capture spec {}))
  ([capture spec {:keys [tolerance]
                  :or   {tolerance geometry/default-tolerance}
                  :as   opts}]
   (vec
     (mapcat
       (fn [{:keys [subject target relation size] :as c}]
         (let [tolerance (:tolerance c tolerance)]
           (or (seq (keep #(unmatched c % capture)
                          (cond-> [subject] target (conj target))))
               (element-failures capture c
                                 (cond
                                   relation (partial relation-failure capture tolerance c)
                                   size (partial size-failure tolerance c)
                                   :else (partial style-failure c))))))
       (spec/checks spec)))))
