(ns dk.simongray.onionskin.browser
  "The browsers that render pages for a capture.

  A browser is anything that implements Browser. The library comes with
  Chrome, Chromium, Edge and Firefox, from browser in the root namespace.
  A browser of your own could implement Browser with a WebDriver client,
  e.g. to capture pages in Safari.")

(defprotocol Browser
  "A browser that loads pages and runs JavaScript in them."
  (-visit [browser url viewport expression]
    "Load `url` in a new page of `browser` with the `viewport`, return the
    value of the JavaScript `expression` in it, and close the page.

    The `viewport` is a map of its :width and :height in CSS pixels and its
    :scale, the device pixel ratio. Wait for the page's load event before
    you run the `expression`. Its value is a promise of a string, so wait
    for that too and return the string."))
