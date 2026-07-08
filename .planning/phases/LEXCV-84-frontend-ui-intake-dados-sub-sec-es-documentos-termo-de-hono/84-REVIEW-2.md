---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
reviewed: 2026-07-08T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/processos/[id]/page.tsx
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: issues_found
---

# Phase LEXCV-84: Code Review Report (Re-Review, Round 2)

**Reviewed:** 2026-07-08T00:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found (1 pre-existing Info item found; no new Critical/Warning defects introduced by the fixes)

## Summary

This is a targeted re-review of `web/src/app/(dashboard)/processos/[id]/page.tsx` after 5 fixes (1 critical + 4 warnings) from `84-REVIEW.md` were applied across 5 separate commits (`ec7280f`, `b24b34a`, `3b273f3`, `903fcd3`, `da43edc`). Each fix commit was read individually via `git show` and cross-checked against the current file state. All five fixes are correctly implemented and none of them introduced a new Critical or Warning defect. `npx tsc --noEmit` and `pnpm run build` both pass cleanly (matching the expected baseline of 3 pre-existing unrelated vitest-module errors in `tsc`, zero errors in `build`).

One pre-existing dead-code item (`textareaClassName`, unused since the file's very first commit — confirmed via `git show <every commit>:page.tsx | grep -c textareaClassName`, always `1`, i.e. only the declaration, never a use site) surfaced via `eslint` and is reported below for completeness; it predates this review round and was **not** introduced by any of the round-2 fixes.

### Fix-by-fix verification

**CR-01 (fase status Guardar falls back to current status when untouched) — confirmed fixed, no new issues.**
`onUpdateFaseStatus` now takes `(faseId: number, currentStatus: ProcessoFaseStatus)` and computes `const status = faseDraftStatus[faseId] ?? currentStatus;` (line 471-472). The call site at line 1726 passes `f.status` as `currentStatus`, matching the fase row being acted on. `ProcessoFaseStatus` is imported as a real type (not `any`/widened) and flows through `payload: ProcessoFaseUpdateRequest = { status }` without a cast. Diff was minimal and self-contained (`git show ec7280f`).

**WR-01 (dead Movimentação code removal + isLoading/isError gate fix) — confirmed fixed, no dangling references.**
Grepped the whole file for `mov|Movimentac` (case-sensitive, would catch `movForm`, `addMov`, `ProcessoMovimentacaoFormValues`, `ProcessoMovimentacaoCreateRequest`, `useProcessoMovimentacoes`, `useAddProcessoMovimentacao`, `processoMovimentacaoFormSchema`, `movimentacoes.data/isLoading/isError`, `movServerError`, `onSubmitMov`, etc.). The only remaining matches are the literal string `"movimentacao"` (a `TimelineItemType` value used for the Timeline tab's tipo filter chips, e.g. `selectedTipos.has("movimentacao")`) and two unrelated Portuguese-language UI strings ("movimentação"/"movimentações" — the justification dialog description and the empty-timeline hint). None of these are dangling references to the removed form/hooks — the Timeline tab consumes `useTimeline`, a separate, still-live hook, not the deleted `useProcessoMovimentacoes`.
The top-level gate at lines 276-277 is now `processo.isLoading || clientes.isLoading || partes.isLoading || fases.isLoading` / `processo.isError || clientes.isError || partes.isError || fases.isError`, matching the fix instruction — `movimentacoes` is fully gone from both the gate and the `isError` fallback message chain (line 690-701, which previously chained through `movimentacoes.error`). No other code path reads `movimentacoes.data`/`.isLoading`/`.isError` — confirmed via the same grep pass and by inspecting the `git show da43edc` diff, which is a clean, symmetric removal (imports, hook call, form, handler, gate, error-message chain — nothing left half-removed).

**WR-02 (onOpenAddParte/onOpenAddFase reset handlers) — confirmed fixed, correctly wired, correct field sets.**
`onOpenAddParte` (line 429-433) resets `parteForm` to `{ tipo: undefined, nome: "", nif: undefined }`, which is an exact match for `parteForm`'s own `defaultValues` at line 281 — not a copy from a different form. Same for `onOpenAddFase` (line 450-454) resetting `faseForm` to `{ nome: "" }`, matching `faseForm`'s `defaultValues` at line 288. Both handlers are wired to the respective `DialogTrigger`'s inner `Button onClick` (lines 1528 and 1636 respectively) — not merely defined and unused.

**WR-03 (Fases status select disabled for view-only users) — confirmed fixed.**
The `<select>` at line 1705-1719 now has `disabled={!canEditProcessos}` (line 1714). The adjacent "Guardar" button was already gated the same way, so the two controls are now consistent for view-only users.

**WR-04 (Facto ordem input clamped to positive integers) — confirmed fixed.**
`onChange` handler for `facto_ordem` (line 1968-1970) is now `setFactoOrdemDraft(Math.max(1, Math.trunc(Number(e.target.value) || 1)))`, clamping to a minimum of 1 and truncating fractional/garbage input, consistent with the `min={1}` on the `<input type="number">`.

### Build / typecheck verification

- `cd web && npx tsc --noEmit` → 3 errors, all in `*.test.ts` files failing to resolve the `vitest` module (`use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`) — matches the stated pre-existing baseline exactly, no new type errors.
- `cd web && pnpm run build` → `✓ Compiled successfully`, all 23 routes generated (static + dynamic), zero errors.

## Info

### IN-01: Unused `textareaClassName` constant (pre-existing, not introduced by this round's fixes)

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:141`
**Issue:** `const textareaClassName = "..."` is declared but has zero use sites in the file (`eslint` flags it: `'textareaClassName' is assigned a value but never used @typescript-eslint/no-unused-vars`). Verified via `git show <commit>:page.tsx | grep -c textareaClassName` across the file's entire commit history back to the initial monorepo-merge commit (`40008dc`) — the count is `1` (declaration only) at every single commit, meaning this was already dead code before the WR-01 Movimentação-form removal and before the phase's first review round; it was never introduced or affected by any of the round-2 fixes. All `<Textarea>` usages in the file (Decisão resumo, Testemunha notas, Facto descrição) use inline `className="rounded-none"` instead of this shared constant.
**Fix:** Either delete the unused constant, or replace the ad-hoc `className="rounded-none"` on the three `<Textarea>` elements (lines ~1802, ~2137, ~1937) with `className={textareaClassName}` if a shared style was the original intent. Since this predates the current phase's scope, it can be deferred to a follow-up cleanup rather than blocking this round.
```tsx
// Option A — remove the dead constant:
// (delete lines 141-142)

// Option B — actually use it where Textareas are rendered:
<Textarea id="decisao_resumo" className={textareaClassName} {...decisaoForm.register("resumo")} />
```

---

_Reviewed: 2026-07-08T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
