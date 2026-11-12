(ns ^:no-doc dk.simongray.onionskin.impl.driver
  "What the drivers of Chrome and Firefox share: the browser's process and
  profile, and the WebSocket that carries commands to it.

  Both browsers take a command as JSON with an id and answer with the same
  id. The JDK's own WebSocket client carries the messages, so a driver
  needs nothing but the installed browser."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.net URI]
           [java.net.http HttpClient WebSocket WebSocket$Listener]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [java.util.concurrent TimeUnit]))

(def default-timeout
  "The ms that a driver waits for its browser to start or to respond."
  30000)

(def executables
  "Where each kind of browser is usually installed, by operating system."
  {:chrome   {:mac     "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
              :windows "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
              :linux   "google-chrome"}
   :chromium {:mac     "/Applications/Chromium.app/Contents/MacOS/Chromium"
              :windows "C:\\Program Files\\Chromium\\Application\\chrome.exe"
              :linux   "chromium"}
   :edge     {:mac     "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
              :windows "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
              :linux   "microsoft-edge"}
   :firefox  {:mac     "/Applications/Firefox.app/Contents/MacOS/firefox"
              :windows "C:\\Program Files\\Mozilla Firefox\\firefox.exe"
              :linux   "firefox"}})

(defn executable
  "The path of the browser of `kind` to run: the one in the environment
  variable of its name in capitals, e.g. FIREFOX, or else where the browser
  is usually installed."
  [kind]
  (or (System/getenv (str/upper-case (name kind)))
      (let [os (str/lower-case (System/getProperty "os.name"))]
        (get-in executables [kind (cond
                                    (str/includes? os "mac") :mac
                                    (str/includes? os "win") :windows
                                    :else :linux)]))))

(defn profile
  "A new, empty directory for the profile of a browser."
  ^File []
  (.toFile (Files/createTempDirectory "onionskin-" (make-array FileAttribute 0))))

(defn start
  "Start a browser with `command`, its executable followed by its
  arguments, and return the process."
  ^Process [command]
  (.start (doto (ProcessBuilder. ^java.util.List (vec command))
            (.redirectErrorStream true))))

(defn websocket-url
  "The WebSocket URL that `process` prints once the browser is ready,
  waiting at most `timeout` ms.

  A thread reads and drops the rest of the output, so the process never
  blocks on a full pipe."
  [^Process process timeout]
  (let [url (promise)]
    (future
      (with-open [r (io/reader (.getInputStream process))]
        (doseq [line (line-seq r)]
          (when-let [[_ u] (re-find #"listening on (ws://\S+)" line)]
            (deliver url u))))
      (deliver url nil))
    (or (deref url timeout nil)
        (do (.destroyForcibly process)
            (throw (ex-info "The browser didn't start" {:timeout timeout}))))))

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

(defn connect
  "A connection to the browser at the WebSocket `url`, whose commands wait
  at most `timeout` ms for a response. Every other message from the
  browser goes to `on-event`."
  [url timeout on-event]
  (let [pending (atom {})
        receive (fn [text]
                  (let [{:keys [id] :as message} (json/read-str text :key-fn keyword)]
                    (if id
                      (when-let [p (get @pending id)]
                        (swap! pending dissoc id)
                        (deliver p message))
                      (on-event message))))
        socket  (.join (.buildAsync (.newWebSocketBuilder (HttpClient/newHttpClient))
                                    (URI. url)
                                    (listener receive)))]
    {:socket socket :pending pending :counter (atom 0) :timeout timeout}))

(defn request
  "Send `command` over `connection` and return the browser's response, or
  throw when none comes within the connection's timeout.

  The `command` is a map of the :method, the :params and whatever else the
  browser's protocol takes."
  [{:keys [^WebSocket socket pending counter timeout] :as connection} command]
  (let [id (swap! counter inc)
        p  (promise)]
    (swap! pending assoc id p)
    ;; A WebSocket sends one message at a time, and threads share it.
    (locking socket
      (.join (.sendText socket (json/write-str (assoc command :id id)) true)))
    (let [response (deref p timeout nil)]
      (when-not response
        (swap! pending dissoc id)
        (throw (ex-info (str (:method command) " got no response within "
                             timeout " ms")
                        {:command command})))
      response)))

(defn stop!
  "Wait for `process` to exit, kill it when that takes more than 5 seconds,
  and delete its `profile`."
  [^Process process ^File profile]
  (when-not (.waitFor process 5 TimeUnit/SECONDS)
    (.destroyForcibly process)
    (.waitFor process))
  (doseq [^File f (reverse (file-seq profile))]
    (.delete f)))
