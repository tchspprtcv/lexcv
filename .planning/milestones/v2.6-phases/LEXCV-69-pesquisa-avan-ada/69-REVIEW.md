---
phase: 69-pesquisa-avan-ada
reviewed: 2026-07-01T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - web/src/hooks/use-pareceres.ts
  - web/src/app/(dashboard)/pareceres/page.tsx
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 69: Code Review Report

**Reviewed:** 2026-07-01
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Reviewed the Pesquisa Avançada addition to `/pareceres`: the new `usePesquisarPareceres` hook (`web/src/hooks/use-pareceres.ts`) and the panel/results wiring in `web/src/app/(dashboard)/pareceres/page.tsx`.

Core claims from the plan/summary hold up under direct inspection:
- **Date format**: `buildParecerPesquisaSearch` appends `T00:00:00`/`T23:59:59` to `dataInicio`/`dataFim` respectively (lines 54-55) before sending — matches the backend's `LocalDateTime` `@RequestParam` binding requirement.
- **Query-key namespace isolation**: `usePesquisarPareceres` uses `["pareceres", "pesquisa", texto, clienteId, advogadoId, status, dataInicio, dataFim]` (line 70), distinct from `usePareceres`'s `["pareceres", "list", clienteId, advogadoId, status]` (line 29). No collision; no shared cache invalidation calls reference the "pesquisa" key either (mutations only invalidate `["pareceres","list"]`, `["pareceres","detail",...]`, `["pareceres","versoes",...]` — the new search results are correctly never invalidated by CRUD, which is a minor staleness concern noted below).
- **pesquisaOpen vs advancedOpen**: genuinely separate `useState` booleans (lines 61 and 67), each with independent draft-state fields, independent panels, and independent toggle buttons — no accidental merge.
- **Dual-view reuse**: results block (lines 367-468) is a single shared render driven by `rows`/`resultsLoading`/`resultsError`, which are derived once via the `searchActive` conditional (lines 91-94) — no duplicated JSX for mobile cards / desktop table.
- **RBAC**: the page-level gate `canView = permissions.can.view("pareceres")` (line 44) covers the whole `ParecerPageContent`, including the new panel and `usePesquisarPareceres` call — consistent with the existing `usePareceres` gating, and matches the backend's `pareceres:view` guard on `/pareceres/pesquisa`. No new client-side permission surface introduced.

Two independent form elements in the same JSX tree (the "Filtros" form and the "Pesquisa Avançada" form) are both legitimate `<form>` tags — no nested-form HTML violation, they're siblings.

Issues found below are mostly UX/robustness gaps and minor state-consistency smells rather than functional breaks.

## Warnings

### WR-01: Toggling "Filtros" open does not close "Pesquisa Avançada" panel and vice versa, and both panels can render simultaneously with independent forms both submitting into different rows/loading state without visual disambiguation of which result set is showing

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:61-75, 244-365`
**Issue:** Both `advancedOpen` and `pesquisaOpen` can be `true` at once (nothing closes one when the other opens). If a user opens both panels, applies simple filters (`onApply`), and separately has an old committed `pesquisaFilters` with `pesquisaSubmitted=true` from an earlier session, the results block silently shows search results (`searchActive` wins) even though the visible "Filtros" panel's "Aplicar" was just clicked and looks like the active action. There is no visual indicator in the results card stating "showing search results" vs "showing filtered list", so a user who clicks "Aplicar" in the Filtros panel while a stale search is still submitted will see no change and may conclude Aplicar is broken.
**Fix:** Either (a) clicking "Aplicar" in the Filtros form should also reset `pesquisaSubmitted` to `false` (and vice versa, submitting the search should not need to touch `filters`), or (b) add a visible label above the results table indicating the active mode (e.g., "Resultados da pesquisa" vs "Lista de solicitações"). Minimal fix:
```tsx
const onApply = (e: React.FormEvent) => {
  e.preventDefault();
  setPesquisaSubmitted(false); // ensure simple list becomes visible again
  setFilters({ ... });
};
```

### WR-02: `estado` filter values sent to `/pareceres/pesquisa` collide in meaning between the simple list's `status` param and the search endpoint's `status` param, but empty-string is sent as `""` not omitted when both `pesquisaStatus` is `""` — verify against `buildParecerPesquisaSearch`'s trim-and-skip logic is fine, but the committed `pesquisaFilters` object always has all six keys present as empty strings, meaning `usePesquisarPareceres` recomputes `trim() ?? ""` redundant but harmless. However, `pesquisaFilters` type is `ParecerPesquisaFilters` yet is always populated with a mix of real values and forced `""` for unset fields (line 114-121) — this differs from the "clear" path where the object is `{}`. This inconsistency means the query key at rest right after a submit vs. right after a clear are structurally different shapes even though semantically equivalent (`""` for every unset key vs. absent keys), which is harmless for TanStack Query's key equality (both produce the same key array of `""` since `usePesquisarPareceres` normalizes with `?.trim() ?? ""`) but is a maintenance foot-gun if a future change to `usePesquisarPareceres` starts branching on `key in filters`.

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:114-121` and `web/src/hooks/use-pareceres.ts:60-67`
**Issue:** Minor inconsistency in shape between "submitted" filters object (always fully-populated with `""` for unset) and "cleared" filters object (`{}`), even though current normalization logic makes them behave identically today.
**Fix:** For consistency, either always pass `{}`-shaped partial objects (only set keys that are non-empty) from both `onPesquisar` and `onLimparPesquisa`, or document that `usePesquisarPareceres` intentionally treats absent and empty-string filter values as equivalent so future edits don't accidentally introduce a branch that trusts key presence.

