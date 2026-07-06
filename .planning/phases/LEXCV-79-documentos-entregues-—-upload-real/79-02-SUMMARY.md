---
phase: 79-documentos-entregues-upload-real
plan: 02
subsystem: ui
tags: [nextjs, react, tanstack-query, file-upload, documentos, clientes, rbac]

# Dependency graph
requires:
  - phase: 79-01
    provides: "GET /clientes/{id}/documentos tenant-scoped listing endpoint"
provides:
  - "useDocumentos queryFn repointed to /clientes/{id}/documentos when cliente_id is present"
  - "ClienteDocumentosEntreguesTab lazy-mount sub-component: real upload/list/download/delete for cliente documents"
  - "Legacy text-based Documentos Entregues section fully removed (state, handler, effect branch, JSX, payload field)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Lazy-mount sub-component consuming a scoped list hook internally (Phase 77 precedent), applied to a real upload+listing tab instead of a read-only Table"
    - "Native <input list> + <datalist> combobox sourced client-side from distinct field values already fetched (no new backend endpoint for option values)"

key-files:
  created: []
  modified:
    - web/src/hooks/use-documentos.ts
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"

key-decisions:
  - "RBAC for the new tab uses documentos:view/documentos:edit (not clientes:*), matching backend @PreAuthorize scopes on the Documento endpoints — deliberate exception among this ficha's 'Adicionar' buttons, per 79-CONTEXT.md"
  - "Uploaded-documents list renders in both read and edit mode (unlike Documentos a Tratar/Deslocações) since it is server-persisted content, not client-staged form state; only the Adicionar trigger and per-row delete control are gated by editable && canEditDocumentos"
  - "Uploads/deletes are immediate independent mutations (useUploadDocumentoComProgresso/useDeleteDocumento) with zero interaction with the cliente onSubmit/Guardar payload"
  - "Removed the now fully-unused PlaceholderEmBreve component — its last remaining tab consumer (documentosEntregues) was replaced by real content in this plan, and every other tab had already been wired in Phases 77-78"
  - "useDocumentos queryFn branches to /clientes/{id}/documentos only when cliente_id is present; the processo_id path is left falling through to the generic /documentos endpoint unchanged, matching the plan's minimal-scope instruction"

patterns-established: []

requirements-completed: [CLI-25, CLI-26, CLI-28, CLI-29]

# Metrics
duration: ~35min
completed: 2026-07-06
---

# Phase 79 Plan 02: Documentos Entregues Tab — Real Upload Summary

**Replaced the placeholder "Documentos Entregues" tab with a real file-backed upload/list/download/delete surface, and deleted the old text-only intake section that lived inside the "Dados" tab.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-07-06T15:50:00Z
- **Completed:** 2026-07-06T16:25:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- `useDocumentos` now calls the tenant-scoped `GET /clientes/{id}/documentos` (built in Plan 01) whenever `cliente_id` is supplied, instead of the generic `GET /documentos` which silently ignored that filter server-side.
- The entire legacy "Documentos Entregues" text-list feature (state, dialog, handler, dialog-reset effect branch, load-effect reset, `onCancel`/`onSubmit` resets, and the `documentosEntregues` field in the `ClienteUpdateRequest` Guardar payload) was deleted with zero orphaned references.
- A new `ClienteDocumentosEntreguesTab` lazy-mount sub-component (mirroring the Phase 77 `ClienteProcessosTab`/`ClienteParecerTab` pattern) lists this client's uploaded `Documento` records, supports upload via a Dialog (`FileDropZone` + a native `<input list>`/`<datalist>` "tipo" combobox sourced from this client's own distinct tipo values), direct-link download, and confirmed delete — all gated by `documentos:view`/`documentos:edit` RBAC and the ficha's `isEditing` toggle, with the list itself visible in both read and edit mode.
- `pnpm build` passes; `pnpm lint` shows the exact same 23 pre-existing problems (5 errors, 18 warnings) as the unmodified baseline — no new lint issues introduced (verified by diffing lint output against a `git stash`-based baseline run).

