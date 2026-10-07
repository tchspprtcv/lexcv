---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 06
subsystem: backend/fiscal pdf
tags: [pdf, openhtmltopdf, pdfbox, ssrf, markup-injection, simulation-mark]
requires: [137-01]
provides:
  - ClasspathPdfResolver {PREFIXO, CSS, resolver(uri, base), lerRecurso, relativoDeCanonico}
  - FormatacaoFiscal {dinheiro(BigDecimal, moeda), data(LocalDate)} (public, shared with 137-14)
  - DadosPdfDocumentoFiscal (+ Emitente, Adquirente, Linha, Totais, Origem; de(doc, linhas, iud, origem))
  - ModeloPdfDocumentoFiscal {MARCA_SIMULACAO, titulo, xhtml}
  - PdfDocumentoFiscalRenderer.renderizar(DadosPdfDocumentoFiscal) -> byte[] (@Component)
  - FalhaGeracaoPdf (unchecked, fixed message)
  - pdf/documento-fiscal.css
affects: [137-10 (storage/generation timing), 137-14 (email composer uses FormatacaoFiscal)]
tech-stack:
  added: []
  patterns: [whitelist URI resolver on a synthetic base, fail-fast @Component loading resources once, single escape path]
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/pdf/ClasspathPdfResolver.java
    - backend/src/test/java/com/lexcv/fiscal/pdf/ClasspathPdfResolverTest.java
    - backend/src/main/java/com/lexcv/fiscal/pdf/FormatacaoFiscal.java
    - backend/src/test/java/com/lexcv/fiscal/pdf/FormatacaoFiscalTest.java
    - backend/src/main/java/com/lexcv/fiscal/pdf/DadosPdfDocumentoFiscal.java
    - backend/src/main/java/com/lexcv/fiscal/pdf/ModeloPdfDocumentoFiscal.java
    - backend/src/main/resources/pdf/documento-fiscal.css
    - backend/src/main/java/com/lexcv/fiscal/pdf/PdfDocumentoFiscalRenderer.java
    - backend/src/main/java/com/lexcv/fiscal/pdf/FalhaGeracaoPdf.java
    - backend/src/test/java/com/lexcv/fiscal/pdf/PdfDocumentoFiscalRendererTest.java
  modified: []
decisions:
  - "PDFBox's global FontMapper is replaced, once, by a classpath-only mapper. It serves DejaVu Sans for the Standard-14 fallback fonts that OpenHTMLtoPDF instantiates, so there is no system font scan and no ~/.pdfbox.cache write. The mappings report isFallback=false to avoid 14 WARN lines per render. None of these fonts end up in the PDF."
  - "A synthetic 'classpath' stream protocol serves only the preloaded CSS bytes. file, jar, http, https, ftp and data, plus the HTTP implementation, get factories that return null."
  - "Watermark is two lines ('SIMULAÇÃO —' / 'SEM VALIDADE FISCAL'), following the UI-SPEC fallback. On one line, 44pt bold was clipped on both sides of the page. The full extractable string is in the running header band on every page."
  - "OpenHTMLtoPDF ignores opacity, word-break and overflow-wrap. The watermark uses the pre-blended colour #edeef1 (#9ca3af at 0.18 on white), and the IUD uses word-wrap: break-word."
  - "DadosPdfDocumentoFiscal.Emitente has an extra boolean isento (regime == ISENTO), which drives the 'IVA: isento' and exemption-statement branches"
  - "simulado is fail-safe: true when ambiente is null or SIMULADO"
metrics:
  duration: ~45min
  completed: 2026-10-07
  tasks: 3
  files: 10
---

# Phase 137 Plan 06: Hardened classpath-only PDF renderer Summary

This plan adds the renderer for Fatura-Recibo and Nota de Crédito PDFs, built with OpenHTMLtoPDF 1.1.87 and PDFBox 3.0.7.
- **Input:** only the stored snapshot.
- **Simulation mark:** every page carries the header band "SIMULAÇÃO — SEM VALIDADE FISCAL" and a diagonal watermark.
- **Fonts:** only the three DejaVu fonts, embedded.
- **No outside access:** all network and filesystem resource loading is refused, through a whitelist resolver and refusing stream factories.
- **Text safety:** snapshot text is always HTML-escaped.

