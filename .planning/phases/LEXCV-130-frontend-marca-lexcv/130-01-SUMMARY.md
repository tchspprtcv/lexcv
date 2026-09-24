---
phase: 130-frontend-marca-lexcv
plan: 01
completed: 2026-09-24
---

# Phase 130 Plan 01 Summary

Substituída a marca "ALCv" por "LexCV" em 10 ficheiros de `web/` (títulos, metadata, breadcrumbs,
fallback de nome de tenant, `TENANT_RESERVADO`) e 5 de `webpage/` (landing pública: layout, marca,
footer, branding fallback, email de contacto), mais os 3 scripts de verificação manual do
frontend, mantidos sincronizados com o novo literal de `columns.tsx`.

**Deviations from plan:** Nenhuma.

**Verification commands run:**
- `grep -rn -i alcv <ficheiros-alvo>` — zero ocorrências remanescentes
- `pnpm build` em `web/` — sucesso, 27 rotas compiladas, TypeScript limpo
- `pnpm build` em `webpage/` — sucesso, 2 rotas compiladas, TypeScript limpo
- `node web/scripts/verify-consola-tenants.mjs` — 12/12 PASS
- `node web/scripts/verify-papeis-escritorio.mjs` — 18/18 PASS
- `node web/scripts/verify-relatorio-utilizacao.mjs` — 15/15 PASS
