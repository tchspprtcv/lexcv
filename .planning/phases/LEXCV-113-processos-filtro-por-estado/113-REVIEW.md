---
phase: LEXCV-113-processos-filtro-por-estado
reviewed: 2026-07-21T21:10:05Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/processos/page.tsx
findings:
  critical: 0
  warning: 1
  info: 3
  total: 4
status: clean
closed_at: 2026-07-21T21:15:00Z
closed_by: orchestrator (all 4 findings are pre-existing/out-of-scope, not introduced by this phase's relocation — see Closure Note)
---

## Closure Note (2026-07-21)

The relocation itself (this phase's actual change) is confirmed clean on all 4 requested dimensions — 0 findings against the diff. All 4 findings below (WR-01, IN-01/02/03) are pre-existing conditions in the same file, surfaced by the reviewer's full-file read, not regressions from this phase's JSX move. WR-01 (proximasAudiencias missing lower-bound date check) is flagged separately as an out-of-scope task rather than fixed here, to keep this phase's change to its stated single-file relocation scope. IN-01/02/03 are pre-existing/deliberately-out-of-scope UX notes, not phase-blocking.

# Phase LEXCV-113: Code Review Report

**Reviewed:** 2026-07-21T21:10:05Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed the sole changed file for the "promote Estado filter to the always-visible main filter bar" relocation (commit `3c4f277`). Confirmed via `git show 3c4f277` that the diff is exactly what the phase brief claims: a straight cut-and-paste of the `draftEstado` `NativeSelect` block out of the `advancedOpen` panel into the main flex row, plus `lg:col-span-3` → `lg:col-span-4` on the three fields left behind. No handler logic, state, or backend call was touched.

Verification of the four requested checks:

1. **JSX structural integrity** — CONFIRMED clean. Manually traced every open/close tag in the `<form>` subtree (lines 204-319) and cross-checked with an automated tag/brace/paren-balance script: 18 `<div>` opens / 18 closes, 1 `<form>` open/close, 5 `<Button>` open/close, and whole-file `{}`/`()` depth returns to exactly 0 with no negative dip anywhere. The `advancedOpen ? (...) : null` conditional (lines 269-318) is intact and still wraps only Tribunal/Área jurídica/Cliente. `tsc --noEmit` and `eslint` were run against the file directly — zero errors/warnings from either (the only `tsc` output in the whole project is three pre-existing, unrelated "Cannot find module 'vitest'" errors in `*.test.ts` files).
2. **Grid rebalance math** — CONFIRMED correct. The `advancedOpen` panel is `lg:grid-cols-12` (line 270) containing exactly three `lg:col-span-4` children (lines 271, 284, 297) = 12, an exact fit. No fourth `lg:col-span-3` leftover, no orphaned column budget.
3. **Estado still wired into onApply/onClear** — CONFIRMED correct. `onApply` (lines 106-116) still reads `draftEstado.trim()` into `filters.estado` (line 111); `onClear` (lines 118-133) still resets both `draftEstado` (line 120) and `filters.estado` (line 126). These functions were not part of the diff at all — the relocated JSX still binds to the exact same `value={draftEstado}` / `onChange={(e) => setDraftEstado(e.target.value)}` pair (lines 226-227) it used inside the old panel.
4. **Accessibility regression from the move itself** — NONE found. Tab order is pure DOM order (no `tabIndex` anywhere in the file — confirmed by grep), and the DOM order after the move (Pesquisar → Estado → Filtros toggle → Download/Aplicar/Limpar/Novo Processo → conditionally Tribunal/Área/Cliente) matches the visual left-to-right/top-to-bottom order exactly, since the parent `<form>` is a plain `flex flex-wrap` with no CSS `order`. The pre-existing lack of a semantic `<label htmlFor>`/`aria-labelledby` on any filter control (there is no `<label>`, `htmlFor`, or `aria-*` anywhere in this file — confirmed by grep) is unchanged by the move: Estado used the same bare-`<div>`-as-caption pattern in its old location and still does in its new one, identically to Pesquisar/Tribunal/Área/Cliente. Flagged below as IN-01 for completeness since the brief specifically asked about label association, but it is pre-existing and uniform, not something this phase's diff introduced or worsened.

While reading the full file (standard depth requires whole-file reading, not diff-only), I found one genuine, provable logic bug elsewhere in the same file and two minor polish gaps. These predate this phase's commit and are unrelated to the Estado relocation itself, but they exist in the file under review, so they're reported below rather than suppressed.

## Warnings

### WR-01: "Próximas Audiências" count includes overdue events, not just the next 7 days

**File:** `web/src/app/(dashboard)/processos/page.tsx:90-97`
**Issue:** The dashboard card at lines 190-194 renders `{proximasAudiencias} audiências agendadas para os próximos 7 dias` ("...scheduled for the next 7 days"), but the underlying computation has no lower bound:
```tsx
const eventos = useEventos({ concluido: false });
const now7days = new Date().getTime() + 7 * 24 * 60 * 60 * 1000;
const proximasAudiencias = (eventos.data ?? []).filter((e) => {
  const titulo = (e.titulo ?? "").toLowerCase();
  const isAudiencia = e.tipo?.toUpperCase() === "AUDIENCIA" || titulo.includes("audiência") || titulo.includes("audiencia");
  if (!isAudiencia) return false;
  return new Date(e.dataInicio).getTime() < now7days;
}).length;
```
`useEventos({ concluido: false })` (verified in `web/src/hooks/use-eventos.ts`) is called with no `dataInicio`/`dataFim` params, so it fetches *every* not-yet-concluded audiência regardless of date, including ones from last month. The filter only checks `dataInicio < now + 7 days` — it never checks `dataInicio >= now`. An overdue audiência from three weeks ago that nobody has marked `concluido` yet will satisfy `< now7days` and get counted as "upcoming in the next 7 days," inflating the KPI and mislabeling overdue hearings as upcoming ones. This is unrelated to the Estado relocation (untouched by commit `3c4f277`) but is a real defect in the reviewed file.
**Fix:** Add the missing lower bound:
```tsx
const nowMs = new Date().getTime();
const now7days = nowMs + 7 * 24 * 60 * 60 * 1000;
const proximasAudiencias = (eventos.data ?? []).filter((e) => {
  const titulo = (e.titulo ?? "").toLowerCase();
  const isAudiencia = e.tipo?.toUpperCase() === "AUDIENCIA" || titulo.includes("audiência") || titulo.includes("audiencia");
  if (!isAudiencia) return false;
  const inicio = new Date(e.dataInicio).getTime();
  return inicio >= nowMs && inicio < now7days;
}).length;
```

## Info

### IN-01: Filter controls (including the relocated Estado select) have no programmatic label association

**File:** `web/src/app/(dashboard)/processos/page.tsx:206-239, 271-316`
**Issue:** Every filter caption ("Pesquisar", "Estado", "Tribunal", "Área jurídica", "Cliente") is a plain `<div className="text-[11px] ...">` sibling, not a `<label htmlFor>` bound to the control's `id`, and none of the `Input`/`NativeSelect` instances carry `aria-label`/`aria-labelledby`/`id` (confirmed: no `<label`, `htmlFor`, or `aria-` anywhere in the file). This is pre-existing and applies uniformly to every field, so relocating Estado neither introduced nor worsened it — it inherited the same pattern it already had inside the advanced panel. Noting it here only because the review brief specifically asked about label association for the relocated control: a screen reader user tabbing to the Estado `<select>` gets no accessible name from the visible "Estado" caption today, exactly as before the move.
**Fix:** Add an `id` to each control and a matching `<label htmlFor="...">` (or `aria-labelledby`) wrapping the caption `<div>`s, e.g. for Estado: `<label htmlFor="processos-filtro-estado" className="...">Estado</label>` + `<NativeSelect id="processos-filtro-estado" ...>`. Worth doing as a single pass across all five fields rather than singling out Estado, since it's a file-wide pattern.

### IN-02: Empty-state message only special-cases "TRIAGEM", not the other Estado values

**File:** `web/src/app/(dashboard)/processos/page.tsx:332-335`
**Issue:**
```tsx
) : !processos.data?.length ? (
  <div className="p-6 text-sm text-slate-500">
    {filters.estado === "TRIAGEM" ? "Nenhum processo em triagem." : "Nenhum processo encontrado."}
  </div>
```
Filtering by `SUSPENSO`, `ENCERRADO`, `CONCLUIDO`, or `ATIVO` and getting zero rows falls back to the generic "Nenhum processo encontrado," while only `TRIAGEM` gets a state-specific message. This predates the current commit, but now that Estado is a first-class, always-visible control (rather than buried in the collapsed advanced panel), users will exercise every Estado value far more often, making this asymmetry more likely to be noticed.
**Fix:** Generalize using the same label set already defined in the `NativeSelect` options (lines 231-236), e.g. a small `ESTADO_LABELS` map keyed by value, so any selected estado gets a tailored empty message instead of only `TRIAGEM`.

### IN-03: Estado now sits next to an auto-applying field but still requires an explicit "Aplicar" click

**File:** `web/src/app/(dashboard)/processos/page.tsx:99-104, 220-239, 254-256`
**Issue:** The `Pesquisar` input auto-applies via the 300ms debounce effect (lines 99-104), and now sits directly beside the Estado select in the same always-visible row. Estado (like Tribunal/Área/Cliente) still requires clicking "Aplicar" (line 254-256) or submitting the form before `filters.estado` changes — this is unchanged behavior (the brief explicitly scoped this phase to a pure JSX move with "no new handlers"), so it is not a defect in this commit. Flagging only as a UX note: promoting Estado to sit adjacent to an auto-applying control may set a user expectation of immediacy that the current wiring doesn't meet.
**Fix:** Not a fix for this phase — noting as a candidate for a follow-up phase if instant-apply-on-change for Estado is desired (e.g., trigger `setFilters` directly from the Estado `onChange` instead of waiting for `onApply`).

---

_Reviewed: 2026-07-21T21:10:05Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
