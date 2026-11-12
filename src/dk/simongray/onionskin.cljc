(ns dk.simongray.onionskin
  "Layout and style tests for web pages, at the level of detail that each
  test needs.

  Start a browser and capture a page in it. Then check the capture against
  a spec, or compare it with another capture of the page:

      (with-open [browser (onionskin/browser)]
        (let [capture (onionskin/capture browser url {:spec spec})]
          (onionskin/explain (onionskin/check capture spec))))

  A spec maps a CSS selector to the expectations of the elements that it
  matches:

      {\"nav .logo\"   {:left-of \"nav .search\"
                      :top-aligned \"nav .search\"}
       \".legend li\"  {:inside \".legend\"}
       \"h1 .prefix\"  {:left-of {:target \"h1 .title\" :by [4 8]}}
       \"a.rdf-uri\"   {:style {:color \"var(--color-link)\"}}
       \"main\"        {:width [nil 1100]}}

  A relation names its target by selector. For options, it takes a map of
  the :target, the :by range of the distance it measures and a :tolerance.
  With a vector of targets, the relation must hold for each of them. These
  are the relations:

  - :left-of, :right-of, :above and :below, which boxes that touch still
    satisfy. They measure the gap between the boxes.
  - :inside, :centered-in, :contains, :overlaps, :touches and :apart
  - :left-aligned, :right-aligned, :top-aligned and :bottom-aligned, then
    :center-aligned and :middle-aligned for the horizontal and the vertical
    centres. They measure the offset between the edges or centres.
  - :same-width-as, :same-height-as and :same-size-as, then :wider-than,
    :narrower-than, :taller-than and :shorter-than. All but :same-size-as
    measure the difference in size.

  The size of an element under :width or :height is a number or a [low
  high] range in CSS pixels, in which either end may be nil. The values
  under :style are CSS in any syntax. The browser resolves them for each
  element before the comparison, so a token, a named colour or a length in
  ch can stand in for the computed value.

  Specs, captures, failures and differences are plain data. Checking and
  comparing work in ClojureScript too, while browsers and captures need
  the JVM."
  (:require #?(:clj [dk.simongray.onionskin.impl.capture :as capture])
            [dk.simongray.onionskin.impl.check :as check]
            #?(:clj [dk.simongray.onionskin.impl.chrome :as chrome])
            [dk.simongray.onionskin.impl.diff :as diff]
            #?(:clj [dk.simongray.onionskin.impl.firefox :as firefox])
            [dk.simongray.onionskin.impl.report :as report]))

#?(:clj
   (defn browser
     "A headless browser of `kind` with the `opts` below, which with-open
     closes. It's Chrome unless you ask for another.

         (with-open [browser (onionskin/browser :firefox)]
           (onionskin/capture browser url))

     The kinds are :chrome, :chromium, :edge and :firefox. Each one runs
     from where it's usually installed. To run it from somewhere else, give
     the :executable or set the environment variable of the kind's name in
     capitals, e.g. FIREFOX. The options are:

         :executable  the path of the browser to run
         :args        more command-line arguments for it
         :timeout     ms to wait for the browser to start, to respond or to
                      load a page (default 30000)

     Threads can share a browser, e.g. to capture a page at several widths
     with pmap. For a browser that isn't among the kinds, implement the
     Browser protocol of the browser namespace."
     ([]
      (browser :chrome))
     ([kind]
      (browser kind {}))
     ([kind opts]
      (case kind
        (:chrome :chromium :edge) (chrome/launch kind opts)
        :firefox (firefox/launch opts)
        (throw (ex-info (str "Unknown kind of browser " kind) {:kind kind}))))))

