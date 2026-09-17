// Shared theme + layout helpers for the ALCv manual deck.
const path = require("path");
const IMG = path.join(__dirname, "..", "images") + path.sep;

const C = {
  bg:       "0E1726",  // deep navy — dominant
  bgDeep:   "070D18",  // dividers / title
  surface:  "18233A",  // cards
  surface2: "1E2B45",  // raised cards
  line:     "2C3B59",  // hairlines
  gold:     "D4A94A",  // accent
  goldDim:  "8A6E27",
  ice:      "8FB4E3",  // secondary
  text:     "F2F5FA",
  muted:    "9AAAC2",
  red:      "E06A63",
  green:    "57C08A",
};

const F = { head: "Cambria", body: "Calibri" };

// pptxgenjs mutates option objects in place — always hand it a fresh one.
const sh = (o = {}) => ({ type: "outer", color: "000000", blur: 14, offset: 4, angle: 90, opacity: 0.45, ...o });

const W = 13.3, H = 7.5, M = 0.6;

/** Dark background + the deck's one repeated motif: a thin gold rule in the top-left corner block. */
function base(pres) {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  return s;
}

/** Kicker + title header used by every content slide. */
function header(s, kicker, title) {
  s.addText(kicker.toUpperCase(), {
    x: M, y: 0.40, w: W - 2 * M, h: 0.26, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 11, bold: true, color: C.gold, charSpacing: 1.4,
  });
  s.addText(title, {
    x: M, y: 0.68, w: W - 2 * M, h: 0.76, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 30, bold: true, color: C.text, valign: "top",
  });
}

/** Screenshot with a rounded mat + caption. Returns nothing. */
function shot(s, file, { x, y, w, caption }) {
  const h = w * (900 / 1440);
  s.addShape("roundRect", {
    x: x - 0.10, y: y - 0.10, w: w + 0.20, h: h + 0.20, rectRadius: 0.06,
    fill: { color: C.surface2 }, line: { color: C.line, width: 1 }, shadow: sh(),
  });
  s.addImage({ path: IMG + file, x, y, w, h });
  if (caption) {
    s.addText(caption, {
      x, y: y + h + 0.20, w, h: 0.34, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 10, italic: true, color: C.muted,
    });
  }
  return h;
}

/** Numbered "icon + text" rows — the workhorse text layout. */
function rows(s, items, { x, y, w, gap = 0.86, numColor = C.gold }) {
  items.forEach((it, i) => {
    const ry = y + i * gap;
    s.addShape("ellipse", {
      x, y: ry, w: 0.34, h: 0.34,
      fill: { color: numColor === C.gold ? C.gold : numColor },
    });
    s.addText(String(i + 1), {
      x, y: ry, w: 0.34, h: 0.34, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12, bold: true, color: C.bgDeep, align: "center", valign: "middle",
    });
    const tH = estLines(it.t, w - 0.50, 13.5, true) * lineH(13.5);
    s.addText(it.t, {
      x: x + 0.50, y: ry - 0.04, w: w - 0.50, h: Math.max(0.28, tH), isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 13.5, bold: true, color: C.text, lineSpacingMultiple: 0.95,
    });
    if (it.d) {
      s.addText(it.d, {
        x: x + 0.50, y: ry - 0.04 + Math.max(0.28, tH + 0.02), w: w - 0.50, h: 0.52,
        isTextBox: true, margin: 0, valign: "top",
        fontFace: F.body, fontSize: 11, color: C.muted, lineSpacingMultiple: 0.95,
      });
    }
  });
}


/* --- Text measurement -------------------------------------------------------
   LibreOffice/PowerPoint wrap text for us, but we have to place the NEXT
   element, so we estimate how many lines a run will take. Widths are a
   fraction of the font size, calibrated for Calibri. */
const CHAR_W = { normal: 0.55, bold: 0.63 };

