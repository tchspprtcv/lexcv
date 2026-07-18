---
phase: 104-padr-o-datatable-partilhado
plan: 05
subsystem: ui
tags: [tanstack-table, shadcn, pagination, data-table, badge, documentos, notificacoes]

requires:
  - phase: 104-02
    provides: "Shared DataTable pattern (data-table.tsx, data-table-column-header.tsx, data-table-pagination.tsx, data-table-view-options.tsx) and the official shadcn Pagination primitive"
provides:
  - "Documentos desktop list migrated to <DataTable> through the reconciled Table primitives (first-ever Table adoption for this screen, raw <table> eliminated)"
  - "documentos/columns.tsx with an exhaustive, invented 4-value Confidencialidade Badge mapping (PUBLICO/INTERNO/CONFIDENCIAL/RESTRITO)"
  - "/notificacoes migrated from a hand-rolled Anterior/Seguinte Button-pair pager to the official shadcn Pagination primitive, closing DTB-03"
affects: [104-06]

tech-stack:
  added: []
  patterns:
    - "columns(...) factory function pattern for screens whose Ações column needs RBAC flags threaded through (column defs are plain objects/functions and cannot call hooks or read props directly)"
    - "Ações cell delegated to its own small component (e.g. DocumentoAcoesCell) so per-row mutation hooks (useDeleteDocumento) can be called safely from within flexRender"
    - "shadcn Pagination's PaginationPrevious/PaginationNext wired with href=\"#\" + onClick(e.preventDefault()) + aria-disabled/pointer-events-none for client-side (non-URL-driven) pagination, since PaginationLink always renders an anchor via asChild"

key-files:
  created:
    - "web/src/app/(dashboard)/documentos/columns.tsx"
  modified:
    - "web/src/app/(dashboard)/documentos/page.tsx"
    - "web/src/app/(dashboard)/notificacoes/page.tsx"

key-decisions:
  - "Confidencialidade Badge mapping invented from scratch (no prior mapping existed anywhere in the codebase, verified against schemas/documentos.ts and the upload form) using an exhaustive switch statement over all 4 enum values: PUBLICO->gray, INTERNO->blue, CONFIDENCIAL->amber, RESTRITO->red, ordered by increasing sensitivity, with an explicit default fallback to gray for any unexpected/legacy value"
  - "Processo/Cliente columns in documentos/columns.tsx kept as plain-text IDs (not links), matching the pre-existing DocumentoRow behavior verified in 104-PATTERNS.md -- no name-resolution Map was introduced, since that would be a scope expansion beyond the DataTable migration"
  - "/notificacoes' Previous/Next use href=\"#\" + onClick(e.preventDefault()) rather than omitting href entirely, since PaginationLink hardcodes an <a> via asChild with no button-only variant, and an anchor without href is excluded from the tab order -- this preserves keyboard accessibility while keeping the swap a pure component substitution, not a primitive edit"

patterns-established:
  - "Confidencialidade closed-set Badge mapping (documentos/columns.tsx): PUBLICO=gray, INTERNO=blue, CONFIDENCIAL=amber, RESTRITO=red -- reusable if any future screen renders this same field"

requirements-completed: [DTB-02, DTB-03]

duration: ~25min
completed: 2026-07-16
---

# Phase 104: Padrão DataTable Partilhado — Plan 05 Summary

**Documentos' raw hand-written `<table>` replaced by the shared `<DataTable>` with an invented exhaustive Confidencialidade Badge mapping, plus `/notificacoes`' hand-rolled pager swapped for the official shadcn `Pagination` primitive**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-16T11:39:56Z
- **Tasks:** 2
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- `documentos/columns.tsx` created, porting `DocumentoRow`'s cell content into `ColumnDef<Documento>[]` — Nome (link, `enableHiding:false`), Tipo (`Badge variant="blue"`, carried forward from the mobile card), Processo/Cliente (plain IDs), Confidencialidade (new exhaustive `Badge` mapping), Ver. (`enableSorting:false`), Tamanho, Criado, Ações (`Apagar` gated by `canEditDocumentos`, `window.confirm()` guard preserved 1:1 via a dedicated `DocumentoAcoesCell` component)
- Documentos' desktop branch in `page.tsx` now renders `<DataTable columns={tableColumns} data={list.data} />` inside `hidden md:block`; the raw `<table>`/`<thead>`/`<tbody>` and the now-obsolete `DocumentoRow` component are gone entirely — first-ever `Table`-primitive adoption for this screen
- `/notificacoes`' hand-rolled `Anterior`/`Seguinte` `Button`-pair pager replaced with `Pagination`/`PaginationContent`/`PaginationItem`/`PaginationPrevious`/`PaginationNext`, preserving the exact copy ("Página {n} de {total}", "Anterior"/"Seguinte"), disabled-at-bounds logic, and the unchanged `useNotificacoes({page, size: 20})` server-pagination contract — closes DTB-03 (its full scope, per 104-UI-SPEC.md, is `/notificacoes` alone)
- Full `pnpm --dir web build` green (Turbopack, both `tsc` and static generation); targeted `pnpm exec eslint` on all 3 touched files reports zero issues