#?(:clj
   (defn capture
     "A capture of the page at `url`, loaded in a new page of `browser` with
     the `opts` below.

         (onionskin/capture browser url {:spec spec :width 400})

     With a :spec, the capture has what check needs. Under :elements, each
     selector of the spec maps to the elements that it matched, with their
     :path in the document, their :box and the styles that the spec reads.

     Without a :spec, the capture has what diff needs: every element under
     :nodes, with its :path, its :box, its computed :style and the styles
     of its :pseudo elements. Each element also has its own :text and the
     :attributes that name it, which pair the elements of two captures when
     the markup changed. The options are:

         :spec    the spec whose elements and values to read
         :all     true to also read every element, or a map of :root (a
                  selector), :styles (the properties to read), :pseudo (the
                  pseudo elements to read) and :attributes (the attributes
                  to read: data-testid, id, name, type and aria-label by
                  default). It's true by default when there's no :spec.
         :width   the viewport width in CSS pixels (default 1280)
         :height  the viewport height in CSS pixels (default 800)
         :scale   the device pixel ratio (default 1)
         :css     CSS to add to the page first, e.g. to try a change
         :settle  false to capture without turning off animations and
                  waiting for fonts and frames (default true)"
     ([browser url]
      (capture/capture browser url {}))
     ([browser url opts]
      (capture/capture browser url opts))))

(defn check
  "The failures of `spec` in `capture` with the `opts` below: a vector of
  maps that's empty when the page matches the spec.

      (onionskin/check capture {\"nav .logo\" {:left-of \"nav .search\"}})
      ;; => []

  Each failure has the :check that failed and one of these under :problem:

      :relation          a relation that doesn't hold, with the :fine
                         relation of the two :boxes and, when the relation
                         measures a distance, its :measure and :value
      :size              a width or height out of range, with its :value
      :style             a property with another :value than the expected
                         one, which the browser :resolved for the element
      :invalid-style     an expected value that the browser doesn't accept
                         for the property, e.g. an undefined variable
      :no-match          a :selector that matched no element
      :ambiguous-target  a target :selector that matched several elements
      :invalid-selector  a :selector that the browser can't parse
      :not-captured      a :selector that the capture wasn't made with

  A failure of an element also has the element's :path and its :index
  among the :count elements that the selector matched. The options are:

      :tolerance  the CSS pixels within which edges, centres and sizes count
                  as equal, unless a check sets its own (default 0.5)"
  ([capture spec]
   (check/check capture spec {}))
  ([capture spec opts]
   (check/check capture spec opts)))

(defn diff
  "The differences between `before` and `after`, two captures of the same
  page, with the `opts` below: a vector of maps that's empty when nothing
  changed.

      (onionskin/diff (onionskin/capture browser url)
                      (onionskin/capture browser url {:css \"nav { gap: 0 }\"}))

  While the markup is the same, the elements of the two captures pair by
  their path. Where it changed, they pair by what tells them apart, from
  the most trusted sign to the least: a key attribute with a value that no
  other element has, identical contents, most of the same contents, the
  same shape or tag among the children of a pair, and last the same text or
  box. An element with no clear partner counts as removed or added, so a
  new wrapper is one added element and what's inside it still pairs.

  Removed and added elements come first, then the changes of each element
  in document order, then the relations. Each difference has one of these
  under :change:

      :removed, :added  the element at :path, which only one of the
                        captures has
      :moved            the element at :path, which was in another place of
                        the document, and not just under a new wrapper
      :style            a :property of the element at :path, or of its
                        :pseudo element, with the value :before and :after
      :box              the element at :path, which moved on the page or
                        changed size, with its box :before and :after
      :relation         a :subject and a :target among the selectors of a
                        spec, with the relations that they :lost and
                        :gained

  A :path is the element's path in `after`, or in `before` for a removed
  element. Where the two differ, the difference also has the :old-path and
  the sign that the element was :paired-by: :key, :subtree, :container,
  :shape, :tag, :text or :box. The options are:

      :levels     what to compare, a set of :styles, :boxes and :relations
                  (default #{:styles :boxes})
      :tolerance  the CSS pixels within which edges count as equal
                  (default 0.5)
      :keys       the attributes that name an element, the most trusted
                  first (default [\"data-testid\" \"id\"])"
  ([before after]
   (diff/diff before after {}))
  ([before after opts]
   (diff/diff before after opts)))

(defn explain
  "The `results` of check or diff as text for people to read: a sentence
  for each failure, or a line for each cause of the differences."
  [results]
  (if (some :change results)
    (report/differences results)
    (report/failures results)))
