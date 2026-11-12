# Page states and user preferences: capturing more than the default rendering

Checked on 2026-11-12. Stable browsers on that day: Chrome 157 ([Chrome for Testing versions](https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions.json)) and Firefox 160.0, released 2026-11-10 ([Mozilla product details](https://product-details.mozilla.org/1.0/firefox_versions.json)).

Source labels used below: **[PR]** peer-reviewed paper, **[Preprint]**, **[Spec]** specification, **[Source]** browser or tool source code, **[Docs]** tool or vendor documentation, **[Registry]** package registry or repository record, **[Grey]** survey or blog, **[Test]** a local test run for these notes.

**About the [Test] findings.** They are my own measurements, not published results, and the report should present them that way. I drove headless Chrome 157.0.8103.51 and headless Firefox 160.0 on macOS through onionskin's own drivers (`impl/chrome.clj`, `impl/firefox.clj`) against a small local page. The page had a `.card` container holding a button, a link, an input and three paragraphs, with rules for `:hover`, `:focus`, `:focus-visible`, `:active`, `:link`, `:visited`, `:focus-within`, a `.card:hover .title` descendant rule, `color-scheme: light dark`, and one `prefers-color-scheme: dark` media rule. After each step a page script read `matches()`, `getComputedStyle`, `matchMedia`, `document.hasFocus()` and box sizes. Each [Test] finding links to the reference for the command it used. The scripts were temporary and are not part of the repository.

The earlier report (`doc/prior-art.md`) already covers StyleProof and sosie in general, VizAssert's font-size range, CILLA's selector coverage and the limits of `getComputedStyle`. These notes add protocol detail, tool practice and research for states and preferences only. Viewport widths are left to another researcher.

## 1. Chrome DevTools Protocol: what each command does, its limits and its status

### Takeaway
Chrome can do everything onionskin needs per page and without a restart: `CSS.forcePseudoState` forces 28 pseudo-classes and page script sees the result, `Emulation.setEmulatedMedia` overrides the media type and seven media features, and `Page.setFontSizes` changes the default font size. The main traps are that forced `:hover` does not reach ancestors, forced `:focus` does not imply `:focus-visible`, `:visited` colours are hidden from page script, and the whole CSS domain is marked experimental.

### Cited Findings

**Status in the protocol definition (tip of tree, commit d961d96, 2026-11-12)**

| Command | Marked as | What the definition says it does |
|---|---|---|
| `CSS.forcePseudoState` | experimental (the whole CSS domain is) | Makes a node have the given pseudo-classes whenever the browser computes its style. Takes `nodeId` and `forcedPseudoClasses` |
| `CSS.forceStartingStyle` | experimental (domain) | Puts a node in its `@starting-style` state |
| `CSS.startRuleUsageTracking`, `takeCoverageDelta`, `stopRuleUsageTracking` | experimental (domain) | Record which rules were used; each `RuleUsage` has a style sheet id, start and end offsets and a `used` flag |
| `CSS.getComputedStyleForNode` | experimental (domain) | Computed style of a node by `nodeId` |
| `Emulation.setEmulatedMedia` | stable | Emulates a media type (empty string turns it off) and a list of `{name, value}` media features |
| `Emulation.setAutoDarkModeOverride` | experimental | Renders all content with an automatic dark theme; omit `enabled` to clear |
| `Emulation.setDefaultBackgroundColorOverride` | stable | Sets the frame's default background colour, used only when the content sets none |
| `Emulation.setEmulatedVisionDeficiency` | stable | One of none, blurredVision, reducedContrast, achromatopsia, deuteranopia, protanopia, tritanopia |
| `Emulation.setFocusEmulationEnabled` | experimental | Simulates a focused and active page |
| `Emulation.setEmulatedOSTextScale` | stable | Emulates an operating-system text scale |
| `Emulation.setDeviceMetricsOverride` | stable; `scale`, `scrollbarType`, `textLayoutMode` parameters experimental | Width, height, device scale factor, mobile flag |
| `Emulation.setPageScaleFactor` | experimental | Sets a page scale factor (pinch zoom) |
| `Page.setFontSizes`, `Page.setFontFamilies` | experimental | Default standard and fixed font sizes; generic font families |
| `DOM.focus` | stable | Focuses an element given by node id, backend node id or object id |
| `Input.dispatchMouseEvent`, `Input.dispatchKeyEvent` | stable | Mouse events of type mousePressed, mouseReleased, mouseMoved, mouseWheel at viewport coordinates in CSS pixels; key events |

Sources for the table: [Spec] [CSS.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/CSS.pdl); [Emulation.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/Emulation.pdl); [Page.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/Page.pdl); [DOM.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/DOM.pdl); [Input.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/Input.pdl). Rendered reference: [CDP CSS domain](https://chromedevtools.github.io/devtools-protocol/tot/CSS/), [CDP Emulation domain](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/).

**`CSS.forcePseudoState`**
- [Source] The names Chromium accepts are: active, hover, focus, focus-visible, focus-within, target, target-current, enabled, disabled, valid, invalid, user-valid, user-invalid, required, optional, read-only, read-write, in-range, out-of-range, visited, checked, indeterminate, placeholder-shown, autofill, link, open, buffering, stalled. This closes the gap noted in `doc/research-notes/css_refactoring_and_equivalence.md` — [Chromium inspector_css_agent.cc, ComputePseudoClassMask](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_css_agent.cc) (file last changed 2026-11-09)
- [Source] The command needs the CSS agent enabled and an element node id from the DOM agent. Each call replaces the node's forced set, and an empty list clears it. Any change marks every element in the document for style recalculation — [Chromium inspector_css_agent.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_css_agent.cc)
- [Source] Forcing `focus` or `focus-within` on a node also makes its ancestors match `:focus-within`; the code comment says this is to mimic real focus. There is no such step for `hover` — [Chromium inspector_css_agent.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_css_agent.cc)
- [Test] Chrome 157: after forcing `hover` on the button, in-page `button.matches(':hover')` was true and in-page `getComputedStyle` returned the hover colour. The parent `.card` did not match `:hover`. Forcing `hover` on `.card` did apply the `.card:hover .title` rule to the descendant — command reference: [CDP CSS.forcePseudoState](https://chromedevtools.github.io/devtools-protocol/tot/CSS/#method-forcePseudoState)
- [Test] Chrome 157: forcing `focus` alone made `:focus` match and gave the ancestor `:focus-within`, but `:focus-visible` did not match and `document.activeElement` did not change. Forcing `focus-visible` alone made `:focus-visible` match without `:focus`. Forcing both gave the keyboard-focus look. `active` worked. A made-up name was accepted without error and did nothing — [CDP CSS.forcePseudoState](https://chromedevtools.github.io/devtools-protocol/tot/CSS/#method-forcePseudoState)
- [Test] Chrome 157: with `visited` forced on a link, in-page `getComputedStyle` still returned the unvisited colour, while `CSS.getComputedStyleForNode` returned the visited colour — [CDP CSS.getComputedStyleForNode](https://chromedevtools.github.io/devtools-protocol/tot/CSS/#method-getComputedStyleForNode)
- [Source] The reason: the inspector builds its computed style with the `allow_visited_style` flag set, and the flag defaults to false for everyone else — [Chromium css_computed_style_declaration.h](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/css/css_computed_style_declaration.h); [inspector_css_agent.cc, getComputedStyleForNode](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_css_agent.cc)
- [Spec] Selectors 4 requires browsers to protect privacy for `:visited` and lists, as one accepted method, restricting the styles it may apply and what style-reading APIs report — [CSS Selectors Level 4, link history pseudo-classes](https://drafts.csswg.org/selectors-4/#link)
- [Docs] MDN: `getComputedStyle` always reports links as unvisited; `:visited` may only change colour properties (color, background-color, border colours, column-rule-color, outline-color, text-decoration-color, text-emphasis-color, and the colour parts of fill and stroke), and differences in transparency are not applied — [MDN, Privacy and the :visited selector](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Selectors/Privacy_and_:visited)

**`Emulation.setEmulatedMedia`**
- [Source] Blink applies overrides for exactly seven features: color-gamut, prefers-color-scheme, prefers-contrast, prefers-reduced-motion, prefers-reduced-data, prefers-reduced-transparency and forced-colors — [Chromium media_feature_overrides.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/css/media_feature_overrides.cc)
- [Source] Each call clears the earlier feature list before it applies the new one, so a call is a full replacement and not a merge. With `forced-colors: active` Blink also switches on its forced-colours emulation, dark or light according to the `prefers-color-scheme` value in the same call, or the page's setting if none is given — [Chromium inspector_emulation_agent.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_emulation_agent.cc)
- [Test] Chrome 157: prefers-color-scheme dark, prefers-reduced-motion reduce, prefers-contrast more and prefers-reduced-transparency reduce all took effect in `matchMedia` and in computed style without a reload. `forced-colors: active` matched and changed computed colours to system colours. Media type `print` matched. `{name: "hover", value: "none"}` returned no error and changed nothing — [CDP Emulation.setEmulatedMedia](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setEmulatedMedia)
- [Docs] DevTools exposes the same set in its Rendering tab: prefers-color-scheme, print media type, forced-colors, prefers-contrast (more, less, custom), prefers-reduced-motion, prefers-reduced-transparency (from Chrome 118) and color-gamut — [Chrome DevTools, Emulate CSS media features](https://developer.chrome.com/docs/devtools/rendering/emulate-css)

**The other Emulation commands**
- [Test] `setAutoDarkModeOverride {enabled: true}`: on a page that declares `color-scheme: light dark`, the computed text colour turned white, but `matchMedia('(prefers-color-scheme: dark)')` stayed false and the page's dark media rule did not apply — [CDP Emulation.setAutoDarkModeOverride](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setAutoDarkModeOverride)
- [Source] `setEmulatedVisionDeficiency` sets a vision-deficiency mode on the page object, and `setDefaultBackgroundColorOverride` sets the web view's base background colour. Neither goes through style resolution — [Chromium inspector_emulation_agent.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_emulation_agent.cc)
- [Test] `setFocusEmulationEnabled`: in headless Chrome 157 with one page, `document.hasFocus()` was already true, and switching the emulation off and on changed nothing — [CDP Emulation.setFocusEmulationEnabled](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setFocusEmulationEnabled)
- [Source] Playwright sends `Emulation.setFocusEmulationEnabled {enabled: true}` to the main frame of each Chromium page by default — [Playwright crPage.ts](https://github.com/microsoft/playwright/blob/main/packages/playwright-core/src/server/chromium/crPage.ts)
- [Test] `setEmulatedOSTextScale {scale: 2}` was accepted by Chrome 157 and had no visible effect on font sizes or boxes of a desktop page — [CDP Emulation.setEmulatedOSTextScale](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setEmulatedOSTextScale)
- [Source] Blink's settings file describes the accessibility font scale factor as applied as part of text autosizing, which is the mobile text layout mode — [Chromium settings.json5](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/frame/settings.json5)

**Real input and focus**
- [Test] `Input.dispatchMouseEvent` with `mouseMoved` over the button made the button and its ancestor `.card` match `:hover`. `mousePressed`, held, made the button match `:active` and `:focus` but not `:focus-visible`. After `mouseReleased` the focus stayed. Hover lasted until the pointer was moved away — [CDP Input.dispatchMouseEvent](https://chromedevtools.github.io/devtools-protocol/tot/Input/#method-dispatchMouseEvent)
- [Test] A Tab key sent with `Input.dispatchKeyEvent` moved focus to the next element and it matched `:focus-visible`. `DOM.focus` after that also gave `:focus-visible` — [CDP Input.dispatchKeyEvent](https://chromedevtools.github.io/devtools-protocol/tot/Input/#method-dispatchKeyEvent); [CDP DOM.focus](https://chromedevtools.github.io/devtools-protocol/tot/DOM/#method-focus)
- [Spec] Selectors 4: an element matches `:hover` when a descendant in the flat tree is hovered, so real hover always covers the ancestor chain — [CSS Selectors Level 4, :hover](https://drafts.csswg.org/selectors-4/#the-hover-pseudo)

### Inferences
- onionskin's capture script can stay in the page. Forced states and emulated media are visible to in-page `getComputedStyle` and `getBoundingClientRect`, so the Chrome driver only needs protocol calls before and after the existing script. The `-visit` protocol method must grow from "load, evaluate once" to a sequence of driver steps and evaluations on one loaded page.
- Forced `hover` is narrower than real hover. A rule like `.card:hover .title` is exercised only when the state is forced on `.card`. To mimic a real pointer, force `hover` on the element and on each ancestor, or use `Input.dispatchMouseEvent`.
- Decide what "focus" means in a capture. `["focus"]` alone is mouse focus (no ring from `:focus-visible` rules). `["focus", "focus-visible"]` is keyboard focus. Both are worth naming as separate states.
- `:visited` cannot be captured by the in-page script in any engine. In Chrome the only route is `CSS.getComputedStyleForNode` on links with `visited` forced. Since `:visited` can only change a short list of colour properties, it can never change layout.
- Avoid `setAutoDarkModeOverride`, `setEmulatedVisionDeficiency` and `setDefaultBackgroundColorOverride` for box and style capture: they act at paint time or on the canvas default and leave author styles and layout alone. They would only matter for a pixel check.
- Sending `setFocusEmulationEnabled {enabled: true}` is cheap insurance, since onionskin lets threads share one browser with several pages open.
- Because every `CSS.*` command is formally experimental, the Chrome driver should detect a failed `forcePseudoState` and report the state as not captured, not silently capture the resting state.

### Gaps
- Only hover, focus, focus-visible, active and visited were tested on Chrome 157. The other 23 names come from tip-of-tree source and may be newer than the stable release.
- Whether forced states survive a navigation or `CSS.disable` was not tested.
- Whether `document.hasFocus()` stays true for several pages open at once in one headless Chrome was not tested.
- I did not test `Emulation.setEmulatedOSTextScale` with `mobile: true` or `textLayoutMode: mobile`, where it may have an effect.
- I found no official statement on how long the CSS domain will stay experimental.

## 2. WebDriver BiDi and Firefox: what the specification offers, what Firefox implements, and which preferences fill the holes

### Takeaway
BiDi now specifies a media-feature override, but Firefox 160 implements it for `prefers-color-scheme` only (since Firefox 159) and has no command to force a pseudo-class, set a default font size or zoom. Real hover, active and focus work through `input.performActions`. Every other preference needs a Firefox preference written into the profile before launch, so each combination costs a browser start.

### Cited Findings

**The specification (Editor's Draft, commit 24ff1a2, 2026-11-11)**
- [Spec] The emulation module defines 14 commands: setForcedColorsModeThemeOverride, setGeolocationOverride, setLocaleOverride, setMediaFeaturesOverride, setNetworkConditions, setScreenOrientationOverride, setScreenSettingsOverride, setScriptingEnabled, setScrollbarTypeOverride, setTextLayoutModeOverride, setTimezoneOverride, setTouchOverride, setUserAgentOverride and setViewportMetaOverride — [WebDriver BiDi, emulation module](https://w3c.github.io/webdriver-bidi/#module-emulation)
- [Spec] `emulation.setMediaFeaturesOverride` takes a `features` map (or null to reset) and optional `contexts` or `userContexts`. The map may hold 29 features, among them prefers-color-scheme (light, dark), prefers-contrast (no-preference, more, less, custom), prefers-reduced-motion, prefers-reduced-transparency, prefers-reduced-data, forced-colors (none, active), inverted-colors, hover, any-hover, pointer, any-pointer, color-gamut, display-mode and scripting. A browser that cannot override one of the given features must answer with the unsupported operation error — [WebDriver BiDi, emulation.setMediaFeaturesOverride](https://w3c.github.io/webdriver-bidi/#command-emulation-setMediaFeaturesOverride)
- [Spec] `emulation.setForcedColorsModeThemeOverride` sets the theme of forced-colours mode to light or dark. Whether forced colours are active is the `forced-colors` entry of the media-feature map — [WebDriver BiDi, emulation.setForcedColorsModeThemeOverride](https://w3c.github.io/webdriver-bidi/#command-emulation-setForcedColorsModeThemeOverride)
- [Spec] Settings of this kind are stored per top-level page, per user context or globally, and looked up in that order — [WebDriver BiDi, WebDriver configuration](https://w3c.github.io/webdriver-bidi/)
- [Spec] `emulation.setTextLayoutModeOverride` has one value, `mobile`, which turns on text autosizing. `emulation.setScrollbarTypeOverride` takes classic or overlay — [WebDriver BiDi, emulation module](https://w3c.github.io/webdriver-bidi/#module-emulation)
- [Spec] `input.performActions` takes a context and a list of none, key, pointer and wheel action sources and defers to the WebDriver actions model — [WebDriver BiDi, input.performActions](https://w3c.github.io/webdriver-bidi/#command-input-performActions)
- [Spec] A text search of the specification source finds no command for pseudo-class state, page zoom, text zoom or default font size. Zoom appears only in the print scale and in the wording of `browsingContext.setViewport` — [WebDriver BiDi source, index.bs](https://github.com/w3c/webdriver-bidi/blob/main/index.bs)

**What Firefox implements**
- [Source] Firefox's emulation module has eight commands: setGeolocationOverride, setLocaleOverride, setMediaFeaturesOverride, setNetworkConditions, setScreenOrientationOverride, setScreenSettingsOverride, setTimezoneOverride and setUserAgentOverride. Its list of supported media features has one entry, `prefers-color-scheme`; any other feature raises the unsupported operation error — [Firefox emulation.sys.mjs](https://github.com/mozilla-firefox/firefox/blob/main/remote/webdriver-bidi/modules/root/emulation.sys.mjs) (last changed 2026-09-28)
- [Registry] The `prefers-color-scheme` support landed on 2026-09-28 for Firefox 159 (released 2026-10-27). The umbrella bug for the full command is still open — [Bugzilla 2064354](https://bugzilla.mozilla.org/show_bug.cgi?id=2064354); [Bugzilla 2064352](https://bugzilla.mozilla.org/show_bug.cgi?id=2064352); [Firefox release dates](https://product-details.mozilla.org/1.0/firefox_history_major_releases.json)
- [Source] The override is applied by setting the browsing context's `prefersColorSchemeOverride`, the same field Firefox DevTools uses — [Firefox emulation.sys.mjs](https://github.com/mozilla-firefox/firefox/blob/main/remote/webdriver-bidi/modules/root/emulation.sys.mjs)
- [Test] Firefox 160: `emulation.setMediaFeaturesOverride` with `prefers-color-scheme: dark` for one context took effect at once, and `features: null` reset it. `prefers-reduced-motion` and `forced-colors` were refused as unsupported. `setForcedColorsModeThemeOverride`, `setScrollbarTypeOverride` and `setTextLayoutModeOverride` were unknown commands — [WebDriver BiDi, emulation module](https://w3c.github.io/webdriver-bidi/#module-emulation)
- [Test] Firefox 160: `input.performActions` with a pointer move made the button and its ancestor match `:hover`. Pointer down, held, gave `:active` and `:focus` without `:focus-visible`. A Tab key action gave `:focus-visible` on the next element. The results matched Chrome's real-input results step for step — [WebDriver BiDi, input.performActions](https://w3c.github.io/webdriver-bidi/#command-input-performActions)
- [Source] The Remote Agent sets `focusmanager.testmode` to true by default so that the application can have focus in the background — [Firefox RecommendedPreferences.sys.mjs](https://github.com/mozilla-firefox/firefox/blob/main/remote/shared/RecommendedPreferences.sys.mjs)
- [Test] Firefox 160 headless: `document.hasFocus()` was true and script `focus()` worked without any extra preference in onionskin's profile — [Firefox RecommendedPreferences.sys.mjs](https://github.com/mozilla-firefox/firefox/blob/main/remote/shared/RecommendedPreferences.sys.mjs)
- [Source] BiDi's `browsingContext.setViewport` device pixel ratio is stored in `overrideDPPX`. Firefox's own interface comment says that field changes only the reported `devicePixelRatio`, some media queries and image selection, not rendering, and that `fullZoom` is the way to change the real ratio — [Firefox browsingContext.sys.mjs](https://github.com/mozilla-firefox/firefox/blob/main/remote/webdriver-bidi/modules/root/browsingContext.sys.mjs); [Firefox BrowsingContext.webidl](https://github.com/mozilla-firefox/firefox/blob/main/dom/chrome-webidl/BrowsingContext.webidl)
- [Test] Firefox 160: with `devicePixelRatio: 2` through BiDi, `window.devicePixelRatio` was 2 but box heights were identical to the 1x run (17 and 7 px). With the preference `layout.css.devPixelsPerPx` at 2.0, heights snapped to half pixels (16.5 and 6.5 px) — [Firefox BrowsingContext.webidl](https://github.com/mozilla-firefox/firefox/blob/main/dom/chrome-webidl/BrowsingContext.webidl)

**Firefox preferences that do the job**

| Preference | Values (default) | Tested effect on Firefox 160 |
|---|---|---|
| `layout.css.prefers-color-scheme.content-override` | 0 dark, 1 light, 2 auto (2) | 0 made `prefers-color-scheme: dark` match |
| `ui.systemUsesDarkTheme` | 1 for dark | 1 made dark match |
| `ui.prefersReducedMotion` | 1 for reduce | 1 made `prefers-reduced-motion: reduce` match |
| `browser.display.document_color_use` | 0 default behaviour, 1 always use page colours, 2 never (0 on Windows, 1 elsewhere) | 2 made `forced-colors: active` and `prefers-contrast: more` match and replaced link colours with system colours |
| `font.size.variable.x-western` | pixels (16) | 20 made the root and `1rem` text 20px; `16px` text unchanged |
| `font.minimum-size.x-western` | pixels (0) | 12 doubled the height of a 6px paragraph, but `getComputedStyle` still reported 6px |
| `layout.css.devPixelsPerPx` | float (-1.0, automatic) | 2.0 gave `devicePixelRatio` 2 and half-pixel layout |
| `ui.textScaleFactor` | percent | 200 gave half-pixel layout, as a doubled device pixel ratio does; font sizes were unchanged, so it is not text-only zoom |
| `ui.prefersReducedTransparency` | 1 for reduce | no effect; the media feature is behind `layout.css.prefers-reduced-transparency.enabled`, default false |

Sources for the table: [Source] [Firefox StaticPrefList.yaml](https://github.com/mozilla-firefox/firefox/blob/main/modules/libpref/init/StaticPrefList.yaml) for the `layout.*`, `browser.display.*` and `zoom.*` entries and their comments; [Firefox all.js](https://github.com/mozilla-firefox/firefox/blob/main/modules/libpref/init/all.js) for the `font.*` defaults; [Firefox nsXPLookAndFeel.cpp](https://github.com/mozilla-firefox/firefox/blob/main/widget/nsXPLookAndFeel.cpp) for the `ui.*` look-and-feel integers. The effects are [Test] results.

- [Source] `browser.display.os-zoom-behavior` decides what the text scale factor does: 0 nothing, 1 full zoom (default), 2 text zoom — [Firefox StaticPrefList.yaml](https://github.com/mozilla-firefox/firefox/blob/main/modules/libpref/init/StaticPrefList.yaml)
- [Source] Other relevant defaults: `layout.css.visited_links_enabled` true (if false, `:visited` never matches), `browser.display.show_focus_rings` false, `browser.display.always_show_rings_after_key_focus` false, `zoom.minPercent` 20 and `zoom.maxPercent` 500 — [Firefox StaticPrefList.yaml](https://github.com/mozilla-firefox/firefox/blob/main/modules/libpref/init/StaticPrefList.yaml)
- [Source] Firefox has per-page switches for most of this, but only for privileged code: the browsing context attributes `prefersColorSchemeOverride`, `prefersReducedMotionOverride`, `forcedColorsOverride`, `prefersContrastOverride`, `mediumOverride`, `fullZoom` and `textZoom`, and `InspectorUtils.addPseudoClassLock(element, pseudoClass)` with its remove, has and clear companions — [Firefox BrowsingContext.webidl](https://github.com/mozilla-firefox/firefox/blob/main/dom/chrome-webidl/BrowsingContext.webidl); [Firefox InspectorUtils.webidl](https://github.com/mozilla-firefox/firefox/blob/main/dom/chrome-webidl/InspectorUtils.webidl)
- [Docs] By default the Remote Agent can only reach web content processes. Starting Firefox with the environment variable `MOZ_REMOTE_ALLOW_SYSTEM_ACCESS=1` gives Marionette and BiDi access to all Gecko APIs, and Mozilla's page says to use it only when really needed — [Firefox Remote Protocols, Security](https://firefox-source-docs.mozilla.org/remote/Security.html)
- [Source] Playwright does not use stock Firefox for this: its Firefox driver sends a `Page.setEmulatedMedia` command with type, colorScheme, reducedMotion, forcedColors and contrast over its own Juggler protocol, which only its patched Firefox build understands — [Playwright ffPage.ts](https://github.com/microsoft/playwright/blob/main/packages/playwright-core/src/server/firefox/ffPage.ts)

### Inferences
- In Firefox the cheap dimensions are colour scheme (per context, BiDi, Firefox 159+) and real-input states (per page). Reduced motion, forced colours, contrast, default font size, minimum font size and real zoom are per profile, so each combination needs its own Firefox process. onionskin already writes `user.js`, so the mechanism exists; the cost is start-up time per combination.
- onionskin should feature-detect: try `emulation.setMediaFeaturesOverride` and fall back to a relaunch with `layout.css.prefers-color-scheme.content-override` on Firefox older than 159 or for features that come back unsupported. More features will probably move to BiDi as the open umbrella bug is worked on.
- onionskin's `:scale` option means different things in the two engines today. In Chrome the device scale factor is real. In Firefox it only changes what the page is told. This matters for any zoom emulation and for tolerance defaults.
- The privileged route (system access plus `addPseudoClassLock`) would give Firefox the same forced states as Chrome, but it widens what the test browser may do and I could not confirm that content elements are reachable that way. Treat it as a later option, not a base design.
- Font preferences are per language group. The `x-western` ones tested here should apply to Latin-script pages only; a page in another script reads a different preference. This is from general knowledge of Firefox and was not checked.

### Gaps
- Unconfirmed: whether a BiDi session with system access can call `InspectorUtils.addPseudoClassLock` or set `fullZoom` and `textZoom` on a content page. Not tested.
- Unconfirmed: whether preferences can be changed in a running Firefox through BiDi without system access. I found no such command in the specification or in Firefox's module list.
- `browser.display.os-zoom-behavior = 2` with `ui.textScaleFactor` should give text-only zoom according to the source comment, but I did not test it.
- I did not check wpt.fyi results; the Firefox findings rest on its source and on the local test.
- `setScriptingEnabled`, `setTouchOverride` and `setViewportMetaOverride` are absent from Firefox's module source but were not tested.

## 3. Forcing a state from page script, without the engine's help

### Takeaway
There is no script API that forces a pseudo-class. The engine-independent choices are rewriting style sheet rules into class selectors, which misses browser default styles and script behaviour, real input through each protocol, which behaved the same in both engines in my test, and `el.focus()`, whose `:focus-visible` result depends on what input came before.

### Cited Findings

**Rewriting style sheet rules**
- [Docs] The Storybook pseudo-states addon rewrites every document style sheet so that rules with a pseudo-class also match a class, then toggles those classes on a root element or on chosen elements. Its README states the limit: it does not render browser default styles, and no JavaScript API can toggle real pseudo states — [Storybook pseudo-states addon README](https://github.com/storybookjs/storybook/tree/next/code/addons/pseudo-states)
- [Source] It handles eight pseudo-classes: hover, active, focus-visible, focus-within, focus, visited, link and target. Each `:hover` in a selector gets two extra forms, `.pseudo-hover` on the same compound and a `.pseudo-hover-all` ancestor form — [Storybook addon constants.ts](https://github.com/storybookjs/storybook/blob/next/code/addons/pseudo-states/src/constants.ts); [rewriteStyleSheet.ts](https://github.com/storybookjs/storybook/blob/next/code/addons/pseudo-states/src/preview/rewriteStyleSheet.ts)
- [Source] Limits visible in the code: it stops after 1,000 rewritten selectors per sheet with a warning, it cannot read sheets blocked by cross-origin rules and skips them with a warning, it leaves vendor pseudo-elements and `::part()` alone, it parses `:not()` to three levels of brackets only, and it splits rules to stay under WebKit's limit of 4,096 selectors per rule — [rewriteStyleSheet.ts](https://github.com/storybookjs/storybook/blob/next/code/addons/pseudo-states/src/preview/rewriteStyleSheet.ts)
- [Registry] The stand-alone addon repository is archived because the addon moved into the Storybook monorepo; npm version 10.6.7 was published 2026-11-10 — [archived repository](https://github.com/chromaui/storybook-addon-pseudo-states); [npm storybook-addon-pseudo-states](https://www.npmjs.com/package/storybook-addon-pseudo-states)
- [Docs] postcss-pseudo-classes does the same at build time: `.a:hover` becomes `.a:hover, .a.\:hover`. It can be limited to chosen pseudo-classes, can emit all combinations, and ignores double-colon pseudo-elements — [postcss-pseudo-classes README](https://github.com/giuseppeg/postcss-pseudo-classes)
- [Registry] postcss-pseudo-classes 0.4.0 dates from 2023-11-27, with the last push in July 2024 and 29 stars: low activity — [npm postcss-pseudo-classes](https://www.npmjs.com/package/postcss-pseudo-classes)
- [Docs] Percy's newer option (CLI 1.31.5 or later) takes a list of selectors, ids, classes or XPaths under `pseudoClassEnabledElements` and stores the computed CSS of those elements with the snapshot. It lists :hover, :focus, :active, :visited, :disabled, :checked and :popover-open. Percy does not trigger the state; the test must put the element in it first — [Percy, configure pseudo-class](https://www.browserstack.com/docs/percy/percy-snapshot-config/configure-pseudo-class)

**Real input events**
- [Docs] Events made by page script do not set CSS states. Cypress has no hover command; its documentation says `.trigger('mouseover')` only affects JavaScript listeners and has no effect in CSS, and points to a Chrome remote debugging recipe and to the cypress-real-events plugin — [Cypress, hover](https://docs.cypress.io/api/commands/hover)
- [Docs] Chromatic gives the same reason: CSS hover needs a trusted event that test code cannot fake — [Chromatic, Hover and focus states](https://www.chromatic.com/docs/hoverfocus/)
- [Docs] cypress-real-events fires real events through the Chrome DevTools Protocol, which gives hover and Tab focus, and works only in Chromium-based browsers — [cypress-real-events README](https://github.com/dmtrKovalenko/cypress-real-events)
- [Test] Protocol-level input gave the same pseudo-class results in Chrome 157 (CDP `Input`) and Firefox 160 (BiDi `input.performActions`): hover on the element and its ancestors, active while the button is held, mouse focus without `:focus-visible`, Tab focus with it — [WebDriver BiDi, input.performActions](https://w3c.github.io/webdriver-bidi/#command-input-performActions); [CDP Input domain](https://chromedevtools.github.io/devtools-protocol/tot/Input/)

**`el.focus()`**
- [Spec] Selectors 4 leaves the `:focus-visible` rule to browsers and suggests heuristics: always indicate focus for elements that take keyboard input; indicate it after keyboard interaction; do not after pointer interaction on other elements; and when script moves focus, carry over whether the previously focused element indicated focus. It also says browsers should use `:focus-visible` for their default focus style — [CSS Selectors Level 4, :focus-visible](https://drafts.csswg.org/selectors-4/#the-focus-visible-pseudo)
- [Docs] MDN describes the same behaviour and lists `:focus-visible` as widely available since March 2022 — [MDN, :focus-visible](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/:focus-visible)
- [Test] On a freshly loaded page, `button.focus()` from script matched both `:focus` and `:focus-visible` in Chrome 157 and in Firefox 160, and the ancestor matched `:focus-within`. After a real mouse click on the button, script `link.focus()` matched `:focus` but not `:focus-visible` in both engines. A text input matched `:focus-visible` on script focus — [CSS Selectors Level 4, :focus-visible](https://drafts.csswg.org/selectors-4/#the-focus-visible-pseudo)

### Inferences
- Costs of rewriting, for a tool that tests the page's CSS: it edits the style sheets under test; it misses browser default styles such as the default focus ring and native control states; it cannot see cross-origin sheets from page script; it does not run hover or focus listeners, so script-built tooltips and menus stay closed; and the ancestor form changes selector meaning. A class has the same specificity as a pseudo-class, so the same-compound form should keep the cascade order (my reasoning; not tested).
- Costs of real input: one hover chain and one focused element at a time, so many captures; the target must be in the viewport and on top at the pointer position; listeners run, which is realistic but can change the DOM; transitions start, so the settle step must come after the input; and state leaks into the next capture unless the pointer is moved away and focus is cleared.
- `:active` cannot be reached from script at all. It needs a held pointer button or a forced state.
- Script focus gives reproducible `:focus-visible` only if the tool controls the input history. A simple rule: for keyboard focus, send a Tab key first or focus on a fresh page; for mouse focus, use a real click.
- A workable split: forced states in Chrome for speed and for many elements at once; real input in both engines as the common base and as a cross-check; rewriting only as a documented fallback for states that Firefox cannot reach (for example `:target`, `:checked`-style form states can instead be set through the DOM).

### Gaps
- I did not measure how often rewriting and real states disagree on real pages.
- Not tested: `focus({preventScroll: true})`, focus on elements without `tabindex`, and focus inside iframes or shadow trees.

## 4. How established tools handle states and preferences, and the pitfalls they document

### Takeaway
Mainstream tools use real input for element states and per-page media emulation for preferences; only StyleProof forces pseudo-classes through CDP, and it marks states as skipped on Firefox and WebKit. The documented pitfalls are untrusted script events, focus rings cut off or missing, transitions, and the growth in capture count.

### Cited Findings

**Playwright** (1.65.0, 2026-11-05, active — [npm playwright](https://www.npmjs.com/package/playwright))
- [Docs] `page.emulateMedia` options: `media` (screen, print), `colorScheme` (light, dark, no-preference), `reducedMotion` (reduce, no-preference, since 1.12), `forcedColors` (active, none, since 1.15) and `contrast` (more, no-preference, since 1.51); null clears each — [Playwright, page.emulateMedia](https://playwright.dev/docs/api/class-page#page-emulate-media)
- [Docs] The same options can be set for a whole context or project through `use`, per test with `test.use()`, or per page — [Playwright, Emulation](https://playwright.dev/docs/emulation)
- [Source] On Chromium all four features go into one `Emulation.setEmulatedMedia` call — [Playwright crPage.ts](https://github.com/microsoft/playwright/blob/main/packages/playwright-core/src/server/chromium/crPage.ts)
- [Docs] States are reached by actions: `locator.hover()` hovers the element and `locator.focus()` calls the DOM `focus()` method. There is no forced pseudo-class API — [Playwright class-locator docs source](https://github.com/microsoft/playwright/blob/main/docs/src/api/class-locator.md)
- [Docs] Screenshot assertions disable animations by default: finite animations and transitions are fast-forwarded to their end, infinite ones are reset to their start. The text caret is hidden by default — [Playwright params docs source](https://github.com/microsoft/playwright/blob/main/docs/src/api/params.md)

**Puppeteer** (25.17.0, 2026-11-10, active — [npm puppeteer](https://www.npmjs.com/package/puppeteer))
- [Docs] `page.emulateMediaFeatures` takes `{name, value}` pairs; the documented examples are prefers-color-scheme, prefers-reduced-motion, color-gamut, prefers-contrast, forced-colors and prefers-reduced-transparency — [Puppeteer, Page.emulateMediaFeatures](https://pptr.dev/api/puppeteer.page.emulatemediafeatures)
- [Source] Its Firefox profile also sets `focusmanager.testmode` to true — [Puppeteer firefox.ts](https://github.com/puppeteer/puppeteer/blob/main/packages/browsers/src/browser-data/firefox.ts)

**Storybook and Chromatic** (chromatic CLI 18.14.3, 2026-11-09, commercial, active — [npm chromatic](https://www.npmjs.com/package/chromatic))
- [Docs] Chromatic lists four ways to capture states: a `play` function or a Playwright or Cypress hover for script-driven hover; the pseudo-states addon for CSS hover; hand-written mirror classes such as `.hover`; and `focus()` in a `play` function. It warns that snapshots are cropped to the story's root, which can cut off outlines and other focus styles, and suggests padding the story — [Chromatic, Hover and focus states](https://www.chromatic.com/docs/hoverfocus/)
- [Docs] The addon's `pseudo` story parameter takes true for all elements, or a selector or list of selectors per state, plus a `rootSelector` for portals, dialogs and tooltips rendered outside the story root — [Storybook pseudo-states addon README](https://github.com/storybookjs/storybook/tree/next/code/addons/pseudo-states)

**BackstopJS** (npm 6.3.25 from 2024-09-07; repository pushed 2026-10-10; 7,202 stars; maintained with slow releases — [npm backstopjs](https://www.npmjs.com/package/backstopjs); [repository](https://github.com/garris/BackstopJS))
- [Docs] Each scenario may set `hoverSelector` or `hoverSelectors` (pointer moved over each in turn), `clickSelector` or `clickSelectors`, `keyPressSelectors`, `scrollToSelector`, and `postInteractionWait`, a selector or a time in milliseconds to wait after the interaction, described as meant for click or hover transitions. `onReadyScript` runs arbitrary engine code before the screenshot — [BackstopJS README](https://github.com/garris/BackstopJS)

**Percy** (@percy/cli 1.32.12, 2026-10-21, commercial, active — [npm @percy/cli](https://www.npmjs.com/package/@percy/cli))
- [Docs] See section 3: Percy stores computed CSS for listed elements but the test must create the state — [Percy, configure pseudo-class](https://www.browserstack.com/docs/percy/percy-snapshot-config/configure-pseudo-class)

**Cypress** (16.3.0, 2026-11-10, active — [npm cypress](https://www.npmjs.com/package/cypress))
- [Docs] No hover command. Workarounds are `.trigger('mouseover')` for listeners, `.invoke('show')`, forced clicks, or the cypress-real-events plugin (1.15.1, 2026-09-12, Chromium only) — [Cypress, hover](https://docs.cypress.io/api/commands/hover); [cypress-real-events README](https://github.com/dmtrKovalenko/cypress-real-events)

**Galen** (inactive since 2019, per `doc/prior-art.md`)
- [Docs] A test line may run page actions before `check`: `inject file.js` runs script in the page, `run file.js '{...}'` runs a WebDriver script, `wait` waits for a time or for an element to exist, be gone, be visible or be hidden, and `cookie` sets a cookie and reloads. Galen has no notion of pseudo-class state or media preference — [Galen test suite syntax](https://galenframework.com/docs/reference-galen-test-suite-syntax/)

**StyleProof** (9.0.0, 2026-10-29, one author, 3 stars, active — [npm styleproof](https://www.npmjs.com/package/styleproof))
- [Docs] Forces :hover, :focus with :focus-visible, :active, :checked, :disabled and :placeholder-shown through Chromium CDP, one interactive control at a time, and records only the difference from the resting capture. The changed style may sit on the control, a descendant, a sibling or an ancestor reached through `:has()`, so it re-reads more than the control — [StyleProof forced-state capture](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/forced-state-capture.md)
- [Docs] Work is bounded: 800 controls by default, a scan scope of 2,000 elements, and 32,000 element snapshots per surface for each sweep. If a bound is hit, a control detaches, or the browser is Firefox or WebKit, the capture is saved with `statesSkipped: true` and the diff treats the state layer as not certified — [StyleProof forced-state capture](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/forced-state-capture.md)
- [Docs] Form states are forced only on controls where they are false at rest, for example `checked` on unchecked boxes — [StyleProof forced-state capture](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/forced-state-capture.md)
- [Docs] `mediaStates` declares named media environments from `colorScheme`, `media` (screen, print) and `forcedColors`. Reduced motion is refused as an option because motion is always frozen at reduce. Each environment adds one capture per surface and per explicit variant, but not per discovered popup or transient state, to limit growth. Nothing expands by default; when a readable style sheet uses prefers-color-scheme, print, forced-colors or prefers-contrast and no media state is declared, it prints one warning — [StyleProof REFERENCE.md, Media environments](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/REFERENCE.md)
- [Docs] Animations, transitions, real hover, real focus and the caret are neutralised before the resting map is read; forced states are captured separately — [StyleProof REFERENCE.md](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/REFERENCE.md)

### Inferences
- Borrow from Playwright: one option map per capture for media type and features, with nil meaning "leave alone", and animations brought to their end state before reading.
- Borrow from BackstopJS: a declared wait condition after an interaction (a selector or a time), since hover and click often start transitions or insert elements.
- Borrow from StyleProof: capture a state as a delta against the resting capture; re-read beyond the forced element because siblings and `:has()` ancestors can change; bound the work and record plainly when a state layer was skipped or cut short; warn when the style sheets use a media feature that no capture exercised.
- Borrow from Chromatic's cropping pitfall: outlines and box shadows are painted outside the border box and do not move other boxes. A focus ring will therefore show in onionskin's style diff (outline and box-shadow properties) and not in its box diff. Checks on focus rings must be style checks.
- Avoid Percy's split, where the tool records state styles but leaves creating the state to the user: it gives no guarantee that the state was actually active.
- Avoid script-dispatched events as a hover substitute, which Cypress and Chromatic both document as not affecting CSS.
- Tooltips and menus that script builds on hover need real input in both engines; a forced `:hover` in Chrome will not open them.

### Gaps
- I did not read Applitools or Percy's rendering internals, so how they keep states when they re-render a DOM snapshot in other browsers is unknown.
- No user reports on StyleProof's forced-state layer were found; its design is documented but not independently evaluated.
- Puppeteer's `page.hover` documentation was seen only in a search summary (scroll into view, move pointer to the element's centre); the page was not fetched.

## 5. Default font size and zoom

### Takeaway
Text-only resizing is testable in both engines but by different means: per page in Chrome with `Page.setFontSizes`, per profile in Firefox with font preferences. Page zoom is, for layout, the same as a narrower viewport at a higher device pixel ratio, so it belongs with the viewport-width work. A minimum font size changes layout in both engines but shows in computed style only in Chrome.

### Cited Findings
- [Spec] WCAG 2.2 success criterion 1.4.4: text, except captions and images of text, can be resized up to 200 percent without assistive technology and without loss of content or function. The understanding document accepts any text-scaling mechanism the browser supports, zoom included, treats scaling as mainly the browser's job, and lists failures F69 (content clipped, truncated or obscured at 200%), F80 (text form controls that do not resize) and F94 (text sized in viewport units) — [W3C, Understanding SC 1.4.4 Resize Text](https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html)
- [Test] Chrome 157: `Page.setFontSizes {fontSizes: {standard: 20}}` changed the root and `1rem` font size to 20px on the loaded page without a reload, widened the button from 54.5 to 66.1 px, and left `16px` text alone — [CDP Page.setFontSizes](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-setFontSizes)
- [Test] Chrome 157 started with `--blink-settings=defaultFontSize=20,minimumFontSize=12` gave a 20px root size, and `getComputedStyle` reported 12px for text authored at 6px — setting names from [Chromium settings.json5](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/frame/settings.json5)
- [Source] Blink's `minimumFontSize` is in CSS pixels and applies before device scale and zoom; `minimumLogicalFontSize` applies only to relative sizes — [Chromium settings.json5](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/frame/settings.json5)
- [Test] Firefox 160: `font.size.variable.x-western = 20` gave a 20px root size. `font.minimum-size.x-western = 12` raised the height of a 6px paragraph from 7 to 13 px while `getComputedStyle` still reported 6px — defaults in [Firefox all.js](https://github.com/mozilla-firefox/firefox/blob/main/modules/libpref/init/all.js)
- [Test] 200% zoom emulated as half the viewport width at twice the device pixel ratio worked in both engines for `innerWidth` and `devicePixelRatio` (640 and 2). In Firefox the BiDi ratio is reported only and not rendered; see section 2 — [CDP Emulation.setDeviceMetricsOverride](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setDeviceMetricsOverride); [WebDriver BiDi, browsingContext.setViewport](https://w3c.github.io/webdriver-bidi/#command-browsingContext-setViewport)
- [Source] Firefox's desktop profile sets `browser.zoom.full` to true, so its zoom command is full-page zoom by default; text-only zoom is the `textZoom` attribute, reachable only by privileged code — [Firefox firefox.js](https://github.com/mozilla-firefox/firefox/blob/main/browser/app/profile/firefox.js); [Firefox BrowsingContext.webidl](https://github.com/mozilla-firefox/firefox/blob/main/dom/chrome-webidl/BrowsingContext.webidl)
- [Grey] HTTP Archive Web Almanac 2025, accessibility chapter (about 17 million sites crawled monthly; published 2026-01-15): px accounts for 67% of font-size units on desktop sites, em for 16% and rem for 9% (the chapter's figure 6.5, as summarised by the fetch tool; the exact basis was not checked); 15% of desktop and 13% of mobile sites disable zoom with `user-scalable=no`, and 19% and 17% set a maximum scale of 1 — [Web Almanac 2025, Accessibility](https://almanac.httparchive.org/en/2025/accessibility)
- [PR] Chiou, Winn, Alotaibi and Halfond (ICSE 2024) detect reflow accessibility issues, where content or function is lost when a page is viewed zoomed in or in a small viewport, and report better results than earlier techniques on real pages. The abstract calls reflow issues among the most common modern web accessibility issues — [Chiou et al. 2024](https://doi.org/10.1145/3597503.3639229)
- [PR] Su and colleagues (ASE 2022), dVermin: finds scaling issues such as truncated text and overlapping components in Android apps by comparing a view at the default and at a larger display scale. 97% precision and 97% recall for pages with issues, 84% and 91% for individual views, and 21 new issues found in F-Droid apps, 20 of them confirmed or fixed — [Su et al. 2022](https://doi.org/10.1145/3551349.3556935)
- [PR] Alshayban and Malek (ESEC/FSE 2022), AccessiText: automated detection of text accessibility issues in Android apps under text scaling; only the title and a search summary were read — [Alshayban and Malek 2022](https://doi.org/10.1145/3540250.3549118)
- [PR] Already in `doc/prior-art.md`: VizAssert (PLDI 2018) verifies layout assertions for default font sizes from 16 to 32 px together with viewport ranges — [Panchekha et al. 2018](https://doi.org/10.1145/3192366.3192407)

### Inferences
- Treat "default font size" (16, 20, 24, 32 px) as its own capture parameter, separate from width. It is the parameter that exposes px-based heights and containers, which the Almanac figures suggest are common. Treat "zoom" as width divided by the factor and hand it to the width dimension.
- The dVermin oracle fits onionskin as it stands: capture at the default and at a larger font size, then require that chosen relations still hold (no new overlaps, nothing protruding from its parent, order kept). That is a diff between two variants of one version, a use the current diff already supports mechanically.
- Read font size effects from boxes, not from computed `font-size`. With a minimum font size the two engines disagree on what `getComputedStyle` reports while both change the layout.
- In Chrome a font-size sweep is cheap (one page, several `Page.setFontSizes` calls). In Firefox it costs one launch per size, so offer it there as opt-in.
- GWALI and IFix in the earlier report deal with text that grows through translation. Their relation checks (direction, alignment, intersection) are the same checks that text growth through resizing needs.

### Gaps
- I found no peer-reviewed study of text-resize failures on web pages specifically; the direct evidence is from Android (dVermin, AccessiText) and from the reflow work.
- WCAG 1.4.10 (Reflow) and 1.4.12 (Text Spacing) were not read for these notes.
- `Page.setFontSizes` is experimental. I did not check whether it applies to pages opened later in the same browser or only to the target that received it.

## 6. Research on state coverage, CSS coverage and dark-mode defects

### Takeaway
I found no peer-reviewed study that measures which interaction states web visual-regression tools miss, and none on dark-mode defects in web pages; the solid work is on Android settings (dark mode, font and display scale) and on web keyboard accessibility. Usage surveys show that state rules are near-universal, so a resting, light-mode capture leaves much of a style sheet unexercised, and Chrome's rule-usage tracking can report exactly which rules.

### Cited Findings

**How common state-only and preference-only rules are**
- [Grey] Project Wallace, The CSS Selection 2026 (home pages of over 100,000 sites from the Majestic Million; static HTML only, so script-loaded sheets are missed): adoption of each pseudo-class, which appears to mean the share of sites that use it at least once: :hover 95.2%, :focus 88.6%, :active 83.9%, :disabled 58.8%, :checked 58.5%, :visited 54.7%, :focus-visible 48.2%, :has 41.3%, :focus-within 41.0%. Media features: prefers-reduced-motion 44.2%, hover 23.8%, forced-colors 8.6%, pointer 7.5%, prefers-color-scheme 5.1% — [Project Wallace, The CSS Selection 2026](https://www.projectwallace.com/the-css-selection/2026)
- [Grey] Web Almanac 2025, accessibility chapter: about 90% of pages have a `:focus` selector, 67% have a `:focus` rule that removes the outline, about 25% use `:focus-visible`, about 50% use prefers-reduced-motion, about 13% prefers-color-scheme, about 1% prefers-contrast, and 16% (desktop) to 19% (mobile) forced-colors — [Web Almanac 2025, Accessibility](https://almanac.httparchive.org/en/2025/accessibility)
- [Grey] The two surveys disagree on prefers-color-scheme (5% against 13%) and on `:focus-visible` (48% against 25%). They differ in sample (home pages of a top list against a 17-million-site crawl with secondary pages) and in analyser — [Project Wallace 2026](https://www.projectwallace.com/the-css-selection/2026); [Web Almanac 2025](https://almanac.httparchive.org/en/2025/accessibility)

**CSS coverage as a report of what a capture never exercised**
- [Source] Chromium records a rule as used when it matched an element during style resolution; the tracker is fed from the matched rules of the element rule collector — [Chromium element_rule_collector.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/css/element_rule_collector.cc)
- [Source] `startRuleUsageTracking` forces a full style recalculation, `takeCoverageDelta` returns the rules that became used since the last call, and `stopRuleUsageTracking` returns the last delta and ends tracking — [Chromium inspector_css_agent.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/inspector/inspector_css_agent.cc); [CSS.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/CSS.pdl)
- [Test] Chrome 157: at rest, the delta listed the base rules and not the `:hover`, `:focus`, `:active` or dark-mode rules. After `button:hover` was forced and style was read, the next delta held exactly the `button:hover` rule. The `a:visited` rule was reported as used at rest although the link was unvisited — [CDP CSS.takeCoverageDelta](https://chromedevtools.github.io/devtools-protocol/tot/CSS/#method-takeCoverageDelta)
- [Docs] The DevTools Coverage panel reports used and unused bytes of CSS and records while the user interacts, so its result depends on what was done during the recording — [Chrome DevTools, Coverage](https://developer.chrome.com/docs/devtools/coverage)
- [Docs] StyleProof records, for every leaf rule of every sheet, whether it matched a compared element, counts forced-state reads, marks matches it cannot attribute (shadow roots, iframes, `@scope`, `@container`) as outside, and can fail a run when classes defined in the CSS were never rendered — [StyleProof REFERENCE.md](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/REFERENCE.md)
- [Registry] Mozilla's tracking bug for CSS coverage (status not checked) separates two levels: selectors matching, which is easier, and declarations applying, which is harder — [Bugzilla 1561004](https://bugzilla.mozilla.org/show_bug.cgi?id=1561004)
- [PR] Already in `doc/prior-art.md`: CILLA found on average 60% of selectors unused across crawled DOM states, and Mazinanian and colleagues name unvisited states as the main threat to their refactoring preconditions — [Mesbah and Mirshokraie 2012](https://doi.org/10.1109/ICSE.2012.6227174); [Mazinanian et al. 2014](https://doi.org/10.1145/2635868.2635879)
- [PR] Behfarshad and Mesbah (2013): in 500 websites, 95% had content reachable only through client-side script, and on average 62% of 50 crawled states per site were of that kind. Figures are from a search summary of the paper, not from the full text — [Behfarshad and Mesbah 2013](https://doi.org/10.1007/978-3-642-39200-9_7)

**Defects triggered by settings and preferences**
- [PR] Sun and colleagues (ISSTA 2021; extended in TSE 2023), SetDroid: studied 1,074 setting-related defects from 180 Android apps; about 70.7% cause non-crash failures that ordinary testing tools cannot detect for lack of an oracle. Their oracle is metamorphic: behaviour should stay consistent when a setting is changed and then restored — [Sun et al. 2021](https://doi.org/10.1145/3460319.3464806); [Sun et al. 2023](https://doi.org/10.1109/TSE.2023.3236449)
- [PR] Ma, Chen, Khalajzadeh and Grundy (TOSEM 33(1), 2023): the first study of dark mode in Android apps, from more than 300 Stack Overflow threads and over 6,000 apps, with a review of four support tools. It warns of the risks of unsuitable dark-mode design choices — [Ma et al. 2023](https://doi.org/10.1145/3604607)
- [PR] Gu and Huang (ASE 2025), DroidPalette: repairs low-contrast colour issues in Android apps; 497 issues in 105 apps, 66.6% repaired, and 8 of 13 submitted patches welcomed by developers — [Gu and Huang 2025](https://doi.org/10.1109/ASE63991.2025.00169)
- [Preprint] Huang, Liu, Gu and Wu (arXiv, July 2026), SUDFinder: derives oracles from patterns of wrong UI adaptation to settings; found 98 unknown display bugs in 29 F-Droid apps at 0.76 precision, 67 confirmed and 37 fixed — [Huang et al. 2026](https://arxiv.org/abs/2607.04120)

**Focus and keyboard states on the web**
- [PR] Chiou, Alotaibi and Halfond (ESEC/FSE 2021): detect and localise keyboard accessibility failures in web applications with high precision and recall, by driving the keyboard — [Chiou et al. 2021](https://doi.org/10.1145/3468264.3468581)
- [PR] Chiou, Alotaibi and Halfond (ICSE 2023): dialog-related keyboard navigation failures — [Chiou et al. 2023](https://doi.org/10.1109/ICSE48619.2023.00120)
- [PR] Winn, Chiou and Halfond (ICST 2026), LYNX: linear keyboard navigation failures, from an empirical study of how they arise — [Winn et al. 2026](https://doi.org/10.1109/ICST69053.2026.00089)

### Inferences
- What a diff covers can be made measurable. In Chrome, take a coverage delta per captured variant and state, union them, and list the rules of the page's sheets that never appear. Those are the rules the capture matrix cannot vouch for. This extends design choice 17 of the earlier report from never-matched selectors to never-exercised states and media conditions.
- Read `:visited` coverage with care: Chrome reports the rule as used even when no link is visited, so coverage overstates what was verified for that pseudo-class.
- Firefox has no coverage command in BiDi. A page-script fallback in the CILLA style (walk the CSSOM, strip state pseudo-classes from each selector, test with `querySelector`, evaluate `@media` conditions with `matchMedia`) would give selector-level coverage in both engines, at the weaker "could match" level.
- The Android work suggests oracles that need no written spec and suit a diff tool. Light against dark: boxes should be equal and only colour-typed properties should differ. Default against larger font size: relations should be preserved. Setting changed and restored: the capture should equal the first one.
- The usage figures give a simple argument for the feature: `:hover` rules exist on about 95% of sites and focus rules on about 90%, so a resting capture is blind to rules almost every site has.

### Gaps
- No peer-reviewed study was found on which interaction states visual-regression tools miss on the web, on dark-mode defects in web pages, or on CSS coverage as a test adequacy measure. My searches covered ICSE, FSE, ASE, ISSTA and ICST through general web search and Crossref, not venue by venue.
- The Behfarshad and Mesbah figures and the AccessiText details come from summaries, not full texts.
- I did not check how Crawljax and similar crawlers treat hover events, or whether CILLA strips state pseudo-classes when it matches selectors (the earlier notes list the latter as open).
- Neither usage survey reports what share of rules, as opposed to sites, depend on a state.

## 7. Vocabulary and data model for naming a state or variant

### Takeaway
Tools separate at least two axes and name them differently: the UI situation (scenario, story, surface, variant, state) and the rendering environment (mode, viewport, project, media state, configuration). They differ on whether axes multiply automatically, and Chromatic's choice to key baselines by mode name alone is a documented trap.

### Cited Findings
- [Docs] Chromatic: a mode is a named combination of settings such as viewport, theme, locale or background, declared as a map from name to values. Modes set at project, component and story level stack, and each story is captured once per mode. Baselines are tied to the mode's name, not its values: changing a mode's viewport keeps comparing against the old baseline, and renaming creates a new one — [Chromatic, Modes](https://www.chromatic.com/docs/modes/)
- [Docs] BackstopJS: a list of scenarios (label, URL, interactions, waits) crossed with a list of viewports (label, width, height); a scenario may override the viewport list — [BackstopJS README](https://github.com/garris/BackstopJS)
- [Docs] Playwright: projects, each with a name and a `use` block of options such as device, viewport, colorScheme and locale; tests run once per project — [Playwright, Emulation](https://playwright.dev/docs/emulation)
- [Docs] StyleProof: a surface is one UI state to certify (a route, tab or open modal). Variants are extra deterministic states of a surface, live states and state recipes are separate captures, state layers are the forced pseudo-class reads within a capture, and media states are named media environments. Captures are stored by key and width, and media copies are named by appending the media key. Media environments multiply surfaces and explicit variants only — [StyleProof REFERENCE.md](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/REFERENCE.md); [StyleProof forced-state capture](https://github.com/BenSheridanEdwards/StyleProof/blob/main/docs/forced-state-capture.md)
- [Docs] Galen: tests are parameterised by tables (device name, tags, size; browser), tables can be nested to give the full product (3 browsers by 3 sizes gives 9 runs) and reused by name, and tags select which spec sections apply — [Galen test suite syntax](https://galenframework.com/docs/reference-galen-test-suite-syntax/)
- [Docs] Storybook pseudo-states addon: a `pseudo` parameter per story, a map from state name to true or to selectors — [Storybook pseudo-states addon README](https://github.com/storybookjs/storybook/tree/next/code/addons/pseudo-states)
- [Docs] Percy: per snapshot or global `pseudoClassEnabledElements` with selectors, ids, class names or XPaths — [Percy, configure pseudo-class](https://www.browserstack.com/docs/percy/percy-snapshot-config/configure-pseudo-class)
- [Docs] Android Compose: a preview has a name, a group and configuration parameters (widthDp, heightDp, locale, fontScale, uiMode, device, wallpaper). Multipreview templates exist for screen sizes, font scales, light and dark, and dynamic colours. Combining multipreview annotations does not produce all combinations: each renders only its own variants — [Android Developers, Preview your UI with composable previews](https://developer.android.com/develop/ui/compose/tooling/previews)
- [Spec] The protocols' own words: CDP speaks of forced pseudo classes and emulated media with media features; BiDi speaks of overrides held as configuration per page, user context or globally — [CSS.pdl](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/CSS.pdl); [WebDriver BiDi](https://w3c.github.io/webdriver-bidi/#module-emulation)

### Inferences
- A plain-data model that fits onionskin: a capture is identified by a map of independent parameters, for example page, width, engine, preferences (colour scheme, reduced motion, forced colours, contrast, default font size) and state (which element, which pseudo-classes or which real input). The map itself is the key. Names, if offered, are labels over the map.
- Avoid Chromatic's trap: do not key stored captures by a user-chosen name alone. Compare two captures only when their parameter maps are equal, or when the user asks for a cross-variant diff on purpose.
- Use CSS's own words where they exist, since they are exact and already known: pseudo-class names for states, media feature names and values for preferences. Keep "state" for element and interaction state and use another word (environment or preferences) for user settings, as StyleProof and Chromatic both end up separating the two.
- Do not multiply by default. StyleProof expands nothing unless declared and Compose renders each annotation's variants independently; Galen's nested tables show how fast the full product grows. A sensible default is the resting, light, 16px capture plus single-axis variants (dark; larger font; each state), with the full product opt-in.
- Record in each capture which requested parameters the engine could not honour (for example forced states in Firefox), so that a diff can say "not captured" where StyleProof says skipped.

### Gaps
- I did not read Applitools' or Percy's data model for browsers and widths, or Xcode's preview variants.
- No study compares these models for maintenance cost; the choices above rest on tool documentation only.

### Sources for all sections, by type

**Peer-reviewed research**
- Alshayban, A., Malek, S. (2022). AccessiText: automated detection of text accessibility issues in Android apps. ESEC/FSE 2022, pp. 984-995. https://doi.org/10.1145/3540250.3549118
- Behfarshad, Z., Mesbah, A. (2013). Hidden-web induced by client-side scripting: an empirical study. In a 2013 Springer proceedings volume (the venue name, which I believe is ICWE 2013, was not confirmed). https://doi.org/10.1007/978-3-642-39200-9_7
- Chiou, P., Alotaibi, A., Halfond, W. (2021). Detecting and localizing keyboard accessibility failures in web applications. ESEC/FSE 2021, pp. 855-867. https://doi.org/10.1145/3468264.3468581
- Chiou, P., Alotaibi, A., Halfond, W. (2023). Detecting dialog-related keyboard navigation failures in web applications. ICSE 2023, pp. 1368-1380. https://doi.org/10.1109/ICSE48619.2023.00120
- Chiou, P., Winn, R., Alotaibi, A., Halfond, W. (2024). Automatically detecting reflow accessibility issues in responsive web pages. ICSE 2024. https://doi.org/10.1145/3597503.3639229
- Gu, J., Huang, H. (2025). Characterizing and repairing color-related accessibility issues in Android apps. ASE 2025, pp. 2033-2045. https://doi.org/10.1109/ASE63991.2025.00169
- Ma, S., Chen, C., Khalajzadeh, H., Grundy, J. (2023). A first look at dark mode in real-world Android apps. ACM Transactions on Software Engineering and Methodology 33(1), 1-26. https://doi.org/10.1145/3604607
- Mazinanian, D., Tsantalis, N., Mesbah, A. (2014). Discovering refactoring opportunities in cascading style sheets. FSE 2014. https://doi.org/10.1145/2635868.2635879 (from the earlier report)
- Mesbah, A., Mirshokraie, S. (2012). Automated analysis of CSS rules to support style maintenance. ICSE 2012. https://doi.org/10.1109/ICSE.2012.6227174 (from the earlier report)
- Panchekha, P., Geller, A. T., Ernst, M. D., Tatlock, Z., Kamil, S. (2018). Verifying that web pages have accessible layout. PLDI 2018. https://doi.org/10.1145/3192366.3192407 (from the earlier report)
- Su, Y., Chen, C., Wang, J., Liu, Z., Wang, D., Li, S., Wang, Q. (2022). The Metamorphosis: automatic detection of scaling issues for mobile apps. ASE 2022. https://doi.org/10.1145/3551349.3556935
- Sun, J., Su, T., Li, J., Dong, Z., Pu, G., Xie, T., Su, Z. (2021). Understanding and finding system setting-related defects in Android apps. ISSTA 2021, pp. 204-215. https://doi.org/10.1145/3460319.3464806
- Sun, J., Su, T., Liu, K., Peng, C., Zhang, Z., Pu, G., Xie, T., Su, Z. (2023). Characterizing and finding system setting-related defects in Android apps. IEEE Transactions on Software Engineering, pp. 2941-2963. https://doi.org/10.1109/TSE.2023.3236449
- Winn, R., Chiou, P., Halfond, W. (2026). Lost in navigation: detecting keyboard navigation accessibility issues in web pages. ICST 2026, pp. 682-693. https://doi.org/10.1109/ICST69053.2026.00089

**Preprints (not peer-reviewed as cited)**
- Huang, H., Liu, W., Gu, J., Wu, R. (2026). Spotting setting-related UI display bugs in Android apps. arXiv:2607.04120. https://arxiv.org/abs/2607.04120

**Specifications**
- W3C Browser Testing and Tools Working Group. WebDriver BiDi, Editor's Draft (source commit 24ff1a2, 2026-11-11). https://w3c.github.io/webdriver-bidi/ ; source https://github.com/w3c/webdriver-bidi/blob/main/index.bs
- Chrome DevTools Protocol, protocol definition files (commit d961d96, 2026-11-12): CSS, Emulation, Page, DOM, Input. https://github.com/ChromeDevTools/devtools-protocol/tree/master/pdl/domains ; rendered reference https://chromedevtools.github.io/devtools-protocol/
- W3C CSS Working Group. Selectors Level 4, Editor's Draft. https://drafts.csswg.org/selectors-4/
- W3C. Understanding WCAG 2.2, Success Criterion 1.4.4 Resize Text. https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html

**Browser source code and vendor documentation**
- Chromium (main, read 2026-11-12): inspector_css_agent.cc, inspector_emulation_agent.cc, media_feature_overrides.cc, css_computed_style_declaration.h, element_rule_collector.cc, settings.json5. https://github.com/chromium/chromium/tree/main/third_party/blink/renderer/core
- Firefox (main, read 2026-11-12): remote/webdriver-bidi/modules/root/emulation.sys.mjs, browsingContext.sys.mjs and input.sys.mjs; remote/shared/RecommendedPreferences.sys.mjs; modules/libpref/init/StaticPrefList.yaml and all.js; browser/app/profile/firefox.js; dom/chrome-webidl/BrowsingContext.webidl and InspectorUtils.webidl; widget/nsXPLookAndFeel.cpp. https://github.com/mozilla-firefox/firefox
- Mozilla. Firefox Remote Protocols, Security. https://firefox-source-docs.mozilla.org/remote/Security.html
- Mozilla Bugzilla. Bug 2064354 (prefers-color-scheme in emulation.setMediaFeaturesOverride, fixed for Firefox 159); bug 2064352 (umbrella, open); bug 1561004 (CSS coverage, status not checked). https://bugzilla.mozilla.org/
- Mozilla product details (release versions and dates). https://product-details.mozilla.org/1.0/firefox_versions.json
- Chrome for Testing, last known good versions. https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions.json
- Chrome DevTools documentation: Emulate CSS media features https://developer.chrome.com/docs/devtools/rendering/emulate-css ; Coverage https://developer.chrome.com/docs/devtools/coverage
- MDN Web Docs: :focus-visible https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/:focus-visible ; Privacy and the :visited selector https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Selectors/Privacy_and_:visited
- Android Developers. Preview your UI with composable previews. https://developer.android.com/develop/ui/compose/tooling/previews

**Tools (documentation, source and registry records), with status on 2026-11-12**
- Playwright (Microsoft), 1.65.0 of 2026-11-05, active. https://playwright.dev/docs/api/class-page#page-emulate-media ; https://playwright.dev/docs/emulation ; https://github.com/microsoft/playwright
- Puppeteer (Google), 25.17.0 of 2026-11-10, active. https://pptr.dev/api/puppeteer.page.emulatemediafeatures ; https://github.com/puppeteer/puppeteer
- Cypress, 16.3.0 of 2026-11-10, active. https://docs.cypress.io/api/commands/hover
- cypress-real-events (Dmitriy Kovalenko), 1.15.1 of 2026-09-12, active, 840 stars, Chromium only. https://github.com/dmtrKovalenko/cypress-real-events
- Storybook pseudo-states addon, npm 10.6.7 of 2026-11-10, active inside the Storybook monorepo; the stand-alone repository is archived. https://github.com/storybookjs/storybook/tree/next/code/addons/pseudo-states
- Chromatic (commercial), CLI 18.14.3 of 2026-11-09, active. https://www.chromatic.com/docs/modes/ ; https://www.chromatic.com/docs/hoverfocus/
- Percy (BrowserStack, commercial), @percy/cli 1.32.12 of 2026-10-21, active. https://www.browserstack.com/docs/percy/percy-snapshot-config/configure-pseudo-class
- BackstopJS, npm 6.3.25 of 2024-09-07, repository pushed 2026-10-10, 7,202 stars, maintained with slow releases. https://github.com/garris/BackstopJS
- postcss-pseudo-classes (Giuseppe Gurgone), 0.4.0 of 2023-11-27, last push 2024-07-17, 29 stars, low activity. https://github.com/giuseppeg/postcss-pseudo-classes
- StyleProof (Ben Sheridan-Edwards), 9.0.0 of 2026-10-29, active, single author, 3 stars. https://github.com/BenSheridanEdwards/StyleProof
- Galen Framework, inactive since 2019. https://galenframework.com/docs/reference-galen-test-suite-syntax/

**Surveys and other grey literature**
- HTTP Archive. Web Almanac 2025, Accessibility chapter (published 2026-01-15). https://almanac.httparchive.org/en/2025/accessibility
- Project Wallace (Bart Veneman). The CSS Selection 2026. https://www.projectwallace.com/the-css-selection/2026