function estLines(text, widthIn, fontSize, bold) {
  const cw = (fontSize * (bold ? CHAR_W.bold : CHAR_W.normal)) / 72;
  const maxChars = Math.max(4, Math.floor(widthIn / cw));
  let lines = 1, cur = 0;
  for (const word of String(text).split(/\s+/).filter(Boolean)) {
    if (cur === 0) cur = word.length;
    else if (cur + 1 + word.length <= maxChars) cur += 1 + word.length;
    else { lines++; cur = word.length; }
    while (cur > maxChars) { lines++; cur -= maxChars; }
  }
  return lines;
}

const lineH = (fontSize, mult = 1.22) => (fontSize * mult) / 72;

/** Card grid. items: {t, d, tag?}. */
function cards(s, items, opts) {
  const { x, y, w, cols, gapX = 0.30, gapY = 0.28, accent = C.gold,
          titleSize = 14, descSize = 11, centerIn } = opts;
  const innerW = w - 0.52;
  const PAD_T = 0.22, PAD_B = 0.24, GAP_TD = 0.08, TAG_H = 0.26;

  // One title block for the whole grid, sized to the longest title, so every
  // description starts on the same baseline.
  const titleLines = Math.max(...items.map((it) => estLines(it.t, innerW, titleSize, true)));
  const titleH = titleLines * lineH(titleSize);

  const need = items.map((it) => {
    let n = PAD_T + (it.tag ? TAG_H : 0) + titleH;
    if (it.d) n += GAP_TD + estLines(it.d, innerW, descSize, false) * lineH(descSize, 1.16);
    return n + PAD_B;
  });
  const h = opts.h && opts.h !== "auto" ? opts.h : Math.max(...need);

  const rowCount = Math.ceil(items.length / cols);
  const blockH = rowCount * h + (rowCount - 1) * gapY;
  const y0 = centerIn ? centerIn[0] + (centerIn[1] - centerIn[0] - blockH) / 2 : y;

  items.forEach((it, i) => {
    const cx = x + (i % cols) * (w + gapX);
    const cy = y0 + Math.floor(i / cols) * (h + gapY);
    s.addShape("roundRect", {
      x: cx, y: cy, w, h, rectRadius: 0.05,
      fill: { color: C.surface }, line: { color: C.line, width: 1 },
      shadow: sh({ blur: 10, offset: 3, opacity: 0.35 }),
    });
    let ty = cy + PAD_T;
    if (it.tag) {
      s.addText(it.tag.toUpperCase(), {
        x: cx + 0.26, y: ty, w: innerW, h: 0.22, isTextBox: true, margin: 0, valign: "top",
        fontFace: F.body, fontSize: 9.5, bold: true, color: accent, charSpacing: 1.1,
      });
      ty += TAG_H;
    }
    const tH = titleH;
    s.addText(it.t, {
      x: cx + 0.26, y: ty, w: innerW, h: tH, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: titleSize, bold: true, color: C.text, lineSpacingMultiple: 0.95,
    });
    if (it.d) {
      s.addText(it.d, {
        x: cx + 0.26, y: ty + tH + GAP_TD, w: innerW,
        h: cy + h - (ty + tH + GAP_TD) - PAD_B + 0.06,
        isTextBox: true, margin: 0, valign: "top",
        fontFace: F.body, fontSize: descSize, color: C.muted, lineSpacingMultiple: 0.95,
      });
    }
  });
  return { h, blockH, y0 };
}

/** Callout box for IMPORTANT / WARNING / NOTE blocks from the manual. */
function callout(s, { x, y, w, h, label, text, tone = C.gold }) {
  s.addShape("roundRect", {
    x, y, w, h, rectRadius: 0.05,
    fill: { color: C.surface2 }, line: { color: tone, width: 1.25 },
  });
  s.addText(label.toUpperCase(), {
    x: x + 0.26, y: y + 0.18, w: w - 0.52, h: 0.24, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 10, bold: true, color: tone, charSpacing: 1.2,
  });
  s.addText(text, {
    x: x + 0.26, y: y + 0.46, w: w - 0.52, h: h - 0.66, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 11.5, color: C.text, lineSpacingMultiple: 0.98, valign: "top",
  });
}

module.exports = { C, F, W, H, M, IMG, sh, base, header, shot, rows, cards, callout, estLines, lineH };
