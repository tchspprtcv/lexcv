---
phase: LEXCV-107-m-dulos-documentos-financeiro
plan: 04
subsystem: ui
tags: [react, react-hook-form, combobox, rbac, documentos]

# Dependency graph
requires:
  - phase: LEXCV-107-m-dulos-documentos-financeiro
    plan: 01
    provides: "web/src/components/shared/combobox.tsx exporting Combobox + ComboboxOption (LOCKED prop signature)"
provides:
  - "Documentos list Processo/Cliente filters migrated from free-text UUID Input to closed-list searchable Combobox"
  - "Documentos list RBAC gate using permissions.isFetched (closes the isLoading pre-resolve render race)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Controller-wrapped Combobox (RHF precedent from agenda/novo/page.tsx's DatePickerField) applied to a second Wave-2 consumer with zero new fetches — options built from data already fetched by the page for its label maps"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/documentos/page.tsx

key-decisions:
  - "processoOptions/clienteOptions built as separate useMemo values (not reused from processoById/clienteNomeById Maps) since Combobox needs an array of {value,label}, while the existing Maps are keyed by id for O(1) lookup in the table/mobile-card renderer — both derive from the same processos.data/clientes.data source and the same label logic (numero ?? titulo ?? id / nome), so there is no divergent label risk"
  - "Ran pnpm install --offline in the worktree before tsc verification (same precedent as 107-01-SUMMARY.md: Claude Code worktrees don't inherit node_modules from the main checkout)"

requirements-completed: [DOF-02]

duration: ~15min
completed: 2026-07-17
---

# Phase 107 Plan 04: Documentos List Filters — Closed-List Combobox Summary

**Migrated the Documentos list's two free-text UUID filters (Processo, Cliente) to the shared closed-list searchable `Combobox` from Plan 01, and fixed the page's RBAC gate `isLoading` → `isFetched` race.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-17T01:44:14Z
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- `Processo ID` filter: free-text `<Input id="processo_id" {...form.register("processo_id")}>` replaced with a `Controller`-wrapped `Combobox`, options built from the already-fetched `useProcessos()` data (`processoOptions`, label `p.numero ?? p.titulo ?? p.id` — identical logic to the existing `processoById` map/mobile-card renderer). No second fetch added.
- `Cliente ID` filter: same treatment — `Controller`-wrapped `Combobox`, options from `useClientes({})` (`clienteOptions`, label `c.nome` — identical to `clienteNomeById`).
- RBAC gate: `if (!permissions.isLoading && !canViewDocumentos)` → `if (permissions.isFetched && !canViewDocumentos)`, closing the same pre-resolve render race documented and fixed for Dashboard (Phase 103) and Clientes/Processos (Phase 105).
- `documentosFiltersFormSchema` untouched — `processo_id`/`cliente_id` remain plain optional strings; the submit model (click "Filtrar") is unchanged.
- Removed the now-unused `Input` import after both filters were migrated (last usage removed in Task 2).

## Task Commits

Each task was committed atomically:

1. **Task 1: isFetched gate + Processo filter to closed-list Combobox (Controller)** - `b72155f` (feat)
2. **Task 2: Cliente filter to closed-list Combobox (Controller)** - `bc632a6` (feat)

**Plan metadata:** (this SUMMARY.md + STATE.md/ROADMAP.md updates are applied by the orchestrator after all Wave 2 worktree agents complete, per this plan's parallel-execution instructions)

## Files Created/Modified
- `web/src/app/(dashboard)/documentos/page.tsx` - RBAC gate fix + both filters migrated to `Controller`+`Combobox`; `processoOptions`/`clienteOptions` added; `Input` import removed

## Decisions Made
- Built `processoOptions`/`clienteOptions` as new `useMemo` arrays (`{value, label}[]`) rather than deriving them from the existing `processoById`/`clienteNomeById` `Map`s — the Combobox contract needs an array, while the Maps exist for O(1) id lookup elsewhere on the page (table columns, mobile card). Both derive from the same `processos.data`/`clientes.data` and the same label precedence, so there is zero risk of the filter's displayed label ever diverging from the table's displayed label.
- Ran `pnpm install --offline` in the worktree before running `pnpm exec tsc --noEmit`, per the established precedent from `107-01-SUMMARY.md` (Claude Code worktrees do not inherit `node_modules` from the main checkout).

## Deviations from Plan

None - plan executed exactly as written. Both `<action>` blocks (Processo in Task 1, Cliente in Task 2) were implemented verbatim, including the exact placeholders/search placeholders/empty messages specified, and the schema was left untouched as instructed.

## Issues Encountered

**Pre-existing, out-of-scope `tsc` failures unrelated to this task's file:** `cd web && pnpm exec tsc --noEmit` reports 3 errors, all `TS2307: Cannot find module 'vitest'`, in `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, and `src/schemas/clientes.legacy-documento-tipo.test.ts`. This is the identical, previously-documented gap from `107-01-SUMMARY.md` (Phase 97/v2.11, deliberate no-test-runner-installed repo convention) — none of the 3 errors reference `documentos/page.tsx` or any file this plan touched. Per the executor's Scope Boundary rule, left unfixed; not a deviation introduced by this plan.

**Bash `grep -q` unreliability against the `(dashboard)` route-group path:** During Task 2 verification, `grep -q 'name="cliente_id"' "web/src/app/(dashboard)/documentos/page.tsx"` returned a non-zero exit code (falsely reporting no match) while a plain `grep` (no `-q`) against the identical pattern/path printed the matching line, and the dedicated Grep tool also confirmed the match. This is an environment/shell-hook quirk (this session's `rtk` command-rewriting hook, per the user's global `CLAUDE.md`/`RTK.md`), not a defect in the source file. Verification for Task 2 was completed using the Grep tool instead, which is authoritative and confirmed all required patterns present and the old `<Input>` pattern absent.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- DOF-02 requirement satisfied for the Documentos list (2 of the phase's Combobox call sites from `107-01-SUMMARY.md`'s readiness note); the remaining call site (`Documento.tipo` field, Processo/Cliente document tabs) is Plan 05's responsibility, running in parallel in this same wave.
- The pre-existing `vitest`-missing `tsc` baseline gap (3 files, unrelated to this plan) remains open; not blocking, and out of this plan's scope to fix.

---
*Phase: LEXCV-107-m-dulos-documentos-financeiro*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/documentos/page.tsx
- FOUND: .planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-04-SUMMARY.md
- FOUND: b72155f (Task 1 commit, verified in `git log --oneline --all`)
- FOUND: bc632a6 (Task 2 commit, verified in `git log --oneline --all`)
