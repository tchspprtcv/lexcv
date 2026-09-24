---
phase: 130-frontend-marca-lexcv
verified: 2026-09-24
status: passed
score: 4/4 roadmap success criteria verified (automated evidence)
overrides_applied: 0
---

# Phase 130: Frontend — Marca LexCV Verification Report

**Phase Goal:** Todo o texto de marca visível ao utilizador nas apps `web/` e `webpage/`, e os
scripts de verificação manual do frontend, usam "LexCV".
**Verified:** 2026-09-24
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | App `web/` mostra "LexCV" em título, metadata e texto de marca visível | ✓ VERIFIED | `grep -rn -i alcv` nos 10 ficheiros alvo devolve zero resultados; `grep -c LexCV web/src/app/layout.tsx` = 1 (`title: "LexCV"`) |
| 2 | App `webpage/` mostra "LexCV" nos componentes/libs de marca | ✓ VERIFIED | `grep -rn -i alcv` nos 5 ficheiros alvo devolve zero resultados; `brand-mark.tsx`/`branding.ts`/`layout.tsx`/`site-footer.tsx` confirmados com "LexCV" |
| 3 | `pnpm build` corre limpo em `web/` e em `webpage/` | ✓ VERIFIED | `web/`: "Compiled successfully in 8.4s", TypeScript limpo, 27/27 rotas geradas. `webpage/`: "Compiled successfully in 3.5s", TypeScript limpo, 2/2 rotas geradas |
| 4 | Os 3 scripts de verificação referem "LexCV" e continuam a correr sem erro | ✓ VERIFIED | `verify-consola-tenants.mjs` 12/12 PASS (exit 0); `verify-papeis-escritorio.mjs` 18/18 PASS (exit 0); `verify-relatorio-utilizacao.mjs` 15/15 PASS (exit 0) |

**Score:** 4/4 ROADMAP success criteria verified by build output and script exit codes.

### Additional Notes

- `TENANT_RESERVADO` em `plataforma/columns.tsx` renomeado de `"ALCv"` para `"LexCV"` — este valor
  tem de ficar sincronizado com o tenant reservado que a Phase 131 renomeia no backend
  (`DatabaseSeeder`); até essa fase correr, o valor frontend e o valor backend ficam
  transitoriamente desalinhados, o que é aceitável dentro da mesma sessão de execução do marco
  (nenhum ambiente com tráfego real entre as duas fases).
- `contacto@alcv.cv` → `contacto@lexcv.cv` em `webpage/src/lib/contacto.ts`, pelo mesmo raciocínio
  já aplicado na Phase 129 ao email `admin@alcv.cv`/`admin@lexcv.cv` (domínio `*.cv` tratado como
  derivado da marca no código deste projeto, distinto do domínio real `alcv.tech` preservado).

## Human Verification Required

Nenhuma — verificação por build determinístico (TypeScript + geração de páginas) e scripts de
análise estática com asserções byte-exatas; nenhuma alteração visual/CSS foi introduzida (apenas
texto).
