# Deferred Items — Phase 106

Items discovered during execution/verification that are out of scope for the current
phase (pre-existing, not introduced by this phase's changes) and therefore not
auto-fixed per the Scope Boundary rule.

## From 106-04 human-verify checkpoint (live UAT)

| Item | File | Evidence | Status |
|------|------|----------|--------|
| **Pre-existing timezone bug: dataInicio/dataFim silently shift by +1h (Cabo Verde UTC-01:00) on every Evento create/edit/reschedule** | `web/src/app/(dashboard)/agenda/novo/page.tsx:79-80`, `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx:123-124`, `web/src/app/(dashboard)/agenda/page.tsx:96,428` (drag-and-drop reschedule), `web/src/hooks/use-eventos.ts:18` | Reproduced live during UAT: created an event with Início 16/07/2026 09:00 / Fim 17/07/2026 10:00 via the new DatePickerField; backend (`POST /api/v1/eventos` → 201, confirmed via `GET /api/v1/eventos/2`) stored `dataInicio: "2026-07-16T10:00:00"` / `dataFim: "2026-07-17T11:00:00"` — exactly +1h on both. Root cause: `new Date(values.dataInicio).toISOString().slice(0, 19)` (all 4 occurrences above) parses the naive `YYYY-MM-DDTHH:mm` string as **local** time (browser tz confirmed `Atlantic/Cape_Verde`, UTC-01:00), then `.toISOString()` converts to **UTC**, and `.slice(0,19)` drops the `Z` but keeps the UTC-shifted clock value — so a naive-local string goes in and a UTC-shifted string comes out looking like a naive-local string. Confirmed present verbatim in the pre-Phase-106 base commit (`ba896e3`) via `git show` — predates this phase entirely; Phase 106 only replaced the *input widgets* (`<Input type="datetime-local">` → `DatePickerField`), never touched this `onSubmit` serialization line in either form, nor the drag-and-drop reschedule logic in `agenda/page.tsx` (explicitly locked out of scope by `106-CONTEXT.md` — "a vista de calendário mensal existente... não é alterada"). | **FIXED** (commit `248a3e1`, same day, direct follow-up request after this deferred-items entry was written). All 4 sites replaced: `agenda/novo/page.tsx`/`agenda/[id]/editar/page.tsx` now use pure string concatenation (`` `${v}:00` ``, no Date round-trip); `agenda/page.tsx`'s drag-and-drop reschedule now uses a new `addDurationLocal()` helper (local-getter re-formatting, never `toISOString()`); `use-eventos.ts`'s `normalizeDateParam()` rewritten to handle both date-only and date+time shapes without a UTC round-trip. Verified live: created/edited events with specific times, confirmed the backend stores exactly what was entered; `addDurationLocal`'s duration-preservation logic confirmed directly in-browser under the real `Atlantic/Cape_Verde` timezone. Re-confirmed via `106-VERIFICATION.md` (goal-backward check against current source, not just this note). No existing-data migration was performed — any Eventos created/edited before this fix may still carry a +1h-shifted timestamp; that's a separate data-correction judgment call for product/ops, not addressed here. |

This finding is unrelated to the Popover+Calendar/Select/NativeSelect migration itself (AGD-36/AGD-37) —
verified separately that DatePickerField's own `commit()`/`parseDateOnly()` do pure string
concatenation with zero Date-to-UTC conversion (see `106-PATTERNS.md`'s timezone-off-by-one fix,
which addressed a *different*, date-only issue in the same component and was fixed *before*
execution). The two bugs are adjacent but distinct: the date-only bug (recurrenceEndDate) was
caught by `gsd-plan-checker` before execution and fixed in the plan; this hour-level bug lives
one layer up, in each form's `onSubmit` handler, and predates Phase 106.
