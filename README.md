onionskin
=========

Test the layout and style of a web page at the level of detail each test
needs. A test says how elements relate, e.g. that the logo is left of the
search field and top-aligned with it, and onionskin checks that against the
page as a headless Chrome renders it. Where a detail matters, the same test
can say how far apart they are, or what colour a link has.

You can also compare two renderings of a page element by element, to see
what a change to the CSS did, or that it did nothing.

- **Relations, not coordinates.** A test that says "left of" keeps passing
  when a font or a margin moves things by a few pixels. Each named
  relation is defined by the interval relation of the two boxes on each
  axis (Allen's 13), so a coarse check and a fine one can't disagree. Edges
  within half a pixel of each other count as equal, unless you say
  otherwise.
- **Values in your own terms.** Expected styles are CSS in any syntax,
  e.g. `var(--color-link)` or `0.2ch`, which the browser resolves for each
  element before they're compared.
- **Differences you can read.** A diff groups the changes that share a
  cause, e.g. one line for a font size that changed on many elements.
- **Plain data.** Specs, captures, failures and differences are maps and
  vectors, and checking and comparing work in Clojure and ClojureScript.
  Capturing needs the JVM and Chrome, but no WebDriver or Playwright.

The design rests on [earlier tools and research](doc/prior-art.md).


Getting started
---------------

It requires Clojure 1.11+, Java 11+ and Chrome. There's no release yet, so
depend on the latest commit on `main` in your `deps.edn`:

```clojure
io.github.simongray/onionskin {:git/sha "..."}
```

Set the `CHROME` environment variable to use another browser based on
Chromium.


Checking a page
---------------

A spec maps a CSS selector to what the elements it matches should look
like:

```clojure
(require '[dk.simongray.onionskin :as onionskin])

(def spec
  {"nav .logo"    {:left-of     {:target "nav .search" :by 8}
                   :top-aligned "nav .search"}
   "ul.legend li" {:inside       "ul.legend"
                   :left-aligned "ul.legend li:first-child"}
   "a"            {:style {:color "var(--color-link)"}}})

(with-open [browser (onionskin/browser)]
  (let [capture (onionskin/capture browser url {:spec spec :width 400})]
    (onionskin/check capture spec)))
;; => []
```

Every element that a selector matches must hold, and a target must match
exactly one element. The relations are:

- **Directions:** `:left-of`, `:right-of`, `:above` and `:below`, which
  allow the boxes to touch.
- **Containment:** `:inside`, `:contains`, `:overlaps`, `:touches` and
  `:apart`.
- **Alignment:** `:left-aligned`, `:right-aligned`, `:top-aligned`,
  `:bottom-aligned`, `:center-aligned` (the horizontal centres),
  `:middle-aligned` (the vertical centres) and `:centered-in`.
- **Size:** `:same-width-as`, `:same-height-as`, `:same-size-as`,
  `:wider-than`, `:narrower-than`, `:taller-than` and `:shorter-than`.

Give a map with the `:target` to add options: `:by` is the distance that
the relation measures, as a number or a `[low high]` range in CSS pixels,
e.g. the gap for a direction or the offset for an alignment, and
`:tolerance` replaces the half pixel. `:width` and `:height` take a number
or a range too.

When a spec doesn't hold, `explain` says where and by how much:

```clojure
(onionskin/explain
  (onionskin/check capture {"nav .logo" {:left-of {:target "nav .search"
                                                   :by [0 4]}}}))
;; => "nav .logo should be left of nav .search by 0 to 4 px, but the gap
;;     is 8 px, and the boxes are x: before, y: equals.
;;       at html:1>body:2>nav:1>div:1"
```

In a test:

```clojure
(deftest layout
  (let [failures (onionskin/check capture spec)]
    (is (empty? failures) (onionskin/explain failures))))
```


Comparing two versions
----------------------

Capture every element with `:all`, before and after a change, and compare
the two. The `:css` option adds CSS to the page, so you can try a change
without editing the style sheets:

```clojure
(with-open [browser (onionskin/browser)]
  (let [before (onionskin/capture browser url {:all true})
        after  (onionskin/capture browser url {:all true
                                               :css "nav { gap: 12px; }"})]
    (onionskin/explain (onionskin/diff before after))))
;; => "column-gap: 8px -> 12px on 1 element, e.g. html:1>body:2>nav:1
;;     row-gap: 8px -> 12px on 1 element, e.g. html:1>body:2>nav:1
;;     ...
;;     Moved or resized on 1 element, e.g. html:1>body:2>nav:1>input:2
;;     (left 116 -> 120)"
```

Elements are paired by their place in the document, so a diff suits a
change to the CSS that leaves the markup alone. It compares styles and
boxes by default; add `:relations` to the `:levels` to also compare which
named relations hold between the elements of a spec.


License
-------

MIT
