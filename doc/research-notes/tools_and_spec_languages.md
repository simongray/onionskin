# Tools and specification languages for testing web page layout and style (prior art for onionskin)

How these notes were made. Maintenance data was checked on 2026-11-03 with the GitHub REST API (`gh api repos/<owner>/<repo>`, `.../commits?per_page=1`, `.../releases`, `.../tags`), the npm registry (`npm view <pkg> time`), the Maven Central search API and the opam package page. "Last commit" means the newest commit on the default branch. Each finding is tagged by source type: [Docs] = the tool's own documentation or README, [Source] = the tool's source code, [Registry] = GitHub/npm/Maven/opam metadata, [Author] = blog post or talk by the tool's author, [Third party] = opinion or summary by someone else.

## Galen Framework: the Galen Spec language, its relations, ranges, objects, tags, reporting and status

### Takeaway
Galen is the closest prior art to onionskin's "symbolic relations with ranges" idea. It has a small declarative language of box relations (`near`, `left-of`, `above`, `inside`, `aligned`, `centered`, `width ... % of`) with pixel ranges, approximate values and per-device tags. It runs on Selenium, and its HTML report draws the measured distances. It has had no release since March 2019 and no commit since then. Its author and the Thoughtworks Radar both point to the cost of long specs and of end-to-end speed and brittleness.

### Cited Findings
**What it checks and how specs are written**
- [Docs] Galen uses Selenium to open a browser, and a spec language to describe the layout at different browser sizes. The README states its idea: "The best way to test the layout is to check location and dimensions of elements relatively to each other." Example from the README: `menu:` / `inside screen 0px left right` / `below header 0px`, and under `@on desktop`, `side-panel: ... width 300px` / `near content 0px right` - [Galen README](https://github.com/galenframework/galen)
- [Docs] Objects are declared in an `@objects` block as name plus locator. The locator types are `id`, `css` and `xpath`, and CSS is the default: `@objects search_panel id search-bar`. Nested objects are scoped to their parent (`search_panel.input`). A trailing `*` numbers the matches: `menu_item-* css #menu li a` gives `menu_item-1`, `menu_item-2` and so on. A measured box can be corrected with `@(0, 0, -50, 0)` or `@(0, 0, 0, =200)`. Groups (v2.2+) use `@groups skeleton_elements header, menu, content, footer` and are referenced as `&skeleton_elements` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] Special objects: `viewport` (the visible area), `screen` (the whole page), `parent`/`self` (inside component specs) and `global` (for page-wide checks such as `count`) - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)

**Relation vocabulary (verbatim examples from the guide)**
- [Docs] `near`: `textfield: near button 10px left`, `near button 5px bottom left` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `above`/`below`: `caption: above description 10 to 20 px`. `left-of`/`right-of`: `textfield: left-of button 10px` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `inside` and `inside partly`. Sides must always be given: `button: inside container 10px left right, 20px top bottom`, `inside partly container 10px top left` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `width`/`height`, absolute or relative to another object's dimension: `button: width 100 px height 25px`, `comments: width 100 % of main/width`, `width 30 to 100 % of screen/width` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `aligned horizontally` with `all|top|bottom|centered` and `aligned vertically` with `all|left|right|centered`, with an optional tolerance: `menu_item-1: aligned horizontally all menu_item-2 1px` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `centered horizontally|vertically|all inside|on`: `button: centered all inside box`, `label: centered horizontally inside box 10px` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `on <corner> edge`: `user-picture-label: on top left edge user-picture 20 px left, 10px bottom` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] `contains` / `contains partly`, `absent`, `visible`, `count any|visible|absent <pattern> is <range>` (for example `global: count any menu_item-* is 3`), `text is|contains|starts|ends|matches` with `lowercase|uppercase|singleline` transforms, and `css <prop> is "<value>"`. The guide warns that `css` should be used sparingly because it ties tests to CSS - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] Pixel and colour checks also exist: `image file imgs/menu_item-1.png, error 12px` (or `error 4%`), with `tolerance` (default 30), ignore regions, filters (`blur`, `denoise`, `mask` and others), `color-scheme 10% white, 4 to 5 % black`, and `ocr` (v2.4+, Google Vision) - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] Reuse and abstraction: `@set` variables, `@import`, `component other.gspec` (runs a sub-spec scoped to an object), `@for`/`@forEach` loops with `prev`/`next`, `@if` with JavaScript functions such as `isVisible("banner-1")`, and `@rule` custom rules invoked with `|`. A `%` prefix makes a check a warning instead of an error - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] galen-extras is a rule library, embedded in Galen from 2.4. It has higher-level phrases such as `| menu.item-* are aligned horizontally next to each other with equal distance`, `| box-* are aligned vertically above each other with 25 to 30px margin`, `| menu.item-* are rendered in 2 column table layout`, `| header_icon, menu_button should be almost squared` and `| amount of visible menu.item-* should be 4 to 7` - [galen-extras README](https://github.com/galenframework/galen-extras); [Galen CHANGELOG](https://github.com/galenframework/galen/blob/master/CHANGELOG.md)

