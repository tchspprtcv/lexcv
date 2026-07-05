---
phase: LEXCV-76-separadores-—-dados-contactos-e-notas
reviewed: 2026-07-05T11:30:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: clean
---

# Phase LEXCV-76: Code Review Report (Re-review, iteration 2)

**Reviewed:** 2026-07-05T11:30:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** clean

## Summary

Re-reviewed `web/src/app/(dashboard)/clientes/[id]/page.tsx` after the fix pass recorded in `76-REVIEW-FIX.md` (commits `51edeb2`, `b3ef070`). Both in-scope findings from the prior round are confirmed fixed and correct; no regressions were introduced by either change. The advisory WR-02 (component size) was explicitly and appropriately skipped as non-blocking per the prior review's own wording, and remains unchanged.

**CR-01 (add-dialogs reopening with stale state) — confirmed fixed.** A `React.useEffect` keyed on `tab` (lines 197-213) now closes `addDocEntreModal` / `addDocATratarModal` / `addDeslocacaoModal` and resets their paired draft objects (`newDocEntre` / `newDocATratar` / `newDeslocacao`) whenever `tab !== "dados"`. Traced the full reachable sequence from the original finding (open dialog → type draft → switch tab → switch back): the effect fires on the tab-away transition, before the "Dados" subtree unmounts, so the boolean is already `false` and the draft already cleared by the time the user navigates back — the dialog no longer springs back open with stale text. The effect is a no-op on mount (initial `tab` is `"dados"`) and a no-op on subsequent switches back to `"dados"`, so it does not fight with `confirmAddDocEntre`/`confirmAddDocATratar`/`confirmAddDeslocacao` or with the `onSubmit`/`onCancel` explicit resets (lines 312-317, 333-338) — those simply become redundant no-ops when the tab is already away from "dados", not conflicting writes. No new dialog-remount edge case was found (e.g., rapid tab-switch-then-back before React flushes) because Radix's `open` prop and the JSX unmount are both driven by the same `tab` state read on the same render, so there is no window where the effect's async timing could race the conditional unmount.

**WR-01 (invisible validation errors outside Dados tab) — confirmed fixed.** The header "Guardar" button's `onClick` now calls `form.handleSubmit(onSubmit, onError)` (lines 382-394) where the `onError` callback calls `setTab("dados")` and raises `toast.error(...)`. Cross-checked against `web/src/schemas/clientes.ts`: every validated field (`tipo`, `nome`, `nif`, `email`, `telefone`, `morada`, `localidade`, `documento_tipo`, `documento_numero`, the `superRefine` cross-field checks) is rendered exclusively inside the `tab === "dados"` branch (lines 470-1035), so routing back to "Dados" on any validation failure is sufficient to surface every possible inline error. `handleSubmit`'s `onError` path is invoked synchronously by react-hook-form before `onSubmit` runs, so there's no double-toast risk with `onSubmit`'s own catch-block toast (mutually exclusive code paths). The existing forced `setTab("dados")` on the "Editar" click (line 397) is unaffected and remains a separate, complementary safeguard (covers the moment edit mode is entered; the new fix covers the moment "Guardar" is clicked from elsewhere later in the same edit session).

**WR-02 (component size) — appropriately skipped, no follow-up required for this pass.** Left as previously recorded; not a blocking condition for this phase's completion.

**No new issues were introduced** by either fix commit — both diffs are minimal and additive (a new `useEffect` block, and an `onClick` handler signature change), touching no unrelated logic.

One pre-existing (not newly introduced) observation is noted below for completeness, unrelated to CR-01/WR-01.

## Info

### IN-01: `pnpm lint` reports 3 pre-existing `react-hooks/set-state-in-effect` errors in this file, unrelated to the two fixes under review

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1240, 1385, 1587`
**Issue:** Running `npx eslint` against this file surfaces 3 errors (not warnings) from the `react-hooks/set-state-in-effect` rule, all pre-dating this fix round (Phase 75 patterns: `ResponsaveisCard`, `ClienteContactosCard`, `ClienteNotasCard` each call `setState` directly in a `useEffect` body keyed on `editable`). The new CR-01 effect (line 204) uses the same "set state directly in effect based on a boolean flip" shape but is not flagged by the current ESLint config/version — worth confirming why (rule config nuance, e.g. related to number of setState calls or component vs. non-memoized context) rather than assuming it's exempt for a structural reason. Since `lint` is a listed project command and these are reported as `error` severity (not `warning`), they would fail a strict CI gate on `pnpm lint` if one exists, independent of this phase's changes.
**Fix:** No action required for this phase — these three errors pre-date Phase 76 and are out of scope for the CR-01/WR-01 fix verification. Recommend tracking as a separate cleanup item (e.g., replace `if (!editable) setModalOpen(false)` effects with derived/synchronized state, or an event-based reset instead of an effect) if `pnpm lint` is enforced as a CI gate.

---

_Reviewed: 2026-07-05T11:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
