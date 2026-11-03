(ns dk.simongray.onionskin.check-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.onionskin.check :as check]
            [dk.simongray.onionskin.report :as report]
            [dk.simongray.onionskin.spec :as spec]))

(defn- element
  [path left top right bottom & {:as more}]
  (merge {:path path :box {:left left :top top :right right :bottom bottom}}
         more))

(def capture
  {:elements
   {"nav .logo"   {:matches [(element "html:1>body:2>nav:1>img:1" 0 0 100 40)]}
    "nav .search" {:matches [(element "html:1>body:2>nav:1>input:2" 108 0 300 40)]}
    "nav li"      {:matches [(element "a" 0 50 80 70) (element "b" 0 72 120 92)]}
    "h1 .prefix"  {:matches [(element "c" 0 100 40 130
                                      :style {"letter-spacing" "1px"}
                                      :resolved {["letter-spacing" "0.2ch"] "1.96px"})]}
    "p"           {:matches [(element "d" 0 0 10 10) (element "e" 0 20 10 30)]}
    "aside"       {:matches []}
    "a["          {:error "not a valid selector"}}})

(deftest passing-test
  (is (= [] (check/check capture {"nav .logo" {:left-of "nav .search"
                                               :top-aligned "nav .search"
                                               :width [90 110]}
                                  "nav li" {:left-aligned "nav .logo"
                                            :below "nav .logo"}}))))

(deftest relation-failure-test
  (let [[f & more] (check/check capture {"nav .logo" {:left-of {:target "nav .search"
                                                                :by [0 4]}}})]
    (is (nil? more))
    (is (= {:problem :relation :measure :gap :value 8 :index 0 :count 1}
           (select-keys f [:problem :measure :value :index :count])))
    (is (= (str "nav .logo should be left of nav .search by 0 to 4 px, but the gap"
                " is 8 px, and the boxes are x: before, y: equals.\n"
                "  at html:1>body:2>nav:1>img:1")
           (report/failures [f])))))

(deftest each-subject-test
  (testing "every element that the subject matches must hold"
    (let [failures (check/check capture {"nav li" {:width 80}})]
      (is (= [1] (map :index failures)))
      (is (= "nav li (2 of 2) should have a width of 80 px, but has 120 px.\n  at b"
             (report/failures failures))))))

(deftest style-test
  (let [failures (check/check capture {"h1 .prefix" {:style {:letter-spacing "0.2ch"}}})]
    (is (= "h1 .prefix should have letter-spacing 0.2ch (1.96px), but has 1px.\n  at c"
           (report/failures failures)))))

(deftest matching-test
  (is (= [:no-match :ambiguous-target :invalid-selector]
         (map :problem (check/check capture {"aside" {:above "nav .logo"}
                                             "nav .logo" {:above "p"}
                                             "a[" {:width 10}})))))

(deftest tolerance-test
  (let [spec {"nav .search" {:right-of {:target "nav .logo" :by 7}}}]
    (is (seq (check/check capture spec)))
    (is (= [] (check/check capture spec {:tolerance 1})))))

(deftest unknown-relation-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
               (spec/checks {"a" {:lefft-of "b"}}))))

(deftest requirements-test
  (is (= {"h1 .prefix" {:styles #{"letter-spacing"}
                        :resolve #{["letter-spacing" "0.2ch"]}}
          "nav .logo" {:styles #{} :resolve #{}}
          "nav .search" {:styles #{} :resolve #{}}}
         (spec/requirements {"h1 .prefix" {:style {:letter-spacing "0.2ch"}}
                             "nav .logo" {:left-of "nav .search"}}))))