**Ranges and tolerance**
- [Docs] Range forms: exact `100px`, `50 to 200 px`, `> 40 px`, `< 40 px`, `>= 40 px`, `<= 40 px`, approximate `~ 100 px`, and percentages `50 % of screen/width`. Units are required - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Source] `~` is widened by the property `galen.range.approximation`. The shipped config template sets it to `2`. Its comment explains that with approximation 5, `height: ~ 50 px` becomes `45 to 55px` - [Galen config-template.conf](https://github.com/galenframework/galen/blob/master/galen-core/src/main/resources/config-template.conf)
- [Docs] The configuration page shows `galen.range.approximation=3`, and image defaults `galen.spec.image.tolerance = 30` and `galen.spec.image.error = 0px` - [Galen configuration](http://galenframework.com/docs/getting-started-configuration/). The two sources give different example values (2 and 3). Neither states the built-in Java default.

**Responsive widths and tags**
- [Docs] Sections are written `= Header section =`. Tags select checks per device: `@on mobile`, `@on *`, `@on mobile, desktop` - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- [Docs] The CLI runs one spec at one window size and filters by tag: `galen check homepage.gspec --url "http://example.com" --size "640x480"` with `--include`/`--exclude` tag lists. `galen test` runs suites, with `--parallel-tests`, `--groups` and `--htmlreport` - [Galen command line reference](http://galenframework.com/docs/reference-working-in-command-line/)

**Failure reporting, spec generation and mutation testing**
- [Docs] Galen 2.4 (changelog date 2018-11-03) added "Validations highlight in HTML report", a "Failure map popup", a "Spec generator", a "'mutate' action for galen specs mutation testing" and the embedded galen-extras lib - [Galen CHANGELOG](https://github.com/galenframework/galen/blob/master/CHANGELOG.md)
- [Author] In the 2.4 release post, the report draws arrows that show the measured distance for each validation, because users found the error messages confusing. The failure map colours each page object by status to show which element causes the failures - [Galen 2.4 release post](http://galenframework.com/post/2018-11-03-released-version-2.4-spec-generator-and-mutation-testing/)
- [Author] `galen generate "login-pagedump/page.json" --export login.gspec` builds a spec from a page dump. It uses relative rules ("aligned vertically above each other", "inside screen", "centered horizontally inside", "left-of", "below") and skips checks that are redundant when a child is attached to an inner side of its parent. `--no-galen-extras` turns off the extras phrasing - [Galen 2.4 release post](http://galenframework.com/post/2018-11-03-released-version-2.4-spec-generator-and-mutation-testing/)
- [Author] `galen mutate login.gspec --url ... --size 1200x800 --offset 5 --htmlreport mutation-report` changes each object (for example width +5px and -5px) and reports the mutations the spec did not catch. The spec must pass first - [Galen 2.4 release post](http://galenframework.com/post/2018-11-03-released-version-2.4-spec-generator-and-mutation-testing/)

**Pitfalls and criticism**
- [Third party] Thoughtworks Technology Radar placed Galen in "Trial" (Nov 2016 and Mar 2017). It said Galen "suffers from the typical brittleness and speed issues" of end-to-end testing, but early feedback on design issues was valuable. The blip is no longer on the current Radar - [Thoughtworks Radar: Galen](https://www.thoughtworks.com/radar/tools/galen)
- [Author, podcast] Ivan Shubin, Galen's author, advised "try not to write a complete page spec". Specs of several hundred lines become unmaintainable and people throw them away. His fix is custom rules that describe the layout in short sentences. Galen checks bounding boxes and is not a pixel visual-testing tool (TestGuild podcast #144, 2017-03-12) - [TestGuild #144](https://testguild.com/podcast/144-automated-responsive-design-testing-using-galen-ivan-shubin/)
- [Registry] Open issues include missing support for `:before`/`:after` pseudo-elements (#327, 2015) and a request for .NET or WebDriver.js ports (#565, 2018). The repository has 166 open issues - [Galen issue #327](https://github.com/galenframework/galen/issues/327); [Galen issue #565](https://github.com/galenframework/galen/issues/565)

**License, runtime, maintenance**
- [Docs] Apache License 2.0 (the README and LICENSE-2.0.txt say so; the GitHub API reports no detected license). Java on Selenium, with JavaScript test files and a JS API inside `${ }` - [Galen README](https://github.com/galenframework/galen)
- [Registry] Last GitHub release `galen-2.4.4`, published 2019-03-15. Last commit 2019-03-10 ("[maven-release-plugin] prepare for next development iteration"). Not archived. 1,417 stars - [Galen releases](https://github.com/galenframework/galen/releases); [Galen commits](https://github.com/galenframework/galen/commits/master)
- [Registry] Maven Central: `com.galenframework:galen-core` 2.4.4, last upload 2019-03-10 - [Maven Central search API](https://search.maven.org/solrsearch/select?q=g:com.galenframework&rows=20&wt=json)
- [Registry] The npm wrapper `galenframework-cli` 2.4.4-build43 is dated 2022-05-20. Its GitHub repo hypery2k/galenframework-cli is archived - [npm galenframework-cli](https://www.npmjs.com/package/galenframework-cli); [hypery2k/galenframework-cli](https://github.com/hypery2k/galenframework-cli)

### Inferences
- Galen's vocabulary is a good checklist for onionskin's relation set: near/left-of/right-of/above/below with side and distance, inside/inside-partly with per-side distances, aligned (edge or centre) with tolerance, centered inside/on, size as an absolute value or a percentage of another object, count and presence.
- Galen already made three onionskin ideas real: relations derived from a page dump (`generate`), mutation testing to measure how tight a spec is (`mutate`), and reports that draw the measured distance. Onionskin could reuse the mutation idea: perturb captured facts and check that the expectations fail.
- The author's own advice (keep specs short, use rules) supports onionskin's choice of coarse symbolic facts over exhaustive per-element pixel specs.
- A Clojure EDN form could map almost directly onto Galen spec lines (object, relation, other object, range, sides), without a custom parser.

### Gaps
- I did not find the built-in Java default for `galen.range.approximation`, only the config-template value (2) and the docs example (3).
- I did not fetch the Galen test-suite syntax page (`@@ table` device parameterisation), so how devices map to `--size` and tags in suites is not described here.
- No commit history after 2019 exists, so the reason development stopped is not documented in the sources I found.

## Quixote: relative CSS assertions, descriptors, failure messages, design rationale and status

### Takeaway
Quixote expresses layout as arithmetic on "descriptors" (edges, centre, size) of elements, the viewport and the page. Assertions read like equations: `navbar.top.should.equal(header.bottom)`. Failure messages state the gap in pixels and where the expected value came from. James Shore built it for fast, unit-level TDD of CSS, in reaction to slow and brittle screenshot tools and the Selenium-based Hardy. The last release (1.0.1) and the last commit date from April 2020.

### Cited Findings
- [Docs] Quixote renders elements in an iframe and reads `getComputedStyle()` and `getBoundingClientRect()`. It claims "over 100 tests/second" and supports a unit style (load a CSS file, add HTML fixtures with `frame.add(...)`) and an integration style (`quixote.createFrame({ src: "/", width: 800 })`) - [Quixote README](https://github.com/jamesshore/quixote)
- [Docs] Example assertions: `navbar.top.should.equal(header.bottom);`, `navbar.width.should.equal(frame.viewport().width);`, `navbar.top.should.equal(logo.bottom.plus(10), "navbar should be below logo");`, `logo.center.should.equal(frame.page().center, "logo should be centered");` - [Quixote README](https://github.com/jamesshore/quixote)
- [Docs] Example failure output, which names the descriptor, the direction and size of the gap, and where the expected value came from: `top edge of '.navbar' should be 13px higher.` / `Expected: 50px (bottom edge of '#header')` / `But was:  63px` - [Quixote README](https://github.com/jamesshore/quixote)
- [Docs] `PositionDescriptor` assertions: `should.equal`, `should.notEqual`, `should.beAbove`, `should.beBelow`, `should.beLeftOf`, `should.beRightOf`. Arithmetic: `.plus()`, `.minus()`, and `.to()`, which makes a span between two positions: `header.width.should.equal(leftColumn.left.to(rightColumn.right));` - [Quixote PositionDescriptor](https://github.com/jamesshore/quixote/blob/release/docs/PositionDescriptor.md)
- [Docs] `SizeDescriptor` assertions: `should.equal`, `should.notEqual`, `should.beBiggerThan`, `should.beSmallerThan`, with `.plus()`, `.minus()` and `.times()`: `lightbox.width.should.equal(frame.viewport().width.times(1/2));` - [Quixote SizeDescriptor](https://github.com/jamesshore/quixote/blob/release/docs/SizeDescriptor.md)
- [Docs] Element descriptors include top/right/bottom/left/center/middle/width/height. `element.render` (an `ElementRender`) says whether any part is rendered (DOM presence, size, position, `clip`, `overflow`), with its own edges for the rendered part: `caption.render.bottom.should.equal(content.bottom);`. `clip-path` and `overflow: clip` are not supported - [Quixote QElement](https://github.com/jamesshore/quixote/blob/release/docs/QElement.md); [Quixote ElementRender](https://github.com/jamesshore/quixote/blob/release/docs/ElementRender.md)
- [Docs] Tolerance is built in and fixed: "We consider pixel values to be the same if they're within 0.5px of each other." The docs advise 100% browser zoom when errors are larger - [Quixote QElement](https://github.com/jamesshore/quixote/blob/release/docs/QElement.md)
- [Docs] Styles are read raw: `button.getRawStyle("text-align")`, checked with an ordinary assertion library - [Quixote README](https://github.com/jamesshore/quixote)
- [Docs] Responsive widths: `frame.resize(width, height)`, plus `frame.viewport()` and `frame.page()` descriptors - [Quixote QFrame](https://github.com/jamesshore/quixote/blob/release/docs/QFrame.md). Quixote 0.6 was announced as "Test Responsive Designs" - [Quixote 0.6 post](http://www.letscodejavascript.com/v3/blog/2014/11/quixote_06_test_responsive_designs) (title seen in search results only, not fetched)
- [Docs] The older object-literal API `element.assert({ top: 13, bottom: otherElement.top })` and `element.diff(expected)`, which returns a human-readable difference string, are deprecated in favour of `should` - [Quixote QElement](https://github.com/jamesshore/quixote/blob/release/docs/QElement.md)
- [Docs] Ideas on the roadmap but never built: z-ordering, colours and contrast, a plugin API - [Quixote ROADMAP](https://github.com/jamesshore/quixote/blob/release/ROADMAP.md)
- [Author] Announcement (Let's Code JavaScript blog, dated 2014-10-01 in the post). Shore's problem: the cascade makes CSS changes risky, and there are too many style and page combinations to test by hand. He calls screenshot tools such as BBC's Wraith slow and brittle end-to-end tests. He calls Hardy polished but says it uses a cumbersome custom syntax and depends on Selenium. He wanted narrow unit tests that run in milliseconds, so they fit TDD's red-green-refactor loop, written in ordinary JavaScript test frameworks (Karma, Mocha, Chai), to make CSS safe to refactor - [Announcing Quixote](https://www.letscodejavascript.com/v3/blog/2014/09/announcing_quixote)
- [Registry] npm `quixote` 1.0.1, published 2020-04-22. Last commit 2020-04-22 (message "1.0.1"), tag v1.0.1, no GitHub releases, not archived, 834 stars, default branch `release`. MIT per README and npm (the GitHub API reports NOASSERTION). JavaScript, runs in the browser (IE 8+) - [npm quixote](https://www.npmjs.com/package/quixote); [Quixote repo](https://github.com/jamesshore/quixote)

### Inferences
- Quixote's descriptor algebra (edge + offset, span between edges, size times fraction) is a precise way to state "exact value" facts that stay relative. It fits onionskin's fine-grained tier well. The `.to()` span gives "gap" quantities directly.
- The failure message format (which way, how many pixels, and which descriptor the expected value came from) is worth copying for onionskin's exact-with-tolerance checks.
- A single fixed 0.5px tolerance worked for Quixote. Onionskin's per-check tolerance is more flexible, but a sensible global default near 0.5px matches prior practice (Compose uses 0.5dp, see below).
- Quixote has no symbolic relation layer of its own (no "inside" or "overlaps" fact). `beAbove` and similar compare single edges, not boxes.

### Gaps
- I did not fetch the Quixote 0.6 post or the Øredev talk, so Shore's reasoning on responsive testing is not covered beyond the title.
- No stated reason was found for the end of development after 1.0.1.

## Computed-style tools: Hardy, css-wrangler, computed-style-diff, StyleProof, sosie

### Takeaway
Two generations exist. The 2013 to 2018 tools (Hardy, css-wrangler, computed-style-diff) read computed styles through Selenium or in the page and compare them with values or with a baseline. They are all unmaintained. Two 2026 tools aim at the same "prove a refactor changed nothing visible" goal as onionskin: StyleProof (npm, Playwright, computed-style diff gated in PR review) and sosie (OCaml, layout plus a whitelist of visual properties, GumTree tree matching). Both are new, actively developed, and have very few users.

### Cited Findings
**Hardy (thingsinjars/Hardy)**
- [Docs] Hardy is "a collection of CSS testing steps" written in Cucumber and run through Selenium via WebDriverIO. It grew out of GhostStory (CasperJS/PhantomJS). It also does image diffs with GraphicsMagick or PhantomJS - [Hardy README](https://github.com/thingsinjars/Hardy)
- [Source] The step vocabulary, as regexes: `"<selector>" should have "<property>" of "<value>"`, `... of "<v1>" or "<v2>"`, `"<selector>" should have offset "<prop>" of "<value>"`, `"<selector>" should have "<property>" of <less|greater|more> than "<value>"`, and `"<selector>" should look the same as before` - [Hardy css.js step definitions](https://github.com/thingsinjars/Hardy/blob/master/features/step_definitions/css.js)
- [Source] Example scenario: `Given I visit "http://hardy.io/test-1.html"` / `And the window size is "500px" by "1000px"` / `Then "section" should have "height" of less than "600px"`. Selectors can be replaced by names through a `selectors.js` map, so `"floating div"` can stand for a CSS selector - [Hardy acceptance feature](https://github.com/thingsinjars/Hardy/blob/master/tests/acceptance/test.feature); [Hardy selectors.js](https://github.com/thingsinjars/Hardy/blob/master/features/support/selectors.js)
- [Registry] npm `hardy` 1.1.0, published 2013-11-13. GitHub tag v1.2.0. Last commit 2014-10-28. MIT per GitHub. Node.js plus Java (Selenium) - [npm hardy](https://www.npmjs.com/package/hardy); [Hardy repo](https://github.com/thingsinjars/Hardy)
- [Author, about Hardy] James Shore called Hardy's custom syntax cumbersome and its Selenium dependency a drawback for unit testing - [Announcing Quixote](https://www.letscodejavascript.com/v3/blog/2014/09/announcing_quixote)

**css-wrangler (moefinley/css-wrangler)**
- [Docs] A "Frozen DOM/Computed Style testing" crawler for refactoring CSS across a large site. It takes `beforeUrl`, `afterUrl` and `pages: [{ id, path, elementsToTest: ['h1', '.element-with-class', '#elementWithId'] }]`, scrapes computed styles of those elements on both versions, writes original, revised and diff JSON files, and shows them in `Results/Results.html` with counts of style changes and element/content changes. It can gather a baseline alone (`--getOriginal`) and compare later (`--original file.json`). Roadmap items never built include per-difference "ignore this property/value" checkboxes, page "states" driven by Selenium before scraping, and configurable browser width - [css-wrangler README](https://github.com/moefinley/css-wrangler)
- [Registry] npm `css-wrangler` 0.12.0, published 2018-02-10. Last commit 2018-02-10. 2 stars. MIT per npm. TypeScript/Node plus Selenium WebDriver - [npm css-wrangler](https://www.npmjs.com/package/css-wrangler); [css-wrangler repo](https://github.com/moefinley/css-wrangler)

**computed-style-diff (m3g4p0p/computed-style-diff)**
- [Docs] "Toggle style sheets and inspect the effects they have on the elements present on the page." `getStyleDiff(['theme.css']).then(console.table)` returns computed-style changes grouped by the CSS selectors. Stated limitations: media queries are ignored, element states such as `:hover` are ignored, and only absolute values are reported - [computed-style-diff README](https://github.com/m3g4p0p/computed-style-diff)
- [Registry] npm 1.0.3, published 2018-08-14. Repo last pushed 2018-08-16. MIT. Browser JavaScript. The npm entry has no repository field - [npm computed-style-diff](https://www.npmjs.com/package/computed-style-diff); [computed-style-diff repo](https://github.com/m3g4p0p/computed-style-diff)

**StyleProof (BenSheridanEdwards/StyleProof)**
- [Docs] StyleProof records "the computed CSS of every element" for declared UI states ("surfaces": routes, open modals, dropdowns, toasts) at real breakpoint widths, on the base branch and the PR head. It posts the differences as a PR comment. "StyleProof is not a screenshot diff": the gate compares "resolved longhands, pseudo-elements, layout boxes, motion longhands, and forced `:hover`/`:focus`/`:active` deltas". Screenshots are attached only so humans can see the change - [StyleProof README](https://github.com/BenSheridanEdwards/StyleProof)
- [Docs] Two modes. Review-gate (`require-approval: true`) keeps the check red until a reviewer approves, and approved changes become the new baseline. Certify (`fail-on-diff: true`) fails on any difference and is meant for a "supposedly no-op refactor, such as CSS-to-Tailwind". The README says this mode is "the job StyleProof was born for" - [StyleProof README](https://github.com/BenSheridanEdwards/StyleProof)
- [Docs] Report format: one card per changed element (`button.btn`), the surface and width (`demo-button @ 900`), a property table (`padding` `14px 28px` to `18px 32px`), before/after crops with a highlight overlay, and `currentColor`-derived properties nested under the colour that drove them. Restyled elements that the PR also re-nested (wrapper added or removed) are "paired back by geometry" - [StyleProof README](https://github.com/BenSheridanEdwards/StyleProof)
- [Docs] Much of its machinery is about the limits of a green result. A coverage registry fails if a declared surface was not captured. A determinism ledger blocks a green from an unproven capture. An inventory guard flags removed links, tabs and menu items. Failed data requests are gated. A "product-state identity" check covers pairing. The README states the boundary: "StyleProof certifies only what it captured" and "maps prove only the states the spec exercises" - [StyleProof README](https://github.com/BenSheridanEdwards/StyleProof)
- [Docs] CLI: `npx styleproof capture` then `npx styleproof compare`. Maps are stored as `<key>@<width>.json`. Requires Node 22.13+ and Playwright - [StyleProof README](https://github.com/BenSheridanEdwards/StyleProof)
- [Registry] npm `styleproof` first published 2026-06-13. v9.0.0 published 2026-10-29 (GitHub release the same day). Last commit 2026-11-03. 3 stars. MIT. JavaScript - [npm styleproof](https://www.npmjs.com/package/styleproof); [StyleProof repo](https://github.com/BenSheridanEdwards/StyleProof)

**sosie (cuihtlauac/sosie, opam)**
- [Docs] "A DOM equivalence checker for UI-conservative HTML/CSS refactoring" that answers "did this refactoring change the visual output?". It captures layout and style from a real browser with `getBoundingClientRect` and `getComputedStyle` (the Chrome DevTools Protocol is used only as a Chromium speed-up) and compares two captures structurally. Subcommands: `capture`, `compare`, `capture-all` (routes x viewports x colour schemes), `audit-whitelist`, `show-config` - [sosie README](https://github.com/cuihtlauac/sosie)
- [Docs] Design principle: "False negatives (missed visual regressions) are fatal", while false positives are tolerable. "The property whitelist is the explicit trust boundary." The whitelist has about 20 visual properties (display, visibility, opacity, overflow, color, background-color, font family/size/weight, line-height, text-align, text-decoration, borders, border-radius, box-shadow, z-index, a resolved paint order, cursor). Geometry comes from bounding boxes - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- [Docs] Levels of equivalence, from weakest to strongest: semantic (accessibility tree), layout (same boxes), layout plus visual properties (the chosen target), pixel - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- [Docs] Normalisation runs as a pure `snapshot -> snapshot` step before comparison, with rules declared in a JSON config: `drop_attributes` (for example `class`, `style`, `data-x-*`), `round_bounds: 0.5`, `canonicalize_colors`, `canonicalize_fonts`, `mask_text` by selector, `mask_text_matching` by regex, and `drop_subtrees` - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- [Docs] Tree matching does not walk both trees in lockstep, because wrapping elements in a new `<div>` is common. It uses a simplified GumTree algorithm: hash matching of identical subtrees, container matching by descendant overlap (Dice coefficient), then LCS alignment of children. Diff types include `Bounds_diff`, `Style_diff`, `Text_diff`, `Paint_order_diff`, `Extra_node`, `Moved_node`, `Wrapper_inserted` and `Wrapper_removed`. Config: `bounds_tolerance` and `value_tolerance` - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- [Docs] The design doc cites Cassius (OOPSLA 2016) and VizAssert (PLDI 2018) from UW PLSE as academic precedent - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- [Registry] opam `sosie` 0.1.2, published Aug 24, 2026. GitHub releases v0.1.0, v0.1.1 and v0.1.2 all on 2026-08-21. Last commit 2026-08-24. 1 star. MIT. OCaml with js_of_ocaml. The README says it was developed with substantial help from Claude Code - [opam sosie](https://opam.ocaml.org/packages/sosie/); [sosie repo](https://github.com/cuihtlauac/sosie)

### Inferences
- StyleProof and sosie already cover onionskin's "compare two versions" use case at the exact-value level. Onionskin's distinctive part is the graded fact layer (symbolic relations and coarse ranges) and developer-written expectations, which neither tool has.
- From sosie, onionskin could borrow the explicit property whitelist as a stated trust boundary, normalisation as a pure, configured function, and wrapper-tolerant tree matching. Matching elements across versions is the hard part of any "two versions" comparison when the refactor changes the DOM.
- From StyleProof: capture states, not just URLs (forced `:hover`/`:focus`/`:active`, open menus), key maps by surface and width, and be loud about what was not captured. A green comparison of two captures says nothing about states that were never driven.
- Hardy and css-wrangler show the cost of Selenium-era tooling (Java server, slow runs) and of plain-English step syntax that adds parsing but not abstraction.

### Gaps
- I did not find docs for computed-style-diff beyond its short README.
- hardy.io, Hardy's documentation site, was not checked, so its full step list may be longer than the source regexes.
- StyleProof's `docs/REFERENCE.md` and `what-it-catches.md` were not read, so details of its geometry pairing and tolerances are missing.

## Screenshot and visual-diff tools: what they check and how they handle noise

### Takeaway
Pixel tools (CSS Critic, PhantomCSS, Wraith, BackstopJS, reg-suit, Lost Pixel, Playwright `toHaveScreenshot`, Cypress visual plugins) all fight the same noise: anti-aliasing, fonts and GPU differences, dynamic content and animation. The usual controls are a per-pixel colour threshold (often in YIQ), a total-diff budget (pixel count or ratio), anti-aliasing detection, and hiding or masking selectors. Commercial services (Percy, Chromatic, Applitools) add perceptual or "AI" matching. Applitools' "Layout" match level, which checks only that the relative positions of elements stay consistent, is the closest commercial analogue to onionskin's symbolic tier. Several early tools are archived.

### Cited Findings
**CSS Critic (cburgmer/csscritic)**
- [Docs] It checks the current layout against a reference image accepted earlier: "If your layout breaks (or simply changes - CSS Critic can't tell) your tests fail." It runs as a browser page (`RegressionRunner.html`, `csscritic.add({ url: ... })`), and accepting new references is meant to be quick and light - [CSS Critic README](https://github.com/cburgmer/csscritic)
- [Registry] npm and GitHub release 2.0.1, 2022-01-21. Last commit 2023-01-28 ("npm update"). 488 stars. MIT per npm (no license detected by GitHub). JavaScript in the browser - [npm csscritic](https://www.npmjs.com/package/csscritic); [CSS Critic repo](https://github.com/cburgmer/csscritic)

**PhantomCSS (HuddleEng/PhantomCSS)**
- [Docs] CasperJS plus PhantomJS or SlimerJS screenshots, compared with Resemble.js. `mismatchTolerance` defaults to 0.05%. Selectors can be hidden at capture time (`phantomcss.screenshot("#feedback-form", undefined, 'input[type=file]')`) - [PhantomCSS README](https://github.com/HuddleEng/PhantomCSS)
- [Docs] Lessons in its own README: full-page screenshots are a bad idea (2px of footer padding fails 50 page tests), screenshot tests are slower than DOM assertions and do not replace functional tests, and anti-aliasing differences across machines cause false failures ("How can we solve this? Contributions welcome!"). It also advises stable selectors such as `#feedback-form` over `#sidebar li:nth-child(3) > div form` - [PhantomCSS README](https://github.com/HuddleEng/PhantomCSS)
- [Registry] Unmaintained from 2017-12-22, because headless Chrome made PhantomJS obsolete. Archived. npm 1.6.0 (2018-12-10). Last commit 2018-12-06. MIT - [PhantomCSS README](https://github.com/HuddleEng/PhantomCSS); [npm phantomcss](https://www.npmjs.com/package/phantomcss)

**Wraith (bbc/wraith, BBC News)**
- [Docs] Captures screenshots of two domains, or of one domain over time (`wraith capture`, `wraith history`, spidering when no paths are given), and highlights differing areas in blue. Exits with an error code when a diff exceeds the threshold - [Wraith README](https://github.com/bbc/wraith)
- [Source] Config: `screen_widths: [320, 600x768, ...]`, `fuzz: '20%'` (ImageMagick fuzz: "A higher fuzz makes the comparison less strict"), `threshold: 5` (maximum percent difference, default 0) - [Wraith capture.yaml template](https://github.com/bbc/wraith/blob/master/templates/configs/capture.yaml)
- [Registry] Archived, with the README note "This repository is no longer maintained". Last release v4.2.4, 2019-06-26. Last commit 2026-01-16 (adding the archive note). Apache-2.0. Ruby - [Wraith repo](https://github.com/bbc/wraith)

**BackstopJS (garris/BackstopJS)**
- [Docs] Scenario JSON with `viewports` (`{"label": "phone", ...}`), `selectors`, `selectorExpansion` (capture every match) with `expect` (number of matches), `readySelector`/`readyEvent`/`delay` (when to capture), `hideSelectors` (visibility: hidden), `removeSelectors` (display: none), `misMatchThreshold` ("Percentage of different pixels allowed to pass the test", example value 0.1) and `requireSameDimensions` (any change in selector size fails) - [BackstopJS README](https://github.com/garris/BackstopJS)
- [Registry] npm `backstopjs` 6.3.25, published 2024-09-07. Last commit 2026-09-08 (README edit). GitHub releases stop at v3.8.8 (2019), and tags go to v6.3.7. MIT. Node.js with Puppeteer/Playwright - [npm backstopjs](https://www.npmjs.com/package/backstopjs); [BackstopJS repo](https://github.com/garris/BackstopJS)

**reg-suit (reg-viz/reg-suit)**
- [Docs] Compares images and publishes a report. Options: `thresholdRate` (share of differing pixels, 0 to 1, default 0), `thresholdPixel` (absolute count, default 0), `matchingThreshold` (YUV colour distance per pixel), `enableAntialias` (detect and ignore anti-aliased pixels, default false). An optional `core.ximgdiff` uses x-img-diff-js, "a difference detection engine which calculates more structural information than naive pixel based comparison", to show which parts were inserted or moved - [reg-suit README](https://github.com/reg-viz/reg-suit)
- [Registry] Release v0.14.6, 2026-03-16. Last commit 2026-03-16. Repo pushed 2026-10-27. MIT. TypeScript/Node - [reg-suit repo](https://github.com/reg-viz/reg-suit)

**Lost Pixel (lost-pixel/lost-pixel)**
- [Docs] Described as an "Open source alternative to Percy, Chromatic, Applitools", with "Flexible sensitivity thresholds". The README now says the team is joining Figma and "We are sunsetting the product" - [Lost Pixel README](https://github.com/lost-pixel/lost-pixel)
- [Registry] Archived. Last release v3.22.0, 2024-11-14 (npm the same). Last commit 2026-04-22 ("Lost Pixel team is joining Figma"). MIT. TypeScript - [Lost Pixel repo](https://github.com/lost-pixel/lost-pixel); [npm lost-pixel](https://www.npmjs.com/package/lost-pixel)

**pixelmatch and looks-same (comparison engines used by many tools)**
- [Docs] pixelmatch: `threshold` is a per-pixel perceptual colour difference from 0 to 1 (example 0.1). `includeAA: true` disables anti-aliasing detection (default false, so anti-aliased pixels are ignored). ISC. Commit 2026-10-06 - [pixelmatch README](https://github.com/mapbox/pixelmatch)
- [Docs] looks-same (Yandex/Testplane): ignores anti-aliasing by default, and `antialiasingTolerance` loosens detection ("We recommend that you don't increase this value above 10"). v10.0.0, 2025-08-18. MIT - [looks-same README](https://github.com/gemini-testing/looks-same)

**Playwright (microsoft/playwright)**
- [Docs] `expect(locator).toHaveScreenshot()` options: `animations` (default `"disabled"`), `caret` (default `"hide"`), `mask: Locator[]` with `maskColor` (default `#FF00FF`), `maxDiffPixels`, `maxDiffPixelRatio`, `threshold` (default 0.2, "perceived color difference in the YIQ color space"), `stylePath` (inject CSS during capture), `scale` (`"css"` or `"device"`) - [Playwright LocatorAssertions](https://playwright.dev/docs/api/class-locatorassertions)
- [Docs] `await expect(locator).toHaveCSS('display', 'flex');` checks the element's computed CSS value (string or RegExp), with a `pseudo: "before" | "after"` option (v1.60). `toBeInViewport({ ratio: 0.5 })` checks intersection with the viewport - [Playwright LocatorAssertions](https://playwright.dev/docs/api/class-locatorassertions)
- [Registry] Latest release v1.64.1, 2026-10-14. Repo pushed 2026-11-03. Apache-2.0 - [Playwright releases](https://github.com/microsoft/playwright/releases)

**Cypress visual plugins**
- [Registry] `cypress-visual-regression` 6.0.1, published 2026-09-08. MIT - [npm cypress-visual-regression](https://www.npmjs.com/package/cypress-visual-regression). (Cypress layout-assertion plugins are covered in the next section.)

**Percy, Chromatic, Applitools (commercial services)**
- [Docs] Chromatic: `diffThreshold` default `.063`. Values near 0 are more sensitive and more prone to false positives. Anti-aliased pixels are detected and ignored by default (`diffIncludeAntiAliasing` overrides this). Colour distance is measured in YIQ space at each (x, y) - [Chromatic threshold docs](https://www.chromatic.com/docs/threshold/). npm `chromatic` 18.13.9, active on 2026-11-03 - [npm chromatic](https://www.npmjs.com/package/chromatic)
- [Docs, from search summaries, pages not fetched] Percy (BrowserStack) offers region sensitivity levels and a "diff ignore" percentage. Its "Intelli Ignore" AI option is on by default for projects created after 2024-04-08. Its engine does not flag isolated 1px differences within a 7px surrounding circle. Ignore regions can be set by CSS or XPath selector - [Percy: ignore 1px diffs](https://www.browserstack.com/docs/percy/stabilize-screenshots/1px-diffs); [Percy: intelli ignore](https://www.browserstack.com/docs/percy/visual-testing-workflows/view-percy-build-results/intelli-ignore); [Percy: regions overview](https://www.browserstack.com/docs/percy/set-regions/overview). npm `@percy/cli` 1.32.12 (beta 2026-10-29) - [npm @percy/cli](https://www.npmjs.com/package/@percy/cli)
- [Docs] Applitools match levels. Exact is pixel-to-pixel and "not recommended". Strict "detects changes in text, font, color, graphics, and position of elements" and ignores platform rendering differences. Ignore colors is like Strict without colour. Layout checks that "the relative positions of these elements are consistent", "can detect elements that have appeared, disappeared, or moved", and ignores "differences in the actual content text and graphics, color, and other style changes". Dynamic recognises patterns such as dates and URLs and is the default for Web tests on supported SDKs. Match levels can be set per region (Strict page with Layout regions over dynamic content). "Floating regions" give a range in which an element may move. "Ignore regions should only be used as a last resort" - [Applitools match levels](https://applitools.com/docs/eyes/concepts/best-practices/match-levels)
- [Docs] Applitools suggests Layout for dynamic content, localisation and cross-environment checks, and Layout can compare a baseline from one device with a checkpoint from another - [Applitools: choosing match level](https://applitools.com/tutorials/guides/advanced-use-cases/choosing-match-level)
- [Registry] `@applitools/eyes-playwright` 1.51.7, 2026-11-02, license "SEE LICENSE IN LICENSE" (proprietary service) - [npm @applitools/eyes-playwright](https://www.npmjs.com/package/@applitools/eyes-playwright)

### Inferences
- Every pixel tool ends up with the same three knobs: a per-pixel colour threshold, a total-diff budget, and selector-based hide or mask. Onionskin's equivalents would be per-relation tolerance, a cap on how many facts may change, and named exclusions.
- Applitools Layout shows there is commercial demand for "same structure, ignore content and colour". Its definition is opaque, though (it runs on images, with no published relation list). Onionskin can offer the same idea transparently, with facts the user can read.
- PhantomCSS's lesson ("full page screenshots are a bad idea", because one padding change fails every page) applies to fact diffs too: a shift high on the page changes the absolute position of everything below. Relative facts (gaps and relations between neighbours) limit the spread, which supports onionskin's design.
- reg-suit's x-img-diff ("inserted or moved" parts) shows that even image tools move toward structural explanations of differences.

### Gaps
- I did not verify Applitools' internal algorithm for Layout. The docs describe the behaviour but not how it is computed.
- Percy details come from search-result summaries of BrowserStack doc pages that I did not fetch.
- I did not check Lost Pixel's thresholds page or Cypress's own screenshot docs.

## Other tools that assert spatial relations between elements (web, Android, iOS, research)

### Takeaway
Spatial-relation vocabularies recur across platforms. Espresso has completely/partially left-of/right-of/above/below and edge alignment, with exact integer comparison and no tolerance. Compose has position and size equality with a 0.5dp default tolerance. Cypress has `cypress-layout-inspector` (leftOf, above, inside, aligned, overlapping, overflowing). Playwright had deprecated layout selectors (`:near` within 50px). Two tools find faults with no spec at all: LinkedIn's LayoutTest-iOS (overlapping views over data combinations) and the research tool ReDeCheck (relations tracked across viewport widths). jsdom-based matchers cannot help, because jsdom does no layout.

### Cited Findings
**Espresso PositionAssertions (Android)**
- [Source] Class doc: "A collection of ViewAssertions for checking relative position of elements on the screen." Methods: `isCompletelyLeftOf`, `isCompletelyRightOf`, `isCompletelyAbove`, `isCompletelyBelow`, `isPartiallyLeftOf`, `isPartiallyRightOf`, `isPartiallyAbove`, `isPartiallyBelow`, `isLeftAlignedWith`, `isRightAlignedWith`, `isTopAlignedWith`, `isBottomAlignedWith`. `isLeftOf`/`isRightOf`/`isAbove`/`isBelow` are deprecated aliases for the "completely" forms - [PositionAssertions.java](https://github.com/android/android-test/blob/main/espresso/core/java/androidx/test/espresso/assertion/PositionAssertions.java)
- [Source] Semantics, using `getLocationOnScreen` and integer pixels. COMPLETELY_LEFT_OF is `left1 + width1 <= left2`, so touching edges count and any horizontal overlap fails. PARTIALLY_LEFT_OF is `left1 < left2 && left2 < left1 + width1`, so the views overlap and view 1 starts first. LEFT_ALIGNED is `left1 == left2` exactly. There is no tolerance parameter - [PositionAssertions.java](https://github.com/android/android-test/blob/main/espresso/core/java/androidx/test/espresso/assertion/PositionAssertions.java)
- [Source] Usage: `onView(withId(...)).check(isCompletelyLeftOf(withId(...)))`. The other view is found by a Hamcrest matcher, and `AmbiguousViewMatcherException` is thrown if more than one view matches. The failure text is "View: <description> is not <POSITION> view <matcher>". Apache-2.0. File last changed 2023-01-07 - [PositionAssertions.java](https://github.com/android/android-test/blob/main/espresso/core/java/androidx/test/espresso/assertion/PositionAssertions.java)

**Jetpack Compose test bounds assertions (Android)**
- [Source] `assertWidthIsEqualTo(expected, tolerance)`, `assertHeightIsEqualTo`, `assertWidthIsAtLeast`, `assertHeightIsAtLeast`, `assertPositionInRootIsEqualTo`, `assertTopPositionInRootIsEqualTo`, `assertLeftPositionInRootIsEqualTo`, `assertTouchWidthIsEqualTo`, plus `getBoundsInRoot`, `getUnclippedBoundsInRoot` and `getAlignmentLinePosition`. The tolerance "Defaults to the tolerance configured in ComposeUiTestConfig, or `0.5.dp` if unspecified". The old overloads without tolerance are deprecated with "Use assertWidthIsEqualTo with tolerance". File last changed 2026-09-02 - [BoundsAssertions.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/BoundsAssertions.kt)

**cypress-layout-inspector (msmps/cypress-layout-inspector)**
- [Docs] Chai extensions for Cypress, based on `getBoundingClientRect()`: `cy.get(".block-2").should("be.rightOf", ".block-1", 50);`. Vocabulary: `horizontallyAligned(el, 'top'|'bottom'|'centered'|'all')`, `verticallyAligned(el, 'left'|'right'|'centered'|'all')`, `overflowing('vertically'|'horizontally'|'any')`, `overlapping(el)`, `width(n)`/`height(n)` chainable with `.gt`, `.within`, `.lt`, `rightOf`/`leftOf`/`above`/`below(el, distance)` (distance >= 0 if not given), `inside(el, {top, left, bottom, right})`, `style(prop, value)`. Config: `cy.configureLayoutInspector({ excludePadding: true, threshold: 5 })` - [cypress-layout-inspector README](https://github.com/msmps/cypress-layout-inspector)
- [Registry] v1.7.0, 2022-12-05 (Cypress 11 support). Last commit 2022-12-05. 14 stars. MIT - [npm cypress-layout-inspector](https://www.npmjs.com/package/cypress-layout-inspector); [repo](https://github.com/msmps/cypress-layout-inspector)

**Playwright layout selectors (locating, not asserting)**
- [Docs] `:right-of()`, `:left-of()`, `:above()`, `:below()` ("at any vertical/horizontal position") and `:near()` ("within 50 CSS pixels"). All take an optional maximum pixel distance as the last argument: `button:near(:text("Username"), 120)`. They "use bounding client rect". The docs say "Layout selectors are deprecated and may be removed in the future" and "Matching based on layout may produce unexpected results" - [Playwright other locators](https://playwright.dev/docs/other-locators)

**LayoutTest-iOS (linkedin/LayoutTest-iOS)**
- [Docs] Lays out a view many times, over combinations generated from a data spec (`@"text": [[LYTStringValues alloc] init]`, `@"showButton": [[LYTBoolValues alloc] init]`) and over several view sizes. It runs user validations plus automatic checks for Auto Layout errors, missing accessibility and "overlapping views" - [LayoutTest-iOS README](https://github.com/linkedin/LayoutTest-iOS)
- [Registry] Release 6.1.0, 2024-05-02. Last commit 2024-05-03. Apache-2.0. Objective-C/Swift - [LayoutTest-iOS repo](https://github.com/linkedin/LayoutTest-iOS)

**ReDeCheck (redecheck/redecheck, research tool)**
- [Docs] Extracts the layout at many viewport widths and analyses how it changes to find five failure types: element collision, element protrusion (overflowing a parent), viewport protrusion, small-range layouts (a layout that holds only for a few pixels of width) and wrapping elements - [ReDeCheck README](https://github.com/redecheck/redecheck)
- [Docs] Reports name elements by XPath and give width ranges, with highlighted screenshots: `ELEMENTS /HTML/BODY/DIV/DIV[2]/DIV/DIV[2] AND /HTML/BODY/DIV/DIV[2]/DIV/DIV/FORM/DIV ARE OVERLAPPING BETWEEN 768 AND 1132`. Relation sets between element pairs are printed as tokens such as `{belowleftAlignrightAlignxMidAlign}` versus `{...overlapping}` for widths 990 to 991, against neighbouring ranges 320 to 989 and 992 to 1400 - [ReDeCheck README](https://github.com/redecheck/redecheck)
- [Registry] Last commit 2018-11-24. No releases. 18 stars. MIT. Java/Maven plus Selenium - [ReDeCheck repo](https://github.com/redecheck/redecheck)

**jsdom-based matchers**
- [Docs] jsdom "does not do any layout or rendering". Layout-related properties such as `getBoundingClientRect()` and `offsetTop` return dummy values, often zeros. So jest-dom-style matchers (for example `toHaveStyle`) can check declared styles but not geometry - [jsdom README](https://github.com/jsdom/jsdom). jest-dom v7.0.1, 2026-08-09, MIT - [jest-dom releases](https://github.com/testing-library/jest-dom/releases)

### Inferences
- Espresso's split between completely and partially is a clean definition worth copying: completely-left-of means `a.right <= b.left` (touching allowed), partially-left-of means overlapping with a starting first. Galen and cypress-layout-inspector do not make this split explicit.
- Espresso's exact integer equality for alignment works on Android's integer pixels but would break on the web's fractional layout. Compose's later move to an explicit tolerance (0.5dp default, old API deprecated) is evidence that exact-equality layout assertions caused trouble.
- ReDeCheck's relation sets per element pair, tracked as width ranges, are close to onionskin's "facts at a granularity" idea extended across widths. A fact that holds only over a tiny width range is itself a useful warning.
- LayoutTest-iOS and ReDeCheck show the value of spec-free invariants (no unintended overlap, no overflow of the parent or the viewport). These could ship as default onionskin expectations.
- Playwright deprecating layout selectors is a warning about using geometry to identify elements. Onionskin should select elements by stable identity (selectors, test ids, roles) and use geometry only in facts.

### Gaps
- Google's "Fighting Layout Bugs" (an automatic layout-bug detector for WebDriver) survives only as Google Code exports on GitHub (for example [knorrium/fighting-layout-bugs](https://github.com/knorrium/fighting-layout-bugs), last pushed 2015-04-21). I did not read its contents.
- I did not check iOS snapshot tools that record the view hierarchy as text (for example pointfreeco/swift-snapshot-testing, release 1.19.7 on 2026-10-21, per [its repo](https://github.com/pointfreeco/swift-snapshot-testing)), so whether they offer a recursive-description strategy is not confirmed here.
- Academic systems (Cassius, VizAssert, the ReDeCheck papers) are left to the academic-literature notes.

## Design choices worth borrowing, and choices that caused problems

### Takeaway
Worth borrowing: a small declarative relation vocabulary with sides, ranges and `~` approximation (Galen); relative descriptor arithmetic and failure messages that name the source of the expected value (Quixote); completely/partially semantics (Espresso); explicit default tolerance (Compose, Quixote); mutation testing of specs and spec generation from a dump (Galen 2.4); a stated property whitelist with configured normalisation (sosie); and capture of states as well as pages (StyleProof). Problems recorded: long hand-written specs that people abandon (Galen), the slowness and brittleness of Selenium end-to-end runs (Galen, Hardy), noise in pixel comparison and full-page baselines (PhantomCSS, Wraith), exact equality on fractional geometry, and dependence on a dead runtime (PhantomJS).

### Cited Findings
**Worth borrowing**
- Relative relations with ranges and per-device tags, in a short declarative form, are Galen's core and what reviewers valued - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/); [Thoughtworks Radar: Galen](https://www.thoughtworks.com/radar/tools/galen)
- Higher-level rules ("aligned horizontally next to each other with equal distance", "rendered in 2 column table layout") as the cure for long specs - [galen-extras README](https://github.com/galenframework/galen-extras); [TestGuild #144](https://testguild.com/podcast/144-automated-responsive-design-testing-using-galen-ivan-shubin/)
- Mutation testing to show which objects a spec does not constrain, and spec generation from a page dump - [Galen 2.4 release post](http://galenframework.com/post/2018-11-03-released-version-2.4-spec-generator-and-mutation-testing/)
- Failure messages that give direction, size and the origin of the expected value ("should be 13px higher ... (bottom edge of '#header')") - [Quixote README](https://github.com/jamesshore/quixote)
- A built-in default tolerance near half a pixel: 0.5px in Quixote, 0.5dp in Compose - [Quixote QElement](https://github.com/jamesshore/quixote/blob/release/docs/QElement.md); [Compose BoundsAssertions.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/BoundsAssertions.kt)
- Separate "completely" and "partially" relations with exact edge semantics - [Espresso PositionAssertions.java](https://github.com/android/android-test/blob/main/espresso/core/java/androidx/test/espresso/assertion/PositionAssertions.java)
- An explicit whitelist as the trust boundary, normalisation as a pure configured step, and tree matching that tolerates inserted or removed wrapper elements - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- Capturing forced `:hover`/`:focus`/`:active` and open states at each width, and failing when a declared surface was not captured - [StyleProof README](https://github.com/BenSheridanEdwards/StyleProof)
- Region-scoped strictness, for example a strict page with layout-only regions over dynamic content - [Applitools match levels](https://applitools.com/docs/eyes/concepts/best-practices/match-levels)
- Spec-free invariants across widths (collision, protrusion, small-range layouts, wrapping) - [ReDeCheck README](https://github.com/redecheck/redecheck); [LayoutTest-iOS README](https://github.com/linkedin/LayoutTest-iOS)

**Caused problems**
- Galen: specs of several hundred lines become unmaintainable and get thrown away (author). "Typical brittleness and speed issues" of end-to-end runs (Thoughtworks) - [TestGuild #144](https://testguild.com/podcast/144-automated-responsive-design-testing-using-galen-ivan-shubin/); [Thoughtworks Radar: Galen](https://www.thoughtworks.com/radar/tools/galen)
- Galen: the `css` spec ties tests to CSS, and Galen's own guide says to use it sparingly - [Galen Spec language guide](http://galenframework.com/docs/reference-galen-spec-language-guide/)
- Hardy and Galen depend on a Selenium server. Shore cited this, and Hardy's custom step syntax, as reasons to write Quixote - [Announcing Quixote](https://www.letscodejavascript.com/v3/blog/2014/09/announcing_quixote)
- Pixel diffs: anti-aliasing false failures across machines, full-page images that fail everywhere after one small change, slower than DOM assertions, and repository growth from baseline images - [PhantomCSS README](https://github.com/HuddleEng/PhantomCSS)
- Pixel diffing is "both too strict" and "not structural", and CSS diffing flags the changes a refactor is meant to make (the sosie author's assessment) - [sosie design doc](https://github.com/cuihtlauac/sosie/blob/main/sosie-design.md)
- Runtime dependence: PhantomCSS stopped because PhantomJS was replaced by headless Chrome. Hardy bundled PhantomJS 1.9 and Selenium 2.32 - [PhantomCSS README](https://github.com/HuddleEng/PhantomCSS); [Hardy README](https://github.com/thingsinjars/Hardy)
- Geometry as an element locator is unreliable: Playwright deprecated its layout selectors - [Playwright other locators](https://playwright.dev/docs/other-locators)
- Exact equality without tolerance was replaced in Compose by tolerance-taking overloads - [Compose BoundsAssertions.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/BoundsAssertions.kt)

**Maintenance summary (verified 2026-11-03 via GitHub API, npm, Maven Central, opam)**

| Tool | Last release (date) | Last commit | State | License | Runtime |
|---|---|---|---|---|---|
| Galen | 2.4.4 (2019-03-15 GitHub; Maven 2019-03-10) | 2019-03-10 | inactive, not archived | Apache-2.0 | Java/Selenium |
| Quixote | 1.0.1 (npm 2020-04-22) | 2020-04-22 | inactive | MIT | browser JS |
| Hardy | 1.1.0 (npm 2013-11-13), tag v1.2.0 | 2014-10-28 | inactive | MIT | Node + Selenium |
| css-wrangler | 0.12.0 (npm 2018-02-10) | 2018-02-10 | inactive | MIT | Node + Selenium |
| computed-style-diff | 1.0.3 (npm 2018-08-14) | pushed 2018-08-16 | inactive | MIT | browser JS |
| StyleProof | 9.0.0 (2026-10-29) | 2026-11-03 | active, 3 stars | MIT | Node 22 + Playwright |
| sosie | 0.1.2 (opam 2026-08-24) | 2026-08-24 | new, 1 star | MIT | OCaml / js_of_ocaml |
| CSS Critic | 2.0.1 (2022-01-21) | 2023-01-28 | inactive | MIT (npm) | browser JS |
| PhantomCSS | 1.6.0 (npm 2018-12-10) | 2018-12-06 | archived, unmaintained since 2017-12-22 | MIT | CasperJS/PhantomJS |
| Wraith | v4.2.4 (2019-06-26) | 2026-01-16 (archive note) | archived | Apache-2.0 | Ruby |
| BackstopJS | 6.3.25 (npm 2024-09-07) | 2026-09-08 | maintained, slow releases | MIT | Node |
| reg-suit | v0.14.6 (2026-03-16) | 2026-03-16 | maintained | MIT | Node/TS |
| Lost Pixel | v3.22.0 (2024-11-14) | 2026-04-22 | archived, sunset (team joined Figma) | MIT | Node/TS |
| Playwright | v1.64.1 (2026-10-14) | 2026-11-03 | active | Apache-2.0 | Node and others |
| cypress-layout-inspector | 1.7.0 (2022-12-05) | 2022-12-05 | inactive | MIT | Cypress |
| ReDeCheck | none | 2018-11-24 | inactive research tool | MIT | Java/Selenium |
| LayoutTest-iOS | 6.1.0 (2024-05-02) | 2024-05-03 | low activity | Apache-2.0 | ObjC/Swift |
| Espresso PositionAssertions | part of androidx.test | file changed 2023-01-07 | maintained platform API | Apache-2.0 | Android |

Sources for the table: the per-tool registry findings above, for example [Galen releases](https://github.com/galenframework/galen/releases), [npm quixote](https://www.npmjs.com/package/quixote), [npm styleproof](https://www.npmjs.com/package/styleproof), [opam sosie](https://opam.ocaml.org/packages/sosie/), [npm backstopjs](https://www.npmjs.com/package/backstopjs), [reg-suit repo](https://github.com/reg-viz/reg-suit), [Lost Pixel repo](https://github.com/lost-pixel/lost-pixel).

### Inferences
- No maintained open-source tool combines symbolic relation expectations (Galen-style) with two-version fact comparison (sosie/StyleProof-style). The field split into dead spec-language tools and new computed-style diff tools. Onionskin would sit between them.
- Onionskin's choice to capture once and then check many expectations against stored facts addresses the speed criticism aimed at Galen and Hardy, which drove the browser for every check. Capture still needs a real browser, so the cost moves to capture rather than disappearing.
- Galen's main maintenance problem was spec length. Onionskin can reduce it with generated baseline facts (as in `galen generate`), with rule-like combinators over element groups (as in galen-extras), and by asking users to write only the expectations they care about.
- For the "prove nothing changed" use, sosie's asymmetry (missed regressions are fatal, spurious diffs are tolerable) conflicts with onionskin's "rarely break" goal for hand-written expectations. Onionskin may need two modes with different defaults: lenient symbolic expectations, and a strict equivalence comparison.
- Tolerance defaults found: 0.5px (Quixote), 0.5dp (Compose), the `~` approximation of 2 or 3px (Galen), a threshold of 5px in the cypress-layout-inspector example. None of the sources justify their numbers with data.

### Gaps
- I found no published measurements of flake rates or run times for Galen, Quixote or the computed-style tools. The speed and brittleness claims are qualitative.
- I found no user reports on StyleProof or sosie in practice. Both are months old and have one to three stars.
- No source compares symbolic layout assertions with pixel tools on the same project.
