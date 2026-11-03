(ns dk.simongray.onionskin.relation-test
  (:require [clojure.test :refer [deftest is are testing]]
            [dk.simongray.onionskin.geometry :as geometry]
            [dk.simongray.onionskin.relation :as relation]))

(defn- box
  [left top right bottom]
  {:left left :top top :right right :bottom bottom})

(defn- holds?
  [k a b]
  ((relation/vocabulary k) (geometry/relation a b)))

(deftest directions-test
  (let [a (box 0 0 100 20)]
    (are [k b result] (= result (holds? k a b))
      :left-of (box 110 0 200 20) true
      :left-of (box 100 0 200 20) true
      :left-of (box 90 0 200 20) false
      :above (box 0 20 100 40) true
      :above (box 0 10 100 40) false
      :below (box 0 -30 100 -10) true
      :right-of (box -50 0 0 20) true)))

(deftest containment-test
  (let [outer (box 0 0 200 100)]
    (are [k a result] (= result (holds? k a outer))
      :inside (box 10 10 50 50) true
      :inside (box 0 0 200 100) true
      :inside (box 10 10 250 50) false
      :overlaps (box 150 50 250 150) true
      :overlaps (box 200 0 250 50) false
      :touches (box 200 0 250 50) true
      :touches (box 200 100 250 150) true
      :touches (box 210 0 250 50) false
      :apart (box 210 0 250 50) true
      :centered-in (box 50 25 150 75) true
      :centered-in (box 50 25 150 80) false)))

(deftest alignment-test
  (let [a (box 10 0 50 20)]
    (are [k b result] (= result (holds? k a b))
      :left-aligned (box 10 30 90 50) true
      :left-aligned (box 11 30 90 50) false
      :right-aligned (box 0 30 50 50) true
      :top-aligned (box 60 0 90 40) true
      :bottom-aligned (box 60 -20 90 20) true
      :center-aligned (box 0 30 60 50) true
      :middle-aligned (box 60 5 90 15) true
      :same-width-as (box 100 100 140 300) true
      :wider-than (box 100 100 120 300) true
      :narrower-than (box 100 100 120 300) false)))

(defn- consistent?
  "True if the named relations of `a` to `b` don't contradict each other."
  [a b]
  (let [held (relation/holding (geometry/relation a b))]
    (and (not (and (held :left-of) (held :right-of)))
         (not (and (held :above) (held :below)))
         (= 1 (count (filter held [:overlaps :touches :apart])))
         (or (not (and (held :inside) (held :contains))) (= a b)))))

(deftest consistency-test
  (testing "the directions on an axis exclude each other, and so do the
            ways that two boxes can meet, for boxes wider than twice the
            tolerance"
    (is (empty? (for [x1 (range 0 60 7) w1 [2 13 40] x2 (range 0 60 11) w2 [2 9 30]
                      y1 (range 0 40 13) h1 [3 20] y2 (range 0 40 9) h2 [2 25]
                      :let [a (box x1 y1 (+ x1 w1) (+ y1 h1))
                            b (box x2 y2 (+ x2 w2) (+ y2 h2))]
                      :when (not (consistent? a b))]
                  [a b])))))
