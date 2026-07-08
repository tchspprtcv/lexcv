---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
fixed_at: 2026-07-08T01:10:00Z
review_path: .planning/phases/LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono/84-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase LEXCV-84: Code Review Fix Report

**Fixed at:** 2026-07-08T01:10:00Z
**Source review:** .planning/phases/LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono/84-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (1 critical, 4 warnings; IN-01..IN-04 left untouched, out of scope)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### CR-01: Fases "Guardar" can silently no-op / fail when the row's dropdown was never touched

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** ec7280f
**Applied fix:** Changed `onUpdateFaseStatus` signature to `(faseId: number, currentStatus: ProcessoFaseStatus)` and added `const status = faseDraftStatus[faseId] ?? currentStatus;`, mirroring the fallback already used by the `<select>`'s `value`. Updated the call site to `onClick={() => onUpdateFaseStatus(f.id, f.status)}`.

### WR-01: Dead Movimentação add-form, and its query still gates the whole detail page

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** da43edc
**Applied fix:** Removed the dead `movimentacoes` query (`useProcessoMovimentacoes`), `addMov` (`useAddProcessoMovimentacao`), `movForm`, `movServerError`, and `onSubmitMov` — none were rendered anywhere. Dropped `movimentacoes.isLoading`/`movimentacoes.isError` from the top-level `isLoading`/`isError` gate and removed the corresponding `movimentacoes.error` branch from the error-message chain. Removed now-unused imports (`useAddProcessoMovimentacao`, `useProcessoMovimentacoes`, `processoMovimentacaoFormSchema`, `ProcessoMovimentacaoFormValues`, `ProcessoMovimentacaoCreateRequest`). Confirmed via grep that `movimentacoes`/`movForm`/`addMov`/`movServerError`/`onSubmitMov` had no other references in the file before removing the hook calls.

### WR-02: Partes/Fases "Adicionar" dialogs don't reset stale form input on reopen

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** 903fcd3
**Applied fix:** Added `onOpenAddParte` and `onOpenAddFase` handlers (matching the existing `onOpenAddDecisao` pattern) that call `parteForm.reset(...)`/`faseForm.reset(...)`, clear `parteServerError`/`faseServerError`, and open the respective modal. Wired both handlers to the `onClick` of the Partes/Fases `DialogTrigger` `Button`s.

### WR-03: Fases status `<select>` remains interactive for view-only users

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** b24b34a
**Applied fix:** Added `disabled={!canEditProcessos}` to the Fases status `<select>`, consistent with every other edit affordance on the page.

### WR-04: Facto `ordem` input has no client-side validation (accepts negative/non-integer values)

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** 3b273f3
**Applied fix:** Changed the `onChange` handler to `setFactoOrdemDraft(Math.max(1, Math.trunc(Number(e.target.value) || 1)))`, clamping to a positive integer.

## Skipped Issues

None — all in-scope findings were fixed.

## Verification

Both commands run against `master` after all 5 fix commits were fast-forwarded in from the isolated worktree:

- `cd web && npx tsc --noEmit -p tsconfig.json` — clean for `processos/[id]/page.tsx`; only the 3 pre-existing unrelated `vitest`-module errors remain (`use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`), unrelated to this fix set.
- `pnpm run build` (from `web/`) — succeeded: "Compiled successfully in 15.3s", TypeScript check passed, all 23 routes generated with no errors.

---

_Fixed: 2026-07-08T01:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
