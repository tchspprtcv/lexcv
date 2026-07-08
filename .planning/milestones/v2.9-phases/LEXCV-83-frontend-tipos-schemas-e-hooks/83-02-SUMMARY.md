---
phase: LEXCV-83-frontend-tipos-schemas-e-hooks
plan: "02"
subsystem: api
tags: [tanstack-query, typescript, react, hooks, nextjs]

# Dependency graph
requires:
  - phase: LEXCV-83-01
    provides: Decisao/Testemunha/Facto types, Zod schemas, Processo.juizo/origem types
provides:
  - "web/src/lib/processo-juizo-origem-mapping.ts — módulo partilhado sem path-alias com mapJuizoOrigemFromApi/mapJuizoOrigemToPayload"
  - "12 hooks CRUD TanStack Query para Decisão/Testemunha/Facto em web/src/hooks/use-processos.ts"
  - "normalizeProcesso/toProcessoApiPayload exportados, delegando juizo/origem ao módulo partilhado"
  - "verificação de round-trip real e executável (script Node) + spec vitest durável"
affects: [LEXCV-84-frontend-ui-processos]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Módulo partilhado sem path-alias (web/src/lib/processo-juizo-origem-mapping.ts) consumido tanto por código runtime (@/lib/...) como por um script Node puro (import relativo .ts direto), evitando reimplementação de lógica de mapeamento"
    - "Node >=22 executa .ts diretamente via type-stripping nativo — usado para prova de round-trip sem instalar test runner"

key-files:
  created:
    - web/src/lib/processo-juizo-origem-mapping.ts
    - web/src/hooks/use-processos.round-trip.test.ts
    - web/scripts/verify-juizo-origem-roundtrip.mjs
  modified:
    - web/src/hooks/use-processos.ts

key-decisions:
  - "juizo/origem mapping centralizado num módulo partilhado dedicado em vez de inline em normalizeProcesso/toProcessoApiPayload, para que a mesma lógica seja importada (não copiada) pelo script de verificação"
  - "Verificação de round-trip real implementada como script Node puro (node:assert) em vez de instalar vitest — repo continua sem test runner (precedente Phase 74/82)"

patterns-established:
  - "Quarteto list/create/update/delete replicado 3x (Decisão/Testemunha/Facto) seguindo exatamente a estrutura de useProcessoFases/useAddProcessoFase/useUpdateProcessoFaseStatus + padrão delete de useDeleteDocumento"

requirements-completed: []

# Metrics
duration: ~6min
completed: 2026-07-07
---

# Phase 83 Plan 02: Hooks CRUD Decisão/Testemunha/Facto + mapeamento juizo/origem Summary

**12 hooks TanStack Query novos (list/create/update/delete x Decisão/Testemunha/Facto) e mapeamento juizo/origem centralizado num módulo partilhado, com prova de round-trip real executável via Node puro que importa esse mesmo módulo.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-07-07T22:59:23Z
- **Completed:** 2026-07-07T23:04:09Z
- **Tasks:** 2 completed
- **Files modified:** 4 (1 modified, 3 created)

## Accomplishments
- Criado `web/src/lib/processo-juizo-origem-mapping.ts`, módulo partilhado sem imports `@/...`, exportando `mapJuizoOrigemFromApi`/`mapJuizoOrigemToPayload`
- `normalizeProcesso`/`toProcessoApiPayload` em `use-processos.ts` agora exportados e delegam o mapeamento juizo/origem a esse módulo (fecha PITFALLS.md Pitfall 1 pela 4ª vez, desta vez com verificação real)
- 12 hooks CRUD novos adicionados a `use-processos.ts`: `useDecisoes/useAddDecisao/useUpdateDecisao/useDeleteDecisao`, `useTestemunhas/useAddTestemunha/useUpdateTestemunha/useDeleteTestemunha`, `useFactos/useAddFacto/useUpdateFacto/useDeleteFacto`
- `useDeleteProcesso` estendido para limpar as caches `["processos", "decisoes", id]`, `["processos", "testemunhas", id]`, `["processos", "factos", id]`
- Prova de round-trip real e executável: `web/scripts/verify-juizo-origem-roundtrip.mjs` importa diretamente o módulo partilhado (sem reimplementar nenhuma linha) e confirma exit 0 + `PASS`
- Spec vitest durável (`use-processos.round-trip.test.ts`) criado para quando um test runner for instalado, seguindo o precedente da Phase 74

## Task Commits

Each task was committed atomically:

1. **Task 1: Hooks CRUD Decisão/Testemunha/Facto + módulo partilhado de mapeamento juizo/origem** - `81131b4` (feat)
2. **Task 2: Verificação de round-trip juizo/origem (spec vitest durável + script Node executável)** - `f07fe89` (test)

**Plan metadata:** pending (this commit)

## Files Created/Modified
- `web/src/lib/processo-juizo-origem-mapping.ts` - módulo partilhado sem path-alias com `mapJuizoOrigemFromApi`/`mapJuizoOrigemToPayload`
- `web/src/hooks/use-processos.ts` - 12 hooks novos + `normalizeProcesso`/`toProcessoApiPayload` exportados e delegando juizo/origem; `useDeleteProcesso` limpa 3 novas caches
- `web/src/hooks/use-processos.round-trip.test.ts` - spec vitest durável (não executável nesta repo — sem test runner instalado)
- `web/scripts/verify-juizo-origem-roundtrip.mjs` - prova Node ESM executável, importa diretamente o módulo partilhado

## Decisions Made
- Nenhuma decisão nova além das já registadas na plan (módulo partilhado dedicado; verificação via script Node puro em vez de vitest). Executado exatamente conforme a plan revista.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. `node web/scripts/verify-juizo-origem-roundtrip.mjs` emite um aviso não-fatal do Node (`MODULE_TYPELESS_PACKAGE_JSON`) por `web/package.json` não ter `"type": "module"` — não afeta o resultado (`PASS`, exit code 0) e não é um erro; não foi corrigido por estar fora do âmbito desta task (alterar `package.json` teria raio de impacto sobre todo o build/dev do frontend).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 84 (frontend UI) pode agora consumir os 12 hooks novos e os campos `juizo`/`origem` sem risco de regressão de mapeamento — a verificação de round-trip real garante que qualquer quebra futura no mapeamento é detetada automaticamente
- `web/src/lib/processo-juizo-origem-mapping.ts` é o único ponto de manutenção para lógica juizo/origem; qualquer alteração futura deve continuar a ser feita ali, nunca inline em `normalizeProcesso`/`toProcessoApiPayload`

---
*Phase: LEXCV-83-frontend-tipos-schemas-e-hooks*
*Completed: 2026-07-07*

## Self-Check: PASSED

All created files verified present on disk; both task commits (81131b4, f07fe89) verified present in git history.
