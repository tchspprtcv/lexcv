---
phase: LEXCV-107-m-dulos-documentos-financeiro
plan: 05
subsystem: ui
tags: [react, shadcn, cmdk, popover, combobox, progress, typescript, documentos]

# Dependency graph
requires:
  - phase: LEXCV-107-m-dulos-documentos-financeiro
    provides: "web/src/components/shared/combobox.tsx exporting Combobox + ComboboxOption (Plan 01, LOCKED prop signature)"
provides:
  - "ProcessoDocumentosTab (processos/[id]/page.tsx): Documento.tipo field migrated from native <input list=>+<datalist> to the shared creatable Combobox; upload progress bar migrated to the official Progress component"
  - "ClienteDocumentosEntreguesTab (clientes/[id]/page.tsx): identical sibling migration (Combobox + Progress)"
  - "REQUIREMENTS.md DOF-V2-01 annotated as covered/resolved by Phase 107"
affects: [107-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Second/third consumer of the Plan-01 Combobox (creatable mode) — wired to plain React.useState (novoTipo/setNovoTipo), not react-hook-form Controller, matching the plan's LOCKED interface contract"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/[id]/page.tsx"
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
    - ".planning/REQUIREMENTS.md"

key-decisions:
  - "Ran a scoped `pnpm install --offline` inside the worktree before running `tsc --noEmit`, since worktrees do not inherit node_modules from the main checkout (same finding as 107-01-SUMMARY.md, itself citing PROJECT.md Phase 101) — required to satisfy Task 2's acceptance criterion."
  - "Verified acceptance-criteria source assertions via a Node.js script reading file contents directly, instead of chained shell `grep -q ... && grep -q ...` commands — the shell's `rtk` grep-proxy hook (Rust Token Killer, from the user's global CLAUDE.md) was intermittently intercepting only some commands in a multi-grep `&&` chain (confirmed via `set -x` tracing: the first grep in a chain ran as plain `grep`, the second was silently rewritten to `rtk grep`), producing non-deterministic pass/fail results on file content that was independently confirmed correct. Same instability was independently observed on `pnpm exec tsc --noEmit`, which printed a fabricated \"TypeScript: No errors found\" success message while the underlying captured log (`~/AppData/Local/rtk/tee/*_tsc.log`) showed the real npx \"This is not the tsc command you are looking for\" failure (missing local `tsc` binary before the offline install). Running `./node_modules/.bin/tsc --noEmit` directly (bypassing the proxy) gave a trustworthy result."
---

# Phase 107 Plan 05: Documentos `tipo` Combobox + Progress Bar Migration Summary

**Migrated the `Documento.tipo` field in both sibling document-upload dialogs (`ProcessoDocumentosTab`, `ClienteDocumentosEntreguesTab`) from a native `<input list=>`+`<datalist>` to the shared creatable `Combobox`, replaced both hand-rolled `bg-blue-600` upload progress bars with the official `Progress` component, and closed `DOF-V2-01` in REQUIREMENTS.md.**

## Performance

- **Duration:** ~15 min (across 2 task commits; session included an interruption/resume)
- **Completed:** 2026-07-17T00:49:32-01:00
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments
- `ProcessoDocumentosTab` (`processos/[id]/page.tsx`): tipo field is now a creatable `Combobox` (`id={datalistId}`, `emptyMessage="Nenhuma sugestão."`, `triggerClassName="rounded-none"`, wired to `novoTipo`/`setNovoTipo`); upload progress bar is `<Progress value={progresso ?? 0} />`
- `ClienteDocumentosEntreguesTab` (`clientes/[id]/page.tsx`): byte-identical sibling migration — same Combobox props, same Progress swap, only `datalistId` scoping and the `cliente_id` upload payload key differ (both untouched, per plan)
- `tipoOptions` suggestion source and the upload mutation calls (`novoTipo.trim()`, `processo_id`/`cliente_id`) left completely unchanged in both files, as specified
- `.planning/REQUIREMENTS.md` `DOF-V2-01` bullet annotated in place with a parenthetical referencing Phase 107 as its resolution, without deleting the historical v2-deferred row

## Task Commits

Each task was committed atomically:

1. **Task 1: ProcessoDocumentosTab — creatable Combobox (tipo) + Progress upload bar** - `887fc1a` (feat)
2. **Task 2: ClienteDocumentosEntreguesTab — same migration (sibling) + close DOF-V2-01 in REQUIREMENTS.md** - `4faba4e` (feat)

**Plan metadata:** (this SUMMARY.md + STATE.md/ROADMAP.md updates are applied by the orchestrator after all Wave 2 worktree agents complete, per this plan's parallel-execution instructions)

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - `ProcessoDocumentosTab`: `Combobox` (creatable) + `Progress` imports and JSX swap
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - `ClienteDocumentosEntreguesTab`: identical `Combobox` (creatable) + `Progress` imports and JSX swap
- `.planning/REQUIREMENTS.md` - `DOF-V2-01` bullet annotated as covered/resolved by Phase 107

## Decisions Made
- Ran `pnpm install --offline` inside the worktree before verification (same documented Phase 101/107-01 finding: Claude Code worktrees do not inherit `node_modules` from the main checkout). The offline install resolved entirely from the local pnpm store; `git status` after install showed zero lockfile/dependency drift.
- Bypassed the shell's `rtk` grep/tsc proxy for verification once it was shown (via `set -x` tracing and a captured `~/AppData/Local/rtk/tee/*_tsc.log`) to intermittently rewrite only some commands inside a multi-step `&&` chain, producing non-deterministic results and, in one case, a fabricated "TypeScript: No errors found" success line masking a real underlying npx failure. Verified acceptance criteria directly instead: a Node.js script reading file contents for the source assertions, and `./node_modules/.bin/tsc --noEmit` (direct binary path) for the type-check.

## Deviations from Plan

None - plan executed exactly as written. Both sibling tabs received the identical Combobox (creatable, `id={datalistId}`, `emptyMessage="Nenhuma sugestão."`, `triggerClassName="rounded-none"`) and Progress migration specified in the plan's `<action>` blocks; `DOF-V2-01` was annotated (not deleted) exactly as instructed.

## Issues Encountered

**Pre-existing, out-of-scope `tsc` failures unrelated to this task's files (same 3 errors already documented in 107-01-SUMMARY.md):** `./node_modules/.bin/tsc --noEmit` exits 1 with exactly 3 errors, all `TS2307: Cannot find module 'vitest'`, in `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, and `src/schemas/clientes.legacy-documento-tipo.test.ts`. None of these files were touched by this plan, and `vitest` is confirmed absent from `pnpm-lock.yaml` (a deliberate, previously-documented repo convention per Phase 97/74-02-SUMMARY.md and reconfirmed by 107-01-SUMMARY.md). Neither `processos/[id]/page.tsx` nor `clientes/[id]/page.tsx` appear in the error output — both migrated files are error-free. Per the executor's Scope Boundary rule, this pre-existing gap was left unfixed; it is not a deviation introduced by this plan.

**Flaky shell verification tooling (environmental, not a code issue):** the `rtk` grep/CLI proxy hook (from the user's global CLAUDE.md) produced non-deterministic exit codes when multiple `grep -q ... &&` checks were chained in one Bash call, and once fabricated a "TypeScript: No errors found" line for `pnpm exec tsc --noEmit` that contradicted the real captured output (an npx "command not found"-style failure, since `node_modules` didn't exist yet in this worktree). Worked around by verifying source assertions via a direct Node.js script and running the local `tsc` binary by explicit path. This did not affect any committed code — only how verification was performed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- DOF-01 (Progress ×2) and DOF-02 (creatable Combobox for `Documento.tipo`) both satisfied for the two ficha document upload dialogs.
- `DOF-V2-01` closed in REQUIREMENTS.md (Documentos v2-deferred section) rather than left open.
- The pre-existing `vitest`-missing `tsc` baseline gap (3 files, unrelated to this plan) remains open; not blocking, out of this plan's scope, and already tracked since 107-01.
- Plan-level build/lint is covered by the Wave-3 holistic gate (Plan 06), per this plan's own `<verification>` block.

---
*Phase: LEXCV-107-m-dulos-documentos-financeiro*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/processos/[id]/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/[id]/page.tsx
- FOUND: .planning/REQUIREMENTS.md
- FOUND: .planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-05-SUMMARY.md
- FOUND: 887fc1a (Task 1 commit, verified in `git log --oneline --all`)
- FOUND: 4faba4e (Task 2 commit, verified in `git log --oneline --all`)
