(ns dk.simongray.onionskin.capture
  "Captures of pages in a headless Chrome: the boxes and styles that checks
  and diffs compare."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [dk.simongray.onionskin.chrome :as chrome]
            [dk.simongray.onionskin.spec :as spec]))

(def script
  "The JavaScript function that captures a page, from capture.js."
  (delay (slurp (io/resource "dk/simongray/onionskin/capture.js"))))

(defn script-options
  "The options for capture.js that read what `spec` needs, with the `all`,
  `css` and `settle` options of capture."
  [spec all css settle]
  {:select (for [[selector {:keys [styles resolve]}] (spec/requirements spec)]
             {:selector selector
              :styles   (vec styles)
              :resolve  (vec resolve)})
   :all    (cond (map? all) all all {})
   :css    css
   :settle settle})

(defn- match
  "The matched element `m` from capture.js, with maps for its style and its
  resolved values."
  [m]
  {:path     (:path m)
   :box      (:box m)
   :style    (into {} (:style m))
   :resolved (into {} (map (fn [[property value resolved]]
                             [[property value] resolved]))
                   (:resolved m))})

(defn- node
  "The element `n` of a full capture from capture.js, with its style values
  zipped into maps by the property `names`."
  [names n]
  (cond-> {:path     (:path n)
           :tag      (:tag n)
           :rendered (:rendered n)
           :box      (:box n)
           :style    (zipmap names (:style n))}
    (:pseudo n) (assoc :pseudo (into {} (for [[pseudo values] (:pseudo n)]
                                          [pseudo (zipmap names values)])))))

(defn normalize
  "The `raw` result of capture.js, turned into the capture that check and
  diff take."
  [raw]
  (cond-> {:url      (:url raw)
           :viewport (:viewport raw)
           :elements (into {} (for [{:keys [selector error matches]} (:elements raw)]
                                [selector (if error
                                            {:error error}
                                            {:matches (mapv match matches)})]))}
    (:nodes raw) (assoc :nodes (mapv (partial node (:styleNames raw)) (:nodes raw)))))

(defn capture
  "A capture of the page at `url`, loaded in a new page of `browser` with
  the `opts` below.

  The options are:

      :spec    the spec whose elements and values to read
      :all     true to also read every element for a diff, or a map of
               :root (a selector), :styles (the properties to read) and
               :pseudo (the pseudo elements to read)
      :width   the viewport width in CSS pixels (default 1280)
      :height  the viewport height in CSS pixels (default 800)
      :scale   the device pixel ratio (default 1)
      :css     CSS to add to the page first, e.g. to try a change
      :settle  false to capture without turning off animations and
               waiting for fonts and frames (default true)"
  [browser url {:keys [spec all width height scale css settle]
                :or   {width 1280 height 800 scale 1 settle true}
                :as   opts}]
  (let [page    (chrome/open browser)
        options (script-options spec all css settle)]
    (try
      (chrome/resize! page {:width width :height height :scale scale})
      (chrome/navigate! page url)
      (normalize (chrome/evaluate page (str "(" @script ")(" (json/write-str options) ")")))
      (finally
        (chrome/close-page page)))))
