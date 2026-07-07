---
phase: 83-frontend-tipos-schemas-e-hooks
plan: 01
subsystem: types
tags: [typescript, zod, react-hook-form, contracts]

# Dependency graph
requires:
  - phase: 81-backend-decisao-facto-testemunha
    provides: 12 CRUD endpoints for Decisao/Facto/Testemunha + juizo/origem wired into Processo create/update/intake
provides:
  - "TypeScript contracts (Decisao/Facto/Testemunha + Processo.juizo/origem) in web/src/types/processos.ts"
  - "Zod form schemas (decisaoFormSchema/factoFormSchema/testemunhaFormSchema/processoIntakeFormSchema) in web/src/schemas/processos.ts"
  - "PT presentation label maps (tipoDecisaoToLabel/tipoTestemunhaToLabel/origemProcessoToLabel) in web/src/lib/"
affects: [83-02-hooks, 84-frontend-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "processoIntakeFormSchema = processoFormSchema.extend({ origem: origemProcessoSchema }) — origem is a required z.enum only in the intake flow, absent from the general edit schema (matches backend's silent-ignore-on-update behavior)"
    - "ProcessoUpdateRequest deliberately omits origem (not just optional) so callers cannot type-check a PUT payload containing it"
    - "Flat Record<Enum,string>-plus-function label-map pattern (per conflict-check.ts) replicated 3x for TipoDecisao/TipoTestemunha/OrigemProcesso"

key-files:
  created:
    - web/src/lib/tipo-decisao.ts
    - web/src/lib/tipo-testemunha.ts
    - web/src/lib/origem-processo.ts
  modified:
    - web/src/types/processos.ts
    - web/src/schemas/processos.ts

key-decisions:
  - "DecisaoUpdateRequest excludes documentoId/file — backend PUT handler only reads data/tipo/resumo from a Map<String,Object> (WR-03 fix from Phase 81 second review); attachment is create-only"
  - "FactoCreateRequest omits ordem (server-computed on creation); FactoUpdateRequest requires ordem (backend returns 400 if null) — asymmetric shape captured intentionally"
  - "decisaoFormSchema.file uses fileListSchema.optional() (no length===1 refine) since the Decisao attachment is optional, unlike documentoUploadFormSchema's required file"

patterns-established:
  - "Enum-with-label pattern: wire values are ASCII z.enum in schemas/*.ts + TS union types in types/*.ts; PT display labels live only in lib/*.ts, never inline in components"

requirements-completed: []

# Metrics
duration: ~10min
completed: 2026-07-07
---

# Phase 83 Plan 01: Frontend Tipos e Schemas Summary

**TypeScript types and Zod schemas for Decisao/Facto/Testemunha plus Processo.juizo/origem, with origem promoted to a required enum only in the intake flow and three new PT label-map files.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-07-07T22:53:26Z
- **Completed:** 2026-07-07T22:57:01Z
- **Tasks:** 2 completed
- **Files modified:** 5 (2 modified, 3 created)

## Accomplishments
- `web/src/types/processos.ts` now exports `Decisao`/`DecisaoCreateRequest`/`DecisaoUpdateRequest`, `Testemunha`/`TestemunhaCreateRequest`/`TestemunhaUpdateRequest`, `Facto`/`FactoCreateRequest`/`FactoUpdateRequest`, and `TipoDecisao`/`TipoTestemunha`/`OrigemProcesso` union types, matching the exact wire shape documented in `83-PATTERNS.md` (camelCase, no phantom snake_case fields).
- `Processo`/`ProcessoCreateRequest` gained `juizo?`/`origem?`; `ProcessoUpdateRequest` gained only `juizo?` — the compile-time absence of `origem` on the update type enforces immutability post-intake ahead of Plan 83-02's hook layer.
- `web/src/schemas/processos.ts` now exports `decisaoFormSchema`, `factoFormSchema`, `testemunhaFormSchema`, `origemProcessoSchema`, and `processoIntakeFormSchema` (which extends the general `processoFormSchema` to make `origem` a required closed enum, without touching the edit-flow schema).
- Three new PT label-map files (`tipo-decisao.ts`, `tipo-testemunha.ts`, `origem-processo.ts`) follow the existing `conflict-check.ts` flat-`Record`-plus-function pattern, keeping wire values (ASCII) and display labels (accented PT) in separate layers.

## Task Commits

Each task was committed atomically:

1. **Task 1: Tipos TypeScript — Processo.juizo/origem + Decisao/Facto/Testemunha** - `bb05ca5` (feat)
2. **Task 2: Schemas Zod + label maps PT** - `a933b54` (feat)

**Plan metadata:** (pending — this SUMMARY.md commit)

## Files Created/Modified
- `web/src/types/processos.ts` - Added TipoDecisao/TipoTestemunha/OrigemProcesso unions, Processo/ProcessoCreateRequest/ProcessoUpdateRequest juizo/origem fields, Decisao/Facto/Testemunha interface groups
- `web/src/schemas/processos.ts` - Added tipoDecisaoSchema/tipoTestemunhaSchema/origemProcessoSchema enums, processoFormSchema.juizo, processoIntakeFormSchema, decisaoFormSchema/testemunhaFormSchema/factoFormSchema, local fileListSchema
- `web/src/lib/tipo-decisao.ts` - `tipoDecisaoToLabel()` PT label map
- `web/src/lib/tipo-testemunha.ts` - `tipoTestemunhaToLabel()` PT label map
- `web/src/lib/origem-processo.ts` - `origemProcessoToLabel()` PT label map

## Decisions Made
None beyond what the plan already specified — plan's field shapes (sourced from `83-PATTERNS.md`'s live-controller extraction) were followed exactly, including the deliberate asymmetries (DecisaoUpdateRequest excludes documentoId/file; FactoCreateRequest excludes ordem while FactoUpdateRequest requires it; ProcessoUpdateRequest excludes origem entirely).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 83-02 (hooks) can now consume these contracts directly: it will build `useDecisoes`/`useFactos`/`useTestemunhas` TanStack Query hooks against the typed request/response shapes here, and can use the TypeScript `in` operator against `ProcessoUpdateRequest` to prove at compile time that PUT calls never send `origem`. No blockers.

---
*Phase: 83-frontend-tipos-schemas-e-hooks*
*Completed: 2026-07-07*

## Self-Check: PASSED

All created/modified files exist on disk; both task commits (`bb05ca5`, `a933b54`) verified present in git log.
