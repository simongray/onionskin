(ns dk.simongray.onionskin.check
  "Checking a capture of a page against a spec.

  A capture maps each selector of the spec, under :elements, to the elements
  it matched, each with its :path in the document, its :box, the :style
  properties the spec reads, and the CSS values of the spec as the browser
  :resolved them for that element."
  (:require [dk.simongray.onionskin.geometry :as geometry]
            [dk.simongray.onionskin.relation :as relation]
            [dk.simongray.onionskin.spec :as spec]))

(defn- unmatched
  "The failure of `check` when `selector` matched `n` elements, or nil when
  that's fine: a subject must match at least one and a target exactly one."
  [check selector capture]
  (let [{:keys [error matches]} (get-in capture [:elements selector])
        n (count matches)]
    (cond
      error {:problem :invalid-selector :selector selector :error error :check check}
      (zero? n) {:problem :no-match :selector selector :check check}
      (and (= selector (:target check)) (> n 1))
      {:problem :ambiguous-target :selector selector :count n :check check})))

(defn- located
  "`failure` with the index and path of the `i`th `match` of the subject, out
  of `n`."
  [failure i n match]
  (assoc failure :index i :count n :path (:path match)))

(defn- relation-failures
  "The failures of the relation `check` in `capture`, comparing with
  `tolerance` unless the check names its own."
  [capture tolerance {:keys [subject relation target by] :as check}]
  (let [tolerance (:tolerance check tolerance)
        matches   (get-in capture [:elements subject :matches])
        other     (first (get-in capture [:elements target :matches]))
        holds?    (relation/vocabulary relation)
        [measure f] (relation/measures relation)]
    (keep-indexed
      (fn [i match]
        (let [a (:box match)
              b (:box other)
              fine (geometry/relation tolerance a b)
              value (when f (f a b))]
          (when-not (and (holds? fine)
                         (or (nil? by) (geometry/in-range? tolerance by value)))
            (cond-> (located {:problem :relation
                              :check   check
                              :fine    fine
                              :boxes   [a b]}
                             i (count matches) match)
              measure (assoc :measure measure :value value)))))
      matches)))

(defn- size-failures
  "The failures of the size `check` in `capture`, comparing with
  `tolerance` unless the check names its own."
  [capture tolerance {:keys [subject size expected] :as check}]
  (let [tolerance (:tolerance check tolerance)
        matches   (get-in capture [:elements subject :matches])
        measure   (if (= size :width) geometry/width geometry/height)]
    (keep-indexed
      (fn [i match]
        (let [value (measure (:box match))]
          (when-not (geometry/in-range? tolerance expected value)
            (located {:problem :size :check check :value value}
                     i (count matches) match))))
      matches)))

(defn- style-failures
  "The failures of the style `check` in `capture`."
  [capture {:keys [subject style expected] :as check}]
  (let [matches (get-in capture [:elements subject :matches])]
    (keep-indexed
      (fn [i match]
        (let [actual   (get-in match [:style style])
              resolved (get-in match [:resolved [style expected]] expected)]
          (when (not= actual resolved)
            (located {:problem :style :check check :value actual :resolved resolved}
                     i (count matches) match))))
      matches)))

(defn check
  "The failures of `spec` in `capture`, as a vector of maps, which is empty
  when the page looks as the spec says.

  Each failure has a :problem and the :check that failed. A failure of an
  element also has its :index among the :count elements that the selector
  matched, and its :path. The options are:

      :tolerance  CSS pixels within which edges, centres and sizes count as
                  equal, unless a check names its own (default 0.5)"
  ([capture spec]
   (check capture spec {}))
  ([capture spec {:keys [tolerance] :or {tolerance geometry/default-tolerance}}]
   (vec
     (mapcat
       (fn [{:keys [subject target relation size] :as c}]
         (if-let [problems (seq (keep #(unmatched c % capture)
                                      (cond-> [subject] target (conj target))))]
           problems
           (cond
             relation (relation-failures capture tolerance c)
             size (size-failures capture tolerance c)
             :else (style-failures capture c))))
       (spec/checks spec)))))
