/* Folio text reader bridge: layout, position reporting, highlights and selection. */
(function () {
  const folio = {};
  window.folio = folio;

  const content = () => document.getElementById('content');
  const blocks = () => Array.from(document.querySelectorAll('.blk'));

  let paginated = false;
  let reportTimer = null;

  function bridge() {
    return window.FolioBridge;
  }

  folio.applySettings = function (json) {
    const s = typeof json === 'string' ? JSON.parse(json) : json;
    const root = document.documentElement.style;
    if (s.background) root.setProperty('--folio-bg', s.background);
    if (s.foreground) root.setProperty('--folio-fg', s.foreground);
    if (s.accent) root.setProperty('--folio-accent', s.accent);
    if (s.fontFamily) root.setProperty('--folio-font', s.fontFamily);
    if (s.fontSize) root.setProperty('--folio-size', s.fontSize + 'px');
    if (s.lineHeight) root.setProperty('--folio-line', s.lineHeight);
    if (s.letterSpacing !== undefined) root.setProperty('--folio-letter', s.letterSpacing + 'em');
    if (s.wordSpacing !== undefined) root.setProperty('--folio-word', s.wordSpacing + 'em');
    if (s.paragraphSpacing !== undefined) root.setProperty('--folio-para', s.paragraphSpacing + 'em');
    if (s.margin !== undefined) root.setProperty('--folio-margin', s.margin + 'px');
    if (s.maxWidth) root.setProperty('--folio-max-width', s.maxWidth);
    if (s.fontWeight) root.setProperty('--folio-weight', s.fontWeight);
    if (s.textAlign) root.setProperty('--folio-align', s.textAlign);
    folio.setPaginated(!!s.paginated);
  };

  folio.setPaginated = function (value) {
    if (paginated === value) return;
    paginated = value;
    document.body.classList.toggle('paginated', paginated);
    // Re-anchor after the layout changes so the reader does not lose its place.
    const pos = folio.position();
    requestAnimationFrame(() => folio.goToBlock(pos.block, pos.fraction));
  };

  folio.position = function () {
    const list = blocks();
    if (list.length === 0) return { block: 0, fraction: 0, progress: 0 };
    let current = 0;
    let fraction = 0;
    if (paginated) {
      const scroller = content();
      const left = scroller.scrollLeft;
      const width = scroller.clientWidth || 1;
      for (let i = 0; i < list.length; i++) {
        const rect = list[i].getBoundingClientRect();
        if (rect.right > 0) { current = i; break; }
        current = i;
      }
      const total = Math.max(scroller.scrollWidth - width, 1);
      return { block: current, fraction: 0, progress: Math.min(Math.max(left / total, 0), 1) };
    }
    for (let i = 0; i < list.length; i++) {
      const rect = list[i].getBoundingClientRect();
      if (rect.bottom > 0) {
        current = i;
        fraction = rect.height > 0 ? Math.min(Math.max(-rect.top / rect.height, 0), 1) : 0;
        break;
      }
    }
    const doc = document.documentElement;
    const total = Math.max(doc.scrollHeight - window.innerHeight, 1);
    return { block: current, fraction: fraction, progress: Math.min(Math.max(window.scrollY / total, 0), 1) };
  };

  function report() {
    const pos = folio.position();
    if (bridge() && bridge().onPosition) {
      bridge().onPosition(pos.block, pos.fraction, pos.progress);
    }
  }

  function scheduleReport() {
    if (reportTimer) clearTimeout(reportTimer);
    reportTimer = setTimeout(report, 120);
  }

  folio.goToBlock = function (index, fraction) {
    const list = blocks();
    if (list.length === 0) return;
    const target = list[Math.min(Math.max(index, 0), list.length - 1)];
    if (paginated) {
      const scroller = content();
      const rect = target.getBoundingClientRect();
      const page = scroller.clientWidth + 1;
      scroller.scrollLeft = scroller.scrollLeft + rect.left - parseFloat(getComputedStyle(scroller).paddingLeft || '0');
      // Snap to the start of the column that contains the block.
      scroller.scrollLeft = Math.round(scroller.scrollLeft / page) * page;
    } else {
      const top = target.getBoundingClientRect().top + window.scrollY;
      window.scrollTo(0, top + (fraction || 0) * target.offsetHeight);
    }
    report();
  };

  folio.goToProgress = function (progress) {
    if (paginated) {
      const scroller = content();
      const total = Math.max(scroller.scrollWidth - scroller.clientWidth, 1);
      const page = scroller.clientWidth + 1;
      scroller.scrollLeft = Math.round((total * progress) / page) * page;
    } else {
      const total = Math.max(document.documentElement.scrollHeight - window.innerHeight, 1);
      window.scrollTo(0, total * progress);
    }
    report();
  };

  folio.next = function () {
    if (paginated) {
      const scroller = content();
      scroller.scrollBy({ left: scroller.clientWidth + 1, behavior: 'auto' });
    } else {
      window.scrollBy({ top: window.innerHeight * 0.9, behavior: 'auto' });
    }
    report();
  };

  folio.previous = function () {
    if (paginated) {
      const scroller = content();
      scroller.scrollBy({ left: -(scroller.clientWidth + 1), behavior: 'auto' });
    } else {
      window.scrollBy({ top: -window.innerHeight * 0.9, behavior: 'auto' });
    }
    report();
  };

  // ---- Highlights --------------------------------------------------------

  function textNodesOf(element) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null);
    const nodes = [];
    let node;
    while ((node = walker.nextNode())) nodes.push(node);
    return nodes;
  }

  folio.clearHighlights = function () {
    document.querySelectorAll('mark.folio-hl').forEach((mark) => {
      const parent = mark.parentNode;
      while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
      parent.removeChild(mark);
      parent.normalize();
    });
  };

  folio.applyHighlights = function (json) {
    const items = typeof json === 'string' ? JSON.parse(json) : json;
    folio.clearHighlights();
    const list = blocks();
    items.forEach((item) => {
      const block = list[item.block];
      if (!block) return;
      const nodes = textNodesOf(block);
      let offset = 0;
      nodes.forEach((node) => {
        const length = node.nodeValue.length;
        const nodeStart = offset;
        const nodeEnd = offset + length;
        offset = nodeEnd;
        const from = Math.max(item.start - nodeStart, 0);
        const to = Math.min(item.end - nodeStart, length);
        if (from >= to) return;
        const range = document.createRange();
        range.setStart(node, from);
        range.setEnd(node, to);
        const mark = document.createElement('mark');
        mark.className = 'folio-hl';
        mark.dataset.id = item.id;
        mark.style.backgroundColor = item.color;
        try {
          range.surroundContents(mark);
        } catch (e) {
          /* Skip ranges that cross element boundaries in an unsupported way. */
        }
      });
    });
  };

  // ---- Selection ---------------------------------------------------------

  folio.selectionInfo = function () {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
    const range = selection.getRangeAt(0);
    let node = range.startContainer;
    while (node && (!node.classList || !node.classList.contains('blk'))) node = node.parentNode;
    if (!node) return null;
    const blockIndex = parseInt(node.dataset.i, 10);
    const nodes = textNodesOf(node);
    let start = 0;
    let offset = 0;
    for (const textNode of nodes) {
      if (textNode === range.startContainer) { start = offset + range.startOffset; break; }
      offset += textNode.nodeValue.length;
    }
    const text = selection.toString();
    const rect = range.getBoundingClientRect();
    return JSON.stringify({
      block: blockIndex,
      start: start,
      end: start + text.length,
      text: text,
      rect: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom },
    });
  };

  folio.clearSelection = function () {
    const selection = window.getSelection();
    if (selection) selection.removeAllRanges();
  };

  // ---- Input -------------------------------------------------------------

  document.addEventListener('click', (event) => {
    const mark = event.target.closest && event.target.closest('mark.folio-hl');
    if (mark && bridge() && bridge().onHighlightTap) {
      bridge().onHighlightTap(parseInt(mark.dataset.id, 10));
      return;
    }
    if (window.getSelection && !window.getSelection().isCollapsed) return;
    if (bridge() && bridge().onTap) {
      bridge().onTap(event.clientX / window.innerWidth);
    }
  });

  window.addEventListener('scroll', scheduleReport, { passive: true });
  document.addEventListener('DOMContentLoaded', () => {
    const scroller = content();
    if (scroller) scroller.addEventListener('scroll', scheduleReport, { passive: true });
    if (bridge() && bridge().onReady) bridge().onReady(blocks().length);
  });
})();
