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
  critical: 4
  warning: 5
  info: 4
  total: 13
status: issues_found
---

# Phase LEXCV-92: Code Review Report

**Reviewed:** 2026-07-13T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the Evento entity, the eventos/agenda slice of `ResourceController`, the eventos TypeScript
contracts, the `use-eventos` TanStack Query hooks, and the agenda calendar page. The recurrence
engine, drag-and-drop rescheduling, and filter UI are functionally plausible, but the review found
four blocker-level defects that go to the heart of this phase's stated goal (consolidating
RiscoPrazoService usage across the agenda surface) and basic CRUD correctness for the
`processoId` link on an Evento:

1. `updateEvento` (`PUT /eventos/{id}`) silently ignores any change to `processoId` — the field is
   completely absent from its partial-update whitelist, so the edit form's "change/unlink processo"
   control is a no-op despite looking functional.
2. `createEvento` accepts a client-supplied `processoId` without verifying it belongs to the caller's
   tenant, unlike the equivalent checks already present elsewhere in the same controller
   (`uploadDocumento`, `listHonorarios`).
3. The "risco" (urgency) of an Evento is computed from `dataInicio` in `listEventos` (what the agenda
   calendar and its "Urgentes" counter see) but from `dataFim` in `isEventoCritico` (what both
   dashboard KPIs — `prazos_vencer` and `prazos_criticos_count` — see). The same event can be
   simultaneously "urgent" on the calendar and "not urgent" on the dashboard, or vice versa.
4. `GET /eventos` filters events by date range *before* expanding recurrence, using the recurring
   master's own original `dataInicio`. Any recurring event whose first occurrence predates the
   requested window is dropped outright, so none of its otherwise-in-window recurring instances are
   ever generated for a date-ranged query — a capability the endpoint and `EventosListFilters`
   explicitly advertise.

Additional warnings cover missing required-field validation on evento creation, an unguarded
`.equals()` NPE risk, loose typing of `recurrenceRule` in the request DTOs, a stale-risk display
during optimistic drag-and-drop, and an update DTO that advertises null-clearable fields the backend
can never actually clear. Info items note one dead hook export and one dead conditional branch.

## Critical Issues

### CR-01: `updateEvento` never persists `processoId` changes (link is frozen after creation)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2554-2568`
**Issue:** The partial-update block in `updateEvento` copies `titulo`, `descricao`, `tipo`,
`dataInicio`, `dataFim`, `concluido`, `recurrenceRule`, `recurrenceEndDate`, and
`recurrenceExceptions` from `payload` onto the managed `evento` — but never reads
`payload.getProcessoId()`. `Evento.processoId` can therefore be set at creation time but never
changed or cleared afterward via `PUT /eventos/{id}`.

The frontend contract implies this should work: `EventoUpdateRequest.processoId?: string | null`
(`web/src/types/eventos.ts:38`) is explicitly typed to allow both a new UUID and `null` (to unlink),
and the edit page (`web/src/app/(dashboard)/agenda/[id]/editar/page.tsx`) renders a "Processo" select
with a "Sem vínculo" option and submits whatever the user picks. That submission is silently dropped
by the backend — the request succeeds (200 OK) and the user sees a success toast, but the processo
link is unchanged. This is a confirmed functional bug, not just a theoretical gap.

**Fix:**
```java
// backend/src/main/java/com/lexcv/controllers/ResourceController.java (in updateEvento, alongside the other partial-update lines)
if (payload.getProcessoId() != null) {
    Processo linkedProcesso = processoRepository.findById(payload.getProcessoId()).orElse(null);
    if (linkedProcesso == null || !linkedProcesso.getTenantId().equals(getTenantId())) {
        return ResponseEntity.badRequest().body(Map.of("message", "processoId não pertence a este tenant"));
    }
    evento.setProcessoId(payload.getProcessoId());
}
```
Note: this restores "set/change" but does not solve unlinking-to-null under the current
`if (x != null)` partial-update convention (see WR-05) — that needs an explicit sentinel or a move
away from binding the raw entity as the update payload.

