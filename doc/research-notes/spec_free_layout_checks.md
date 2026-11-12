# Spec-free layout checks for rendered web pages: detection rules, measured accuracy and how tools tell intent from defect (as of November 2026)

Source labels: [PR-text] peer-reviewed paper, full text read in this session; [PR-abs] peer-reviewed paper, abstract or project page only; [preprint] arXiv preprint, not peer-reviewed as cited; [thesis]; [spec] W3C or CSSWG text; [docs] vendor or tool documentation; [code] source code read in a public repository; [blog]; [snippet] taken from a search-result summary only, so unconfirmed. "Computed" marks a figure that I derived from a paper's table. The earlier notes in `doc/research-notes/layout_failure_detection_research.md` already cover the RLG model, GWALI's layout graph and heuristics, IFix, MFix, WebSee and ReDeCheck's pixel tolerances; those are not repeated here.

What onionskin captures today (read from `src/dk/simongray/onionskin/impl/capture.js`): per element a DOM path, one box from `getBoundingClientRect` in page coordinates, and computed style values. It does not capture `scrollWidth`/`clientWidth`, text boxes from `Range.getClientRects`, or hit-test results. Several checks below need those.

## 1. ReDeCheck's five failure types: exact rules, counts per type, causes of false positives

### Takeaway
ReDeCheck never judges one rendering alone. Each rule compares a layout with the same page at the next wider viewport, and the authors state the intent heuristic in one sentence: elements that always overlap are probably meant to; elements that overlap only at some widths are probably not. On 26 live pages it produced 328 reports, of which 197 were true positives, 48 false positives and 83 "non-observable issues" (true at the level of boxes, invisible to a person). The three overlap-based types had zero false positives but 70% non-observable reports; the two alignment-based types (small-range, wrapping) had all the false positives.

### Cited Findings

