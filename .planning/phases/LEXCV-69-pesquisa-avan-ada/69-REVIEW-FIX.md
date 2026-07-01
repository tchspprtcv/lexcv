---
phase: 69-pesquisa-avan-ada
fixed_at: 2026-07-01T19:59:49Z
review_path: .planning/phases/LEXCV-69-pesquisa-avan-ada/69-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 69: Code Review Fix Report

**Fixed at:** 2026-07-01T19:59:49Z
**Source review:** .planning/phases/LEXCV-69-pesquisa-avan-ada/69-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Critical + Warning; Info out of scope)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: Toggling "Filtros" open does not close "Pesquisa Avançada" panel and vice versa, and both panels can render simultaneously without visual disambiguation of which result set is showing

**Files modified:** `web/src/app/(dashboard)/pareceres/page.tsx`
**Commit:** 8444827
**Applied fix:** `onApply` (the simple Filtros form submit handler) now calls `setPesquisaSubmitted(false)` before setting `filters`, so submitting the simple filter form always brings the simple list back into view, mirroring the existing `onLimparPesquisa` behavior. Only one result source (simple list vs. search) is visibly active at a time.

### WR-02: Inconsistent shape between the "submitted" pesquisa filters object (always fully populated with `""`) vs. the "cleared" object (`{}`)

**Files modified:** `web/src/app/(dashboard)/pareceres/page.tsx`
**Commit:** 8444827
**Applied fix:** `onPesquisar` now builds a partial `ParecerPesquisaFilters` object, only assigning keys whose trimmed value is non-empty, so the submitted-filters shape matches the cleared-filters shape (`{}`-style partial object) used by `onLimparPesquisa`. Both now consistently omit unset keys rather than mixing `""` placeholders with an empty object.

### WR-03: No mutation hook invalidates the `["pareceres","pesquisa"]` query-key namespace, allowing stale search results for up to 30s after a related mutation

**Files modified:** `web/src/hooks/use-pareceres.ts`
**Commit:** 47d6c07
**Applied fix:** Added `queryClient.invalidateQueries({ queryKey: ["pareceres", "pesquisa"] })` to the `onSuccess` handlers of `useCreateParecer`, `useCreateParecerVersao`, and `useEntregarParecer`, alongside their existing `["pareceres","list"]`/`["pareceres","detail",...]`/`["pareceres","versoes",...]` invalidations.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-01T19:59:49Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