### CR-02: `createEvento` does not verify `processoId` belongs to the caller's tenant

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2496-2521`
**Issue:** `createEvento` binds the full `Evento` from the request body and saves it with only the
`tenantId` forced to the caller's own tenant (line 2519). `evento.getProcessoId()`, however, is
whatever UUID the client supplied and is never checked against `processoRepository` for tenant
ownership. This is inconsistent with the pattern already established in the same controller for the
exact same relationship:
```
// uploadDocumento — ResourceController.java:2642-2646
Processo processo = processoRepository.findById(processoId).orElse(null);
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "processoId não pertence a este tenant"));
}
```
```
// listHonorarios — ResourceController.java:2852-2856 (same check)
```
Without this check, an authenticated user can create an Evento in their own tenant that references
another tenant's `processo_id`, violating the multi-tenancy invariant CLAUDE.md calls out as the
platform's primary data-isolation boundary ("Every domain entity carries a tenant_id ... must scope
all reads/writes by it"). Other endpoints' own tenant checks currently prevent this from becoming a
direct cross-tenant read, but it is a dangling/incorrect reference and a clear authorization gap
relative to the rest of the codebase's own established pattern.

**Fix:**
```java
@PostMapping("/eventos")
public ResponseEntity<?> createEvento(@RequestBody Evento evento) {
    if (evento.getProcessoId() != null) {
        Processo processo = processoRepository.findById(evento.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "processoId não pertence a este tenant"));
        }
    }
    // ... existing validation continues
}
```

### CR-03: Risco computed from `dataInicio` in the agenda list but from `dataFim` in the dashboard KPIs

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2479` (vs. `:3091`)
**Issue:**
```java
// listEventos — line 2479 (feeds the calendar's "risco" badge and the agenda page's weekStats.urgentes)
e.setRisco(riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade()));
```
```java
// isEventoCritico — line 3091 (feeds both /dashboard prazos_vencer and /processos/dashboard prazos_criticos_count)
String risco = riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade());
```
The two call sites feed `RiscoPrazoService.computeRiscoEvento` different fields of the same entity.
For any Evento where `dataInicio` and `dataFim` fall in different risk buckets relative to `hoje`
(e.g. a multi-day event, or one that starts today but doesn't end for another 10 days), the risco
computed for the calendar view and the risco computed for the dashboard KPI will disagree for the
identical row. `AlertasDiariosJob` (line 206) also uses `dataInicio`, matching `listEventos` — so
`isEventoCritico`/dashboard is the outlier. Given this phase's explicit goal is consolidating
RiscoPrazoService usage across the agenda surface, this divergence should have been resolved here;
instead the KPI counters (`prazos_vencer`, `prazos_criticos_count`) and the calendar's own "Urgentes"
stat can legitimately show different numbers for the same underlying data.

**Fix:** Pick one field as the canonical basis for Evento urgency (most likely `dataInicio`, to match
`listEventos` and `AlertasDiariosJob`) and align `isEventoCritico`:
```java
private boolean isEventoCritico(Evento e) {
    String risco = riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade());
    return RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco);
}
```
If `dataFim` is in fact intended to be canonical (e.g., product decided deadlines are tracked by the
event's *end*), update `listEventos` and `AlertasDiariosJob` instead — but the three call sites must
agree.

### CR-04: Recurring events are dropped from ranged `/eventos` queries before recurrence is expanded

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2400-2476`
**Issue:** When `dataInicio`/`dataFim` query params are supplied (a documented capability —
`EventosListFilters` in `web/src/hooks/use-eventos.ts:7-12` types both fields), the handler filters
the raw entity list by the *master* event's own `dataInicio` **before** recurrence expansion runs:
```java
if (start != null) {
    LocalDateTime finalStart = start;
    eventos.removeIf(e -> e.getDataInicio() != null && e.getDataInicio().isBefore(finalStart));
}
if (end != null) {
    LocalDateTime finalEnd = end;
    eventos.removeIf(e -> e.getDataInicio() != null && e.getDataInicio().isAfter(finalEnd));
}
// ... recurrence expansion only runs on what's left of `eventos` here
```
A recurring Evento created long ago (e.g. `dataInicio = 2020-01-01`, `recurrenceRule = "WEEKLY"`,
`recurrenceEndDate` far in the future) will have its master row removed by the first `removeIf` the
moment `finalStart` is any date after its original `dataInicio` — which is true for essentially every
query after the event's creation month. Because the master is removed before the expansion loop
(lines 2414-2476) runs, **none** of its otherwise in-window recurring instances get generated. The
event effectively vanishes from every date-ranged query except the one that happens to include its
original start date.

This does not currently manifest in the reviewed frontend because `agenda/page.tsx` calls
`useEventos({})` with no date filters (so `start`/`end` stay `null` and the buggy `removeIf` calls are
skipped) — but the endpoint's own contract, and the typed hook that wraps it, both support and invite
range-filtered calls (`dashboard/page.tsx` and `processos/page.tsx` already call `useEventos` with
other filters). Any future caller that adds a date range (e.g., a week/day view, or an export) will
silently lose all older recurring events.

**Fix:** Filter on the *expanded* instances' dates, not the master's original `dataInicio`, and only
use `processoId`/`concluido` to prune the raw list up front:
```java
// Drop the two `removeIf` blocks keyed on start/end above recurrence expansion.
// After building `expanded`, apply the date window there instead:
if (start != null) {
    LocalDateTime finalStart = start;
    expanded.removeIf(e -> e.getDataInicio() != null && e.getDataInicio().isBefore(finalStart));
}
if (end != null) {
    LocalDateTime finalEnd = end;
    expanded.removeIf(e -> e.getDataInicio() != null && e.getDataInicio().isAfter(finalEnd));
}
```
(The `effectiveStart`/`effectiveEnd` window already used to bound the recurrence-expansion loop
itself is a separate, correct mechanism — it is the *pre*-expansion `removeIf` on the master list that
needs to move to after expansion.)

## Warnings

### WR-01: `createEvento` does not validate required domain fields; schema doesn't enforce them either

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2496-2521`, `backend/src/main/java/com/lexcv/models/Evento.java:27-35`
**Issue:** `createEvento` only validates `prioridade`'s allowed values and that `dataFim` isn't before
`dataInicio` *when both are present*. `titulo`, `dataInicio`, and `dataFim` are not annotated
`nullable = false` on the entity (contrast with `tenantId` and `concluido`, which are), and nothing in
the controller requires them either. A client that POSTs `{}` (or omits these fields) will create a
persisted Evento with a null title and null dates. The frontend `Evento` type
(`web/src/types/eventos.ts:5-22`) declares `titulo`, `dataInicio`, `dataFim` as required
non-nullable strings, so this contract can be violated by any non-browser caller (or a client-side
bug that bypasses the Zod form schema), producing `Invalid Date` artifacts wherever the calendar
derives grouping/labels from these fields.
**Fix:** Add explicit required-field checks in `createEvento` (mirroring the existing `prioridade`
validation style), e.g.:
```java
if (evento.getTitulo() == null || evento.getTitulo().isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "titulo é obrigatório"));
}
if (evento.getDataInicio() == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "dataInicio é obrigatório"));
}
```

### WR-02: Unguarded `.equals()` call on `getConcluido()` can NPE the whole tenant's eventos listing

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2371`
**Issue:**
```java
eventos.removeIf(e -> !e.getConcluido().equals(concluido));
```
`getConcluido()` returns the `Boolean` wrapper. The DB column is `nullable = false`, so in the normal
path this is safe, but there is no code-level guard — any legacy row inserted before this constraint
existed, or any row touched by a raw migration/manual SQL statement, would throw an NPE here and
return a 500 for the *entire* `GET /eventos` call for that tenant (not just the offending row), rather
than degrading gracefully.
**Fix:**
```java
eventos.removeIf(e -> !concluido.equals(e.getConcluido()));
```
(Flipping operand order avoids the NPE entirely since `concluido` — the request param — is
already null-checked by the enclosing `if`.)

### WR-03: `recurrenceRule` is loosely typed `string` in request DTOs but a strict union in the response type

**File:** `web/src/types/eventos.ts:16` (response) vs. `:33` and `:46` (create/update requests)
**Issue:** `Evento.recurrenceRule` is `'DAILY' | 'WEEKLY' | 'MONTHLY'`, but
`EventoCreateRequest.recurrenceRule` and `EventoUpdateRequest.recurrenceRule` are typed as the
unconstrained `string`. This means TypeScript will not catch a typo (`"Daily"`, `"WEEKLY "`, etc.) at
the call site, even though the backend's recurrence expansion loop only recognizes the three exact
literals and silently treats anything else as "no further recurrence" (`ResourceController.java:2465-2474`).
**Fix:**
```typescript
export interface EventoCreateRequest {
  ...
  recurrenceRule?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  ...
}
export interface EventoUpdateRequest {
  ...
  recurrenceRule?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  ...
}
```

### WR-04: Optimistic drag-and-drop reschedule doesn't recompute `risco` for the moved event

**File:** `web/src/app/(dashboard)/agenda/page.tsx:85-93`
**Issue:**
```typescript
const evs = (eventos.data ?? []).map((e) => {
  if (optimisticOverrides.has(e.id)) {
    const newInicio = optimisticOverrides.get(e.id)!;
    const durMs = new Date(e.dataFim).getTime() - new Date(e.dataInicio).getTime();
    const newFim = new Date(new Date(newInicio).getTime() + durMs).toISOString().slice(0, 19);
    return { ...e, isPrazo: false, dataInicio: newInicio, dataFim: newFim };
  }
  return { ...e, isPrazo: false };
});
```
The spread `{ ...e, ... }` carries over the stale `e.risco` value (computed server-side against the
*old* `dataInicio`) even though `dataInicio`/`dataFim` are being optimistically updated to the new
drop date. `weekStats.urgentes` (line 167) and any risk-based styling read `e.risco` directly, so
immediately after a drag-and-drop reschedule — until the invalidated query refetches — the
"Urgentes" counter and any risk badge reflect the pre-move date, not the post-move date.
**Fix:** Recompute (or drop) `risco` in the optimistic branch, e.g. clear it so risk-dependent UI
doesn't show a stale value: `return { ...e, isPrazo: false, dataInicio: newInicio, dataFim: newFim, risco: undefined };`
or, better, mirror the backend's threshold logic client-side for the optimistic window.

### WR-05: `EventoUpdateRequest` advertises null-clearable `tipo`/`descricao` that the backend can never clear

**File:** `web/src/types/eventos.ts:39,41` vs. `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2558-2559`
**Issue:** `EventoUpdateRequest.tipo?: string | null` and `.descricao?: string | null` type `null` as a
valid, distinct value (implying "clear this field"). But `updateEvento`'s partial-update pattern is
```java
if (payload.getTipo() != null) evento.setTipo(payload.getTipo());
if (payload.getDescricao() != null) evento.setDescricao(payload.getDescricao());
```
Since the request body is bound directly onto the `Evento` entity, JSON `"tipo": null` and an
entirely omitted `tipo` key are indistinguishable once deserialized (`payload.getTipo()` is `null`
either way) — so there is no way to actually null out a previously-set `tipo`/`descricao` via this
endpoint, despite the type signature implying it's supported. (`processoId` has the same issue but is
covered by CR-01, since that field is never even referenced by the update handler.)
**Fix:** Either narrow the TS types to drop the misleading `| null` until the backend supports it, or
change the backend to a proper PATCH DTO (not the raw entity) that can distinguish "field omitted"
from "field explicitly nulled" (e.g. `Optional<String>`/wrapper-per-field, or a `JsonNullable`-style
library), then apply `evento.setTipo(...)` unconditionally from that distinguishable value.

## Info

### IN-01: `useSetEventoConcluido` is unused dead code, duplicating `useToggleEventoConcluido`

**File:** `web/src/hooks/use-eventos.ts:110-126`
**Issue:** `useSetEventoConcluido` is exported but has no call sites anywhere under `web/src`
(verified via project-wide search). It duplicates `useToggleEventoConcluido` (lines 92-108) with a
different call signature (`{id, concluido}` at mutate-time vs. `id` bound at hook-call-time).
**Fix:** Remove the unused export, or if it's intended for a near-term caller, note that at the call
site to avoid future drift between the two near-identical implementations.

### IN-02: Dead conditional in `buildMonthGrid` — both branches return the same value

**File:** `web/src/app/(dashboard)/agenda/page.tsx:556-557`
**Issue:**
```typescript
if (end.getDay() === 6 && days.length === 42) return days;
return days;
```
Both the `if` branch and the fallthrough return the identical `days` array — the condition has no
effect on behavior. This looks like a vestige of an unfinished "5-week vs 6-week grid" optimization.
**Fix:** Remove the dead conditional:
```typescript
return days;
```

### IN-03: Minor formatting nit — stray double space in query `enabled` guards

**File:** `web/src/hooks/use-eventos.ts:37,49`
**Issue:** `typeof window !== "undefined" ;` and `typeof window !== "undefined"  && Boolean(id)` have
an extra space before the semicolon/operator. Purely cosmetic.
**Fix:** `const enabled = typeof window !== "undefined";` / `... "undefined" && Boolean(id);`

### IN-04: `recurrenceExceptions` stored as a raw comma-separated string rather than a structured type

**File:** `backend/src/main/java/com/lexcv/models/Evento.java:52-53`
**Issue:** `recurrenceExceptions` is a single `String` column holding a comma-joined list of
`LocalDate.toString()` values, parsed/rebuilt via `String.join`/`split(",")` at three separate call
sites in the controller (`listEventos`, `deleteEventoInstance`). This works today because
`LocalDate.toString()` never contains a comma, but it's a fragile, string-based encoding for
structured data with no shared parsing helper — each call site reimplements the same
split/trim/filter-empty logic.
**Fix:** Consider a small shared helper (`Set<LocalDate> parseExceptions(String)` /
`String serializeExceptions(Set<LocalDate>)`) on `Evento` or a dedicated utility, or model it as a
proper collection table if/when a follow-up migration touches this area.

---

_Reviewed: 2026-07-13T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
