(ns dk.simongray.onionskin.diff-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.onionskin :as onionskin]))

(defn- node
  [path left style & {:as more}]
  (merge {:path path :box {:left left :top 0 :right (+ left 10) :bottom 10} :style style}
         more))

(def before
  {:nodes [(node "html:1" 0 {"color" "red" "font-size" "20px"})
           (node "html:1>a:1" 0 {"color" "red" "font-size" "20px"})
           (node "html:1>b:2" 20 {"color" "red" "font-size" "20px"}
                 :pseudo {"::before" {"content" "\"x\""}})
           (node "html:1>c:3" 40 {})]
   :elements {"a" {:matches [(node "html:1>a:1" 0 {})]}
              "b" {:matches [(node "html:1>b:2" 20 {})]}}})

(def after
  {:nodes [(node "html:1" 0 {"color" "red" "font-size" "18px"})
           (node "html:1>a:1" 0 {"color" "red" "font-size" "18px"})
           (node "html:1>b:2" 5 {"color" "blue" "font-size" "18px"}
                 :pseudo {"::before" {"content" "\"y\""}})
           (node "html:1>d:3" 40 {})]
   :elements {"a" {:matches [(node "html:1>a:1" 0 {})]}
              "b" {:matches [(node "html:1>b:2" 5 {})]}}})

(deftest no-change-test
  (is (= [] (onionskin/diff before before {:levels #{:styles :boxes :relations}}))))

(deftest changes-test
  (let [changes (onionskin/diff before after {:levels #{:styles :boxes :relations}})]
    (is (= [:removed :added :style :style :style :style :style :box :relation]
           (map :change changes)))
    (is (= {:change :relation :subject "a" :target "b"
            :lost #{:apart :left-of} :gained #{:overlaps}}
           (last changes)))
    (is (= (str "Removed on 1 element, e.g. html:1>c:3\n"
                "Added on 1 element, e.g. html:1>d:3\n"
                "font-size: 20px -> 18px on 1 element and 2 inside it, e.g. html:1\n"
                "color: red -> blue on 1 element, e.g. html:1>b:2\n"
                "::before content: \"x\" -> \"y\" on 1 element, e.g. html:1>b:2\n"
                "Moved or resized on 1 element, e.g. html:1>b:2 (left 20 -> 5, right 30 -> 15)\n"
                "a to b: no longer apart, left-of; now overlaps")
           (onionskin/explain changes)))))

(deftest markup-test
  (testing "a change shows on an element that got a wrapper"
    (let [changes (onionskin/diff
                    {:nodes [(node "html:1" 0 {})
                             (node "html:1>a:1" 0 {"color" "red"} :text "x")
                             (node "html:1>b:2" 20 {} :text "y")]}
                    {:nodes [(node "html:1" 0 {})
                             (node "html:1>div:1" 0 {})
                             (node "html:1>div:1>a:1" 0 {"color" "blue"} :text "x")
                             (node "html:1>b:2" 20 {} :text "y")]})]
      (is (= [{:change :added :path "html:1>div:1"}
              {:change :style :path "html:1>div:1>a:1" :old-path "html:1>a:1"
               :paired-by :subtree :property "color" :before "red" :after "blue"}]
             changes))
      (is (= (str "Added on 1 element, e.g. html:1>div:1\n"
                  "color: red -> blue on 1 element, e.g. html:1>div:1>a:1")
             (onionskin/explain changes)))))
  (testing "an element in another place has moved"
    (let [changes (onionskin/diff
                    {:nodes [(node "html:1" 0 {})
                             (node "html:1>nav:1" 0 {})
                             (node "html:1>nav:1>a:1" 0 {} :text "x")
                             (node "html:1>main:2" 20 {})]}
                    {:nodes [(node "html:1" 0 {})
                             (node "html:1>nav:1" 0 {})
                             (node "html:1>main:2" 20 {})
                             (node "html:1>main:2>a:1" 0 {} :text "x")]})]
      (is (= [{:change :moved :path "html:1>main:2>a:1" :old-path "html:1>nav:1>a:1"
               :paired-by :subtree}]
             changes))
      (is (= (str "Moved in the markup on 1 element, e.g. html:1>main:2>a:1"
                  " (was html:1>nav:1>a:1)")
             (onionskin/explain changes))))))

(deftest follows-test
  (let [changes (onionskin/diff
                  {:nodes [(node "html:1" 0 {})
                           (node "html:1>p:1" 0 {"color" "red" "border-top-color" "red"
                                                 "width" "10px"})
                           (node "html:1>p:1>a:1" 0 {"color" "red"})]}
                  {:nodes [(node "html:1" 0 {})
                           (node "html:1>p:1" 5 {"color" "blue" "border-top-color" "blue"
                                                 "width" "12px"})
                           (node "html:1>p:1>a:1" 0 {"color" "blue"})]})]
    (testing "a change that another one explains says what it follows"
      (is (= {["html:1>p:1" "color"]            nil
              ["html:1>p:1" "border-top-color"] "color"
              ["html:1>p:1" "width"]            :box
              ["html:1>p:1>a:1" "color"]        :parent}
             (into {} (map (juxt (juxt :path :property) :follows))
                   (filter :property changes)))))
    (testing "and is counted on that one's line"
      (is (= (str "color: red -> blue on 1 element and 1 inside it, e.g. html:1>p:1,"
                  " with 1 property that follows it\n"
                  "Moved or resized on 1 element, e.g. html:1>p:1 (left 0 -> 5, right 10 -> 15),"
                  " with 1 style value that follows")
             (onionskin/explain changes))))))

(deftest levels-test
  (is (= #{:removed :added :box}
         (set (map :change (onionskin/diff before after {:levels #{:boxes}}))))))
