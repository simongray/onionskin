(ns dk.simongray.onionskin.browser-test
  "Captures of test/fixtures/page.html in a headless Chrome."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [dk.simongray.onionskin :as onionskin]))

(def ^:dynamic *browser* nil)

(use-fixtures :once (fn [run]
                      (with-open [browser (onionskin/browser)]
                        (binding [*browser* browser]
                          (run)))))

(def url
  (str (.toURI (io/file "test/fixtures/page.html"))))

(def spec
  {"nav .logo"    {:left-of     {:target "nav .search" :by 8}
                   :top-aligned "nav .search"
                   :width       100}
   "h1 .prefix"   {:left-of        "h1 .title"
                   :middle-aligned "h1 .title"
                   :style          {:letter-spacing "0.03125em"}}
   "ul.legend li" {:inside       "ul.legend"
                   :left-aligned "ul.legend li:first-child"}
   "a"            {:style {:color "var(--color-link)"}}})

(deftest check-test
  (let [capture (onionskin/capture *browser* url {:spec spec})]
    (testing "the spec holds, with values in other CSS syntax resolved"
      (is (= [] (onionskin/check capture spec))))
    (testing "a spec that doesn't hold fails, with the element"
      (is (= [["html:1>body:2>nav:1>div:1" :relation]]
             (map (juxt :path :problem)
                  (onionskin/check capture {"nav .logo" {:right-of "nav .search"}})))))))

(deftest widths-test
  (testing "the relations hold at another width, where the pixels differ"
    (let [capture (onionskin/capture *browser* url {:spec spec :width 400})]
      (is (= 400 (get-in capture [:viewport :width])))
      (is (= [] (onionskin/check capture spec))))))

(deftest diff-test
  (let [capture  #(onionskin/capture *browser* url {:spec spec :all true :css %})
        before   (capture nil)
        changes  (onionskin/diff before (capture "nav { gap: 12px; }"))]
    (testing "two captures of the same page are the same"
      (is (= [] (onionskin/diff before (capture nil) {:levels #{:styles :boxes :relations}}))))
    (testing "a change to the CSS shows on the element it styles"
      (is (some #(= {:change :style :path "html:1>body:2>nav:1"
                     :property "column-gap" :before "8px" :after "12px"}
                    %)
                changes))
      (is (some #(and (= :box (:change %))
                      (= "html:1>body:2>nav:1>input:2" (:path %)))
                changes)))))