Citations
- [PR-text] Walsh, Kapfhammer, McMinn. "Automated Layout Failure Detection for Responsive Web Pages without an Explicit Oracle". ISSTA 2017, pp. 192-202. [DOI 10.1145/3092703.3092712](https://doi.org/10.1145/3092703.3092712); [accepted version PDF](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf). This is the paper with the per-type rules and counts. The STVR 2020 paper by the same authors (DOI 10.1002/stvr.1748) is about regression checking between two versions and does not report oracle-free counts.
- [thesis] Walsh. "Automatic Identification of Presentation Failures in Responsive Web Pages". PhD thesis, University of Sheffield, 2018. [record](https://etheses.whiterose.ac.uk/22104/). The PDF is over 10 MB and could not be read in this session.
- [code] ReDeCheck, last commit d3f2364 on 24 Nov 2018 (GitHub API; the repository's last push of any kind was 28 Mar 2023). Unmaintained. [RLGAnalyser.java](https://github.com/redecheck/redecheck/blob/d3f2364960aa7398f7085a502576da70759f5ed0/src/main/java/shef/analysis/RLGAnalyser.java)

Model the rules run on
- Containment is geometric, not from the DOM tree: "An element e2 is said to be contained within e1 if the bounding rectangle of e2 is inside that of e1. An element e2 is a child of e1 if there is no other element eo containing e2 also contained by e1." Siblings share a parent. Each edge carries alignment constraints `(amin, amax, t, P)` with `t` in {parent-child, sibling} and a set `P` of attributes over an inclusive width range. — [ISSTA 2017, sec. 3.1](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- An element counts as displayed when it is "present in the DOM, with visibility property set to 'true' and an opacity greater than zero". — [ISSTA 2017, sec. 3.1](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- The stated oracle: "If the elements always overlap, the effect is likely intended and/or easily noticed by a developer in a manual spotcheck. If the elements overlap infrequently, however, a subtle RLF is likely to be occurring." — [ISSTA 2017, sec. 1](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)

Rule per type (Algorithms 1 to 4, sec. 3.2 of [ISSTA 2017](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf))
- Element collision (Algorithm 1, steps 1-5): for every sibling constraint that has the overlap attribute "O" over `amin..amax`, fetch the constraint for the same pair at width `amax + 1`. If one exists and it has no "O", report a collision for `amin..amax`.
- Element protrusion (Algorithm 1, steps 6-10): otherwise (the pair overlaps as siblings, and it was not a collision), get each element's ancestors at `amax + 1`. If one element is an ancestor of the other at that wider width, report an element protrusion. In words: a child that was inside its container at the wider width now overlaps it as a sibling.
- Viewport protrusion (Algorithm 2): for each element other than `body`, collect the parent-child constraints in which it is the child, sort them by lower bound, and find gaps in the width range. If the element is visible at widths inside a gap, it has no parent there, so it lies outside `body`; report those widths. The paper explains that an element which overflows the viewport "has no parent in the rRLG, as it is no longer contained within the rectangle defined by the body element".
- Small-range layout (Algorithm 3): for each constraint with `amax - amin <= thres`, check that the same pair has a constraint at `amin - 1` and at `amax + 1` with different attributes; if so report. `thres = 5` in the experiments. The code uses `(max - min) < 5`. — [RLGAnalyser.java](https://github.com/redecheck/redecheck/blob/d3f2364960aa7398f7085a502576da70759f5ed0/src/main/java/shef/analysis/RLGAnalyser.java)
- Wrapping (Algorithm 4): for each element `e`, take its children and the sibling constraints among them, and split the width range into consecutive ranges. Two children are "in a row" when their constraint has L or R (left-of, right-of) and neither A nor B (above, below). Report child `c` as wrapped in the narrower range when the row there has at least 2 members (`|IRc| >= 2`), `c` is not in it, and `c` is in the row of the adjacent wider range. The prose says "more than two elements"; the pseudocode says `>= 2`.

Extra conditions that exist only in the code — [RLGAnalyser.java](https://github.com/redecheck/redecheck/blob/d3f2364960aa7398f7085a502576da70759f5ed0/src/main/java/shef/analysis/RLGAnalyser.java)
- Viewport protrusion: an element with no parent constraint at any width is skipped; the comment says it "is always outside the viewport and therefore not reported as a failure". This exempts off-canvas elements that are never on screen.
- A method `filterOutDuplicateReports` removes collisions whose intersection is exactly 1 px wide or high ("simply due to rounding errors") and protrusions that stick out by exactly 1 px on a side. The call to it in `analyse()` is commented out in the last commit, so it does not run.
- Wrapping: the code also requires that the row lost exactly one member, that the wrapped element is visible, that it is still inside the parent, and that it is now below the rest of the row.

Evaluation set-up — [ISSTA 2017, sec. 4.1-4.2](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- 26 live pages (25 picked at random from randomusefulwebsites.com plus ConsumerReports), 41 to 1469 HTML elements. Widths 320 to 1400 px. Firefox driven by Selenium.
- Classification by the first author, reviewed by another author. TP: a failure "evident from viewing the web page at one of the reported viewport widths". FP: "no actual identifiable problem — either visually in the design of the web page, or at the level of the DOM". NOI: no observable problem, but "elements that have collided or protruded their containers or the viewport at the co-ordinate level — yet since their edges are transparent or invisible, no observable issue is apparent".

Counts per type, 26 pages (Table 1b, "Total" row) — [ISSTA 2017](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)

| Type | TP | FP | NOI | Reports |
|---|---|---|---|---|
| Element collision | 8 | 0 | 24 | 32 |
| Element protrusion | 3 | 0 | 36 | 39 |
| Viewport protrusion | 24 | 0 | 23 | 47 |
| Small-range | 152 | 43 | 0 | 195 |
| Wrapping | 10 | 5 | 0 | 15 |
| All | 197 | 48 | 83 | 328 |

- The 197 true positives are 33 distinct failures on 16 of the 26 pages; the reports cover 137 distinct viewport ranges, so "no more than an average of 4.2 different viewport widths" need a look per real failure. — [ISSTA 2017, sec. 4.4](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- 147 of the 152 small-range true positives come from one page (AccountKiller, a grid of icons) and "all corresponded to a single distinct failure". — [ISSTA 2017, sec. 4.4](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- No recall figure: "Our appraisal of the prototype tool does not include the possibility of false negatives". Manual spot checks at tool presets found 66% to 81% of the 33 failures and no failure that ReDeCheck missed. — [ISSTA 2017, sec. 4.4-4.5, Table 2](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)

Causes the authors give — [ISSTA 2017, sec. 4.4](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- Small-range false positives: "coincidental alignment attributes"; for example an element with no particular alignment may "for a small-range appear to be center justified, due to chance co-ordinate values".
- Wrapping false positives: "a set of three elements had been misclassified as a 'row'".
- Non-observable collisions: the overlap "was often significant in the DOM, due to high degrees of invisible padding in the page's CSS".
- Non-observable protrusions: "often non-observable due to HTML elements having the 'overflow:hidden' CSS property set". The authors still call these useful, like lint warnings, because a content change can make them visible.

Later data on the same detector
- [PR-text] Althomali, Kapfhammer, McMinn. "Automated visual classification of DOM-based presentation failure reports for responsive web pages". STVR 31(4), e1756, 2021. [DOI 10.1002/stvr.1756](https://doi.org/10.1002/stvr.1756); [PDF](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf). Table III(b) gives a manual classification of ReDeCheck's reports on 20 further pages:

| Type (20 new pages) | TP | NOI | FP | Reports |
|---|---|---|---|---|
| Element collision | 2 | 10 | 0 | 12 |
| Element protrusion | 0 | 51 | 0 | 51 |
| Viewport protrusion | 8 | 2 | 0 | 10 |
| Wrapping | 4 | 2 | 11 | 17 |
| Small-range | 0 | 0 | 53 | 53 |

- "ReDeCheck reported a total of 53 small-range failures from the additional set that were all FP failures, suggesting the need for improvements to ReDeCheck's small-range detector." — [STVR 2021, RQ4(c)](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- Wrapping false positives on the new pages: four cases "were never observed to form a row"; text links in a footer that the developer clearly means to wrap; an element that developer rules hide when the page is resized; three icons that wrap into a pleasing formation. — [STVR 2021, RQ4(b) and sec. 4.6](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- Three element-protrusion reports were not true even at box level: "there was no protrusion at the DOM level". The authors blame ReDeCheck's collection of boxes by injected JavaScript, because Selenium reported no protrusion. — [STVR 2021, RQ1](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- For 35 viewport-protrusion reports ReDeCheck gave a wrong upper bound of the range because it samples widths at intervals; setting the interval to 1 fixed it. — [Althomali et al., ICST 2019, sec. IV-B](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- An independent 2026 measurement: run at 320 px only and scored against hand-labelled reflow problems (lost information or lost function) on 24 pages, ReDeCheck had 7.29% precision and 4.23% recall. The task differs from the one ReDeCheck was built for, so this shows how little its reports say about content loss, not how good its geometry is. — [He et al., ReFLAIR, FSE 2026, Table 2](https://ics.uci.edu/~seal/projects/ReFLAIR/ReFLAIR.pdf)

### Inferences
- Share of reports a person can see, computed from the two tables above (45 pages, StumbleUpon excluded as in STVR 2021): collision 9 of 43 (21%); element protrusion 3 of 90 (3%); viewport protrusion 32 of 57 (56%); wrapping 14 of 31 (45%); small-range 152 of 248 (61%), but 5 of 96 (5%) once the one AccountKiller grid is left out.
- Ranking by evidence: viewport protrusion is the only ReDeCheck type that is right more often than wrong and has no box-level false positives. Element protrusion as defined (child box leaves parent box) is almost never visible. Small-range and wrapping are unreliable outside the first sample.
- Borrow: the cross-width intent test ("overlaps at some widths but not at the adjacent wider width") as the default filter for any overlap or protrusion finding. onionskin already tags facts with width ranges (design choice 12), so this is a set comparison.
- Borrow: report distinct failures and distinct width ranges, not one report per element pair. The 147-for-1 case shows why; group by nearest common ancestor and width range.
- Borrow: the three-way verdict TP / FP / NOI. An "invisible but real at box level" class maps well onto a lower severity.
- Avoid: geometric parenthood. The viewport-protrusion rule depends on "has no containing box", which breaks for positioned elements and for a `body` that is itself wider than the viewport (both noted in the earlier notes). Use the DOM parent and the viewport rectangle directly.
- Avoid: shipping small-range and wrapping checks as "failures". If offered at all they should be informational.
- Avoid: reading boxes two different ways. The three DOM-level false positives came from two extraction methods that disagreed.

### Gaps
- Per-page rows of ISSTA Table 1b lost their column alignment in text extraction; only the totals row and the per-page rows reprinted in STVR 2021 Table III(a) were used.
- Walsh's thesis may contain per-type detail (for example a fuller list of false-positive causes); it could not be fetched (size limit).
- No source measures recall of any of the five rules against an independent ground truth of layout defects.
- No source varies the thresholds (the 5-width small-range threshold, the 1 px overlap rule) and reports the effect on precision.

## 2. VISER, VERVE and later work that checks reports against pixels; which CSS makes a box overlap invisible

### Takeaway
VISER and VERVE take each ReDeCheck report, hide the involved elements one at a time by setting `opacity` to 0, screenshot the "area of concern" and call the report visible only if the pixels differ between layers. On the published samples 70% to 86% of collision and protrusion reports were not visible. The causes the papers name are transparent backgrounds, borders or padding in the page's background colour, `overflow: hidden` on the container, and empty or zero-size elements. No paper measures z-index, `pointer-events` or `clip-path` separately.

### Cited Findings

Citations
- [PR-text] Althomali, Kapfhammer, McMinn. "Automatic Visual Verification of Layout Failures in Responsively Designed Web Pages". ICST 2019, pp. 183-193. [DOI 10.1109/ICST.2019.00027](https://doi.org/10.1109/ICST.2019.00027); [accepted version PDF](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf). Tool: [redecheck/viser](https://github.com/redecheck/viser), last push 17 Apr 2019, unmaintained.
- [PR-text] Althomali, Kapfhammer, McMinn, STVR 2021 (VERVE), cited above. Tool: [verve-tool/verve](https://github.com/verve-tool/verve), last push 1 Sep 2022, unmaintained.
- [thesis] Althomali. "Automated Classification and Repair of Presentation Failures in Responsive Web Pages". University of Sheffield, 2022. [record](https://etheses.whiterose.ac.uk/31157/). Covered in the earlier notes; not re-read.

How much of the DOM-based output is invisible
- Manual classification of the 117 collision and protrusion reports on 20 pages: collision 7 TP and 24 NOI; element protrusion 3 TP and 36 NOI; viewport protrusion 24 TP and 23 NOI. That is 83 of 117 (71%) non-observable. — [ICST 2019, Table II](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- On 20 further pages: 63 of 73 collision and protrusion reports were non-observable (10 of 12, 51 of 51, 2 of 10). — [STVR 2021, Table III(b)](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- This resolves the "118 reports from 26 pages" versus "117 from 20 subjects" inconsistency flagged in the earlier notes: ISSTA 2017 has 118 such reports (8+24, 3+36, 24+23); VISER dropped StumbleUpon and its one collision true positive because the page archive could not be rebuilt. — [ICST 2019, sec. IV-A](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)

What the visual step does — [ICST 2019, sec. III](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- A DOM filter first re-reads the boxes and marks the report a false positive if the overlap or protrusion is not there. VERVE adds: every reported element must have non-zero width and height and be reachable by scrolling ("Negative coordinates are inherently unreachable by scrolling"); for protrusions the protruding part must have non-zero width and height. — [STVR 2021, sec. 3](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- Area of concern: for a collision, the part of the front element that lies over the back element; for a protrusion, the overlapped part and the part outside the container, tested separately.
- Layers: set both elements to `opacity: 0`, screenshot; restore the back element, screenshot; restore the front element, screenshot. A collision is visible only if the empty layer differs from both of the other two. For the part outside the container, compare with and without the front element.
- Why opacity: "removing elements can impact the layout of the remaining HTML elements ... By instead manipulating the element's opacity, the element is still 'there' as far as the layout is concerned".
- Any pixel difference counts; there is no threshold for the three overlap types.
- The default inspection width is the narrowest in the reported range.

Agreement with the human labels
- VISER: 87.2% overall at the narrowest width (collision 93.5%, element protrusion 84.6%, viewport protrusion 85.1%), 82.9% at the middle and 77.8% at the widest. All drops were viewport protrusions, most of them cases whose visibility really changes with width. Run time is a few seconds per report at most. — [ICST 2019, Table I, Fig. 6](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- The 15 disagreements: 7 "subjective" (visible in pixels but "almost imperceptible"; two were changes of two pixels, which the humans had called non-observable); 2 "obscured" (ReDeCheck named a less specific element than the one a person saw); 3 viewport protrusions that VISER got wrong; 3 element protrusions that were false positives at box level. — [ICST 2019, sec. IV-D](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- One VISER miss was caused by clipping: "This is due to the overflow property of the protruding element being set as hidden. The protruding content could therefore not be 'seen'". A person counted it as a failure because the content was cut off. Another miss: content overflowed into a blank area, so pixels did not change where VISER looked, yet "a human had to scroll horizontally to read the overflowing content". — [ICST 2019, sec. IV-D](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- VERVE: 86.3% on the first pages and 91.8% on the 20 new pages for the three overlap types. Wrapping: 78.6% on the first pages, 35.3% on the new pages, 64.7% after a stricter DOM filter (question 8). — [STVR 2021, RQ1, RQ2, RQ4, sec. 4.6](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- The human labels are themselves soft. VERVE's authors found the earlier labels "were subject to some 'exemptions' based on the severity of a change" and that deciding whether "n pixels of overlap are negligible" stays "to a great extent, subjective". — [STVR 2021, sec. 4.6](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)

Which properties make a box overlap invisible, as named in the sources
- Transparent backgrounds with content that does not overlap: "while the bounding boxes of two elements may overlap on a page, their backgrounds may be transparent and their respective content non-overlapping". — [ICST 2019, sec. I](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- Borders or padding in the page's background colour: "only the borders of the elements are overlapping — and they are the same color as the background". — [ICST 2019, sec. II-D](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf); "high degrees of invisible padding" — [ISSTA 2017, sec. 4.4](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- `overflow: hidden` on the container, which clips the protruding part. — [ISSTA 2017, sec. 4.4](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- An element with no content and no paint: "if the misplaced element has no content and is transparent, the RLF will not be detectable by a human"; an invisible "spacer" element that wraps. — [STVR 2021, sec. 3, 3.2](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- `visibility` and `opacity: 0` are removed before detection, in the model's definition of a displayed element. — [ISSTA 2017, sec. 3.1](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- Browser-side tests for the same properties: `Element.checkVisibility()` returns false for no box (`display: none`, `display: contents`) or `content-visibility: hidden`, and on request for `opacity: 0` and `visibility: hidden`. MDN warns that "Elements outside the viewport or occluded by other content may still return true". Widely available since March 2024. — [docs] [MDN checkVisibility](https://developer.mozilla.org/en-US/docs/Web/API/Element/checkVisibility)
- `pointer-events` changes hit testing but not paint: the CSSOM View draft notes that `elementFromPoint` need not return the top-most painted element because `pointer-events` can exclude elements. — [spec] [CSSOM View, Editor's Draft 5 Nov 2026](https://drafts.csswg.org/cssom-view/)
- Playwright treats `opacity: 0` elements as visible ("Elements with `opacity:0` are considered visible") and tests occlusion separately by checking that the element "is the hit target of the pointer event at the action point". — [docs] [Playwright actionability](https://playwright.dev/docs/actionability)

### Inferences
- A box-only overlap check will be wrong about visibility roughly three times out of four on real pages. This is the measured basis for design choice 13.
- Borrow: the layered test, but only as an opt-in confirmation step, because it changes the page and needs screenshots. The cheaper version needs no pixels: compare text boxes and replaced-content boxes (images, inputs) instead of element boxes, since the main cause of invisible overlap is transparent or background-coloured box area around content.
- Borrow: VERVE's DOM filter as pre-conditions on any finding: non-zero size, reachable by scrolling (no negative page coordinates), and the overlap or protrusion re-measured to be greater than zero.
- Borrow: treat `overflow: hidden` or `clip` on the container as changing the finding, not cancelling it. A protrusion under `overflow: visible` is "content spills out"; under `hidden` it is "content may be cut off". VISER's miss shows that people count the second as a defect when text is lost.
- Avoid: an "any pixel differs" rule. Seven of fifteen disagreements were differences too small for people to care about. Require a minimum overlap area or depth.
- Avoid: inspecting at one arbitrary width. Visibility of the same finding changed across its range; the narrowest width agreed best with people.
- z-index, stacking contexts, `clip-path`, masks and `mix-blend-mode` can each hide or reveal an overlap, but only a pixel or hit test accounts for them; a rule over computed styles would have to re-implement paint order.

### Gaps
- No paper reports how often each property (transparent background, same-colour padding, `overflow: hidden`, z-index) was the cause; the papers give examples, not counts.
- No work was found that re-checks reports with hit testing (`elementsFromPoint`) instead of screenshots and measures the agreement with people.
- VERVE's histogram thresholds for small-range reports were tuned on the same pages they were evaluated on ("retrospective" thresholds); they are not a transferable rule.

## 3. Internationalisation and text expansion: how overflow, truncation and overlap from longer text are found

### Takeaway
Almost all work in this area compares the page with itself under a changed setting (another language, a larger font or display scale), so it needs no developer-written baseline but is not single-rendering either. The rules are: a relation changed (GWALI, ITDroid), an overlap appears that was absent at the default (AccessiText), text is ellipsised or larger than its view in a locale but not in the default (Android Studio), or a view's text did not grow as expected (AccessiText, dVermin). Reported precision is 73% to 100% depending on type; cropped text is the hardest.

### Cited Findings
- [PR] GWALI (Alameer, Mahajan, Halfond, ICST 2016, [DOI 10.1109/ICST.2016.36](https://doi.org/10.1109/ICST.2016.36)) and IFix are in the earlier notes: baseline language versus translated page, text as separate nodes, 91% precision and 100% recall on 54 pages. The comparison in that paper with Fighting Layout Bugs (an implicit-oracle tool, question 6) could not be re-verified: the author PDF gave a certificate error in this session. The earlier notes read "73% precision and 55% recall" for it from a damaged table; treat as unconfirmed.
- [PR-text] Alshayban, Malek. "AccessiText: Automated Detection of Text Accessibility Issues in Android Apps". ESEC/FSE 2022. [PDF](https://www.ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf). Runs each screen at the default text size (setting S) and with text scaling on (S'), then applies five rules — [sec. 4](https://www.ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf):
  - Unresponsive view: the text height found by OCR inside the view's box is the same under S and S'.
  - Missing view: a view present under S is not found under S', including after scrolling.
  - Overlapping views: "two views ... overlap each other under setting S' but not under S. Intentional overlapping elements such as Floating Action Button (FAB), or overlapping views that are part of the original design are ignored".
  - Cropped view: OCR text height under S' is smaller than the height under S times the scale factor by more than a configurable threshold.
  - Truncated view: OCR text of the view is compared with the `text` property; flagged only "If the first word is truncated". The default keeps at least one whole word before calling truncation a defect.
- AccessiText results on 30 apps (Table 2): unresponsive 58 detected, 98% precision, 100% recall; missing 31, 80.64%, 100%; overlapping 122, 89.34%, 100%; cropped 67, 73.13%, 94.23%; truncated 11, 100%, 84.61%; total 289, 88.27%, 95.76%. Cropped-view false positives are "mainly caused by inaccurate results returned by the OCR tool"; low-contrast text is hard to locate. — [AccessiText, sec. 5](https://www.ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf)
- [PR-abs] Su, Chen, Wang, Liu, Wang, Li, Wang. "The Metamorphosis: Automatic Detection of Scaling Issues for Mobile Apps" (dVermin). ASE 2022. [arXiv 2212.04388](https://arxiv.org/abs/2212.04388). Detects text truncation and component overlap after the user enlarges font or display size by checking each view for an inconsistency "under the default and a larger display scale". 97% precision and 97% recall for pages with an issue, 84% and 91% for the views; 21 new issues in F-Droid apps, 20 confirmed or fixed. Abstract only.
- [code] Android Studio's visual lint `LocaleAnalyzer` (Apache 2.0, in the actively maintained JetBrains mirror, last push 11 Nov 2026): renders the layout in the base configuration and in each locale. It reports "The text is ellipsized in locale X but not in default locale" when any line of a `TextView` has an ellipsis count above zero in the locale and none in the base. It reports "The text might be cut off" when `lineHeight * lineCount > view height` or the widest line's text bounds exceed the view width, and the base did not have that problem. A source comment says a check for the base locale itself being cut off is still to do. — [LocaleAnalyzer.kt](https://github.com/JetBrains/android/blob/master/visual-lint/src/com/android/tools/visuallint/analyzers/LocaleAnalyzer.kt)
- [docs] Android lint `RelativeOverlap` (static, no rendering; since lint 0.2.0, October 2014; severity Warning; category Internationalization): "If relative layout has text or button items aligned to left and right sides they can overlap each other due to localized text expansion unless they have mutual constraints like `toEndOf`/`toStartOf`." — [lint check documentation](https://googlesamples.github.io/android-custom-lint-rules/checks/RelativeOverlap.md.html)
- [snippet] Escobar-Velásquez et al. "An Empirical Study of i18n Collateral Changes and Bugs in GUIs of Android apps" (ITDroid). ICSME 2020. Translates strings automatically, replays the app, builds a layout graph per screen and compares the default-language graph with each translated one; 31 apps, 7 languages. — [paper PDF](https://thesoftwaredesignlab.github.io/ITDroid/assets/pdfs/escobar2020internationalization.pdf) (not read)
- [preprint, abstract only] Huang, Liu, Gu, Wu. "Spotting Setting-Related UI Display Bugs in Android Apps" (SUDFinder). arXiv 2607.04120, July 2026. Uses patterns of wrong adaptation to settings as the oracle; 98 new bugs in 29 apps at precision 0.76; 67 confirmed. — [arXiv](https://arxiv.org/abs/2607.04120)

### Inferences
- The pattern that works is metamorphic: perturb one input that should not break the layout (longer text, larger font, another locale) and flag what newly appears. It gives the same intent filter as ReDeCheck's cross-width rule: an overlap or ellipsis that exists in the unperturbed rendering is taken as designed.
- Borrow for onionskin: an optional "stress" capture (root font size up, or text nodes lengthened by a factor, in the manner of pseudo-localisation) and three diff rules against the normal capture: new text-box overlap, text newly ellipsised or clipped, element no longer present or no longer reachable. This reuses the existing diff machinery; the baseline is the page itself, so no stored baseline is needed.
- Borrow from AccessiText: the truncation rule's leniency (keep one whole word) and reporting the amount of overlap or cropping so that findings can be sorted.
- Borrow from Android Studio: "ellipsised here but not in the base" as the default severity split; an ellipsis that is always there is design.
- Avoid: OCR. It is the stated source of AccessiText's lowest precision. In a browser, text boxes come from `Range.getClientRects` and need no OCR.
- Avoid: static rules like `RelativeOverlap` as a model; they flag what could overlap, not what does.

### Gaps
- "TextExerciser" is, as far as I recall, an Android text-input generation tool (He et al., IEEE S&P 2020) and not about layout; I did not confirm this from a primary source.
- No peer-reviewed evaluation of pseudo-localisation (machine-lengthened strings) as a layout oracle for web pages was found. Android's pseudolocales and similar industry practice were not researched in this session.
- dVermin, ITDroid and SUDFinder full texts were not read; their exact comparison rules are unconfirmed.
- GWALI's own per-cause false-positive breakdown was not re-read.

## 4. Mobile and other platforms: rules and reported precision

### Takeaway
Two families exist. Learned detectors on screenshots (OwlEyes, Nighthawk) report about 0.85 precision and recall on their own data, with text overlap the weakest class. Rule-based checks built into platforms are far simpler and each ships with an explicit intent filter: Android Studio flags only text views that a later sibling covers by at least half; LayoutTest-iOS checks siblings only and exempts a class list; Flutter reports only main-axis overflow of a flex container. None of the platform rules has a published precision.

### Cited Findings

Learned, screenshot-based
- [PR-text] Liu, Chen, Wang, Huang, Hu, Wang. "Owl Eyes: Spotting UI Display Issues via Visual Understanding". ASE 2020. [arXiv 2009.01417](https://arxiv.org/abs/2009.01417). Pilot study of 10,330 unique screenshots from 562 crowd-testing tasks: 4,470 had display issues. Shares by type: component occlusion 47%, missing image 25%, text overlap 21%, null value 6%, blurred screen 1%. The paper attributes occlusion to "improper setting of element's height, or the adaptive issues triggered when setting a larger-sized font". Method: a CNN classifies the screenshot as buggy or not, Grad-CAM marks the region; training data is augmented by heuristics that synthesise each bug type. Results: 85% precision, 84% recall; text overlap is the weakest type (0.82 precision, 0.81 recall), missing image the strongest (0.86, 0.90). 57 new issues in popular apps, 26 confirmed or fixed.
- [PR-abs] Liu, Chen, Wang, Huang, Hu, Wang. "Nighthawk: Fully Automated Localizing UI Display Issues via Visual Understanding". IEEE TSE, 2022, [DOI 10.1109/TSE.2022.3150876](https://doi.org/10.1109/TSE.2022.3150876); [arXiv 2205.13945](https://arxiv.org/abs/2205.13945). Object-detection model on screenshots; 0.84 precision and 0.84 recall for detection, 0.59 AP and 0.60 AR for localisation; 151 new issues, 75 confirmed or fixed. Abstract only; the PDF exceeded the fetch limit.
- [PR, not read] Zhao, Xing, Chen, Xu, Zhu, Li, Wang. "Seenomaly: Vision-Based Linting of GUI Animation Effects Against Design-Don't Guidelines". ICSE 2020, pp. 1286-1297. It lints animations, not static layout; numbers were not collected. [code](https://github.com/DehaiZhao/Seenomaly), last push April 2021.

Android Studio visual lint (rule-based, on a rendered layout) — [code] [JetBrains/android, visual-lint analyzers](https://github.com/JetBrains/android/tree/master/visual-lint/src/com/android/tools/visuallint/analyzers); [docs] [Problems panel](https://developer.android.com/studio/debug/universal-problems-panel)
- Analyzers present: Atf, BottomAppBar, BottomNav, Bounds, ButtonSize, Locale, LongText, Overlap, SystemUi, TextFieldSize, WearMargin.
- `OverlapAnalyzer`: "issues where a view is covered by another sibling view. Limit to covered TextView as they are the most likely to be wrongly covered by another view." For each pair of visible siblings where the first is a `TextView`: the first must be drawn underneath (lower elevation, or equal elevation and earlier child index); a Compose button with its own text sibling is skipped ("not a case of hidden text"); the text bounds are used (from character positions when available); report if `intersection area / text area >= 0.5` (`OVERLAP_RATIO_THRESHOLD = 0.5`, "Proportion of a view area allowed to be covered before emitting an issue"). — [OverlapAnalyzer.kt](https://github.com/JetBrains/android/blob/master/visual-lint/src/com/android/tools/visuallint/analyzers/OverlapAnalyzer.kt)
- `BoundsAnalyzer`: a child is "partially hidden" if `top < 0 || bottom > parentHeight || left < 0 || right > parentWidth` in parent coordinates. Scrolling views (`ScrollView`, anything implementing `ScrollingView` or `NestedScrollingParent`) and everything below them are skipped. No tolerance; integer pixels. — [BoundsAnalyzer.kt](https://github.com/JetBrains/android/blob/master/visual-lint/src/com/android/tools/visuallint/analyzers/BoundsAnalyzer.kt)
- `LongTextAnalyzer`: lines over 120 characters ("Maximum length of a line of text, according to Material Design guidelines"). — [LongTextAnalyzer.kt](https://github.com/JetBrains/android/blob/master/visual-lint/src/com/android/tools/visuallint/analyzers/LongTextAnalyzer.kt)
- Docs: "Android Studio automatically checks for visual lint issues for your layouts that are written in Views"; rules look at the layout in several device sizes and qualifier variants. Compose has "UI Check mode", which "automatically audits your Compose UI" across configurations and reports "issues ... such as text stretched on large screens or low color contrast". — [Problems panel](https://developer.android.com/studio/debug/universal-problems-panel); [Compose tooling](https://developer.android.com/develop/ui/compose/tooling/debug)

Other Android
- [docs] Accessibility Scanner, text scaling: flags text sized in dp ("won't scale") and warns that "Text with a fixed width and height might not provide sufficient room for enlarged content"; advises `wrap_content` and minimum heights instead of fixed ones. This is a rule on attributes, not a measurement of clipping. — [Android Accessibility Help](https://support.google.com/accessibility/android/answer/12159181)
- [docs] Android lint `RelativeOverlap`: see question 3.

iOS
- [code] LinkedIn LayoutTest-iOS (release 6.1.0, last push 3 May 2024, low activity). Overlap: for every view, each pair of sibling subviews is tested with `a.bottom > b.top + eps && a.right > b.left + eps && b.bottom > a.top + eps && b.right > a.left + eps`, `eps = 1e-5`, with the comment "Evading false positives due to computation tolerance". Bounds: a subview fails if `left < -eps`, `top < -eps`, `right > superview.width + eps` or `bottom > superview.height + eps`. — [UIView+LYTTestHelpers.m](https://github.com/linkedin/LayoutTest-iOS/blob/master/LayoutTestBase/UIViewHelpers/UIView%2BLYTTestHelpers.m); [LYTConfig.m](https://github.com/linkedin/LayoutTest-iOS/blob/master/LayoutTestBase/Config/LYTConfig.m)
- Exemptions: hidden views are skipped; subviews of `UISwitch`, `UITextView` and `UIButton` are not tested by default (`viewClassesAllowingSubviewErrors`); a test can add views to `viewsAllowingOverlap`. The failure message tells the user how: "If this is intentional, you should add one of the views to viewsAllowingOverlap." Ambiguity uses UIKit's `hasAmbiguousLayout`. — [LYTLayoutTestCase.m](https://github.com/linkedin/LayoutTest-iOS/blob/master/LayoutTest/TestCase/LYTLayoutTestCase.m); [docs](https://linkedin.github.io/LayoutTest-iOS/pages/040_includedTests.html)
- [docs] XCTest accessibility audits: `XCUIAccessibilityAuditType` has the members `contrast`, `elementDetection`, `hitRegion`, `sufficientElementDescription`, `dynamicType`, `textClipped`, `trait`, `parentChild`, `action`, `all`; available from iOS 17.0 and macOS 14.0. The page gives no rule for `textClipped`. — [Apple documentation](https://developer.apple.com/documentation/xcuiautomation/xcuiaccessibilityaudittype)

Flutter
- [code] `RenderFlex` computes `_overflow = max(0.0, -mainAxisFreeSpace)` and has overflow when `_overflow > precisionErrorTolerance`, which is `1e-10` ("The epsilon of tolerable double precision error"). — [flex.dart](https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/rendering/flex.dart); [constants.dart](https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/foundation/constants.dart)
- [code] The indicator compares container and child rectangles and marks each side whose overflow is `> 0.0`. It runs in debug builds, paints striped bars, and logs "A RenderFlex overflowed by N pixels on the right." once, and again after each hot reload. Used by `RenderFlex` and `RenderConstraintsTransformBox`. — [debug_overflow_indicator.dart](https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/rendering/debug_overflow_indicator.dart)
- [docs] "RenderFlex overflow is one of the most frequently encountered Flutter framework errors"; it happens when a `Row` or `Column` child "isn't constrained in its size". — [Flutter common errors](https://docs.flutter.dev/testing/common-errors)

### Inferences
- Borrow from Android Studio: restrict the overlap check to text that is covered, require the covering element to be painted later, and require a large covered share (they chose 50%). All three cut the intended-overlap cases at source. For onionskin the covered thing would be a text box from a `Range`, and "painted later" needs a hit test or a paint-order rule.
- Borrow from `BoundsAnalyzer`: skip scroll containers and their subtrees for the "child outside parent" check.
- Borrow from LayoutTest-iOS: siblings-only overlap, a default exemption list for composite controls, a per-test allow list, and a failure message that says how to allow the finding.
- Borrow from Flutter: a check owned by the layout mode. Overflow of a flex or grid container along its main axis, with `flex-wrap: nowrap`, is the web equivalent and has a clear author signal that items should fit.
- Avoid: learned screenshot classifiers. They need training data, give a region, not an element, and their weakest class is the one onionskin cares about most (text overlap).
- OwlEyes's pilot data says occlusion and overlap are the common display issues in practice (68% of 4,470), which supports spending the check budget there and on clipped text.

### Gaps
- No published precision or recall for Android Studio visual lint, LayoutTest-iOS, Flutter's overflow error, or Apple's `textClipped` audit was found.
- Apple's rule for `textClipped` and Xcode Accessibility Inspector's audit rules are not documented on the pages read.
- Compose UI Check's rule list is not given in the documentation read; whether it runs the same analyzers as the View-based visual lint is unconfirmed.
- Nighthawk's per-type figures and Seenomaly's results were not read from primary text.

## 5. Formal and solver-based work: VizAssert and Cassius

### Takeaway
VizAssert proves or refutes a property for every window width, height and default font size in a range. Its three geometric spec-free properties are one-line rules: every box's right edge is within the root's right edge; no two non-empty text boxes overlap; interactive boxes are on screen. On 62 template pages almost all pages passed these, and the text-overlap rule produced more false alarms (5) than true violations (2), because text boxes reserve space for descenders that the glyphs do not use.

### Cited Findings
- [PR-text] Panchekha, Geller, Ernst, Tatlock, Kamil. "Verifying that web pages have accessible layout". PLDI 2018. [DOI 10.1145/3192366.3192407](https://doi.org/10.1145/3192366.3192407); [PDF](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf). Cassius: Panchekha, Torlak, OOPSLA 2016, [DOI 10.1145/2983990.2984010](https://doi.org/10.1145/2983990.2984010) (see earlier notes). [code] [uwplse/Cassius](https://github.com/uwplse/Cassius), MIT, last push 15 Feb 2023; research prototype.
- The eight general assertions (Table 1): text at least 14 px tall; text resizable to 200%; lines at most 80 characters; screen-reader-only elements off screen; "The page does not require horizontally scrolling"; headings form a visual hierarchy; "Text does not overlap"; lines appropriately spaced. Six more are page-specific. — [PLDI 2018, Table 1](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf)
- The rules as written in the artifact — [assertions.vizassert](https://github.com/uwplse/Cassius/blob/master/bench/assertions/assertions.vizassert):
  - `no-horizontal-scroll`: for every box `b`, `(<= (right b) (right root))`.
  - `overlapping-text`: for every two distinct boxes of type text that have contents and are viewable, `(not (overlaps b1 b2))`.
  - `interactive-onscreen`: `(=> (is-interactive b) (onscreen b))`.
  - `accessible-offscreen`: text inside known social-button ids must not be viewable.
- "On screen" is deliberately one-sided: `onscreen(b) : b.right >= root.left ∧ b.bottom >= root.top`. A box below or to the right can be scrolled to; a box above or to the left cannot. Screen-reader-only text was common: 38 of the 62 pages placed text for social buttons off screen. — [PLDI 2018, sec. 3.3](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf)
- Range verified: "all possible screen widths 1024-1920 pixels, screen heights 800-1080 pixels, and default font sizes 16-32 pixels". Subjects: 62 pages from a free template site that fit the supported CSS subset, on average 78 elements and 176 boxes. — [PLDI 2018, sec. 5](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf)
- Totals over 502 page-assertion pairs: "414 successful verifications, 64 true positives, 13 false positives, and 11 timeouts"; false positives were "2.6% of all tests and 17% of counterexamples". — [PLDI 2018, sec. 5.2, Table 2](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf)
- Per assertion (Table 2; rows sum to 62 pages): `overlapping_text` 53 verified, 2 true violations, 5 false positives, 2 timeouts. `interactive_onscreen` 59 verified, 1 true, 1 false, 1 timeout. `text_size` 38, 18, 3, 3. `line_width` 39, 18, 3, 2. `no_hscroll` 60 verified and one page in each of two other columns. — [PLDI 2018, Table 2](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf)
- The text-overlap false positive explained: "The text does not overlap ... but the text boxes do because they reserve space for possible descenders. If the text were 'Liquidation' instead of 'SALE' then the text would overlap. VizAssert thus detected a failure of the formal assertion, but not a failure of the accessibility guideline ... VizAssert would need to reason about the shape of individual letters to avoid this false positive." — [PLDI 2018, sec. 5.2](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf)
- Limits (from the earlier notes, same paper sec. 6): HTML and CSS only, no JavaScript, tables unsupported; it needed a 1/6 px tolerance against Firefox.

### Inferences
- The `no_hscroll` row: arithmetic against the totals (13 false positives, 11 timeouts) implies its two non-verified pages are one false positive and one timeout, with no true violation. This is reconstructed from a table whose blank cells were lost in extraction; unconfirmed.
- Borrow: the one-line forms. "No box's right edge beyond the root's right edge" is the element-level form of the horizontal-scroll check and names the culprit, which a page-level `scrollWidth > clientWidth` does not.
- Borrow: the one-sided on-screen rule. Content off the top or left (in a left-to-right page) cannot be reached by scrolling and is either hidden on purpose or lost. Content off the right edge causes a scrollbar. They are different findings.
- Borrow: quantify over text boxes, not element boxes, for overlap.
- Avoid: flagging line boxes that only touch or share descender space. Shrink text boxes vertically (or require overlap deeper than a fraction of the line height) before reporting; VizAssert's 5-to-2 ratio shows what happens otherwise.
- Avoid expecting much signal from "no horizontal scroll" on desktop widths: on designed templates at 1024 px and wider it was true on 60 of 62 pages. Its value is at narrow widths.
- The assertion language itself (quantified formulas over boxes) is what onionskin's specs already are; only the three rules are new material.

### Gaps
- Appendix B of the paper (formal definitions of all 14 assertions, including `viewable`, `overlaps` and `is-interactive`) is not in the open PDF; only the artifact file was read, and the helper definitions were not traced.
- Troika (Panchekha et al., OOPSLA 2019) was not read; no newer solver-based layout checker for modern CSS (flexbox, grid) was found in this session.
- VizAssert's pages were static templates from one site; there is no result on pages with scripts.

## 6. Clipped and truncated content from the DOM and computed style; what developer tools and Lighthouse do; known false positives

### Takeaway
Three signals exist: the scroll size of a box versus its client size (cheap, integer-rounded, misses sub-pixel overflow and cannot name the culprit), text boxes from a `Range` versus the box that clips them (precise, fractional), and style facts that turn overflow into clipping (`overflow` other than `visible`, `text-overflow: ellipsis`, `line-clamp`, `white-space: nowrap`). Browser tools mostly show overflow instead of judging it: Firefox badges the element that causes a scroll container to overflow, Chrome badges scroll containers only, and Lighthouse has removed both of its relevant audits. The known sources of false alarms are screen-reader-only text, carousels and other meant-to-scroll regions, off-canvas panels, and fixed or sticky elements.

### Cited Findings

Definitions in the specifications
- [spec] CSS Overflow Module Level 3, W3C Working Draft, 7 October 2025. Scrollable overflow is "the set of things extending outside of that box's padding edge for which a scrolling mechanism needs to be provided"; ink overflow (shadows, outlines) does not extend it. Content beyond the scroll origin is in the unreachable region and is clipped. `overflow: hidden` clips to the padding box but "the content must still be scrollable programmatically", so the box is a scroll container; `overflow: clip` forbids all scrolling and the box is not a scroll container; `auto` is "Like scroll when the box has scrollable overflow; like hidden otherwise". `text-overflow` "specifies rendering when inline content overflows its end line box edge"; ellipsis affects rendering only, not layout. — [CSS Overflow 3](https://www.w3.org/TR/css-overflow-3/)
- [spec] CSSOM View, Editor's Draft, 5 November 2026: the scrolling area of an element extends to the outermost edges of its descendants' boxes on the scrollable sides; `elementsFromPoint` returns the hit-testable boxes at a point in paint order from the topmost. — [CSSOM View](https://drafts.csswg.org/cssom-view/)
- [docs] `scrollWidth` is "a measurement of the width of an element's content, including content not visible on the screen due to overflow"; it includes padding but not border, margin or scrollbar; "It can also include the width of pseudo-elements"; its value is an integer. MDN's own overflow test is `element.scrollWidth > element.clientWidth` on elements with `overflow: hidden; white-space: nowrap`. — [MDN scrollWidth](https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollWidth)

Why the scroll-size test misses truncation
- CSSWG issue 4123, "It should be detectable whether an element ellipsize text": "rounding of scrollWidth will make queried scrollWidth, clientWidth or DOMRect identical" even though an ellipsis shows; it links Chromium bug 980476. Whether the issue has since been resolved was not checked. — [www-style archive of the issue, July 2019](https://lists.w3.org/Archives/Public/public-css-archive/2019Jul/0300.html)
- [snippet] Mozilla bug 1716572 reports an ellipsised table cell whose `clientWidth` and `scrollWidth` are both 54. — [Bugzilla](https://bugzilla.mozilla.org/show_bug.cgi?id=1716572)
- [code] Ant Design does not use scroll sizes. `isEleEllipsis` appends an empty `em` at the end of the element and reports truncation when that probe's `getBoundingClientRect` lies outside the element's rect on any side (`childRect.right > rect.right`, `childRect.bottom > rect.bottom`, and so on). This uses fractional coordinates and covers both single-line ellipsis and multi-line clamp. — [ant-design typography util.ts](https://github.com/ant-design/ant-design/blob/master/components/typography/Base/util.ts)
- Android Studio's equivalent reads the text layout directly: any line with an ellipsis count above zero, or required text bounds larger than the view. — [LocaleAnalyzer.kt](https://github.com/JetBrains/android/blob/master/visual-lint/src/com/android/tools/visuallint/analyzers/LocaleAnalyzer.kt)

Horizontal scrolling of the page
- VizAssert's rule: no box's right edge beyond the root's right edge (question 5). ReDeCheck's rule: element not inside `body` (question 1).
- [blog] The long-used script flags `el.offsetWidth > document.documentElement.offsetWidth`. (By construction this finds elements wider than the document but not narrower ones pushed past the edge; that is my observation, not the article's.) The same article notes that content past the left edge "doesn't tend to trigger a scrollbar the same way", and that invisible off-screen elements (`position: absolute; left: 100%; opacity: 0`) cause overflow. — [Coyier, CSS-Tricks, 9 July 2014](https://css-tricks.com/findingfixing-unintended-body-overflow/)
- [blog] Causes listed by Polypane: `width: 100vw` on a page with a vertical scrollbar, fixed-width elements such as CMS images wider than the screen, `width: 100%` beside a scrollbar, and off-screen menus not wrapped in an `overflow: hidden` parent. — [Polypane blog](https://polypane.app/blog/strategies-for-dealing-with-horizontal-overflows/)

Developer tools
- [docs] Firefox (since version 83, released 17 November 2020): scroll containers get a `scroll` badge in the Inspector; toggling it highlights the elements that cause the overflow, and those nodes get an `overflow` badge. The documentation states no rule for "causing". — [Firefox source docs](https://firefox-source-docs.mozilla.org/devtools-user/page_inspector/how_to/debug_scrollable_overflow/index.html); [Mozilla Hacks](https://hacks.mozilla.org/2020/11/firefox-83-is-upon-us/)
- [docs] Chrome DevTools has a `scroll` badge on scroll containers (overflow "set to scroll, or auto when there's enough content to cause overflow"). The badge reference lists no badge or issue that names the element causing overflow. — [Chrome DevTools badges](https://developer.chrome.com/docs/devtools/elements/badges)
- [docs] Polypane (commercial browser): "Elements that cause a horizontal scroll bar/horizontal overflow are highlighted in red"; rule not documented. — [Polypane docs](https://polypane.app/docs/layout-debugging/)

Lighthouse
- [code] `content-width` (Lighthouse 11): passes when `window.innerWidth === window.outerWidth`, no tolerance, not applicable on desktop. The documentation says the audit "is an indirect way" to check mobile optimisation and that it can be ignored if the content width "is intentionally different from the viewport width". It belonged to the PWA category, which Lighthouse 12.0.0 removed; the file is absent from the main branch. — [content-width.js v11.7.1](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/audits/content-width.js); [docs](https://developer.chrome.com/docs/lighthouse/pwa/content-width); [12.0.0 release notes](https://github.com/GoogleChrome/lighthouse/releases/tag/v12.0.0)
- [code] `tap-targets` (Lighthouse 11): a target is too small only if every one of its client rects is under 48 px in width or height. For each such target a 48 px square is centred on it; a neighbour fails it when the neighbour's share of that square divided by the target's own share is at least 0.25 (`MAX_ACCEPTABLE_OVERLAP_SCORE_RATIO`). Skipped: two targets with the same link URL; a target fully contained in another ("probably intentional (e.g. an item with a delete button inside)"). — [tap-targets.js audit v11.7.1](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/audits/seo/tap-targets.js)
- [code] Its gatherer shows the filters a real tool needed: drop targets with a tap-target ancestor; drop links inside text blocks ("Links inside text blocks cause a lot of failures"); require a visible box; set `pointer-events: none` on every `position: fixed` or `sticky` element, which also removes them as targets; keep a rect only if `document.elementFromPoint` at its centre returns the target or a descendant. A code comment admits "Just checking the center can cause false failures for large partially hidden tap targets". — [tap-targets.js gatherer v11.7.1](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js)
- Lighthouse 12.0.0 (22 April; the page does not show the year, 2024 is my recollection): "The `tap-targets` audit is no longer a priority for SEO and has been replaced with the `target-size` audit" from axe; `font-size` and `viewport` moved to best practices. — [12.0.0 release notes](https://github.com/GoogleChrome/lighthouse/releases/tag/v12.0.0)
- [snippet] Google retired the Mobile-Friendly Test and the Search Console Mobile Usability report on 1 December 2023 and pointed users to Lighthouse. — [Search Engine Land](https://searchengineland.com/google-officially-drops-mobile-usability-report-mobile-friendly-test-tool-and-mobile-friendly-test-api-435377)

An older implicit-oracle tool for the web
- [code] Fighting Layout Bugs (Michael Tamm, Java and Selenium; presented at GTAC 2009 and QCon London 2010; the Google Code project survives only as exports, last change 2015; unmaintained). Detectors in the source: `DetectTextNearOrOverlappingHorizontalEdge`, `DetectTextNearOrOverlappingVerticalEdge`, `DetectInvalidImageUrls`, `DetectTextWithTooLowContrast`, `DetectElementsWithInvisibleFocus`. Text pixels are found by screenshotting the page with all text coloured black, then white, and taking the pixels that differ. Edges are runs of at least 16 px (horizontal) or 10 px (vertical) of similar colour with contrast of at least 1.5 to the neighbouring pixel, on a screenshot with text made transparent. A bug is any pixel that is text (or the outline of text) and also an edge: "Detects if there is text on the analyzed web page, which is near or overlaps a vertical edge or is truncated at a vertical edge." — [DetectTextNearOrOverlappingVerticalEdge.java](https://github.com/knorrium/fighting-layout-bugs/blob/master/src/main/java/com/googlecode/fightinglayoutbugs/DetectTextNearOrOverlappingVerticalEdge.java); [SimpleTextDetector.java](https://github.com/knorrium/fighting-layout-bugs/blob/master/src/main/java/com/googlecode/fightinglayoutbugs/SimpleTextDetector.java); [SimpleEdgeDetector.java](https://github.com/knorrium/fighting-layout-bugs/blob/master/src/main/java/com/googlecode/fightinglayoutbugs/SimpleEdgeDetector.java); [talk](https://www.infoq.com/presentations/Fighting-Layout-Bugs)
- Walsh et al. note that it "only checks static layout properties"; Althomali et al. say it "reports anomalies at single viewport widths". — [ISSTA 2017, sec. 5](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf); [ICST 2019, sec. II-C](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)

Known false positives and the evidence for each
- Screen-reader-only text. The usual rule sets are `position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden` or `clip: rect(1px, 1px, 1px, 1px); clip-path: inset(50%); height: 1px; width: 1px; margin: -1px; overflow: hidden; padding: 0; position: absolute`. — [docs] [WebAIM](https://webaim.org/techniques/css/invisiblecontent/). 38 of VizAssert's 62 pages had such text. — [PLDI 2018, sec. 3.3](https://homes.cs.washington.edu/~mernst/pubs/verify-layout-pldi2018.pdf). GWALI filtered the same patterns (zero size, `clip` to a zero rectangle, large `text-indent` with `overflow: hidden`), as recorded in the earlier notes.
- Content that is meant to scroll in two dimensions. WCAG 1.4.10 exempts "parts of the content which require two-dimensional layout for usage or meaning" and lists "images required for understanding (such as maps and diagrams), video, games, presentations, data tables" and "interfaces where it is necessary to keep toolbars in view". A carousel passes "As long as each individual panel within the carousel can fit within a 320 CSS pixel viewport". — [spec guidance] [Understanding SC 1.4.10 Reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html)
- Truncation with a way to the full text is acceptable under the same guidance: "The content is presented as truncated, but a link is provided to a web page where the content is fully visible". — [Understanding SC 1.4.10](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html)
- Fixed and sticky elements. Lighthouse removes them before hit testing (above). WCAG's guidance says "Such sticky or fixed content can pose significant issues" at small sizes and shows a fixed ad that "obstructs the hyperlink that has keyboard focus", so overlap by a fixed element is sometimes the defect. The RLG mis-assigns them a container (earlier notes).
- Off-canvas panels. ReDeCheck's code skips elements that are outside the viewport at every width. The CSS-Tricks and Polypane articles both name off-screen menus as a real cause of page overflow when no ancestor clips them.
- Scroll containers. Android Studio's bounds check skips them and their subtrees; `overflow: auto` or `scroll` is an explicit statement that content may exceed the box.

### Inferences
- A small rule set that the evidence supports, with what each needs from capture:
  1. Page scrolls sideways: `documentElement.scrollWidth > clientWidth` at the captured width (needs two numbers per capture). Report the culprits as the elements whose right edge is beyond the viewport width and that have no clipping or scrolling ancestor between them and the root (onionskin has boxes, the tree and `overflow` values already). Skip elements whose right edge is at or left of 0 (unreachable region) and elements matching the screen-reader-only pattern.
  2. Text cut off: a text box (from `Range.getClientRects` on text nodes; new capture field) extends beyond the padding box of its nearest ancestor that has `overflow` other than `visible` in that axis, and that ancestor is not a scroll container the user can scroll (`auto` or `scroll`). Split by severity: `text-overflow: ellipsis` or `line-clamp` present means designed truncation (report as information, or only when it differs across widths or under text stress); plain `hidden` or `clip` cutting through a line of text is the high-confidence defect.
  3. Text spills out: a text box extends beyond its own element's border box, or beyond a sibling's or ancestor's painted box, with `overflow: visible`. Often paired with `white-space: nowrap` or an unbreakable word.
- Use fractional rects for 2 and 3, not `scrollWidth`. The integer rounding makes `scrollWidth` unable to see overflow under 1 px, which is exactly where ellipsis decisions flip. onionskin's 0.5 px tolerance fits rect-based checks.
- Borrow from Lighthouse's gatherer the order of filters: nested same-purpose elements, inline content in text blocks, fixed and sticky, then a hit test. Each was added because of observed noise.
- Avoid `innerWidth === outerWidth` style proxies; Lighthouse's own docs call theirs indirect and it is gone.
- Avoid a pixel-based text and edge detector in the style of Fighting Layout Bugs; in a browser the text boxes and the box edges are known exactly. The idea worth keeping is its target: text that meets an edge.

### Gaps
- No source measures the precision of any DOM-based clipped-text rule on web pages. The nearest numbers are AccessiText's 73% for cropped text on Android (OCR-based) and none for Android Studio.
- Firefox's rule for which element "causes" overflow was not read from source; Polypane's rule is not public.
- Whether Chrome DevTools has added an overflow-culprit feature after the badge reference was written is unconfirmed.
- The exact CSSOM View text for `scrollWidth` rounding and for `Range.getClientRects` was not read (the draft was truncated); the claims above rest on MDN and the CSSWG issue.
- `line-clamp` detection (`scrollHeight > clientHeight`) has the same rounding caveat in principle; no source was found that documents it.

## 7. How tools decide that an overlap is intended, and which heuristics were measured

### Takeaway
Every tool that reports overlap uses at least one structural filter, and the filters fall into six kinds: compare across a setting (width, text size), siblings only, ancestry (containment is never overlap), paint and hit order, what is covered (text only, by a minimum share), and explicit allow lists. Only the first has numbers attached, and it does not solve visibility: with ReDeCheck's cross-width filter in place, about three quarters of reports were still invisible.

### Cited Findings

| Heuristic | Used by | Rule | Measured? |
|---|---|---|---|
| Overlap at every width is intended | ReDeCheck | report only if the pair does not overlap at the adjacent wider width | Yes: 0 box-level false positives but 24 of 32 collisions non-observable ([ISSTA 2017, Table 1b](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)) |
| Overlap at default setting is intended | AccessiText | report only overlap under scaled text that is absent at default; FABs ignored | Yes: 89.34% precision, 100% recall on 122 reports ([AccessiText, Table 2](https://www.ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf)) |
| Siblings only | ReDeCheck (sibling edges), LayoutTest-iOS, Android Studio | only pairs with the same parent are compared | No |
| Ancestor and descendant never "overlap" | ReDeCheck (turns it into protrusion), Lighthouse ("fully contained ... probably intentional") | containment is a separate finding or none | No |
| Paint order | Android Studio | the covered view must have lower elevation, or the same elevation and an earlier child index | No |
| Hit test | Lighthouse, Playwright, axe-core | element at the point must be the target or its descendant | No |
| Only text can be a victim, and by a minimum share | Android Studio | covered share of the text bounds at least 0.5 | No |
| Same action | Lighthouse | two targets with the same URL are not in conflict | No |
| Composite controls exempt | LayoutTest-iOS, Android Studio | subviews of UISwitch, UITextView, UIButton; a Compose button and its text | No |
| Remove fixed and sticky first | Lighthouse | `pointer-events: none` on them before testing | No |
| Minimum overlap depth | ReDeCheck code, LayoutTest-iOS | more than 1 px each way; more than 1e-5 | No |
| Pixel change under opacity toggle | VISER, VERVE | any pixel differs between layers | Yes: 86% to 92% agreement with people ([STVR 2021](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)) |
| Allow list | LayoutTest-iOS, axe-core, WebSee, CrossT | named views or selectors excluded | No |

Sources for the rows not cited inline: [OverlapAnalyzer.kt](https://github.com/JetBrains/android/blob/master/visual-lint/src/com/android/tools/visuallint/analyzers/OverlapAnalyzer.kt); [LYTLayoutTestCase.m](https://github.com/linkedin/LayoutTest-iOS/blob/master/LayoutTest/TestCase/LYTLayoutTestCase.m); [Lighthouse tap-targets audit](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/audits/seo/tap-targets.js) and [gatherer](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js); [Playwright actionability](https://playwright.dev/docs/actionability); [ReDeCheck Sibling.java, from the earlier notes](https://github.com/redecheck/redecheck/blob/d3f2364960aa7398f7085a502576da70759f5ed0/src/main/java/shef/layout/Sibling.java).

- axe-core shows how a mature tool grades occlusion instead of deciding it. In `target-size`: a fully obscured control passes ("ignored because it is fully obscured and thus not clickable"); a partly obscured target fails if what remains is too small; and it returns "incomplete" for "Element size could not be accurately determined due to overflow content" and "too many overlapping elements". In `color-contrast` it returns incomplete when the element "is overlapped by another element", is "partially obscured by another element", or is "outside the viewport". — [code] [target-size.json](https://github.com/dequelabs/axe-core/blob/develop/lib/checks/mobile/target-size.json); [color-contrast.json](https://github.com/dequelabs/axe-core/blob/develop/lib/checks/color/color-contrast.json)
- MFix counts only intersections inside a segment as layout distortion and accepts other movement (earlier notes); GWALI drops alignment changes on text edges. Both are "intended change" filters for diffs, not single-rendering rules.
- OwlEyes separates two things people report: "for text overlap category, two pieces of text are mixed together; while for component occlusion, one component covers part of the other component". — [OwlEyes, sec. 2](https://arxiv.org/abs/2009.01417)

### Inferences
- No source measured positioned elements, stacking contexts, z-index, negative margins or badges as separate predictors of intent. The tools avoid the question by narrowing what can be a victim (text) and requiring that it be covered by something painted later.
- A defensible default for onionskin, combining the filters that the evidence supports:
  - victim: a text box (or the box of an image, input or button), never a bare container box;
  - offender: an element that is not an ancestor or descendant of the victim's element, and that paints something over the victim (hit test at the victim's centre, or a few sample points, returns the offender or its descendant);
  - size: covered share of the victim above a threshold (Android Studio uses 50%; for text even a small covered share harms reading, so the threshold should be a parameter and the share reported);
  - intent: the pair does not overlap at some other captured width, or does not overlap in the unstressed capture. If it overlaps at every width, report at a lower severity or not at all;
  - exclusions: either element is `position: fixed` or `sticky` (report separately, if at all), offender has `pointer-events: none` and paints nothing, victim is not displayed (`checkVisibility` with opacity and visibility options), victim matches the screen-reader-only pattern.
- Text-on-text overlap (two text boxes intersecting by more than a descender's depth) is the one case that needs no paint-order reasoning, since two runs of glyphs in the same place are unreadable whatever is on top. It is also the case VizAssert and OwlEyes single out. It should be the first overlap check to ship.
- Borrow from axe-core: when the offender or victim is in a state the rules cannot judge (too many overlapping elements, overflow content, transforms), return "needs review" and say why.
- Avoid: using z-index or `position` values as intent signals by themselves. Nothing in the literature supports them, and a positioned badge over an avatar and a positioned menu over body text look the same in computed style.

### Gaps
- No controlled comparison of these filters exists. A small labelled set of real overlaps, run with each filter on and off, would be new work.
- No source reports how often intended overlap involves positioned elements versus negative margins versus transforms.
- Hit testing sees only the topmost element at a point and is affected by `pointer-events`; no source quantifies how often that disagrees with paint.

## 8. Wrapping: how a wrapped element is detected and how intended wrapping is told apart

### Takeaway
Only the Sheffield line of work detects wrapping of elements, and it is their least reliable rule: 10 true and 5 false on the first pages, 4 true, 2 non-observable and 11 false on the next twenty. The false alarms are rows that were never rows and wrapping the designer wanted (footer links, icon groups). No source tests a rule that uses CSS intent signals such as `flex-wrap`.

### Cited Findings
- Detection rule (ReDeCheck Algorithm 4): two children are in a row if one is left-of or right-of the other and neither above nor below; a child is wrapped if it is in its parent's row at the wider range and outside that row at the adjacent narrower range while a row of at least two remains. — [ISSTA 2017, sec. 3.2](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- The motivating case is a search button that drops below its input (BugMeNot). — [ISSTA 2017, Fig. 1](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- Counts: 10 TP and 5 FP on 26 pages; all five false positives were three elements "misclassified as a 'row'". — [ISSTA 2017, Table 1b, sec. 4.4](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf). On 20 new pages: 4 TP, 2 NOI, 11 FP. — [STVR 2021, Table III(b)](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- Why people rejected reports: "the HTML elements are text-based links that form several rows in the footer of the page. Clearly, the developer intends these to wrap"; elements that "do not visually form a row, yet were identified as such at the DOM level"; an element that developer rules hide on resize; icons that "wrap in an aesthetically pleasing fashion". Non-observable cases were invisible "spacer" elements. — [STVR 2021, RQ2, RQ4(b), sec. 3.2](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- The pairwise test is "often too sensitive since it does not check, for example, that an element has wrapped beneath all other elements". — [STVR 2021, sec. 4.6](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- VERVE's stricter filter: "(i) there are at least three elements in the original 'row'; (ii) that the wrapped element is below all other row elements, and not just the last row element; and (iii) that all row elements are visible with a width and height greater than zero". Agreement with people rose from 35.3% to 64.7% on the new pages. — [STVR 2021, sec. 4.6](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- VERVE's visual step adds only "is the wrapped element visible", so it cannot tell wanted from unwanted wrapping; the authors say "it is difficult to judge the developer's intent". — [STVR 2021, RQ2](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)
- GWALI handles the same event in translated pages by naming the text children of the lowest common ancestor as suspects when a directional relation changes (earlier notes).
- Whether "awkward" wrapping is a failure at all is contested inside the papers: the same icon-wrapping case is called "subjective". — [STVR 2021, RQ4(b)](https://eprints.whiterose.ac.uk/165542/8/stvr.1756.pdf)

### Inferences
- Wrapping is a change of arrangement, not a defect in one rendering, and responsive design wraps on purpose. A check with 15 false alarms in 31 reports should not be in a default set.
- If offered, make it narrow and informational: a row of at least three siblings on one line at the wider width; exactly one sibling (the last in source order) alone on the next line at the adjacent narrower width; the container is not one whose children are inline text links. Report as "orphaned item", with the width range.
- CSS gives intent signals the papers did not use (my reasoning, untested): `flex-wrap: wrap`, grid with `auto-fit` or `auto-fill`, and inline or inline-block children say that wrapping is expected; `flex-wrap: nowrap`, `white-space: nowrap` and fixed grid columns say that it is not. Where wrapping is not expected, the failure shows up as overflow or protrusion instead, which the other checks catch. So the higher-value check is "a nowrap row overflows its container", in the manner of Flutter's flex overflow.
- A second form may matter more to users and is easy to state with text boxes: a short label inside a control (button, tab, nav item) that takes two lines at some widths and one at others. No source measures it.
- Avoid: pairwise "was left-of, now below" as the trigger. Require that the element is below every other row member.

### Gaps
- No evaluation of a wrapping rule other than ReDeCheck's exists.
- No study of how often wrapped controls are reported as bugs by users or developers was found.
- Text-line wrapping inside controls is not covered by any tool found.

## 9. Noise in practice and how tools let users suppress findings

### Takeaway
The measured noise levels are high for geometric checks without visual confirmation: 40% of ReDeCheck's 328 reports were not true positives, and 72% of the 176 that remain once the 152 small-range reports from one icon grid are set aside (computed from ISSTA 2017 Table 1b and STVR 2021 Table III(a)). Tools that last give each finding a severity and a "could not decide" outcome, keep a default exemption list, and tell the user in the failure message how to allow it. Lighthouse, the most widely used tool that had layout-like audits, dropped them.

### Cited Findings
- ReDeCheck: 131 of 328 reports were false positives or non-observable; the authors' answer was to count distinct viewport ranges (137) and to plan "automatically distinguishing observable from non-observable issues" so that developers can "prioritize visible problems". — [ISSTA 2017, sec. 4.4-4.5](https://eprints.whiterose.ac.uk/id/eprint/116989/10/c50-3.pdf)
- VISER's framing: "Unless VISER classifies an issue as a human-observable layout failure, a web developer can ignore it"; non-observable issues "are unlikely to be a high-priority". — [ICST 2019, abstract and sec. II-D](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)
- Lighthouse code comments record noise that forced filters ("Links inside text blocks cause a lot of failures"), and the project then removed `tap-targets` as "no longer a priority" and dropped `content-width` with the PWA category. — [gatherer](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js); [12.0.0 release notes](https://github.com/GoogleChrome/lighthouse/releases/tag/v12.0.0)
- Lighthouse's `tap-targets` passed small targets that had no close neighbour; only overlap failures counted. With any failure the score was `passing share × 0.89` ("we don't want the audit to pass, so keep the score below 90"), and the share was shown. — [tap-targets.js audit](https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/audits/seo/tap-targets.js)
- axe-core's result classes: `passes`, `violations`, `inapplicable` and `incomplete` ("needs review"), the last for results that "were aborted and require further testing". Violations carry an impact of "minor", "moderate", "serious" or "critical". Suppression: `include`/`exclude` contexts, `runOnly` by tag or rule, `rules: {id: {enabled: false}}`, `disableOtherRules`; tags `best-practice` and `experimental` ("Cutting-edge rules, disabled by default"); a rule can be set to `reviewOnFail` so that failures are reported as needs review. — [docs] [axe-core API](https://github.com/dequelabs/axe-core/blob/develop/doc/API.md)
- LayoutTest-iOS: global config for exempt classes, per-test `viewsAllowingOverlap` and `viewsAllowingAccessibilityErrors`, a switch for the ambiguity test, and the remedy printed in the message. — [LYTLayoutTestCase.m](https://github.com/linkedin/LayoutTest-iOS/blob/master/LayoutTest/TestCase/LYTLayoutTestCase.m)
- Android Studio: visual lint issues are their own filter class in the Problems panel ("Show Visual Lint") beside warnings and weak warnings. — [Problems panel docs](https://developer.android.com/studio/debug/universal-problems-panel)
- Flutter: the overflow error is debug-only and printed once per render object until hot reload, which limits repetition. — [debug_overflow_indicator.dart](https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/rendering/debug_overflow_indicator.dart)
- [snippet] Flutter users report overflow errors that appear only in widget tests because the test font's metrics differ from the device font; common workarounds are loading the real font or filtering errors that start with "A RenderFlex overflowed by". — [Stack Overflow question 57499131](https://stackoverflow.com/questions/57499131/how-to-deactivate-or-ignore-layout-overflow-messages-in-flutter-widget-tests); Flutter's documentation notes that the default test font changed after version 3.7 — [Flutter breaking changes](https://docs.flutter.dev/release/breaking-changes/rendering-changes)
- AccessiText makes its two thresholds configurable and reports "the level of overlap" and "the extent of cropping" so that developers can "prioritize and sort" findings. — [AccessiText, sec. 4](https://www.ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf)
- Human labels disagree at the margin: 7 of VISER's 15 disagreements were differences of a few pixels that people had waved through. — [ICST 2019, sec. IV-D](https://eprints.whiterose.ac.uk/id/eprint/230100/1/althomali2019.pdf)

### Inferences
- A shortlist for onionskin, ordered by the evidence for few false alarms:
  1. Page scrolls sideways at a captured width, with culprits named (viewport protrusion was ReDeCheck's most reliable type; VizAssert and WCAG both treat it as a defect; exemptions are well understood). Severity: failure.
  2. Text cut through by a clipping ancestor without ellipsis or clamp. Severity: failure. With ellipsis or clamp: information, raised to warning when it appears at some widths only or only under text stress.
  3. Text overlapping text. Severity: failure when the overlap is deeper than a set fraction of the line height.
  4. Text or control covered by a non-ancestor element, confirmed by hit test, not present at every width. Severity: warning.
  5. Child box outside parent box with `overflow: visible` and visible content in the protruding part. Severity: information (3% visible in the measurements).
  6. Orphaned row item, small-range layouts. Off by default; information only.
- Result shape: every finding carries check id, severity, verdict (`:fail`, `:review`, `:info`), the elements, the measured amount (px of overflow, covered share), the width range, and a one-line reason for `:review`. This mirrors axe-core and AccessiText and fits design choice 11.
- Suppression, in the order tools converge on: a default exemption list in data (screen-reader-only pattern, scroll containers, fixed and sticky, elements off the unreachable side), a per-run allow list by selector with a stated reason, per-check thresholds, and turning a check to review-only. Print the allow-list form in the failure message.
- Group repeated findings by common ancestor and width range before reporting; one grid produced 147 reports of one failure.
- Run the checks on stable geometry only (fonts loaded, as design choice 4 already requires): Flutter's test-font noise is the same hazard.

### Gaps
- No survey or interview study of web developers' tolerance for layout-check false alarms was found. The evidence above is from tool code, release notes and paper discussions.
- No public data on how often users of Android Studio visual lint, Polypane or Firefox's overflow badge suppress or ignore findings.
- The Lighthouse team's detailed reasons for dropping `tap-targets` beyond "no longer a priority for SEO" were not found.

## 10. Machine learning and large vision-language models that judge layout defects, with measured accuracy

### Takeaway
As of November 2026, general multimodal models asked to find display defects in a screenshot do poorly: near-random F1 on text overflow and container overlap in a CVPR 2026 benchmark, and 6.67% precision with 0.47% recall for a plainly prompted GPT-4.1 on web reflow problems. Systems that do well wrap the model in a structured comparison of DOM-derived widgets across two renderings and let it explore the page; the best web result is 80.49% precision and 95.31% recall, at about 1.18 US dollars per page.

### Cited Findings
- [PR-text] He, He, Huq, Malek. "ReFLAIR: Detecting Responsive Layout Reflow Issues using Multimodal Generative AI". Proc. ACM Softw. Eng. 3 (FSE), article FSE129, July 2026. [DOI 10.1145/3808136](https://doi.org/10.1145/3808136); [PDF](https://ics.uci.edu/~seal/projects/ReFLAIR/ReFLAIR.pdf); [code](https://github.com/seal-hub/ReFLAIR), last push 30 May 2026.
  - Target: WCAG 1.4.10 at 320 px, defined as loss of information or function between the full-width page and the reflowed page. It extracts "informative and actionable widgets", compares "their presence and behavior across original and reflowed layouts", and uses scrolling plus model-guided expansion of menus to find widgets that moved. Model: GPT-4.1, temperature 0, three runs averaged.
  - Table 2, 24 pages: ReFLAIR 80.49% precision and 95.31% recall; GenA11y (LLM on screenshots and HTML, no interaction) 60.00% and 8.45%; GPT-4.1 with engineered prompt 57.82% and 39.91%; ReDeCheck at 320 px 7.29% and 4.23%; GPT-4.1 with a direct prompt 6.67% and 0.47%; QualWeb and WAVE 0% and 0%.
  - On the plain model: "for most webpages, the model generated overly optimistic assessments", typically stating that the mobile version "is well-implemented, with no reflow issues".
  - Ablation: without the exploration stage precision falls by 40.08 points; many widgets "are hidden in sub-menus in the reflow page" and would be reported as missing.
  - On 36 pages: 84.62% precision, 93.32% recall. With Gemini-3-Pro on a subset: 94.85% and 94.85%, against 96.20% and 91.24% for GPT-4.1 on that subset.
  - Cost: about 562 thousand tokens and 1.18 dollars per page on average (median 0.54, maximum 5.53).
- [PR-abs] Xiang, Wu, Chen, Li, Chen. "UI-Lens: Assessing General MLLMs' Potential to Automate UI Display Quality Assurance". CVPR 2026. 4,759 pages annotated by design experts, six defect categories, 10 models (8 closed, 2 open). Task-average F1: text overflow 20.36%, container overlap 31.21%, text inconsistency 10.61%; the authors call performance on the two boundary tasks near random. Chinese-language app interfaces, not web pages. — [CVPR 2026 poster page](https://cvpr.thecvf.com/virtual/2026/poster/38861). Full paper not read (the open-access page refused the fetch).
- [preprint, snippet] Zhang, Wang, Cui, Huang, Xue. "Proactive Detection of GUI Defects in Multi-Window Scenarios via Multimodal Reasoning". arXiv 2604.19081, April 2026. Marks widgets on the screenshot (Set-of-Mark) so that the model reasons about named elements; a fine-tuned Qwen2.5-VL; best F1 of 87.2% for widget occlusion; text truncation 184% more frequent in split-screen than full-screen. Android. — [arXiv](https://arxiv.org/abs/2604.19081). Unconfirmed against the full text.
- [preprint, abstract only] Liu et al. "Seeing is Believing: Vision-driven Non-crash Functional Bug Detection for Mobile Apps" (Trident). arXiv 2407.03037. Multi-agent, multimodal LLM over screenshot sequences; targets functional bugs with visual cues, not layout; reports relative gains only (14% to 112% recall, 108% to 147% precision over the best baseline). — [arXiv](https://arxiv.org/abs/2407.03037)
- [preprint, snippet] Macklon, Bezemer. "Exploring the Capabilities of Vision-Language Models to Detect Visual Bugs in HTML5 Canvas Applications". arXiv 2501.09236, January 2025. 80 injected-bug screenshots including layout bugs; accuracy up to 100% per application when the model is given the README, a description of the bug type and a bug-free screenshot. The bug-free screenshot makes this a baseline method. — [arXiv](https://arxiv.org/abs/2501.09236)
- [PR-abs, snippet] Repair, not detection: "Repairing Responsive Layout Failures Using Retrieval Augmented Generation" (ReDeFix), ICSME 2025, arXiv 2511.00678. Uses ReDeCheck-style detection as input and an LLM with Stack Overflow retrieval to patch CSS; 88% repair accuracy. — [arXiv](https://arxiv.org/abs/2511.00678)
- [PR-abs] Chiou, Winn, Alotaibi, Halfond. "Automatically Detecting Reflow Accessibility Issues in Responsive Web Pages" (SALAD). ICSE 2024. Not ML: it models UI functions and simulates keyboard use to find functions lost after reflow. In ReFLAIR's comparison on a subset SALAD had 27.85% precision against ReFLAIR's 74.90%. — [ICSE 2024 page](https://conf.researchr.org/details/icse-2024/icse-2024-research-track/229/Automatically-Detecting-Reflow-Accessibility-Issues-in-Responsive-Web-Pages); [ReFLAIR, Table 2](https://ics.uci.edu/~seal/projects/ReFLAIR/ReFLAIR.pdf)
- Earlier learned detectors on mobile screenshots (OwlEyes, Nighthawk; question 4) report about 0.84 to 0.85 precision and recall on their own data sets.

### Inferences
- The geometric judgements onionskin can make exactly from boxes (is this text outside its clip, do these two text boxes intersect) are the ones general vision models are worst at. There is no case for replacing a geometric rule with a model.
- Where a model has shown value is the judgement geometry cannot make: is this overlap or truncation intended, and did content go missing or just move. The working designs give the model named elements and a pair of renderings, not a bare screenshot.
- Borrow from ReFLAIR: the comparison "is every piece of text and every control from the wide rendering still present and reachable in the narrow one". onionskin can do the presence part without a model, because it has both captures and a matching step (design choice 14); a model would only be needed to open menus.
- A possible later extension point, not a default: hand a `:review` finding (crop of the region, the element paths, the rule that fired) to an external judge. The output format in question 9 already carries what such a judge would need.
- Avoid: a model-in-the-loop default. Cost per page (about a dollar in ReFLAIR), run-to-run variance (ReFLAIR needed temperature 0 and averaging), and the "all fine" bias of plain prompting all conflict with a test library that must be cheap and repeatable.

### Gaps
- No peer-reviewed benchmark was found that measures vision-language models on single-rendering web layout defects (overlap, clipping, protrusion) with per-defect precision and recall. UI-Lens is the closest and covers Chinese app screens.
- No study was found that feeds a model structured geometry (boxes and relations, as onionskin produces) instead of pixels and measures the gain.
- UI-Lens per-model results, and the multi-window paper's figures, were not confirmed from full texts.
- Commercial "visual AI" testers that claim layout bug detection without a baseline were not researched; no vendor-independent accuracy figures were seen.
