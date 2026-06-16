---
phase: 34-processos-timeline-e-auditoria
plan: "02"
subsystem: web
tags: [react, tanstack-query, typescript, timeline, audit-log, hooks]

# Dependency graph
requires:
  - phase: 34-processos-timeline-e-auditoria
    plan: "01"
    provides: GET /processos/{id}/timeline and GET /processos/{id}/audit endpoints in Spring Boot backend
provides:
  - TimelineItemType union type (5 members)
  - TimelineItem TypeScript interface
  - AuditLogEntry TypeScript interface
  - useTimeline(processoId) TanStack Query hook (staleTime 15_000)
  - useAuditLog(processoId) TanStack Query hook (staleTime 30_000)
  - Extended cache invalidations in useExecutarTransicao and useAddProcessoMovimentacao
affects:
  - 34-03 (UI tab restructuring — Timeline tab consumes useTimeline; Auditoria tab consumes useAuditLog)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Read hook following usePrazos pattern: enabled guard + encodeURIComponent + staleTime"
    - "Dual cache invalidation in mutation onSuccess: movimentacoes + timeline in parallel Promise.all"
    - "Sequential invalidation in useAddProcessoMovimentacao.onSuccess using await chain"

key-files:
  created: []
  modified:
    - web/src/types/processos.ts
    - web/src/hooks/use-processos.ts

key-decisions:
  - "useAuditLog staleTime set to 30_000 (vs 15_000 for useTimeline) — audit log changes less frequently than the operational timeline; longer stale window reduces API calls for the compliance surface"
  - "Timeline invalidation added to useAddProcessoMovimentacao as sequential await (not merged into Promise.all) — preserves existing code structure and makes the addition minimal; semantically equivalent since both invalidations trigger after movimentacao write"

requirements-completed: [PRC-28, AUD-02]

# Metrics
duration: 15min
completed: 2026-06-16
---

# Phase 34 Plan 02: Processos Timeline e Auditoria — Frontend Data Layer Summary

**TypeScript types (TimelineItem, TimelineItemType, AuditLogEntry) added to processos.ts; useTimeline and useAuditLog TanStack Query hooks added to use-processos.ts with timeline cache invalidation wired into both mutation hooks**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-06-16
- **Completed:** 2026-06-16
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Three new type declarations appended to `web/src/types/processos.ts` (append-only, no existing interfaces touched):
  - `TimelineItemType` union: `"movimentacao" | "transicao" | "evento" | "documento" | "decisao"`
  - `TimelineItem` interface with `tipo`, `id`, `timestamp`, `titulo`, `descricao?`, `autorNome?`
  - `AuditLogEntry` interface with `id: number`, `acao`, `entidadeTipo`, `entidadeId`, `autorNome?`, `timestamp`
- `TimelineItem` and `AuditLogEntry` added to the named import block in `use-processos.ts`
- `useTimeline(processoId)` hook added at end of file — follows `usePrazos` pattern exactly; queryKey `["processos", "timeline", processoId]`, staleTime 15_000
- `useAuditLog(processoId)` hook added at end of file — same pattern; queryKey `["processos", "audit", processoId]`, staleTime 30_000
- `useExecutarTransicao.onSuccess` extended: timeline invalidation added inside existing `Promise.all([...])`
- `useAddProcessoMovimentacao.onSuccess` extended: timeline invalidation added as second `await` call alongside existing movimentacoes invalidation

## Task Commits

Each task was committed atomically:

1. **Task 1: Add TimelineItem, TimelineItemType, and AuditLogEntry types to processos.ts** — `9a201b1` (feat)
2. **Task 2: Add useTimeline and useAuditLog hooks; extend useExecutarTransicao and useAddProcessoMovimentacao invalidations** — `50cc840` (feat)

## Files Modified

- `web/src/types/processos.ts` — 27 lines added: TimelineItemType union, TimelineItem interface, AuditLogEntry interface; no existing code modified
- `web/src/hooks/use-processos.ts` — 32 lines added: AuditLogEntry + TimelineItem imports, useTimeline function, useAuditLog function, two timeline invalidation calls in mutation hooks; no existing code removed

## Decisions Made

- `useAuditLog` staleTime is 30_000 (vs 15_000 for useTimeline): audit log is a compliance trail that only grows; 30s cache reduces API pressure without staleness impact
- Timeline invalidation in `useAddProcessoMovimentacao.onSuccess` added as a sequential `await` (not folded into a new `Promise.all`) to preserve the minimal existing code structure

## Deviations from Plan

None — plan executed exactly as written. Both files modified exactly as specified. No architectural changes required.

## Verification Results

- `pnpm exec tsc --noEmit`: exit 0, no output (clean)
- `pnpm lint`: 0 errors, 6 pre-existing warnings (img element + React Compiler incompatible-library — unrelated to this plan)
- `grep -c "export interface TimelineItem" web/src/types/processos.ts`: 1
- `grep -c "export function useTimeline" web/src/hooks/use-processos.ts`: 1
- `grep -c "export function useAuditLog" web/src/hooks/use-processos.ts`: 1
- `grep -c '"processos", "timeline"' web/src/hooks/use-processos.ts`: 3 (useExecutarTransicao, useAddProcessoMovimentacao, useTimeline)

## Known Stubs

None — this is a pure data layer plan. No UI components, no stubbed values.

## Threat Surface Scan

No new network endpoints introduced on the frontend side. The two new hooks consume existing backend endpoints established in Plan 01. The 403 response from `/audit` (processos:manage RBAC) surfaces as a TanStack Query error state — correct behavior per T-34-06 (accept disposition).

No new threat surface outside the plan's threat model.

## Next Phase Readiness

- Wave 3 (34-03): UI tab restructuring can now import `useTimeline` and `useAuditLog` directly; all types are in place for building the Timeline tab and conditional Auditoria tab

---
*Phase: 34-processos-timeline-e-auditoria*
*Completed: 2026-06-16*
