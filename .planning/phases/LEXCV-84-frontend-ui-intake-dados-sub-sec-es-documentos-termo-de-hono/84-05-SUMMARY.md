---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
plan: 05
subsystem: ui
tags: [react, nextjs, tanstack-query, react-hook-form, zod, file-upload]

# Dependency graph
requires:
  - phase: 84-04
    provides: "Decisões/Testemunhas tab bodies wired into processos/[id]/page.tsx (established Dialog list+CRUD pattern this plan reuses for Factos, and permissions-gating precedent reused for Documentos)"
  - phase: 83
    provides: "Facto CRUD hooks (useFactos/useAddFacto/useUpdateFacto/useDeleteFacto), factoFormSchema/FactoFormValues, Facto/FactoCreateRequest/FactoUpdateRequest types"
  - phase: 79 (v2.8)
    provides: "ClienteDocumentosEntreguesTab/ClienteDocumentoEntregueRow pattern (FileDropZone + tipo datalist + progress bar + WR-01 wire-field workaround) mirrored almost verbatim for the processo-scoped Documentos tab"
provides:
  - "Factos tab: list ordered by ordem, Adicionar/Editar (with editable ordem field)/Apagar, gated by processos:edit"
  - "Documentos tab: upload (with progress bar)/list/download/delete scoped to processo_id, gated by documentos:edit"
  - "Corrected FactoUpdateRequest.ordem docblock in types/processos.ts"
  - "processos/[id]/page.tsx tab-content ternary chain fully wired — no null placeholders remain (closes the 84-03/84-04/84-05 sequential chain)"
affects: [processos, documentos]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Facto ordem reordering via a plain (non-Zod) local numeric input inside the Editar Dialog, merged into the FactoUpdateRequest payload only at submit time — avoids touching the Phase-83-owned factoFormSchema for a single edit-only field"
    - "ProcessoDocumentosTab/ProcessoDocumentoRow module-level components mirroring ClienteDocumentosEntreguesTab/ClienteDocumentoEntregueRow near-verbatim, scoped by processo_id instead of cliente_id"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/[id]/page.tsx"
    - "web/src/types/processos.ts"

key-decisions:
  - "Factos ordem field lives in local component state (factoOrdemDraft), not in factoFormSchema — keeps the Phase 83 create-only schema untouched while still satisfying PROC-10's reorder requirement on the edit path"
  - "canEditDocumentos derived from permissions.can.edit(\"documentos\") inside ProcessoDetailContent (re-invoking usePermissions, TanStack-Query-cached) — a scope distinct from canEditProcessos, matching the ClienteDocumentosEntreguesTab precedent exactly"
  - "WR-01 raw-entity field workaround (tamanho/createdAt via 'as unknown as' cast) reused verbatim from clientes/[id]/page.tsx rather than widening the shared Documento type, since /processos/{id}/documentos and /clientes/{id}/documentos serialize the identical JPA entity shape"

requirements-completed: [PROC-10, PROC-13]

# Metrics
duration: 12min
completed: 2026-07-08
---

# Phase 84 Plan 05: Factos and Documentos Tabs Summary

**Factos tab (list/create/edit-with-reorder/delete) and Documentos tab (upload-with-progress/list/download/delete) fill the last two `null` placeholders in `processos/[id]/page.tsx`, closing the sequential 84-03/84-04/84-05 chain on that file.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-08T01:36:26Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Factos tab: table sorted client-side by `ordem`, "Adicionar Facto"/"Editar Facto" Dialog, editable `ordem` numeric field shown only in edit mode (create form omits it since the backend recomputes it server-side), delete with confirm — all gated by `canEditProcessos` (PROC-10)
- Documentos tab: `FileDropZone` upload with progress bar, `tipo` datalist sourced from existing documentos, download opens a pre-signed URL in a new tab, delete with confirm — gated by a distinct `documentos:edit` scope (PROC-13)
- Fixed the stale `FactoUpdateRequest.ordem` docblock in `types/processos.ts` that claimed "no form collects this field yet" — now correctly documents the Phase 84 editable-Ordem-field mechanism
- No `null` placeholders remain anywhere in the tab-content ternary chain of `processos/[id]/page.tsx`

## Task Commits

Each task was committed atomically:

1. **Task 1: Factos tab — list ordered by ordem, Adicionar, Editar (with ordem), Apagar; fix stale docblock** - `73baf36` (feat)
2. **Task 2: Documentos tab — mirror ClienteDocumentosEntreguesTab, scoped by processo_id** - `7c8f4dd` (feat)

**Plan metadata:** pending (final docs commit, this file + STATE/ROADMAP/REQUIREMENTS)

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - Factos tab body (list/Dialog CRUD with editable ordem), Documentos tab body (`ProcessoDocumentosTab`/`ProcessoDocumentoRow` module-level components), `formatDocumentoSize`/`formatDocumentoDate` local helpers, `canEditDocumentos` derived from `permissions.can.edit("documentos")`
- `web/src/types/processos.ts` - `FactoUpdateRequest.ordem` docblock corrected to reflect the Phase 84 editable-field mechanism

## Decisions Made
- `ordem` reordering handled via plain local state (`factoOrdemDraft`), not added to `factoFormSchema` — avoids re-touching a Phase-83-owned schema file for a single edit-only field, per the plan's stated rationale
- `canEditDocumentos` derived independently inside `ProcessoDetailContent` via a second `usePermissions()` call (cheap, TanStack-Query-cached) rather than threaded down as a prop from the parent `ProcessoDetailPage`, matching the plan's explicit instruction
- Documentos tab uses `CardHeader`/`CardTitle` (this file's established Card shell for Partes/Fases/Decisões/Factos/Testemunhas) rather than the Cliente page's `CardContent`+`h4` variant, for intra-file consistency, per the plan's explicit guidance

## Deviations from Plan

None - plan executed exactly as written. No `z.preprocess`/`as any` resolver workaround was needed for `factoFormSchema` (unlike Testemunha's `tipo` field in Plan 84-04) since Factos has no optional enum `<select>` field — `descricao` and `data` are both plain string fields.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `processos/[id]/page.tsx`'s phase-84 scope is now complete — Timeline/Partes/Fases/Decisões/Factos/Testemunhas/Documentos/Auditoria tabs are all fully wired, no plan in this phase touches this file further
- `pnpm --dir web run build` passes cleanly (all 23 routes compile, including `processos/[id]/termo-honorarios`)
- Manual/human-check items (deferred to phase-level UAT per the plan's `<verification>` section): live reorder of two Factos via the Editar Dialog's Ordem field; live document upload via the new Documentos tab confirmed to also appear on the existing generic `/documentos` list page

---
*Phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/processos/[id]/page.tsx`
- FOUND: `web/src/types/processos.ts`
- FOUND: `.planning/phases/LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono/84-05-SUMMARY.md`
- FOUND commit: `73baf36`
- FOUND commit: `7c8f4dd`
