(ns ^:no-doc dk.simongray.onionskin.impl.capture
  "Captures of pages in a browser: the boxes and styles that checks and
  diffs compare."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [dk.simongray.onionskin.browser :as browser]
            [dk.simongray.onionskin.impl.spec :as spec]))

(def script
  "The JavaScript function that captures a page, from capture.js."
  (delay (slurp (io/resource "dk/simongray/onionskin/impl/capture.js"))))

(defn script-options
  "The options for capture.js that read what `spec` needs, with the `all`,
  `css` and `settle` options of capture."
  [spec all css settle]
  {:select (for [[selector {:keys [styles resolve]}] (spec/requirements spec)]
             {:selector selector
              :styles   (vec styles)
              :resolve  (vec resolve)})
   :all    (if (true? all) {} all)
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
  "The element `n` of a full capture from capture.js. Its styles become maps
  from the property `names` to the `values` that its indexes stand for."
  [names values n]
  (let [style (fn [indexes] (zipmap names (map values indexes)))]
    (cond-> {:path  (:path n)
             :box   (:box n)
             :style (style (:style n))}
      (:pseudo n) (assoc :pseudo (into {} (for [[pseudo indexes] (:pseudo n)]
                                            [pseudo (style indexes)]))))))

(defn normalize
  "The `raw` result of capture.js, turned into the capture that check and
  diff take."
  [{:keys [styleNames styleValues nodes] :as raw}]
  (cond-> {:url      (:url raw)
           :viewport (:viewport raw)
           :elements (into {} (for [{:keys [selector error matches]} (:elements raw)]
                                [selector (if error
                                            {:error error}
                                            {:matches (mapv match matches)})]))}
    nodes (assoc :nodes (mapv #(node styleNames styleValues %) nodes))))

(defn capture
  "A capture of the page at `url` in `browser`, with the `opts` of capture
  in the root namespace."
  [browser url {:keys [spec all width height scale css settle]
                :or   {width 1280 height 800 scale 1 settle true}
                :as   opts}]
  (let [all     (if (nil? all) (nil? spec) all)
        options (json/write-str (script-options spec all css settle))
        ;; The page sends the capture as one string of JSON, which is much
        ;; faster than having the browser describe it value by value.
        result  (browser/-visit browser url
                                {:width width :height height :scale scale}
                                (str "(" @script ")(" options
                                     ").then(JSON.stringify)"))]
    (normalize (json/read-str result :key-fn keyword))))
