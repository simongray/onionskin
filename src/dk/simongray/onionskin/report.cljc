(ns dk.simongray.onionskin.report
  "Failures and differences as text for people to read."
  (:require [clojure.string :as str]))

(defn- number-text
  "`n` rounded to two decimals, without a fraction when it has none."
  [n]
  (let [r (/ (Math/round (* n 100.0)) 100.0)]
    (if (== r (Math/floor r)) (str (int r)) (str r))))

(defn- range-text
  "The number or [low high] range `expected` in CSS pixels."
  [expected]
  (if (number? expected)
    (str (number-text expected) " px")
    (let [[low high] expected]
      (cond
        (nil? low) (str "at most " (number-text high) " px")
        (nil? high) (str "at least " (number-text low) " px")
        :else (str (number-text low) " to " (number-text high) " px")))))

(def phrases
  "The words that a failure uses for each named relation, e.g. \"be left
  of\"."
  {:left-of        "be left of"
   :right-of       "be right of"
   :above          "be above"
   :below          "be below"
   :inside         "be inside"
   :contains       "contain"
   :overlaps       "overlap"
   :touches        "touch"
   :apart          "be apart from"
   :left-aligned   "be left-aligned with"
   :right-aligned  "be right-aligned with"
   :top-aligned    "be top-aligned with"
   :bottom-aligned "be bottom-aligned with"
   :center-aligned "be center-aligned with"
   :middle-aligned "be middle-aligned with"
   :centered-in    "be centered in"
   :same-width-as  "have the same width as"
   :same-height-as "have the same height as"
   :same-size-as   "have the same size as"
   :wider-than     "be wider than"
   :narrower-than  "be narrower than"
   :taller-than    "be taller than"
   :shorter-than   "be shorter than"})

(defn- subject-text
  "The selector `s` of `failure`, followed by e.g. \"(2 of 5)\" when it
  matched several elements."
  [s {:keys [index] n :count :as failure}]
  (if (and n (> n 1))
    (str s " (" (inc index) " of " n ")")
    s))

(defn- failure-sentence
  "The sentence that describes `failure`."
  [{:keys [problem check selector] :as failure}]
  (let [{:keys [subject target relation by size style expected]} check]
    (case problem
      :invalid-selector (str selector " is not a valid selector: " (:error failure) ".")
      :no-match (str selector " matched no element.")
      :ambiguous-target (str selector " matched " (:count failure)
                             " elements, but a target must match one.")
      :relation (let [{:keys [fine measure value]} failure]
                  (str (subject-text subject failure) " should "
                       (phrases relation) " " target
                       (when by (str " by " (range-text by)))
                       ", but "
                       (when measure
                         (str "the " (name measure) " is "
                              (number-text value) " px, and "))
                       "the boxes are x: " (name (:x fine))
                       ", y: " (name (:y fine)) "."))
      :size (str (subject-text subject failure) " should have a " (name size)
                 " of " (range-text expected) ", but has "
                 (number-text (:value failure)) " px.")
      :style (str (subject-text subject failure) " should have " style " "
                  expected
                  (when (not= expected (:resolved failure))
                    (str " (" (:resolved failure) ")"))
                  ", but has " (:value failure) "."))))

(defn failures
  "The `failures` of a check as text: a sentence for each, followed by the
  element's path when it has one."
  [failures]
  (str/join "\n" (for [f failures]
                   (cond-> (failure-sentence f)
                     (:path f) (str "\n  at " (:path f))))))

(defn- example-text
  "The number of `changes` and the path of the first one, such as \"on 3
  elements, e.g. html:1>body:2\"."
  [changes]
  (str "on " (count changes) (if (= 1 (count changes)) " element" " elements")
       ", e.g. " (:path (first changes))))

(defn- box-text
  "The edges that moved between the boxes `before` and `after`."
  [before after]
  (str/join ", " (for [edge [:left :top :right :bottom]
                       :let [a (get before edge) b (get after edge)]
                       :when (not= a b)]
                   (str (name edge) " " (number-text a) " -> " (number-text b)))))

(defn differences
  "The `differences` of two captures as text, one line per cause.

  Changes of a style property from the same old value to the same new value
  share a line."
  [differences]
  (let [{:keys [added removed style box relation]} (group-by :change differences)]
    (str/join
      "\n"
      (concat
        (when removed [(str "Removed " (example-text removed))])
        (when added [(str "Added " (example-text added))])
        (for [[[property pseudo before after] changes]
              (sort-by (comp - count val)
                       (group-by (juxt :property :pseudo :before :after) style))]
          (str (when pseudo (str pseudo " ")) property ": " before " -> " after " "
               (example-text changes)))
        (when box
          [(str "Moved or resized " (example-text box) " ("
                (box-text (:before (first box)) (:after (first box))) ")")])
        (for [{:keys [subject target lost gained]} relation]
          (str subject " to " target ":"
               (when (seq lost) (str " no longer " (str/join ", " (map name lost))))
               (when (and (seq lost) (seq gained)) ";")
               (when (seq gained) (str " now " (str/join ", " (map name gained))))))))))
