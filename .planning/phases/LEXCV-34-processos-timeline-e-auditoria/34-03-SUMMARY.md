---
phase: 34-processos-timeline-e-auditoria
plan: "03"
subsystem: web
tags: [react, typescript, timeline, audit-log, tabs, rbac, ui]

# Dependency graph
requires:
  - phase: 34-processos-timeline-e-auditoria
    plan: "01"
    provides: GET /processos/{id}/timeline and GET /processos/{id}/audit endpoints
  - phase: 34-processos-timeline-e-auditoria
    plan: "02"
    provides: useTimeline, useAuditLog hooks and TypeScript types
provides:
  - Restructured tab system: Timeline | Partes | Fases | Auditoria
  - Timeline tab with dot-and-line feed, tipo filter chips (5 types), and De/Até date range
  - Auditoria tab gated by canManageProcessos — absent from DOM for non-managers
  - Movimentacoes tab removed from tab row and content ternary

affects:
  - Page UX: default tab on process detail is now Timeline (not Partes)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Tab ternary chain: timeline ? → partes ? → fases ? → auditoria && canManage ? → null"
    - "Client-side filter: React.useMemo over timeline.data, no re-fetch on filter change"
    - "Dot-and-line feed: relative div with absolute connector line hidden on last item"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/processos/[id]/page.tsx

key-decisions:
  - "Auditoria tab content arm was the only missing piece — Tasks 1 and 2 partially done in prior session; completed by adding the tab === 'auditoria' && canManageProcessos arm to the ternary chain"
  - "acaoBadgeVariant typed as explicit union to avoid Badge variant inference errors"

requirements-completed: [PRC-28, AUD-02]

# Metrics
duration: 20min
completed: 2026-06-16
---

# Phase 34 Plan 03: Processos Timeline e Auditoria — Frontend UI Summary

**Tab system restructured in page.tsx: Timeline is default tab with dot-and-line feed and filter bar; Auditoria tab added with RBAC gate; Movimentacoes tab removed**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-06-16
- **Completed:** 2026-06-16
- **Tasks:** 2 (Task 1 and most of Task 2 were already done; completed Auditoria arm)
- **Files modified:** 1

## Accomplishments

- `TabKey = "timeline" | "partes" | "fases" | "auditoria"` with `"timeline"` as default — already in place from prior work
- Timeline filter state (`selectedTipos`, `dateFrom`, `dateTo`) + `filteredItems` memo — already in place
- `useTimeline` and `useAuditLog` hook calls — already in place
- Tab button group: Timeline, Partes, Fases, Auditoria (RBAC-gated) — already in place
- Timeline tab content (full dot-and-line feed with 5-tipo chips, date range, loading/error/empty states) — already in place
- **Completed this session:** Added missing `tab === "auditoria" && canManageProcessos` ternary arm with:
  - Loading state: "A carregar auditoria..."
  - Error state: "Erro ao carregar a auditoria. Tente novamente."
  - Empty state: "Sem registos de auditoria" with descriptive body
  - Entries list: `AuditLogEntry` rows with timestamp, acao Badge (blue/purple/secondary/red), entidadeTipo, entidadeId (mono truncated), autorNome
  - RBAC enforcement: content arm guarded by `canManageProcessos` — backend also enforces via @PreAuthorize

## Verification Results

- `pnpm exec tsc --noEmit`: exit 0, no type errors
- `pnpm lint`: 0 errors, 9 pre-existing warnings (img + react-hooks/incompatible-library — unrelated)
- Auditoria tab arm confirmed at lines 1355–1411 of page.tsx
- Timeline arm confirmed at lines 915–1172 of page.tsx

## Files Modified

- `web/src/app/(dashboard)/processos/[id]/page.tsx` — 55 lines added: Auditoria tab content arm with loading/error/empty/entries states

## Human Checkpoint Required

Per plan, Task 3 is a human verification checkpoint. Verify end-to-end:

1. Navigate to `/processos/{id}` — default tab should be "Timeline"
2. Timeline shows dot-and-line feed with color-coded entries
3. Tipo filter chips toggle entry visibility; date range filters work client-side
4. Auditoria tab button visible for processos:manage users; absent for others
5. Auditoria tab shows flat list of audit entries with correct badge colors
6. Movimentações tab button is gone

---
*Phase: 34-processos-timeline-e-auditoria*
*Completed: 2026-06-16*
