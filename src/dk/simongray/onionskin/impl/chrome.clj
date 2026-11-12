(ns ^:no-doc dk.simongray.onionskin.impl.chrome
  "Chrome and the other browsers built on Chromium, driven over the Chrome
  DevTools Protocol."
  (:require [dk.simongray.onionskin.browser :as browser]
            [dk.simongray.onionskin.impl.driver :as driver])
  (:import [java.io Closeable]
           [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn send!
  "Send the command `method` with `params` to `chrome`, or to the page of
  its `session`, and return the result."
  ([chrome method params]
   (send! chrome nil method params))
  ([{:keys [connection] :as chrome} session method params]
   (let [command (cond-> {:method method :params params}
                   session (assoc :sessionId session))
         {:keys [result error]} (driver/request connection command)]
     (when error
       (throw (ex-info (str method ": " (:message error))
                       {:method method :params params :error error})))
     result)))

(defn open-page
  "A new page of `chrome`, which close-page closes."
  [{:keys [pages] :as chrome}]
  (let [{:keys [targetId]}  (send! chrome "Target.createTarget" {:url "about:blank"})
        {:keys [sessionId]} (send! chrome "Target.attachToTarget"
                                   {:targetId targetId :flatten true})
        events              (LinkedBlockingQueue.)]
    (swap! pages assoc sessionId events)
    (send! chrome sessionId "Page.enable" {})
    {:chrome chrome :session sessionId :target targetId :events events}))

(defn close-page
  "Close `page`."
  [{:keys [chrome session target] :as page}]
  (swap! (:pages chrome) dissoc session)
  (send! chrome "Target.closeTarget" {:targetId target}))

(defn resize!
  "Give `page` the `viewport`: its :width and :height in CSS pixels and its
  :scale, the device pixel ratio."
  [{:keys [chrome session] :as page} {:keys [width height scale] :as viewport}]
  (send! chrome session "Emulation.setDeviceMetricsOverride"
         {:width width :height height :deviceScaleFactor scale :mobile false}))

(defn- await-event
  "The next event `method` of `page`, skipping its other events. It throws
  after the browser's timeout."
  [{:keys [chrome ^LinkedBlockingQueue events] :as page} method]
  (let [timeout  (get-in chrome [:connection :timeout])
        deadline (+ (System/currentTimeMillis) timeout)]
    (loop []
      (let [left  (- deadline (System/currentTimeMillis))
            event (when (pos? left) (.poll events left TimeUnit/MILLISECONDS))]
        (cond
          (nil? event)
          (throw (ex-info (str "No " method " within " timeout " ms") {:method method}))

          (= method (:method event))
          event

          :else (recur))))))

(defn navigate!
  "Load `url` in `page` and wait for its load event."
  [{:keys [chrome session] :as page} url]
  (let [{:keys [errorText]} (send! chrome session "Page.navigate" {:url url})]
    (when (seq errorText)
      (throw (ex-info (str "Can't load " url ": " errorText) {:url url})))
    (await-event page "Page.loadEventFired")))

(defn evaluate
  "The value of the JavaScript `expression` in `page`, awaited if it's a
  promise. A value of JSON comes back as Clojure data with keywords for
  keys."
  [{:keys [chrome session] :as page} expression]
  (let [{:keys [result exceptionDetails]}
        (send! chrome session "Runtime.evaluate"
               {:expression expression :awaitPromise true :returnByValue true})]
    (when exceptionDetails
      (throw (ex-info (str "JavaScript failed: "
                           (or (get-in exceptionDetails [:exception :description])
                               (:text exceptionDetails)))
                      {:details exceptionDetails})))
    (:value result)))

(defrecord Chrome [process profile connection pages]
  browser/Browser
  (-visit [chrome url viewport expression]
    (let [page (open-page chrome)]
      (try
        (resize! page viewport)
        (navigate! page url)
        (evaluate page expression)
        (finally
          (close-page page)))))

  Closeable
  (close [chrome]
    (try
      (send! chrome "Browser.close" {})
      (catch Exception _))
    (driver/stop! process profile)))

(defn launch
  "A headless Chrome, Chromium or Edge by `kind`, with a fresh profile and
  the `opts` of browser in the root namespace."
  [kind {:keys [executable args timeout]
         :or   {timeout driver/default-timeout}
         :as   opts}]
  (let [profile (driver/profile)
        process (driver/start
                  (concat [(or executable (driver/executable kind))
                           "--headless=new"
                           "--remote-debugging-port=0"
                           (str "--user-data-dir=" profile)
                           "--no-first-run"
                           "--no-default-browser-check"
                           "--hide-scrollbars"
                           "--mute-audio"]
                          ;; The sandbox needs privileges that CI runners
                          ;; don't have.
                          (when (System/getenv "CI") ["--no-sandbox"])
                          args
                          ["about:blank"]))
        url     (driver/websocket-url process timeout)
        pages   (atom {})
        ;; Each page waits for its own events, so that threads can share
        ;; the browser.
        queue   (fn [{:keys [sessionId] :as event}]
                  (some-> ^LinkedBlockingQueue (get @pages sessionId)
                          (.offer event)))]
    (->Chrome process profile (driver/connect url timeout queue) pages)))
