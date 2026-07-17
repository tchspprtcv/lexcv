---
phase: 106-m-dulo-agenda
verified: 2026-07-16T00:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 106: Módulo Agenda Verification Report

**Phase Goal:** Os inputs de data dos formulários de Agenda usam o `Calendar` oficial e os filtros usam `Select`, sem alterar a vista de calendário mensal existente.
**Verified:** 2026-07-16
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Inputs de data de criar/editar Evento usam `Calendar` (shadcn/react-day-picker) via uma composição `Popover`+`Calendar` | ✓ VERIFIED | `web/src/components/shared/date-picker-field.tsx` imports and renders `Calendar` from `@/components/ui/calendar` inside `PopoverContent`; consumed via `Controller` in both `agenda/novo/page.tsx` (dataInicio, dataFim, recurrenceEndDate — 3 sites) and `agenda/[id]/editar/page.tsx` (dataInicio, dataFim — 2 sites). Zero `<Input type="datetime-local">` / `type="date"` remain in either file (grep confirmed 0 matches). |
| 2 | A vista de calendário mensal existente não foi alterada | ✓ VERIFIED | `buildMonthGrid`, `dayKey`, `cursorMonth`/`cursorMonthOverride`, drag-and-drop handlers (`onDragOver`/`onDrop`/`onDragStart`) all present unchanged in `agenda/page.tsx`. Only the filter bar's controls (native `<select>` → `Select`) changed; the CSS grid, day cells, and DnD wiring are untouched code. |
| 3 | Os filtros de categoria/status/processo da Agenda usam `Select` | ✓ VERIFIED | `agenda/page.tsx` renders `Select`/`SelectTrigger`/`SelectContent`/`SelectItem` (Radix, `@/components/ui/select.tsx`) for Processo/Categoria/Estado, replacing the prior native `<select>`s. Zero `<select` elements remain in the file (grep confirmed). Processo filter's sentinel correctly migrated from `""` to `"todos"` (`useState<string>("todos")`, filter predicate `selectedProcessoId !== "todos"`, "Limpar Filtros" resets to `"todos"`). |
| 4 | O `Calendar`/`Select`/`NativeSelect` usados são os primitivos shadcn reais (não stubs) | ✓ VERIFIED | `components/ui/calendar.tsx` wraps real `react-day-picker` (`9.14.0`, pinned in `package.json`) with a full `classNames`/`components` contract (not a placeholder). `components/ui/select.tsx` wraps real `radix-ui` `Select` primitive. `components/ui/native-select.tsx` is a real styled wrapper around a native `<select>`, consumed for the 7 RHF-bound fields (`processoId`/`tipo`/`prioridade` ×2 files, `recurrenceRule` ×1 file). `date-fns@^4.4.0` and `date-fns/locale`'s `pt` are real, installed dependencies (confirmed in `package.json`), not mocked. |
| 5 | O código compila, lint não introduz novas falhas, e a lógica de RBAC não regride | ✓ VERIFIED | `pnpm build` (fresh run during this verification): `Compiled successfully in 21.1s`, all 24 routes generated including `/agenda`, `/agenda/[id]`, `/agenda/[id]/editar`, `/agenda/novo`. `pnpm lint`: 6 errors / 18 warnings project-wide, only 1 pre-existing warning (`react-hooks/incompatible-library` on `form.watch("recurrenceRule")`, confirmed pre-existing and unrelated to this phase's changes) touches an Agenda file. All 4 Agenda files use `permissions.isFetched && !canX` (grep confirmed 4/4, zero remaining `!permissions.isLoading && !canX` races). |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/components/shared/date-picker-field.tsx` | Shared Popover+Calendar(+time) composition, first in project | ✓ VERIFIED | Exists, substantive (locale `pt`, `weekStartsOn={0}`, `parseDateOnly` local-safe date parsing, `commit()` handling both date-only and date+time variants, `required` on `Calendar` per CR-01 fix), wired into both form pages via `Controller`. |
| `web/src/app/(dashboard)/agenda/page.tsx` | 3 filters on `Select`, monthly grid untouched, RBAC `isFetched` | ✓ VERIFIED | Select migration confirmed; `buildMonthGrid`/`dayKey`/DnD intact; `isFetched` gate present. |
| `web/src/app/(dashboard)/agenda/novo/page.tsx` | 4 `NativeSelect` + 3 `DatePickerField` (2 withTime, 1 date-only), RBAC `isFetched` | ✓ VERIFIED | All 4 selects (`processoId`, `tipo`, `prioridade`, `recurrenceRule`) on `NativeSelect`; `dataInicio`/`dataFim` via `Controller`+`DatePickerField withTime`; `recurrenceEndDate` via `Controller`+`DatePickerField` (date-only), gated inside `recurrenceRule !== "NONE"` conditional; `selectClassName` removed, `textareaClassName` retained. |
| `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx` | 3 `NativeSelect` + 2 `DatePickerField` (withTime), RBAC `isFetched`, no recurrence UI added | ✓ VERIFIED | `processoId`/`tipo`/`prioridade` on `NativeSelect`; `dataInicio`/`dataFim` via `Controller`+`DatePickerField withTime`; no recurrence field added (form has none, per locked scope); `eventoEditFormSchema` (recurrence-free) correctly used as resolver. |
| `web/src/app/(dashboard)/agenda/[id]/page.tsx` | RBAC `isFetched` fix only (read-only detail page) | ✓ VERIFIED | `permissions.isFetched && !canViewAgenda` present; no other UI change (this file has no date inputs or select filters — correctly out of AGD-36/37's artifact set). |
| `web/src/schemas/eventos.ts` | Schema supports both create (with recurrence) and edit (without) forms without corrupting existing validation | ✓ VERIFIED | `eventoBaseObjectSchema` (refinement-free) → `eventoFormSchema` (create, full refine+superRefine) and `eventoEditFormSchema` (edit, `.omit()` + `dataFim>=dataInicio` refine only) — resolves the WR-03 finding (zod v4 `.omit()` cannot follow `.refine()`) correctly. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `agenda/novo/page.tsx` | `DatePickerField` | `Controller` (react-hook-form) ×3 | WIRED | `field.value`/`field.onChange` passed straight through; `withTime` correctly applied only to `dataInicio`/`dataFim`. |
| `agenda/[id]/editar/page.tsx` | `DatePickerField` | `Controller` ×2 | WIRED | Same contract, second independent call site confirming the shared component generalizes. |
| `agenda/page.tsx` filters | `Select` state | `onValueChange` → `useState` → `filteredEvents` memo | WIRED | Selecting a value updates state which flows into the `filteredEvents` filter predicate (`selectedProcessoId`/`selectedCategoria`/`selectedConcluido` all read inside the `useMemo` filter). |
| `DatePickerField` | RHF form value | `onChange(v)` → `commit()` string builder | WIRED | `commit()` produces a `YYYY-MM-DDTHH:mm` (withTime) or `YYYY-MM-DD` string matching `eventoFormSchema`'s expected shape; `onSubmit` handlers append `:00` before sending to the API — consistent end-to-end. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| Agenda filters (`Select`) | `selectedProcessoId` | `useProcessos()` (TanStack Query → `GET /processos`) | Yes — real API-backed list, not hardcoded | ✓ FLOWING |
| `DatePickerField` (all 3 call sites) | `field.value` (RHF) | `form.reset()` from `useEvento(id)` (edit) or empty defaults (create) | Yes — edit form pre-populates from the real fetched `Evento`; create form starts empty by design | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Project builds with all Agenda routes generated | `pnpm build` (web/) | `Compiled successfully in 21.1s`; `/agenda`, `/agenda/[id]`, `/agenda/[id]/editar`, `/agenda/novo` all listed in route table | ✓ PASS |
| No native `<select>`/`datetime-local`/`type="date"` remain in migrated Agenda files | `grep -rn "<select\|datetime-local\|type=\"date\"" app/(dashboard)/agenda/` | 0 matches | ✓ PASS |
| No stale RBAC race (`!permissions.isLoading && !canX`) remains in Agenda module | `grep -rn "permissions.isLoading && !can" app/(dashboard)/agenda/` | 0 matches; `permissions.isFetched` present in all 4 files | ✓ PASS |
| Lint does not introduce new errors in Agenda files | `pnpm lint` | 6 errors/18 warnings project-wide; only 1 pre-existing warning touches an Agenda file (`agenda/novo/page.tsx`, confirmed pre-existing by prior review) | ✓ PASS |

Live in-browser interaction (Portuguese month/day names, Sunday-first week, popover alignment, day-click accuracy, RBAC denial/allow flash-free rendering) was not independently re-run by this verifier (no dev server / browser session available in this verification pass). This was already exercised in detail during 106-04's live UAT (specific dates clicked and cross-checked against backend responses, 2 themes, 2 roles) and is treated as sufficient given the specificity of that evidence (real `POST`/`GET` round-trips, not just visual description) — flagged below as a light residual item rather than a blocking gap.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|--------------|--------|----------|
| AGD-36 | 106-01, 106-02, 106-03 | `Calendar` used in Agenda create/edit Evento date fields, monthly grid untouched | ✓ SATISFIED | `DatePickerField` (wrapping `Calendar`) consumed at all 5 date-field call sites across `agenda/novo` and `agenda/[id]/editar`; monthly grid helpers unchanged. |
| AGD-37 | 106-01 | Agenda category/status filters use `Select` | ✓ SATISFIED | All 3 list filters (Processo/Categoria/Estado) on Radix `Select` in `agenda/page.tsx`. |

No orphaned requirements — REQUIREMENTS.md maps only AGD-36/AGD-37 to Phase 106, both claimed and satisfied.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `web/src/components/shared/date-picker-field.tsx` | 82-89 | Time `<Input>` has no associated `<label>`/`aria-label` (IN-01, carried from code review) | ℹ️ Info | Non-blocking accessibility gap, explicitly triaged as deferred/non-blocking by `106-REVIEW.md` (iteration 3, APPROVED). Does not affect AGD-36/37 achievement. |
| `.planning/phases/LEXCV-106-m-dulo-agenda/deferred-items.md` | whole file | Documents the +1h timezone bug as "Not fixed", but commit `248a3e1` (after this doc was written) fixed it in all 4 originally-flagged files | ℹ️ Info | Documentation now stale relative to the codebase — recommend updating `deferred-items.md` to reflect the fix, but this is a docs-only drift, not a functional gap, and is outside AGD-36/37's own scope. |

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any file touched by this phase (grep across `agenda/` tree, `date-picker-field.tsx`, `schemas/eventos.ts`, `hooks/use-eventos.ts`).

### Post-SUMMARY Fixes Verified Against Current Source

Two changes landed on `master` after the phase's own SUMMARY.md files were written; both were independently re-verified against current file contents (not taken on SUMMARY/REVIEW narrative alone):

1. **Code-review fix loop (WR-01/02/03 + CR-01):** `date-picker-field.tsx:70` has `required` on `<Calendar mode="single" required ...>` — confirmed present in the file read during this verification. This closes CR-01 (deselect-click no longer silently resets the field to today). `eventoEditFormSchema` (in `schemas/eventos.ts`) is confirmed derived from a refinement-free base object via `.omit()`, matching the WR-03 fix description, and is the schema actually imported and used as the resolver in `agenda/[id]/editar/page.tsx`.
2. **+1h timezone fix (`248a3e1`):** All 4 originally-flagged sites (`agenda/novo/page.tsx`, `agenda/[id]/editar/page.tsx` onSubmit handlers; `agenda/page.tsx` drag-and-drop reschedule; `hooks/use-eventos.ts` query-param normalization) confirmed free of `.toISOString()` round-trips — `grep -rn "toISOString"` across these files returns only two explanatory code comments, zero live call sites. Both form `onSubmit` handlers now use direct string concatenation (`` `${values.dataInicio}:00` ``); `agenda/page.tsx` and `use-eventos.ts` use local-getter re-formatting (`addDurationLocal`, `normalizeDateParam`). This fix does not touch any Select/NativeSelect/Calendar migration code — it is orthogonal to AGD-36/AGD-37, and its presence does not regress the monthly-grid-untouched constraint (only the date-serialization arithmetic changed, not the grid's rendering/DnD/CSS).

### Human Verification Required

None outstanding as a blocking item. The one residual item — direct human confirmation of the Portuguese locale/Sunday-first rendering and Popover alignment in an actual browser session by a human (as opposed to the prior live-agent UAT already documented in `106-04-SUMMARY.md` with concrete request/response evidence) — is noted above under Behavioral Spot-Checks as a light residual, not classified as a blocking gap, given the specificity of the evidence already produced (real `POST /api/v1/eventos` → `201` round-trip with exact date/time confirmation, cross-checked against `GET /api/v1/eventos/2`).

### Gaps Summary

No gaps found. Both roadmap success criteria for Phase 106 are independently verified against current source:
1. Agenda create/edit Evento date inputs use the shadcn `Calendar` (via a shared `DatePickerField` Popover+Calendar composition), and the existing monthly calendar grid (CSS grid, drag-and-drop, `buildMonthGrid`/`dayKey`/`cursorMonth`) is unchanged.
2. The Agenda list's category/status/process filters use Radix `Select`.

The code-review fix loop (3 iterations, CR-01 resolved) and the subsequent +1h timezone fix were both independently re-confirmed against current file contents, not accepted on narrative alone. `pnpm build` and `pnpm lint` were re-run fresh during this verification pass and are green (build) / unchanged-pre-existing (lint) for all Agenda-module files.

---

_Verified: 2026-07-16_
_Verifier: Claude (gsd-verifier)_