Money and date formatting lives in the public `FormatacaoFiscal` helper, which the email composer (137-14) will reuse.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | ClasspathPdfResolver | RED ed65a80, GREEN 540a520 |
| 2 | FormatacaoFiscal + DadosPdfDocumentoFiscal + ModeloPdfDocumentoFiscal + CSS | a9ce7da |
| 3 | PdfDocumentoFiscalRenderer + FalhaGeracaoPdf | RED 18c1b97, GREEN f03df3c |

## Verification

- `ClasspathPdfResolverTest`: **32/32**. The whitelist is exactly the 4 resources. Rejected: http/https/file/data/jar/ftp, `../` escapes, non-whitelisted names, query/fragment, authority, opaque `classpath:` URIs, null input and bases outside the prefix.
- `FormatacaoFiscalTest`: **4/4**. Covers all plan cases plus EUR, a blank currency and HALF_EVEN, identical under Locale US and GERMANY.
- `PdfDocumentoFiscalRendererTest`: **12/12**.
  - FR shows all legal elements, uses the U+202F thousands separator, and shows the retention line as "- 2 000,00 CVE".
  - Exempt FR shows "Isento", "IVA: isento" and "Motivo de isenção: 02 — ….", and has no "IVA (" line.
  - NC shows origin, date, motivo, description and "Total a crédito".
  - The 60-line fixture renders at least 2 pages, and every page has the mark, "Página n de N", the footer text and the repeated thead.
  - Every font is embedded DejaVu.
  - Title and Producer metadata are set.
  - An `<img src=http://127.0.0.1:9/...>` name and a `<link href=file:///etc/hostname>` description both appear as literal text. The resolver spy only saw whitelisted URIs.
  - No "autorizado", "aprovado" or "validado" anywhere.
  - The FontMapper is the classpath one.
  - A missing CSS fails at startup with the fixed message.
- `FontesPdfIntegridadeTest` (137-01) is still **4/4**.
- `mvn -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check`: clean, with no new exclusion.
- Task 2 grep gates pass: no `url(`/`@import` in the CSS, no authorisation wording in the model.
- Renders produce zero WARN lines (no CSS warnings, no font-fallback warnings).
- I checked FR, exempt FR and NC page 1 by eye, rendered to PNG at 70 DPI in the scratchpad (not committed). Layout follows UI-SPEC 6a, and the watermark and document box fit inside the page.

## Deviations from Plan

1. **[Rule 2 - Security] System font scan disabled.** Without this, PDFBox's default FontMapper scans system fonts and writes `~/.pdfbox.cache` (137-SPIKE B, finding 3). That breaks the must-have "rendering never touches the filesystem". `MapeadorFontesClasspath` is installed once; this is the option the spike proposed.
2. **[Rule 3 - Blocking] `classpath` stream protocol.** The resolver returns canonical `classpath:pdf/...` URIs, which `java.net.URL` cannot open. A protocol factory serves the preloaded CSS bytes and nothing else.
3. **[Rule 1 - Bug] CSS properties the renderer ignores.** `opacity`, `word-break` and `overflow-wrap` are unsupported, so I replaced them with supported equivalents (pre-blended colour, `word-wrap`). Without this the watermark would print at full grey `#9ca3af` over the content.
4. **[Rule 1 - Bug] Watermark clipped.** Rendered on one line, it was clipped on both sides of the page. I applied the UI-SPEC fallback: break after the dash into two lines.
5. **[Rule 1 - Bug] Document box overflow.** The box border sat outside the right margin. The padding and border moved to an inner `div.caixa`, and the table uses `table-layout: fixed`.
6. **NC origin number in plain text.** It is no longer wrapped in a mono span. The extra font run caused PDFTextStripper to read the sentence out of order; the UI-SPEC does not require mono for this line.
7. **Extra file `FalhaGeracaoPdf.java`.** The plan names the exception but does not list its file.
8. **`Emitente.isento` added** (see decisions).

## Open point for review

UI-SPEC totals order is: Base, IVA, "- Retenção", then "Total do documento". "Total do documento" prints `totalDocumento`, which is base + IVA, as the plan's `Totais` record defines. The retention is shown but not deducted, and the net amount (`valorLiquido`) is not printed. This follows the plan literally. If the line after the retention should be the net amount, that is a copy/UI-SPEC decision.

## Self-Check: PASSED