## Task Commits

Each task was committed atomically:

1. **Task 1: Documentos — first Table adoption + DataTable + Confidencialidade Badge** — `1962075` (feat)
2. **Task 2: /notificacoes — swap the hand-rolled pager for the official Pagination primitive (DTB-03)** — `28c2a6f` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/documentos/columns.tsx` — new `ColumnDef<Documento>[]` factory (`columns(canEditDocumentos)`); exhaustive `confidencialidadeVariant()` switch (PUBLICO/INTERNO/CONFIDENCIAL/RESTRITO -> gray/blue/amber/red); `DocumentoAcoesCell` sub-component wrapping `useDeleteDocumento` + the `window.confirm()` guard
- `web/src/app/(dashboard)/documentos/page.tsx` — desktop branch now `<DataTable columns={tableColumns} data={list.data} />` (memoized via `React.useMemo`); raw `<table>` desktop markup and `DocumentoRow` removed; mobile card branch, filters, outer guard untouched
- `web/src/app/(dashboard)/notificacoes/page.tsx` — pager block replaced with official `Pagination` composition; filters/list/mutations/RBAC gate untouched

## Decisions Made
- See `key-decisions` in frontmatter — Confidencialidade mapping invention (exhaustive, ordered by sensitivity), Processo/Cliente columns kept as plain IDs (no new name-resolution lookup), and the `href="#"` + `preventDefault()` wiring for `/notificacoes`' Previous/Next to preserve keyboard accessibility without editing the untouched-after-add `pagination.tsx` primitive.

## Deviations from Plan

None — plan executed exactly as written. The plan's own note anticipated a possible quote-style mismatch between the verify script's grep (`grep -Eq "\"$V\""`) and the codebase's usual unquoted-identifier object-key convention; this was avoided entirely by writing the Confidencialidade mapping as an explicit `switch` statement (matching the ternary/switch idiom already established by Processos' `estadoVariant` and Pareceres' `statusVariant` in this same phase) rather than an object literal — the switch's `case "PUBLICO":` / `"INTERNO"` / `"CONFIDENCIAL"` / `"RESTRITO"` branches are naturally quoted string literals, so the literal grep passed cleanly with no code contortion required.

## Issues Encountered
- This worktree had no `node_modules` installed (confirmed via `ls`, matching the Phase 101 finding recorded in PROJECT.md Key Decisions: "Instalações via `isolation=\"worktree\"` não propagam `node_modules` ao checkout principal"). Ran `pnpm install` inside the worktree (no lockfile changes — `git diff --stat web/pnpm-lock.yaml web/package.json` confirmed empty) so `tsc --noEmit`, `eslint`, and `pnpm build` could run locally in this session.
- `pnpm --dir web exec tsc --noEmit` reports 3 pre-existing failures (`use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`, all missing the never-installed `vitest` module) — confirmed via 104-02-SUMMARY.md as a known, already-logged (`deferred-items.md`) pre-existing gap from Phase 97-02, unrelated to and unmodified by this plan's file scope. `pnpm --dir web build` (the plan's actual automated gate) is unaffected, since Next's build does not type-check standalone test files outside the app tree.
- `next build` initially failed with `Error: BACKEND_API_ORIGIN is required` (this repo's `next.config.ts` validates required env vars at build time, per `web/AGENTS.md`/root `CLAUDE.md`). Not a code defect — supplied `BACKEND_API_ORIGIN=http://localhost:8080 NEXT_PUBLIC_API_BASE_PATH=/api/v1` (the documented `.env.example` values) as inline env vars for the verification build only; no `.env.local` was created or committed.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- Documentos and `/notificacoes` are both fully migrated; DTB-02 (for Documentos) and DTB-03 (its entire scope) are closed by this plan.
- 104-06 (the phase's closing plan) can proceed — no blockers. Note for its holistic verification pass: this worktree's `node_modules` was freshly installed from the existing lockfile (no lockfile drift) to run `tsc`/`eslint`/`build` locally; the merge/orchestrator checkout should still run its own `pnpm install` post-merge per the Phase 101 lesson before any post-merge build gate.

## Self-Check: PASSED

`web/src/app/(dashboard)/documentos/columns.tsx` confirmed present on disk; both task commits (`1962075`, `28c2a6f`) confirmed present via `git log --oneline`; this `104-05-SUMMARY.md` written and about to be committed.

---
*Phase: 104-padr-o-datatable-partilhado*
*Completed: 2026-07-16*
