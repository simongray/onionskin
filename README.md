onionskin
=========

Test the layout and style of a web page at the level of detail that each
test needs. Most tests only say how elements relate to each other, e.g.
that the logo is left of the search field. Each test is checked against
the page using a headless Chrome instance. When a detail matters, a test
can also say how far apart two elements are or what colour a link has.

You can compare two renderings of a page element by element too. This
shows what a change to the CSS did, or that it changed nothing.

- **Relations, not coordinates.** A test that says "left of" keeps passing
  when a font or a margin moves things a few pixels. Every named relation
  is built from one of Allen's 13 interval relations on each axis, so a
  coarse check and a fine one can't disagree. Edges less than half a pixel
  apart count as equal unless you set another tolerance.
- **Values in your own terms.** You write expected styles in any CSS
  syntax, e.g. `var(--color-link)` or `0.2ch`. The browser resolves them
  for each element before the comparison.
- **Differences you can read.** A diff groups the changes that share a
  cause, e.g. one line for a font size that changed on many elements.
- **Plain data.** Specs, captures, failures and differences are all maps
  and vectors. Checking and comparing work in both Clojure and
  ClojureScript. Capturing needs the JVM and Chrome but no WebDriver or
  Playwright.

The design draws on [earlier tools and research](doc/prior-art.md).


Getting started
---------------

It requires Clojure 1.11+, Java 11+ and Chrome. There's no release yet, so
add the latest commit on `main` to your `deps.edn`:

```clojure
io.github.simongray/onionskin {:git/sha "..."}
```

To use another browser based on Chromium, set the `CHROME` environment
variable.


Checking a page
---------------

A spec maps each CSS selector to what its elements should look like:

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

The expectations must hold for every element that a selector matches. A
target must match exactly one element. These are the relations:

- **Directions:** `:left-of`, `:right-of`, `:above` and `:below`. Boxes
  that touch still count.
- **Containment:** `:inside`, `:centered-in`, `:contains`, `:overlaps`,
  `:touches` and `:apart`.
- **Alignment:** `:left-aligned`, `:right-aligned`, `:top-aligned`,
  `:bottom-aligned`, `:center-aligned` and `:middle-aligned`. The last two
  compare the horizontal and the vertical centres.
- **Size:** `:same-width-as`, `:same-height-as`, `:same-size-as`,
  `:wider-than`, `:narrower-than`, `:taller-than` and `:shorter-than`.

For options, give the relation a map with the `:target` in it:

- `:by`: the distance that the relation measures, e.g. the gap for a
  direction or the offset for an alignment. It takes a number of CSS
  pixels or a `[low high]` range.
- `:tolerance`: the number of pixels to use in place of half a pixel.

An element's `:width` and `:height` also take a number or a range.

When a spec doesn't hold, `explain` returns text that says where and by how
much:

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

Capture every element with `:all` before and after a change, then compare
the two captures. With the `:css` option you can try a change without
editing the style sheets:

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

A diff pairs elements by their place in the document, so it works best for
changes to the CSS that leave the markup alone. By default it compares
styles and boxes. To also compare which named relations hold between the
elements of a spec, add `:relations` to the `:levels`.


License
-------

MIT