## Task Commits

Each task was committed atomically:

1. **Task 1: Repoint useDocumentos queryFn + delete the legacy text-list section** - `2f3bf32` (feat)
2. **Task 2: Add ClienteDocumentosEntreguesTab lazy-mount sub-component + wire the tab branch** - `1f736cd` (feat)

**Plan metadata:** (this commit) (docs: complete plan)

## Files Created/Modified
- `web/src/hooks/use-documentos.ts` - `useDocumentos` queryFn branches to `GET /clientes/{id}/documentos` (URL-encoded) when `cliente_id` is present; falls through to the existing generic `/documentos` call otherwise. Hook signature unchanged (no external `enabled` param added).
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Removed the legacy `documentosEntregues`/`newDocEntre`/`addDocEntreModal` state, `confirmAddDocEntre` handler, its dialog-reset effect branch, its JSX block inside the "Dados" tab Card, and its field in the `onSubmit` payload/`onCancel` resets. Added `canViewDocumentos`/`canEditDocumentos` RBAC declarations, repointed the `tab === "documentosEntregues"` branch to a permission-gated `ClienteDocumentosEntreguesTab`/`AccessDeniedState` ternary, and defined the new `ClienteDocumentosEntreguesTab` + `ClienteDocumentoEntregueRow` sub-components (upload Dialog, compact list, download link, delete control). Removed the now-unused `PlaceholderEmBreve` component and the unused `DocumentoEntregue` type import.

## Decisions Made
- Kept the `processoId` path in `useDocumentos` falling through to the generic `/documentos` endpoint unchanged, per the plan's explicit note that only the `cliente_id` branch was in scope for this phase.
- Removed `PlaceholderEmBreve` entirely rather than leaving it as dead code: it became provably unused once this plan wired real content into its last remaining tab consumer, and leaving it in would have tripped a `no-unused-vars` lint warning not present in the pre-existing baseline.
- Split the tipo-options `useMemo` dependency on `list.data` directly (via a `documentosData` alias) rather than on the locally-derived `documentos` array, to avoid a `react-hooks/exhaustive-deps` warning caused by a fresh `[]` fallback literal being created on every render.

## Deviations from Plan

None of substance — two small self-corrections were made during verification (see Decisions Made: the `PlaceholderEmBreve` removal and the `useMemo` dependency fix) to keep `pnpm lint` output byte-for-byte identical to the pre-existing baseline. Both were required to satisfy the plan's own acceptance criterion of introducing no new lint errors/warnings, not scope additions.

## Issues Encountered
- The worktree had no `node_modules` installed and no `web/.env.local`; `pnpm install` and copying `web/.env.example` to `web/.env.local` (gitignored, not committed) were required before `pnpm lint`/`pnpm build` could run at all.
- `pnpm lint` reports 5 pre-existing errors and 18 pre-existing warnings unrelated to this plan's scope (React Compiler `set-state-in-effect`/`incompatible-library` findings in `ContactosCard`/`NotasCard` effects, `documentos/novo`, `dashboard-shell`, etc.) — confirmed via a `git stash`-based baseline comparison that these predate this plan's changes and are out of scope to fix here.

## User Setup Required

None - no external service configuration required. (Local dev needs `web/.env.local` per `web/.env.example`, already documented in root `CLAUDE.md`.)

## Next Phase Readiness
- Phase 79 (final phase of milestone v2.8) is now fully implemented: backend listing endpoint (Plan 01) + frontend real upload/list/download/delete tab (Plan 02).
- `pnpm build` succeeds end-to-end; `pnpm lint` shows no regressions versus the pre-existing baseline.
- Deferred to human verification (per config): live upload/list/download/delete flow in a running app, and read-mode showing the list while hiding Adicionar/delete — no browser/backend environment was available in this worktree to exercise the live flow.
- No blockers for milestone v2.8 closure.

---
*Phase: 79-documentos-entregues-upload-real*
*Completed: 2026-07-06*
