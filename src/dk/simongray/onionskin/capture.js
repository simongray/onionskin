// The capture of a page as plain data, which JSON can carry. The driver
// evaluates this function in the page with the options of a capture:
//
//   select  [{selector, styles, resolve}]: the elements that each selector
//           matches, with the style properties in styles and the pairs of a
//           property and a CSS value in resolve, which a hidden probe inside
//           the element resolves to the value the browser computes
//   all     {root, styles, pseudo} or null: every element under root (the
//           document by default) outside the head, with every style
//           property but the custom ones, or those in styles, and the
//           styles of the pseudo elements that have content
//   css     CSS to add to the page before anything else, e.g. to try a
//           change without editing the style sheets
//   settle  true to turn off animations and transitions and wait for the
//           fonts and two frames before reading anything
//
// Boxes are in CSS pixels from the top left of the document, so the scroll
// position doesn't change them.
async function capture(options) {
  const { select = [], all = null, css = null, settle = true } = options || {};

  if (css) {
    const style = document.createElement('style');
    style.textContent = css;
    (document.head || document.documentElement).append(style);
  }

  if (settle) {
    const style = document.createElement('style');
    style.textContent = '*, *::before, *::after { animation: none !important; ' +
      'transition: none !important; caret-color: transparent !important; }';
    (document.head || document.documentElement).append(style);
    await document.fonts.ready;
    // requestAnimationFrame waits for a frame, unless the page is hidden.
    await Promise.race([
      new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))),
      new Promise(done => setTimeout(done, 200))]);
  }

  const path = el => {
    const steps = [];
    for (let e = el; e; e = e.parentElement) {
      const parent = e.parentElement;
      const i = parent ? Array.prototype.indexOf.call(parent.children, e) + 1 : 1;
      steps.unshift(e.localName + ':' + i);
    }
    return steps.join('>');
  };

  const box = el => {
    const r = el.getBoundingClientRect();
    return { left: r.left + scrollX, top: r.top + scrollY,
             right: r.right + scrollX, bottom: r.bottom + scrollY };
  };

  const read = (style, names) => names.map(name => style.getPropertyValue(name));

  const resolve = (el, property, value) => {
    const probe = document.createElement('span');
    probe.style.setProperty('position', 'absolute');
    probe.style.setProperty('visibility', 'hidden');
    probe.style.setProperty(property, value);
    el.append(probe);
    const resolved = getComputedStyle(probe).getPropertyValue(property);
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
    styleNames = all.styles ||
      Array.from(getComputedStyle(document.documentElement))
        .filter(name => !name.startsWith('--'));
    nodes = (root ? [root, ...root.querySelectorAll('*')] : [])
      .filter(el => !el.closest('head'))
      .map(el => {
        const node = { path: path(el), tag: el.localName,
                       rendered: el.getClientRects().length > 0,
                       box: box(el), style: read(getComputedStyle(el), styleNames) };
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
           elements, styleNames, nodes };
}
