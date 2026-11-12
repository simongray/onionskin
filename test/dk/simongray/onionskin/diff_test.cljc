(ns dk.simongray.onionskin.diff-test
  (:require [clojure.test :refer [deftest is]]
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
                "font-size: 20px -> 18px on 3 elements, e.g. html:1\n"
                "color: red -> blue on 1 element, e.g. html:1>b:2\n"
                "::before content: \"x\" -> \"y\" on 1 element, e.g. html:1>b:2\n"
                "Moved or resized on 1 element, e.g. html:1>b:2 (left 20 -> 5, right 30 -> 15)\n"
                "a to b: no longer apart, left-of; now overlaps")
           (onionskin/explain changes)))))

(deftest levels-test
  (is (= #{:removed :added :box}
         (set (map :change (onionskin/diff before after {:levels #{:boxes}}))))))
