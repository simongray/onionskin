// The capture of a page as plain data, which JSON can carry. The browser
// evaluates this function in the page with the options of a capture:
//
//   select  [{selector, styles, resolve}]: the elements that each selector
//           matches. For each element, the capture reads the properties in
//           styles. A hidden probe inside the element resolves each pair of
//           a property and a CSS value in resolve.
//   all     {root, styles, pseudo} or null: every element under root, the
//           document by default, except those in the head. The capture
//           reads every property but the custom ones, or only those in
//           styles. It also reads the pseudo elements that have content.
//   css     CSS to add to the page before anything else, e.g. to try a
//           change without editing the style sheets
//   settle  true to turn off animations and transitions and wait for the
//           fonts and two frames before reading anything
//
// Boxes are in CSS pixels from the top left of the document, so the scroll
// position doesn't change them.
async function capture(options) {
  const { select = [], all = null, css = null, settle = true } = options || {};

  const addStyle = text => {
    const style = document.createElement('style');
    style.textContent = text;
    (document.head || document.documentElement).append(style);
  };

  if (css) addStyle(css);

  // Reading the page before it settles is the most common cause of flaky UI
  // tests (Romano et al., ICSE 2021).
  if (settle) {
    addStyle('*, *::before, *::after { animation: none !important; ' +
             'transition: none !important; caret-color: transparent !important; }');
    await document.fonts.ready;
    // requestAnimationFrame waits for a frame, unless the page is hidden.
    await Promise.race([
      new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))),
      new Promise(done => setTimeout(done, 200))]);
  }

  // The first path under a parent numbers all of its children, so the paths
  // of a long list don't take quadratic time.
  const paths = new Map();
  const path = el => {
    const parent = el.parentElement;
    if (!parent) return el.localName + ':1';
    if (!paths.has(el)) {
      const prefix = path(parent) + '>';
      Array.from(parent.children).forEach((child, i) =>
        paths.set(child, prefix + child.localName + ':' + (i + 1)));
    }
    return paths.get(el);
  };

  const box = el => {
    const r = el.getBoundingClientRect();
    return { left: r.left + scrollX, top: r.top + scrollY,
             right: r.right + scrollX, bottom: r.bottom + scrollY };
  };

  // Most computed values repeat from element to element, so a full capture
  // lists each value once and refers to it by its index.
  const values = new Map();
  const read = (style, names) => names.map(name => {
    const value = style.getPropertyValue(name);
    if (!values.has(value)) values.set(value, values.size);
    return values.get(value);
  });

  // The browser drops a value that the property doesn't take, and the probe
  // would then have the inherited or the initial value, which a check could
  // match by accident. A value with a var() only shows this once its
  // variables are substituted, as in the computed value of a custom
  // property.
  const valid = (probe, property, value) => {
    if (!value.includes('var(')) return CSS.supports(property, value);
    probe.style.setProperty('--onionskin', value);
    const substituted = getComputedStyle(probe).getPropertyValue('--onionskin');
    return substituted !== '' && CSS.supports(property, substituted);
  };

  const resolve = (el, property, value) => {
    const probe = document.createElement('span');
    probe.style.setProperty('position', 'absolute');
    probe.style.setProperty('visibility', 'hidden');
    el.append(probe);
    let resolved = null;
    if (valid(probe, property, value)) {
      probe.style.setProperty(property, value);
      resolved = getComputedStyle(probe).getPropertyValue(property);
    }
    probe.remove();
    return resolved;
  };

  // Every reading comes before the first probe, which changes the document.
  const found = select.map(({ selector }) => {
    try {
      return Array.from(document.querySelectorAll(selector));
    } catch (e) {
      return e;
    }
  });

  const elements = select.map(({ selector, styles = [] }, i) =>
    found[i] instanceof Error
      ? { selector, error: found[i].message }
      : { selector,
          matches: found[i].map(el => {
            const style = getComputedStyle(el);
            return { path: path(el), box: box(el),
                     style: styles.map(name => [name, style.getPropertyValue(name)]) };
          }) });

  let nodes = null;
  let styleNames = null;
  if (all) {
    const root = all.root ? document.querySelector(all.root) : document.documentElement;
    const pseudos = all.pseudo || ['::before', '::after'];
    // A computed style lists longhands only. Mazinanian and Tsantalis also
    // compared longhands to check their CSS migrations (ASE 2016).
    styleNames = all.styles ||
      Array.from(getComputedStyle(document.documentElement))
        .filter(name => !name.startsWith('--'));
    nodes = (root ? [root, ...root.querySelectorAll('*')] : [])
      .filter(el => !el.closest('head'))
      .map(el => {
        const node = { path: path(el), box: box(el),
                       style: read(getComputedStyle(el), styleNames) };
        const pseudo = [];
        for (const name of pseudos) {
          const style = getComputedStyle(el, name);
          const content = style.getPropertyValue('content');
          if (content !== 'none' && content !== 'normal') {
            pseudo.push([name, read(style, styleNames)]);
          }
        }
        if (pseudo.length) node.pseudo = pseudo;
        return node;
      });
  }

  select.forEach(({ resolve: pairs = [] }, i) => {
    if (found[i] instanceof Error) return;
    found[i].forEach((el, j) => {
      elements[i].matches[j].resolved =
        pairs.map(([property, value]) => [property, value, resolve(el, property, value)]);
    });
  });

  return { url: location.href,
           viewport: { width: innerWidth, height: innerHeight, scale: devicePixelRatio },
           elements, styleNames, styleValues: Array.from(values.keys()), nodes };
}
