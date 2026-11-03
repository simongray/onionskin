(ns dk.simongray.onionskin
  "Layout and style tests for web pages, at the level of detail each check
  needs.

  Capture a page in a headless Chrome, then check the capture against a
  spec, or compare it with another capture of the page:

      (with-open [browser (onionskin/browser)]
        (let [capture (onionskin/capture browser url {:spec spec})]
          (onionskin/explain (onionskin/check capture spec))))

  Checks, diffs and their explanations are functions of plain data in both
  Clojure and ClojureScript. Capturing needs the JVM and Chrome."
  (:require #?(:clj [dk.simongray.onionskin.capture :as capture])
            [dk.simongray.onionskin.check :as check]
            #?(:clj [dk.simongray.onionskin.chrome :as chrome])
            [dk.simongray.onionskin.diff :as diff]
            [dk.simongray.onionskin.report :as report]))

#?(:clj
   (defn browser
     "A headless Chrome of its own, which with-open closes. See chrome/launch
     for the `opts`."
     ([]
      (chrome/launch))
     ([opts]
      (chrome/launch opts))))

#?(:clj
   (defn capture
     "A capture of the page at `url`, loaded in `browser`. See capture/capture
     for the `opts`."
     [browser url opts]
     (capture/capture browser url opts)))

(defn check
  "The failures of `spec` in the capture `c`, which are none when the page
  looks as the spec says. See check/check for the `opts`."
  ([c spec]
   (check/check c spec))
  ([c spec opts]
   (check/check c spec opts)))

(defn diff
  "The differences between the captures `before` and `after`, which are none
  when nothing changed. See diff/diff for the `opts`."
  ([before after]
   (diff/diff before after))
  ([before after opts]
   (diff/diff before after opts)))

(defn explain
  "The `results` of check or diff as text for people to read."
  [results]
  (if (some :change results)
    (report/differences results)
    (report/failures results)))
