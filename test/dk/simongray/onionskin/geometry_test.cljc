(ns dk.simongray.onionskin.geometry-test
  (:require [clojure.test :refer [deftest is are testing]]
            [dk.simongray.onionskin.geometry :as geometry]))

(deftest interval-relation-test
  (testing "each of Allen's 13 relations of [10 20] to another interval"
    (are [b relation] (= relation (geometry/interval-relation 0 [10 20] b))
      [30 40] :before
      [20 30] :meets
      [15 25] :overlaps
      [10 30] :starts
      [5 25] :during
      [5 20] :finishes
      [10 20] :equals
      [0 5] :after
      [0 10] :met-by
      [5 15] :overlapped-by
      [10 15] :started-by
      [12 18] :contains
      [15 20] :finished-by))
  (testing "ends within the tolerance count as equal"
    (are [b relation] (= relation (geometry/interval-relation 0.5 [10 20] b))
      [20.4 30] :meets
      [20.6 30] :before
      [10.3 19.8] :equals
      [9.6 25] :starts)))

(deftest relation-test
  (let [a {:left 0 :top 0 :right 100 :bottom 20}
        b {:left 110 :top 0 :right 160 :bottom 20}]
    (is (= {:x :before :y :equals :center-x -1 :center-y 0 :width 1 :height 0}
           (geometry/relation a b)))
    (is (= {:x :after :y :equals :center-x 1 :center-y 0 :width -1 :height 0}
           (geometry/relation b a)))))

(deftest in-range-test
  (are [expected value result] (= result (geometry/in-range? 0.5 expected value))
    12 12.4 true
    12 12.6 false
    [4 8] 6 true
    [4 8] 8.4 true
    [4 8] 9 false
    [nil 8] -100 true
    [4 nil] 3.6 true
    [4 nil] 3 false))
