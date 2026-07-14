---
phase: LEXCV-92-agenda-riscoprazoservice-consolida-o
reviewed: 2026-07-13T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Evento.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - web/src/types/eventos.ts
  - web/src/hooks/use-eventos.ts
  - web/src/app/(dashboard)/agenda/page.tsx
findings:
  critical: 1
  warning: 2
  info: 6
  total: 9
status: issues_found
---

# Phase LEXCV-92: Code Review Report

**Reviewed:** 2026-07-13T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

This is a re-review of the same five files after `92-REVIEW-FIX.md` (iteration 1) applied fixes for
8 of the 9 in-scope findings from the prior `92-REVIEW.md`. I independently re-verified each claimed
fix against the current committed code (not just the fix report's prose) and confirmed the following
are correctly and completely applied, with no regressions introduced:

- **CR-01** (`updateEvento` silently dropping `processoId` changes) — fixed at
  `ResourceController.java:2595-2601`; tenant ownership is checked the same way `createEvento` does.
- **CR-02** (`createEvento` not verifying `processoId` tenant ownership) — fixed at
  `ResourceController.java:2525-2531`.
- **CR-04** (recurring events dropped from ranged `/eventos` queries before expansion) — fixed by
  moving the `start`/`end` `removeIf` filters from the pre-expansion master list to the post-expansion
  `expanded` list (`ResourceController.java:2478-2486`). Traced the recurrence-expansion loop
  (`2414-2476`) by hand for an old recurring master with a `dataInicio` far outside the query window:
  the loop now correctly walks the cursor forward from the master's original start date and generates
  in-window instances, which are no longer pruned before they're created.
- **WR-01** (missing required-field validation on `createEvento`) — fixed at
  `ResourceController.java:2512-2517`.
- **WR-02** (unguarded `.equals()` NPE risk on `concluido` filter) — fixed at
  `ResourceController.java:2374` (operand order flipped to `concluido.equals(e.getConcluido())`).
- **WR-03** (`recurrenceRule` loosely typed in request DTOs) — fixed in
  `web/src/types/eventos.ts:33,51` (now the strict `'DAILY' | 'WEEKLY' | 'MONTHLY'` union).
- **WR-04** (stale `risco` during optimistic drag-and-drop) — fixed at
  `web/src/app/(dashboard)/agenda/page.tsx:95` (`risco: undefined` in the optimistic branch).
- **WR-05** (`EventoUpdateRequest` advertising null-clearable fields the backend can't clear) — fixed
  in `web/src/types/eventos.ts:43-53` (`processoId`/`tipo`/`descricao` narrowed off `| null`).

**CR-03** (risco computed from `dataInicio` in the agenda list/job but from `dataFim` in the dashboard
KPIs) was explicitly **skipped**, per the fix report's own documented rationale (deferred to the
Phase 97 audit as a product-semantics decision). I independently re-confirmed via `grep` across
`backend/src/main/java` that this divergence is still live in the committed code today — it is not a
stale observation. Because it remains an actual, unresolved data-correctness defect (the same event
can show as "urgent" on the agenda calendar and "not urgent" on the dashboard KPI, or vice versa) and
directly contradicts this phase's own stated goal of consolidating `RiscoPrazoService` usage, I am
carrying it forward here as CR-01 rather than treating the prior deferral as closing it. This is not a
new bug; it is the same one, still present, still unresolved.

Beyond re-verifying the fixes, this pass surfaced two new warnings the prior review missed — a
symmetrical gap to WR-05 on the recurrence fields (`updateEvento` can set but never clear
`recurrenceRule`/`recurrenceEndDate`/`recurrenceExceptions`), and a query-cache staleness gap specific
to the drag-and-drop mutation in `agenda/page.tsx` (it doesn't sync the evento detail cache the way
`useUpdateEvento` does) — plus a few minor info-level code-quality items. The four unfixed info items
from the prior review (`useSetEventoConcluido` dead code, the dead `buildMonthGrid` conditional, the
double-space formatting nits, and the raw-string `recurrenceExceptions` encoding) remain open; none
were in the fixer's critical/warning auto-fix scope, so their persistence is expected, not a fixer
failure.

## Critical Issues

### CR-01: Evento urgency ("risco") still computed from different fields depending on which endpoint you ask (carried forward from 92-REVIEW.md CR-03, not fixed)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2489` (vs. `:3133`); also `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:206`
**Issue:** Re-verified via direct grep of every `computeRiscoEvento` call site in `backend/src/main/java`:
```java
// listEventos — ResourceController.java:2489 (feeds the agenda calendar's risco badge and weekStats.urgentes)
e.setRisco(riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade()));

// AlertasDiariosJob.java:206 (daily alert emails) — agrees with listEventos
String risco = riscoPrazoService.computeRiscoEvento(evento.getDataInicio(), evento.getPrioridade(), hoje);

// isEventoCritico — ResourceController.java:3133 (feeds BOTH /dashboard prazos_vencer
// AND /processos/dashboard prazos_criticos_count)
String risco = riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade());
```
Two of three call sites use `dataInicio`; the dashboard KPI helper is the outlier on `dataFim`. For
any Evento where `dataInicio` and `dataFim` land in different risk buckets relative to today (a
multi-day event, or one that starts today but doesn't end for another week), the calendar's
"Urgentes" count and the daily alert email will disagree with both dashboard KPIs
(`prazos_vencer`, `prazos_criticos_count`) for the identical row. This is exactly the kind of
cross-surface inconsistency a phase whose stated goal is "consolidate RiscoPrazoService usage across
the agenda surface" should resolve, and it was flagged as Critical in the prior review of this same
phase.

This was a deliberate, documented skip in `92-REVIEW-FIX.md` (deferred to Phase 97 as a
product-semantics call, not a mechanical fix) — I am not disputing that deferral was reasonable given
the existing corner-case comment at `ResourceController.java:3119-3131`. But a deferred fix is still an
open defect from a code-review standpoint: the inconsistency ships as-is, and this report's job is to
describe the code as it stands, not to validate a prior decision to defer. If Phase 97 is not
guaranteed to immediately follow this phase, this should stay tracked as a release-blocking
correctness gap rather than be silently dropped once this phase's own review is marked clean.
**Fix:** Pick one field as canonical for Evento urgency (`dataInicio`, to match the two-out-of-three
majority and `AlertasDiariosJob`) and align `isEventoCritico`:
```java
private boolean isEventoCritico(Evento e) {
    String risco = riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade());
    return RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco);
}
```
If `dataFim` is in fact meant to be canonical, update `listEventos` and `AlertasDiariosJob` instead —
but all three call sites must agree before this phase's consolidation goal can be considered met.

## Warnings

### WR-01: `updateEvento` can set but never clear `recurrenceRule` / `recurrenceEndDate` / `recurrenceExceptions`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2607-2609`
**Issue:** The partial-update whitelist for the recurrence trio is:
```java
if (payload.getRecurrenceRule() != null) evento.setRecurrenceRule(payload.getRecurrenceRule());
if (payload.getRecurrenceEndDate() != null) evento.setRecurrenceEndDate(payload.getRecurrenceEndDate());
if (payload.getRecurrenceExceptions() != null) evento.setRecurrenceExceptions(payload.getRecurrenceExceptions());
```
Same architectural class of bug the prior review flagged as WR-05 (for `processoId`/`tipo`/`descricao`)
— binding the raw `Evento` entity as the PUT payload means `null` and "field omitted" are
indistinguishable, so `if (x != null)` can only ever *set* a value, never *clear* one. WR-05's fix
narrowed the TS contract for the three fields it covered, but `recurrenceRule`, `recurrenceEndDate`,
and `recurrenceExceptions` were not in that fix's scope and still carry the same defect: once an
Evento is made recurring, there is no way via `PUT /eventos/{id}` to turn it back into a one-off event
(clear `recurrenceRule` to `null`) or to remove a previously-set `recurrenceEndDate`. The only related
mutation available is `DELETE /eventos/{id}/instances`, which appends a single-date exception — it
does not, and cannot, cancel the recurrence rule itself.
**Fix:** Same remedy class as WR-05: either accept this as a permanent product constraint and document
it explicitly (e.g., "to stop a recurring series, delete it and recreate it as one-off"), or move
`updateEvento` to a proper PATCH DTO (distinguishing "omitted" from "explicitly null" per field) so
`recurrenceRule`/`recurrenceEndDate`/`recurrenceExceptions` can actually be cleared.

### WR-02: Drag-and-drop reschedule mutation doesn't sync the evento detail query cache

**File:** `web/src/app/(dashboard)/agenda/page.tsx:61-76`
**Issue:**
```typescript
const dragDropMutation = useMutation({
  mutationFn: ({ id, dataInicio, dataFim }: { id: number; dataInicio: string; dataFim: string }) =>
    apiFetch<Evento>(`/eventos/${encodeURIComponent(String(id))}`, {
      method: "PUT",
      body: JSON.stringify({ dataInicio, dataFim }),
    }),
  onSuccess: () => {
    setOptimisticOverrides(new Map());
    queryClient.invalidateQueries({ queryKey: ["eventos", "list"] });
    toast.success("Evento reagendado com sucesso.");
  },
  ...
});
```
Compare with `useUpdateEvento` in `web/src/hooks/use-eventos.ts:74-90`, which on success does both
`invalidateQueries(["eventos","list"])` **and** `setQueryData(["eventos","detail", id], updated)`. The
inline `dragDropMutation` here only invalidates the list query — it never updates (or invalidates) the
`["eventos", "detail", id]` cache entry for the specific event that was just moved, even though the
mutation response (`updated`, discarded via `() =>` with no parameter) contains the fresh
`dataInicio`/`dataFim`. If the moved event's detail page (`/agenda/[id]`, which uses `useEvento(id)`
with a 15s `staleTime`) is open in another tab or gets navigated to shortly after the drag, it will
show the pre-move `dataInicio`/`dataFim` until its own 15-second stale window lapses — a real,
user-visible staleness bug, not just a style inconsistency.
**Fix:** Mirror `useUpdateEvento`'s cache-sync behavior:
```typescript
onSuccess: (updated) => {
  setOptimisticOverrides(new Map());
  queryClient.invalidateQueries({ queryKey: ["eventos", "list"] });
  queryClient.setQueryData(["eventos", "detail", updated.id], updated);
  toast.success("Evento reagendado com sucesso.");
},
```
(Or better: reuse `useUpdateEvento`/a shared mutation instead of duplicating the PUT call inline — see
IN-06.)

## Info

### IN-01: `useSetEventoConcluido` is still unused dead code (carried forward from 92-REVIEW.md IN-01, not fixed)

**File:** `web/src/hooks/use-eventos.ts:110-126`
**Issue:** Re-confirmed via project-wide grep: `useSetEventoConcluido` has zero call sites under
`web/src`. Only `useToggleEventoConcluido` is used (`web/src/app/(dashboard)/agenda/[id]/page.tsx:79`).
This is expected to still be open — info-level findings were outside the fixer's critical/warning
auto-fix scope per `92-REVIEW-FIX.md`.
**Fix:** Remove the unused export, or note an intended near-term caller to prevent further drift
between the two near-identical implementations.

### IN-02: Dead conditional in `buildMonthGrid` still present (carried forward from 92-REVIEW.md IN-02, not fixed)

**File:** `web/src/app/(dashboard)/agenda/page.tsx:560-561`
**Issue:**
```typescript
if (end.getDay() === 6 && days.length === 42) return days;
return days;
```
Both branches return the identical `days` array; the condition has no effect on behavior.
**Fix:** `return days;` (drop the dead `if`).

### IN-03: Stray double-space formatting in `use-eventos.ts` guards still present (carried forward from 92-REVIEW.md IN-03, not fixed)

**File:** `web/src/hooks/use-eventos.ts:37,49`
**Issue:** `const enabled = typeof window !== "undefined" ;` and
`const enabled = typeof window !== "undefined"  && Boolean(id);` both have an extra space. Purely
cosmetic.
**Fix:** `const enabled = typeof window !== "undefined";` / `... "undefined" && Boolean(id);`

### IN-04: `recurrenceExceptions` still stored as a raw comma-separated string (carried forward from 92-REVIEW.md IN-04, not fixed)

**File:** `backend/src/main/java/com/lexcv/models/Evento.java:52-53`
**Issue:** Unchanged since the prior review — a single `String` column holding comma-joined
`LocalDate.toString()` values, parsed/rebuilt independently at `ResourceController.java:2421-2427` and
`:2642-2648` with duplicated split/trim/filter-empty logic.
**Fix:** Introduce a shared helper (`Set<LocalDate> parseExceptions(String)` /
`String serializeExceptions(Set<LocalDate>)`) or model as a proper collection table in a future
migration.

### IN-05: Redundant null check in `createEvento`'s date-order validation

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2518`
**Issue:**
```java
if (evento.getTitulo() == null || evento.getTitulo().isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "titulo é obrigatório"));
}
if (evento.getDataInicio() == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "dataInicio é obrigatório"));
}
if (evento.getDataInicio() != null && evento.getDataFim() != null && evento.getDataFim().isBefore(evento.getDataInicio())) {
```
`evento.getDataInicio() != null` on the third line is always true at this point — the method already
returned above if `dataInicio` were null. Harmless but a readability nit; a future edit that reorders
these checks could silently reintroduce a null-dereference-shaped assumption.
**Fix:** Drop the redundant clause: `if (evento.getDataFim() != null && evento.getDataFim().isBefore(evento.getDataInicio())) {`

### IN-06: `agenda/page.tsx`'s inline drag-and-drop mutation duplicates `use-eventos.ts` update logic without the shared type check

**File:** `web/src/app/(dashboard)/agenda/page.tsx:61-66`
**Issue:** `dragDropMutation`'s `body: JSON.stringify({ dataInicio, dataFim })` is a hand-rolled PUT
call that duplicates what `useUpdateEvento` in `web/src/hooks/use-eventos.ts` already provides, but
without the `satisfies EventoUpdateRequest` compile-time check every other mutation in
`use-eventos.ts` uses (lines 66, 81, 99, 117). It's not currently a type error (the shape happens to be
a valid subset of `EventoUpdateRequest`), but a future rename/field change to `EventoUpdateRequest`
would not be caught here the way it would in `use-eventos.ts`, and it's the root cause enabling WR-02
(the duplicated mutation is why the detail-cache sync got missed in the first place).
**Fix:** Replace the inline `useMutation` with `useUpdateEvento` (parameterized per dragged event) or
export a shared `useRescheduleEvento` hook from `use-eventos.ts` that both this page and any future
caller can reuse with the same cache-sync guarantees.

---

_Reviewed: 2026-07-13T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
