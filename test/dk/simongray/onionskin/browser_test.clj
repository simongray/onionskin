(ns dk.simongray.onionskin.browser-test
  "Captures of test/fixtures/page.html in each browser that the BROWSERS
  environment variable names, e.g. \"chrome firefox\". It's Chrome alone by
  default."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [dk.simongray.onionskin :as onionskin]))

(def kinds
  (map keyword (str/split (or (System/getenv "BROWSERS") "chrome") #"\s+")))

(def ^:dynamic *browser* nil)

(use-fixtures :once (fn [run]
                      (doseq [kind kinds]
                        (with-open [browser (onionskin/browser kind)]
                          (binding [*browser* browser]
                            (testing (name kind)
                              (run)))))))

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

(deftest invalid-style-test
  (testing "a style that the browser doesn't accept fails, though the probe
            would otherwise have the element's own value"
    (doseq [style [{:colr "red"}
                   {:color "notacolor"}
                   {:color "var(--color-lnik)"}
                   {:letter-spacing "1 px"}
                   {:letter-spacing "var(--color-link)"}]]
      (let [spec {"h1 .prefix" {:style style}}]
        (is (= [:invalid-style]
               (map :problem (onionskin/check (onionskin/capture *browser* url {:spec spec})
                                              spec)))
            (pr-str style)))))
  (testing "a variable with a fallback is fine"
    (let [spec {"a" {:style {:color "var(--color-lnik, #0066BB)"}}}]
      (is (= [] (onionskin/check (onionskin/capture *browser* url {:spec spec}) spec))))))

(deftest widths-test
  (testing "the relations hold at other widths, where the pixels differ, and
            threads can share the browser"
    (let [widths   [400 700 1000 1300]
          captures (doall (pmap #(onionskin/capture *browser* url {:spec spec :width %})
                                widths))]
      (is (= widths (map #(get-in % [:viewport :width]) captures)))
      (is (= [[] [] [] []] (map #(onionskin/check % spec) captures)))
      (is (apply distinct? (map #(get-in % [:elements "nav .search" :matches 0 :box :right])
                                captures))))))

(deftest diff-test
  (let [before  (onionskin/capture *browser* url)
        changes (onionskin/diff before
                                (onionskin/capture *browser* url {:css "nav { gap: 12px; }"}))]
    (testing "a capture without a spec has every element"
      (is (= "html:1" (:path (first (:nodes before)))))
      (is (= "rgb(0, 102, 187)"
             (some #(when (= "html:1>body:2>p:4>a:1" (:path %))
                      (get-in % [:style "color"]))
                   (:nodes before))))
      (is (some #(get-in % [:pseudo "::before" "content"]) (:nodes before))))
    (testing "two captures of the same page are the same"
      (is (= [] (onionskin/diff before (onionskin/capture *browser* url)))))
    (testing "a change to the CSS shows on the element it styles"
      (is (some #(= {:change :style :path "html:1>body:2>nav:1"
                     :property "column-gap" :before "8px" :after "12px"}
                    %)
                changes))
      (is (some #(and (= :box (:change %))
                      (= "html:1>body:2>nav:1>input:2" (:path %)))
                changes)))
    (testing "the relations of a spec's elements can be compared too"
      ;; The spec names only these two, since where text falls depends on
      ;; the fonts of the machine.
      (let [spec    {"nav .logo" {:left-of "nav .search"}}
            capture #(onionskin/capture *browser* url {:spec spec :all true :css %})]
        (is (= [{:change :relation :subject "nav .logo" :target "nav .search"
                 :lost #{:left-of :apart} :gained #{:overlaps}}]
               (onionskin/diff (capture nil)
                               (capture "nav .search { margin-left: -20px; }")
                               {:levels #{:relations}})))))))
