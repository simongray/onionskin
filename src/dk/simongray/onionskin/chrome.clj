(ns dk.simongray.onionskin.chrome
  "A headless Chrome, driven over the DevTools protocol.

  It needs nothing but an installed Chrome, as the JDK's own WebSocket
  client carries the protocol. The CHROME environment variable names
  another browser of Chromium."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io Closeable File]
           [java.net URI]
           [java.net.http HttpClient WebSocket WebSocket$Listener]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn default-executable
  "The Chrome that CHROME names, or else where Chrome is installed on macOS,
  Windows or Linux."
  []
  (or (System/getenv "CHROME")
      (let [os (str/lower-case (System/getProperty "os.name"))]
        (cond
          (str/includes? os "mac")
          "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

          (str/includes? os "win")
          "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"

          :else "google-chrome"))))

(defn- listener
  "A WebSocket listener that passes each whole text message to `f`."
  [f]
  (let [buffer (StringBuilder.)]
    (reify WebSocket$Listener
      (onOpen [_ socket]
        (.request socket 1))
      (onText [_ socket data last?]
        (.append buffer data)
        (when last?
          (let [message (str buffer)]
            (.setLength buffer 0)
            (f message)))
        (.request socket 1)
        nil))))

(defn- receiver
  "A function of a message from the browser that delivers a response to the
  promise in `pending` with its id, and queues an event in `events`."
  [pending events]
  (fn [text]
    (let [message (json/read-str text :key-fn keyword)]
      (if-let [id (:id message)]
        (when-let [p (get @pending id)]
          (swap! pending dissoc id)
          (deliver p message))
        (.offer ^LinkedBlockingQueue events message)))))

(defn send!
  "The result of the DevTools protocol command `method` with `params`, sent
  to the page that `session` is attached to, or to the browser itself."
  ([browser method params]
   (send! browser nil method params))
  ([{:keys [^WebSocket socket pending counter timeout]} session method params]
   (let [id (swap! counter inc)
         p  (promise)]
     (swap! pending assoc id p)
     (.join (.sendText socket
                       (json/write-str (cond-> {:id id :method method :params params}
                                         session (assoc :sessionId session)))
                       true))
     (let [response (deref p timeout ::timeout)]
       (cond
         (= ::timeout response)
         (throw (ex-info (str method " got no response within " timeout " ms")
                         {:method method}))

         (:error response)
         (throw (ex-info (str method ": " (get-in response [:error :message]))
                         {:method method :params params :error (:error response)}))

         :else (:result response))))))

(defn- await-event
  "The event `method` of `session`, skipping any other event, or an
  exception after the browser's timeout."
  [{:keys [^LinkedBlockingQueue events timeout]} session method]
  (let [deadline (+ (System/currentTimeMillis) timeout)]
    (loop []
      (let [left  (- deadline (System/currentTimeMillis))
            event (when (pos? left) (.poll events left TimeUnit/MILLISECONDS))]
        (cond
          (nil? event)
          (throw (ex-info (str "No " method " within " timeout " ms") {:method method}))

          (and (= method (:method event)) (= session (:sessionId event)))
          event

          :else (recur))))))

(defn- delete-tree!
  "Delete the directory `dir` and everything in it."
  [^File dir]
  (doseq [^File f (reverse (file-seq dir))]
    (.delete f)))

(defrecord Browser [^Process process ^WebSocket socket ^File profile
                    pending events counter timeout]
  Closeable
  (close [_]
    (try
      (.join (.sendText socket (json/write-str {:id -1 :method "Browser.close"}) true))
      (catch Exception _))
    (when-not (.waitFor process 5 TimeUnit/SECONDS)
      (.destroyForcibly process)
      (.waitFor process))
    (delete-tree! profile)))

