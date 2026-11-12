(ns ^:no-doc dk.simongray.onionskin.impl.spec
  "The checks that a spec asks for, and what a capture must read for them.

  The root namespace describes the spec itself."
  (:require [dk.simongray.onionskin.impl.relation :as relation]))

(defn- relation-check
  "The check of the relation `k` from `subject` to the target `t`: a
  selector, or a map of options with the selector under :target."
  [subject k t]
  (let [{:keys [target by] :as check} (merge {:subject subject :relation k}
                                             (if (map? t) t {:target t}))
        mistake (cond
                  (not (contains? relation/vocabulary k))
                  (str "Unknown expectation " k " for " subject)

                  (nil? target)
                  (str k " for " subject " needs a :target")

                  (and by (not (contains? relation/measures k)))
                  (str k " for " subject
                       " takes no :by, since it measures no distance"))]
    (when mistake
      (throw (ex-info mistake check)))
    check))

(defn checks
  "The checks that `spec` asks for: one map for each relation, size and
  style property.

  It throws when the spec itself has a mistake, e.g. a relation that
  doesn't exist."
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
                  (map #(relation-check subject k %)
                       (if (sequential? v) v [v])))]
      check)))

(defn requirements
  "What a capture needs for the checks of `spec`, by selector.

  For each selector, :styles holds the style properties to read and
  :resolve holds the pairs of a property and a CSS value to resolve."
  [spec]
  (let [blank {:styles #{} :resolve #{}}]
    (reduce (fn [m {:keys [subject target style expected]}]
              (cond-> (update m subject #(or % blank))
                target (update target #(or % blank))
                style (-> (update-in [subject :styles] conj style)
                          (update-in [subject :resolve] conj [style expected]))))
            {}
            (checks spec))))
