(ns dk.simongray.onionskin.spec
  "Specs: what a page should look like, as plain data.

  A spec maps a CSS selector to the expectations of the elements it
  matches:

      {\"nav .logo\"   {:left-of \"nav .search\"
                      :top-aligned \"nav .search\"}
       \".legend li\"  {:inside \".legend\"}
       \"h1 .prefix\"  {:left-of {:target \"h1 .title\" :by [4 8]}}
       \"a.rdf-uri\"   {:style {:color \"var(--color-link)\"}}
       \"main\"        {:width [nil 1100]}}

  A relation names its target by selector, or by a map with the :target and
  options: :by, the range of the distance it measures, and :tolerance. A
  vector of targets asks for the relation to each. :width and :height take
  a number or a [low high] range in CSS pixels. :style takes CSS values in
  any syntax, which the browser resolves in the context of each element
  before they're compared, so that a token, a named colour or a length in
  ch can stand in for the value the browser computes."
  (:require [dk.simongray.onionskin.relation :as relation]))

(defn- relation-checks
  "The checks of the relation `k` from `subject` to the targets in `v`."
  [subject k v]
  (when-not (contains? relation/vocabulary k)
    (throw (ex-info (str "Unknown relation " k) {:selector subject :relation k})))
  (for [t (if (sequential? v) v [v])]
    (merge {:subject subject :relation k}
           (if (map? t) t {:target t}))))

(defn checks
  "The checks that `spec` asks for, one map for each relation, size and
  style property."
  [spec]
  (vec
    (for [[subject expectations] spec
          [k v] expectations
          check (case k
                  :style (for [[property css] v]
                           {:subject  subject
                            :style    (name property)
                            :expected (if (keyword? css) (name css) (str css))})
                  (:width :height) [{:subject subject :size k :expected v}]
                  (relation-checks subject k v))]
      check)))

(defn requirements
  "What a capture needs for the checks of `spec`: a map from each selector
  to the style properties to read, under :styles, and the pairs of a
  property and a CSS value to resolve, under :resolve."
  [spec]
  (reduce (fn [m {:keys [subject target style expected]}]
            (cond-> (update m subject #(merge {:styles #{} :resolve #{}} %))
              target (update target #(merge {:styles #{} :resolve #{}} %))
              style (-> (update-in [subject :styles] conj style)
                        (update-in [subject :resolve] conj [style expected]))))
          {}
          (checks spec)))
