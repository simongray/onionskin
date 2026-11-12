(ns ^:no-doc dk.simongray.onionskin.impl.check
  "Checking a capture of a page against a spec.

  Under :elements, a capture maps each selector of the spec to the elements
  it matched. Each element has its :path in the document and its :box. It
  also has the :style properties that the spec reads. Under :resolved, it
  has what the browser computed from the spec's CSS values for that
  element."
  (:require [dk.simongray.onionskin.impl.geometry :as geometry]
            [dk.simongray.onionskin.impl.relation :as relation]
            [dk.simongray.onionskin.impl.spec :as spec]))

(defn- unmatched
  "The failure of `check` when `selector` isn't in `capture` or matched the
  wrong number of elements there, or else nil.

  A subject must match at least one element and a target exactly one."
  [capture check selector]
  (let [{:keys [error matches] :as found} (get-in capture [:elements selector])
        n       (count matches)
        problem (cond
                  (nil? found) :not-captured
                  error :invalid-selector
                  (zero? n) :no-match
                  (and (= selector (:target check)) (> n 1)) :ambiguous-target)]
    (when problem
      (cond-> {:problem problem :selector selector :check check}
        error (assoc :error error)
        (> n 1) (assoc :count n)))))

(defn- relation-failure
  "The failure of the relation `check` for `match`, an element of its
  subject in `capture`, or nil. Values are compared within `tolerance`."
  [capture tolerance {:keys [relation target by] :as check} match]
  (let [a           (:box match)
        b           (:box (first (get-in capture [:elements target :matches])))
        fine        (geometry/relation tolerance a b)
        [measure f] (relation/measures relation)
        value       (when f (f a b))]
    (when-not (and (relation/holds? relation fine)
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
  or nil.

  A capture from a browser has the expected value as the browser resolved
  it for the element, which is nil when the browser doesn't accept it."
  [{:keys [style expected] :as check} match]
  (let [actual   (get-in match [:style style])
        resolved (get (:resolved match) [style expected] expected)]
    (cond
      (nil? resolved) {:problem :invalid-style :check check}
      (not= actual resolved) {:problem  :style
                              :check    check
                              :value    actual
                              :resolved resolved})))

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

(defn- check-failures
  "The failures of the one `check` in `capture`, with values compared within
  `tolerance` unless the check sets its own."
  [capture tolerance {:keys [subject target relation size] :as check}]
  (let [tolerance (:tolerance check tolerance)]
    (or (seq (keep #(unmatched capture check %)
                   (if target [subject target] [subject])))
        (element-failures capture check
                          (cond
                            relation #(relation-failure capture tolerance check %)
                            size #(size-failure tolerance check %)
                            :else #(style-failure check %))))))

(defn check
  "The failures of `spec` in `capture`, with the `opts` of check in the
  root namespace."
  [capture spec {:keys [tolerance]
                 :or   {tolerance geometry/default-tolerance}
                 :as   opts}]
  (into [] (mapcat #(check-failures capture tolerance %)) (spec/checks spec)))
