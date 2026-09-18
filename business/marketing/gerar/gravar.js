const { chromium } = require("playwright");
const BASE = process.env.BASE || "http://localhost:3100";
const TEMA = process.env.TEMA || "light";
const MS_POR_FOTOGRAMA = 100;      // 10 fps: suave que chegue, sem inchar o ficheiro
const MS_POR_PASSO = 2400;         // entrada em cascata + tempo para ler a legenda

(async () => {
  const b = await chromium.launch({
    ...(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {}),
    args: ["--force-color-profile=srgb", "--font-render-hinting=none", "--hide-scrollbars"],
  });
  const ctx = await b.newContext({
    viewport: { width: 1280, height: 1280 },
    locale: "pt-PT",
    colorScheme: TEMA,
    deviceScaleFactor: 2,   // captura a 2x e reduz depois: texto nitido no GIF
  });
  await ctx.addInitScript((t) => { try { localStorage.setItem("theme", t); } catch {} }, TEMA);
  const page = await ctx.newPage();
  await page.goto(BASE, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);

  // Esconder o overlay de dev do Next, que não pertence à peça.
  await page.addStyleTag({ content: "nextjs-portal,[data-nextjs-toast],[data-next-badge-root]{display:none!important}" });

  const moldura = page.locator("div.overflow-hidden.rounded-lg").first();
  // O pai contém a moldura e a legenda: é esse o recorte que torna o GIF
  // legível sozinho. Recortar só a moldura deixava a mensagem de fora.
  const bloco = moldura.locator("xpath=..");
  await bloco.scrollIntoViewIfNeeded();
  await page.evaluate(() => window.scrollBy(0, -60));
  await page.waitForTimeout(500);

  const caixa = await bloco.boundingBox();
  const recorte = {
    x: Math.round(caixa.x - 12),
    y: Math.round(caixa.y - 12),
    width: Math.round(caixa.width + 24),
    height: Math.round(caixa.height + 24),
  };
  const vp = page.viewportSize();
  if (recorte.y + recorte.height > vp.height) {
    throw new Error(`recorte (${recorte.height}px) nao cabe na viewport (${vp.height}px) — seria cortado`);
  }

  let n = 0;
  const porPasso = Math.round(MS_POR_PASSO / MS_POR_FOTOGRAMA);
  for (let passo = 0; passo < 5; passo++) {
    await page.locator("[role=tab]").nth(passo).click();
    for (let f = 0; f < porPasso; f++) {
      await page.screenshot({
        path: `frames/${String(n).padStart(4, "0")}.png`,
        clip: recorte,
        animations: "allow",
      });
      n++;
      await page.waitForTimeout(MS_POR_FOTOGRAMA);
    }
  }
  console.log(`${n} fotogramas · recorte ${recorte.width}x${recorte.height} · tema ${TEMA}`);
  await b.close();
})().catch((e) => { console.error("FALHOU:", e.message); process.exit(1); });
