(ns ^:no-doc dk.simongray.onionskin.impl.firefox
  "Firefox, driven over WebDriver BiDi."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [dk.simongray.onionskin.browser :as browser]
            [dk.simongray.onionskin.impl.driver :as driver])
  (:import [java.io Closeable]))

(def preferences
  "The preferences of the profile. Scrollbars take up no room, as in the
  other browsers, and Firefox neither looks for updates nor sends
  telemetry."
  {"ui.useOverlayScrollbars"                    1
   "app.update.disabledForTesting"              true
   "datareporting.policy.dataSubmissionEnabled" false})

(defn send!
  "Send the command `method` with `params` to `firefox` and return the
  result."
  [{:keys [connection] :as firefox} method params]
  (let [{:keys [type result message]}
        (driver/request connection {:method method :params params})]
    (when (= "error" type)
      (throw (ex-info (str method ": " message) {:method method :params params})))
    result))

(defn evaluate
  "The value of the JavaScript `expression` in the page `context` of
  `firefox`, awaited if it's a promise. Only a string, a number or a
  boolean comes back, since BiDi describes other values type by type."
  [firefox context expression]
  (let [{:keys [type result exceptionDetails]}
        (send! firefox "script.evaluate"
               {:expression   expression
                :target       {:context context}
                :awaitPromise true})]
    (when (= "exception" type)
      (throw (ex-info (str "JavaScript failed: " (:text exceptionDetails))
                      {:details exceptionDetails})))
    (:value result)))

(defrecord Firefox [process profile connection]
  browser/Browser
  (-visit [firefox url {:keys [width height scale]} expression]
    (let [{:keys [context]} (send! firefox "browsingContext.create" {:type "tab"})]
      (try
        (send! firefox "browsingContext.setViewport"
               {:context          context
                :viewport         {:width width :height height}
                :devicePixelRatio scale})
        (send! firefox "browsingContext.navigate"
               {:context context :url url :wait "complete"})
        (evaluate firefox context expression)
        (finally
          (send! firefox "browsingContext.close" {:context context})))))

  Closeable
  (close [firefox]
    (try
      (send! firefox "browser.close" {})
      (catch Exception _))
    (driver/stop! process profile)))

(defn launch
  "A headless Firefox with a fresh profile and the `opts` of browser in the
  root namespace."
  [{:keys [executable args timeout]
    :or   {timeout driver/default-timeout}
    :as   opts}]
  (let [profile (driver/profile)
        _       (spit (io/file profile "user.js")
                      (apply str (for [[k v] preferences]
                                   (str "user_pref(" (json/write-str k) ", "
                                        (json/write-str v) ");\n"))))
        process (driver/start
                  (concat [(or executable (driver/executable :firefox))
                           "--headless"
                           "--remote-debugging-port" "0"
                           "--profile" (str profile)
                           "--no-remote"]
                          args
                          ["about:blank"]))
        url     (str (driver/websocket-url process timeout) "/session")
        firefox (->Firefox process profile
                           (driver/connect url timeout (constantly nil)))]
    (send! firefox "session.new" {:capabilities {}})
    firefox))