(defn- devtools-url
  "The WebSocket URL of the DevTools protocol that `process` prints when
  it's ready, waiting at most `timeout` ms. The rest of what it prints is
  read and dropped, so that the process never blocks on a full pipe."
  [^Process process timeout]
  (let [url (promise)]
    (future
      (with-open [r (io/reader (.getErrorStream process))]
        (doseq [line (line-seq r)]
          (when-let [[_ u] (re-find #"DevTools listening on (ws://\S+)" line)]
            (deliver url u))))
      (deliver url nil))
    (or (deref url timeout nil)
        (do (.destroyForcibly process)
            (throw (ex-info "Chrome didn't start" {:timeout timeout}))))))

(defn launch
  "A headless Chrome of its own, with a fresh profile, which with-open
  closes.

  The options are:

      :executable  the browser to run (default: default-executable)
      :args        more command-line arguments
      :timeout     ms to wait for the browser, a command or a page load
                   (default 30000)"
  ([]
   (launch {}))
  ([{:keys [executable args timeout] :or {timeout 30000}}]
   (let [profile (.toFile (Files/createTempDirectory "onionskin-"
                                                     (make-array FileAttribute 0)))
         command (concat [(or executable (default-executable))
                          "--headless=new"
                          "--remote-debugging-port=0"
                          (str "--user-data-dir=" profile)
                          "--no-first-run"
                          "--no-default-browser-check"
                          "--hide-scrollbars"
                          "--mute-audio"]
                         ;; The sandbox of Chrome needs privileges that the
                         ;; runners of CI services don't give.
                         (when (System/getenv "CI") ["--no-sandbox"])
                         args
                         ["about:blank"])
         process (.start (doto (ProcessBuilder. ^java.util.List (vec command))
                           (.redirectOutput java.lang.ProcessBuilder$Redirect/DISCARD)))
         url     (devtools-url process timeout)
         pending (atom {})
         events  (LinkedBlockingQueue.)
         socket  (.join (.buildAsync (.newWebSocketBuilder (HttpClient/newHttpClient))
                                     (URI. url)
                                     (listener (receiver pending events))))]
     (->Browser process socket profile pending events (atom 0) timeout))))

(defn open
  "A new page of `browser`, which close-page closes."
  [browser]
  (let [{:keys [targetId]} (send! browser "Target.createTarget" {:url "about:blank"})
        {:keys [sessionId]} (send! browser "Target.attachToTarget"
                                   {:targetId targetId :flatten true})]
    (send! browser sessionId "Page.enable" {})
    {:browser browser :session sessionId :target targetId}))

(defn close-page
  "Close `page`."
  [{:keys [browser target]}]
  (send! browser "Target.closeTarget" {:targetId target}))

(defn resize!
  "Give `page` a viewport of `width` and `height` CSS pixels and a device
  pixel ratio of `scale`."
  [{:keys [browser session]} {:keys [width height scale]}]
  (send! browser session "Emulation.setDeviceMetricsOverride"
         {:width width :height height :deviceScaleFactor scale :mobile false}))

(defn navigate!
  "Load `url` in `page`, and wait for its load event."
  [{:keys [browser session]} url]
  (let [{:keys [errorText]} (send! browser session "Page.navigate" {:url url})]
    (when (seq errorText)
      (throw (ex-info (str "Can't load " url ": " errorText) {:url url})))
    (await-event browser session "Page.loadEventFired")))

(defn evaluate
  "The value of the JavaScript `expression` in `page`, once it resolves if
  it's a promise. The value comes back as JSON, with keywords as keys."
  [{:keys [browser session]} expression]
  (let [{:keys [result exceptionDetails]}
        (send! browser session "Runtime.evaluate"
               {:expression expression :awaitPromise true :returnByValue true})]
    (when exceptionDetails
      (throw (ex-info (str "JavaScript failed: "
                           (or (get-in exceptionDetails [:exception :description])
                               (:text exceptionDetails)))
                      {:details exceptionDetails})))
    (:value result)))