### WR-03: Stale search results can be shown/considered "fresh" for up to 30s (staleTime) after data underneath changes (e.g., a parecer's status changes via another tab), and there's no `invalidateQueries` call anywhere touching the `["pareceres","pesquisa"]` namespace

**File:** `web/src/hooks/use-pareceres.ts:112-125, 138-203` (mutations only invalidate `["pareceres","list"]`, `["pareceres","detail",...]`, `["pareceres","versoes",...]`)
**Issue:** After creating a parecer (`useCreateParecer`) or delivering one (`useEntregarParecer`), the search results cache (`["pareceres","pesquisa",...]`) is never invalidated. If a user has an active search open (`pesquisaSubmitted = true`) and creates/updates a parecer that would now match or no longer match the search criteria, the search results panel will show stale data until `staleTime` (30s) expires or the query key changes.
**Fix:** Add a broader invalidation in `useCreateParecer`/`useEntregarParecer`/`useCreateParecerVersao` onSuccess handlers, e.g. `queryClient.invalidateQueries({ queryKey: ["pareceres"] })` (invalidates both "list" and "pesquisa" prefixes), or explicitly add `queryClient.invalidateQueries({ queryKey: ["pareceres", "pesquisa"] })` alongside the existing "list" invalidation.

## Info

### IN-01: `pesquisa.isFetching` used for the submit-button "A pesquisar..." label, but this also flips on any background refetch (e.g., window refocus), not only on explicit user submit

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:348-352`
**Issue:** TanStack Query's default `refetchOnWindowFocus` behavior means `pesquisa.isFetching` can go `true` when the user merely refocuses the browser tab with an active search, momentarily disabling the "Pesquisar" button and showing "A pesquisar..." even though the user didn't just click submit. This is cosmetic, not a functional bug.
**Fix:** If this refetch-triggered relabeling is undesirable, use a local `isSubmitting` state set on `onPesquisar` and cleared once `pesquisa.data`/`pesquisa.error` updates, or check `pesquisa.isFetching && pesquisaSubmitted` (already implicitly gated by only being visible when the panel is open, but the label still flips regardless of whether the query is enabled elsewhere).

### IN-02: `estado`/status option lists ("PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO") are duplicated verbatim across `advancedOpen` panel (lines 192-197) and `pesquisaOpen` panel (lines 311-316)

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:192-197, 311-316`
**Issue:** Code duplication — the same four `<option>` elements (and matching labels) appear twice. Any future addition of a new `ParecerStatus` value requires updating two places, and they could drift out of sync.
**Fix:** Extract a small `STATUS_OPTIONS` constant array (label/value pairs) and `.map()` over it in both places, e.g.:
```tsx
const STATUS_OPTIONS = [
  { value: "PENDENTE", label: "Pendente" },
  { value: "EM_ELABORACAO", label: "Em elaboração" },
  { value: "EM_REVISAO", label: "Em revisão" },
  { value: "CONCLUIDO", label: "Concluído" },
] as const;
```

### IN-03: `formatDate` silently swallows invalid dates by returning the raw string, which could render raw ISO datetimes (e.g. from a malformed `createdAt`) directly to the UI without normalization

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:23-28`
**Issue:** Pre-existing helper (not introduced by this phase), but exercised more heavily now with two data sources feeding the same rows. If the backend ever returns a non-parseable date string for `prazo`/`createdAt` in search results, the UI shows the raw string as-is with no visual indication it's a fallback. Low risk since dates are backend-controlled, not user input, so no injection risk — purely a defensive-coding note.
**Fix:** No action required; noting for completeness since it wasn't touched by this phase's diff.

---

_Reviewed: 2026-07-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
