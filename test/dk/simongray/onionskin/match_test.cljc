(ns dk.simongray.onionskin.match-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.onionskin.impl.match :as match]))

(defn- nodes
  "The nodes of a capture for `form`, a tree like Hiccup: a tag, then maybe a
  map of attributes, maybe a string of text, and the children."
  ([form]
   (vec (nodes nil 1 form)))
  ([parent i [tag & more]]
   (let [attributes (first (filter map? more))
         text       (first (filter string? more))
         path       (str (when parent (str parent ">")) (name tag) ":" i)]
     (cons (cond-> {:path path :box {:left 0 :top 0 :right 0 :bottom 0}}
             attributes (assoc :attributes attributes)
             text (assoc :text text))
           (apply concat (map-indexed #(nodes path (inc %1) %2)
                                      (filter vector? more)))))))

(defn- page
  "A page with `nav` and `main` as the children of its nav and main
  elements."
  [nav main]
  (nodes [:html [:body (into [:nav] nav) (into [:main] main)]]))

(def logo [:div {"id" "logo"}])
(def search [:input {"type" "search"}])
(def title [:h1 "Title"])
(def items [:ul [:li "One"] [:li "Two"] [:li "Three"]])
(def text [:p "A " [:a "link"]])

(def before
  (page [logo search] [title items text]))

(defn- paired
  "The path after by the path before, for the pairs of `a` and `b`."
  [a b]
  (into {} (map (juxt (comp :path :before) (comp :path :after)))
        (match/pairs a b ["data-testid" "id"])))

(defn- rules
  "The rule by the path before, for the pairs of `a` and `b`."
  [a b]
  (into {} (map (juxt (comp :path :before) :rule))
        (match/pairs a b ["data-testid" "id"])))

(defn- moved
  "The paths before of the pairs of `a` and `b` that moved."
  [a b]
  (map (comp :path :before) (filter :moved? (match/pairs a b ["data-testid" "id"]))))

(deftest same-markup-test
  (is (= (zipmap (map :path before) (map :path before))
         (paired before before)))
  (is (= #{:path} (set (vals (rules before before))))))

(deftest wrapper-test
  (let [after (page [[:div logo] search] [title items text])]
    (testing "an element in a new wrapper pairs with itself"
      (is (= "html:1>body:1>nav:1>div:1>div:1"
             (get (paired before after) "html:1>body:1>nav:1>div:1")))
      (is (= :key (get (rules before after) "html:1>body:1>nav:1>div:1"))))
    (testing "every element pairs, and only the wrapper is left"
      (is (= (count before) (count (paired before after))))
      (is (= ["html:1>body:1>nav:1>div:1"]
             (remove (set (vals (paired before after))) (map :path after)))))
    (testing "nothing moved"
      (is (empty? (moved before after))))
    (testing "the same holds when the wrapper goes away"
      (is (= (count before) (count (paired after before))))
      (is (empty? (moved after before))))))

(deftest twins-test
  (testing "one of several identical elements in a new wrapper"
    (let [a (nodes [:ul [:li "x"] [:li "x"] [:li "x"]])
          b (nodes [:ul [:li "x"] [:div [:li "x"]] [:li "x"]])]
      (is (= (count a) (count (paired a b))))
      (is (not (contains? (set (vals (paired a b))) "ul:1>div:2"))))))

(deftest moved-test
  (let [after (page [logo search title] [items text])]
    (is (= "html:1>body:1>nav:1>h1:3"
           (get (paired before after) "html:1>body:1>main:2>h1:1")))
    (is (= ["html:1>body:1>main:2>h1:1"] (moved before after)))))

(deftest changed-test
  (testing "an element with other text pairs by its shape"
    (let [after (page [logo search]
                      [title [:ul [:li "One"] [:li "2"] [:li "Three"]] text [:p "New"]])
          li    "html:1>body:1>main:2>ul:2>li:2"]
      (is (= li (get (paired before after) li)))
      (is (= :shape (get (rules before after) li)))))
  (testing "a new element among others leaves them paired"
    (let [after (page [logo search]
                      [title [:ul [:li "One"] [:li "New"] [:li "Two"] [:li "Three"]] text])
          ul    "html:1>body:1>main:2>ul:2"]
      (is (= {(str ul ">li:1") (str ul ">li:1")
              (str ul ">li:2") (str ul ">li:3")
              (str ul ">li:3") (str ul ">li:4")}
             (select-keys (paired before after) (map #(str ul ">li:" %) [1 2 3]))))
      (is (= (count before) (count (paired before after))))))
  (testing "a container with new contents pairs by what it still holds"
    (let [after (page [logo search]
                      [title [:ol [:li "Zero"]] items [:p "More"] text])]
      (is (= "html:1>body:1>main:2" (get (paired before after) "html:1>body:1>main:2")))
      (is (= "html:1>body:1>main:2>ul:3" (get (paired before after) "html:1>body:1>main:2>ul:2"))))))

(deftest keys-test
  (testing "a key pairs an element that changed and moved"
    (let [a (page [logo search] [[:button {"data-testid" "save"} "Save"] items])
          b (page [logo search [:a {"data-testid" "save"} "Save changes"]] [items])]
      (is (= "html:1>body:1>nav:1>a:3"
             (get (paired a b) "html:1>body:1>main:2>button:1")))
      (is (= :key (get (rules a b) "html:1>body:1>main:2>button:1")))))
  (testing "a key that two elements share pairs nothing"
    (let [a (nodes [:div [:p {"id" "x"} "one"] [:b]])
          b (nodes [:div [:i] [:em {"id" "x"} "two"] [:u {"id" "x"} "three"]])]
      (is (= {"div:1" "div:1"} (paired a b))))))

(deftest ambiguous-test
  (testing "elements pair with nothing when nothing tells them apart"
    (let [a (nodes [:body [:div [:p "x"]] [:div [:span "y"] [:span "z"]]])
          b (nodes [:body [:div [:em "q"]] [:div [:b "r"] [:i "s"] [:u "t"]]])]
      (is (= {"body:1" "body:1"} (paired a b))))))

(deftest invariants-test
  (let [versions [before
                  (page [[:div logo] search] [title items text])
                  (page [logo search title] [items text])
                  (page [search logo] [text items title])
                  (page [logo] [title [:ul [:li "One"] [:li "Three"]] [:section text]])
                  (page [] [[:div [:div [:div title]]]])]]
    (doseq [a versions
            b versions]
      (testing "each element has at most one partner"
        (is (apply distinct? (vals (paired a b)))))
      (testing "the pairs are the same in both directions"
        (is (= (paired a b)
               (into {} (map (comp vec reverse)) (paired b a))))))))
