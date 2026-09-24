---
phase: 129-identidade-e-documentacao
verified: 2026-09-24
status: passed
score: 3/3 roadmap success criteria verified (automated evidence)
overrides_applied: 0
---

# Phase 129: Identidade e Documentação Verification Report

**Phase Goal:** Toda a documentação técnica ativa, o manual do utilizador (e os scripts que o
geram) e a documentação comercial referem o produto como "LexCV", não "ALCv".
**Verified:** 2026-09-24
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `CLAUDE.md`, `DEPLOYMENT.md`, `backend/migrations/README.md`, `.trae/documents/SPEC.md` referem "LexCV"; busca por "alcv" não devolve ocorrência de marca | ✓ VERIFIED | `grep -c -i alcv` nesses 4 ficheiros só devolve o domínio real `alcv.tech` em `DEPLOYMENT.md` (linha 84, preservado por decisão explícita); zero ocorrências do token de marca "ALCv" em qualquer um dos 4 |
| 2 | `docs/MANUAL_DO_UTILIZADOR.md` e os scripts geradores referem "LexCV"; regenerar produz saída com "LexCV" sem erros | ✓ VERIFIED | `python3 docs/html/build.py` → exit 0, `MANUAL_DO_UTILIZADOR.html` regenerado com 12x "LexCV"/0x "ALCv"; `node docs/pptx/build.js` → exit 0, `MANUAL_DO_UTILIZADOR.pptx` regenerado com 4x "LexCV"/0x "ALCv" (verificado via inspeção do XML interno dos slides) |
| 3 | `business/README.md`, `business/documentacao/*`, `business/propostas/*`, `business/scripts/gerar-docx.py` referem "LexCV" | ✓ VERIFIED | `grep -rn -i alcv business/` devolve zero resultados; `python3 business/scripts/gerar-docx.py` regenerou os 6 `.docx` de `business/documentacao/` com `verificacao estrutural OK` |

**Score:** 3/3 ROADMAP success criteria verified by direct inspection and command output.

### Additional Notes

- `admin@alcv.cv` (documentado em `CLAUDE.md` e no manual) foi corrigido para `admin@lexcv.cv`,
  alinhando a documentação com o valor real já semeado por `DatabaseSeeder.java:100` — não era uma
  string de marca que precisasse de decisão, era um doc desatualizado face ao código.
- Domínio real de produção `alcv.tech`/`www.alcv.tech` (4 ficheiros: `DEPLOYMENT.md`,
  `MANUAL_DO_UTILIZADOR.md`, `docs/html/build.py`, `docs/pptx/build.js`) mantido inalterado por
  decisão explícita do utilizador durante `/gsd:new-milestone` — confirmado como o único resíduo
  "alcv" restante nestes ficheiros.
- `business/propostas/plano-financeiro.docx`, `business/especificacoes/especificacao-parecer-juridico.docx`,
  `business/contratos/ficha-cliente.docx`, `business/contratos/termo-honorarios.docx` inspecionados
  diretamente (XML interno) — zero ocorrências de "ALCv", sem alteração necessária.

## Human Verification Required

Nenhuma — alterações são texto estático em documentação e artefactos derivados regenerados
deterministicamente; nada depende de runtime/DB/browser.
