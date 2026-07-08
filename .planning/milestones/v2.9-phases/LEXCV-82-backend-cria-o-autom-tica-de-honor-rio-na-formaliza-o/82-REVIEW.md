---
phase: LEXCV-82-backend-cria-o-autom-tica-de-honor-rio-na-formaliza-o
reviewed: 2026-07-07T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
findings:
  critical: 1
  warning: 1
  info: 1
  total: 3
status: issues_found
---

# Phase LEXCV-82: Code Review Report

**Reviewed:** 2026-07-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed `formalizarProcesso()` (`ResourceController.java:1193-1263`), focused on the newly-added idempotency-guarded Honorario auto-creation on the TRIAGEM→ATIVO transition.

Two claims from the phase SUMMARY were independently verified against the current code, not just trusted:

1. **`valorTotal` is genuinely always `null` on the auto-created row** — confirmed at line 1256, `Honorario.builder().processoId(id).valorTotal(null).dataAcordo(LocalDate.now()).build()`. There is no code path in `formalizarProcesso()` that populates it from `Cliente.honorariosPropostos` or anywhere else.
2. **The idempotency check genuinely runs before creation** — confirmed at line 1253, `if (honorarioRepository.findByProcessoId(id).isEmpty()) { ...save... }`, and the whole method is `@Transactional` (line 1193), so the check-and-create is at least atomic with respect to the rest of the state transition.

However, tracing what a `valorTotal: null` Honorario actually does to existing consumers (as instructed, rather than stopping at "the field is null as intended") surfaced a **BLOCKER**: the two existing `/financeiro` frontend pages that render `Honorario.valorTotal` do so with an unguarded `v.toLocaleString(...)` call, which throws on `null`. This is not a hypothetical edge case — it is the deterministic, 100%-reproducible result of every single successful `formalizarProcesso()` call, and it breaks the very UI path (the honorário edit dialog) that this feature's own design depends on to later fill in the real value. This is the most important finding in this review even though the throwing code lives outside the file that was in scope for this phase, because it is a direct, provable consequence of the value this method persists.

The previously-flagged idempotency race (check-then-act on `honorarioRepository.findByProcessoId(id).isEmpty()` with no DB-level backstop) was re-examined independently and is confirmed present, with no `synchronized` guard either (weaker than the analogous `createFacto` fix in Phase 81). It is recorded here as a **Warning**, consistent with how this project's own reviewers classified the identical bug class (`Facto(processo_id, ordem)`, Phase 81 WR-04) before it was later hardened.

## Critical Issues

### CR-01: Auto-created Honorario's `valorTotal: null` crashes both `/financeiro` list and `/financeiro/[id]` detail pages, blocking the only UI path to set the real fee

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1253-1260` (root cause: `.valorTotal(null)` at line 1256)

**Issue:** `formalizarProcesso()` persists a `Honorario` row with `valorTotal = null` by design (correctly avoiding auto-populating a real currency figure per the milestone's money-safety requirement). But nothing in this phase updated, or even checked, the existing consumers of `Honorario.valorTotal`, and the TypeScript contract for it is declared **non-nullable**:

- `web/src/types/financeiro.ts:4` — `valorTotal: number;` (no `| null`, no `?`).
- `web/src/app/(dashboard)/financeiro/page.tsx:15-17` — `formatMoneyCVE(v: number) { return v.toLocaleString("pt-CV", ...); }`, called unconditionally at **line 353** (desktop table row) and **line 396** (mobile card) for every honorário in the list, with no null check.
- `web/src/app/(dashboard)/financeiro/[id]/page.tsx:58-60` — the same `formatMoneyCVE`, called unconditionally at **line 412** (`formatMoneyCVE(honorario.data.valorTotal)`) in the detail page's summary block.

`null.toLocaleString is not a function` — calling `.toLocaleString()` on `null` throws a `TypeError` synchronously during render. Because this call is an inline JSX expression (`{formatMoneyCVE(h.valorTotal)}`), the exception happens while the parent component's render function is still executing, before React commits any output — the whole component tree for that route unmounts to the nearest error boundary. Concretely:

1. **`/financeiro` (the Honorários list)** becomes unusable the moment any single row has `valorTotal: null` — which, after this phase ships, happens on the very next `formalizar` action anyone performs. The crash is triggered by iterating `filteredList.map(...)`, so one bad row takes down the entire list for every user of that tenant.
2. **`/financeiro/{id}` (the detail page, which hosts the "Editar" dialog that is the *only* UI affordance for setting `valorTotal`)** also throws at line 412, before the "Editar" button/dialog (lines 266-320, earlier in JSX source order but *not* earlier in render — the whole render call fails as one unit) can ever reach the DOM. This means a user cannot open the edit form to fix the null value through the UI at all — the exact workflow this feature's design depends on (auto-create stub → user later confirms real value via a `financeiro:edit`-gated action) is unreachable once the stub exists.

This turns "create a safe, null `valorTotal` stub" into "create a stub that bricks the two pages needed to un-stub it," for every processo that gets formalized. It is a correctness/availability bug directly caused by the reviewed method's persisted data, not a pre-existing latent issue — before this phase, `Honorario` rows were only ever created via `createHonorario`/the `/financeiro/novo` form, whose Zod schema (`web/src/schemas/financeiro.ts`) requires `valorTotal`, so a null value never reached these consumers previously.

**Fix:** This needs a paired fix across both layers touched by this contract change — pick one of:

- **Preferred, minimal blast radius:** null-guard the frontend formatter and status calculation so a pending fee renders gracefully instead of throwing, e.g.:
```ts
// web/src/app/(dashboard)/financeiro/page.tsx and .../[id]/page.tsx
function formatMoneyCVE(v: number | null | undefined) {
  if (v == null) return "A confirmar";
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}
```
and update `HonorarioRow`/`Honorario` (`web/src/types/financeiro.ts:4`) to `valorTotal: number | null;`, plus guard `calcHonorarioStatus`/`restante` (`web/src/app/(dashboard)/financeiro/[id]/page.tsx:231`) against a null `valorTotal` (currently `honorario.data.valorTotal - totalPago` would compute `NaN` once the crash above is fixed, which is a lesser but related bug).
- **Alternative:** don't surface the auto-created stub through `GET /honorarios` / `GET /honorarios/{id}` until `valorTotal` is set (filter it out of `listHonorarios`, or return a distinct "draft" representation), so the existing non-nullable frontend contract stays valid and the stub is only exposed via a dedicated "pending honorários" affordance built for this feature.

Either way, this must land together with (or before) the backend change in this phase — right now the backend change alone ships a guaranteed-reproducible frontend crash.

## Warnings

### WR-01: Check-then-act idempotency guard on Honorario auto-creation has no DB-level backstop against a genuine concurrent race — same bug class as the already-fixed Facto(processo_id, ordem) issue, but with weaker mitigation

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1251-1260`

**Issue:** The guard is:
```java
if (honorarioRepository.findByProcessoId(id).isEmpty()) {
    Honorario honorario = Honorario.builder().processoId(id).valorTotal(null).dataAcordo(LocalDate.now()).build();
    honorarioRepository.save(honorario);
}
```
Under Postgres's default `READ COMMITTED` isolation (nothing in this method or `application.yml` raises it to `SERIALIZABLE`), two concurrent `formalizarProcesso(id)` calls for the *same* `id` can both execute `findByProcessoId(id)` and both observe zero rows before either transaction commits its `save()`, producing two `Honorario` rows for the same `processo_id`. `t_honorario` has no `UNIQUE` constraint on `processo_id` (confirmed: `Honorario.java` has no `@Table(uniqueConstraints=...)`, and no migration under `backend/migrations/` touches `t_honorario`), so nothing at the DB layer catches this even as a last resort. Unlike `createFacto`'s `ordem` assignment (Phase 81), this code doesn't even have an in-process `synchronized` block — it is strictly weaker than a mitigation this project's own reviewers already classified as insufficient (Phase 81 WR-04) and later hardened with a real unique constraint (commits `47be5c3`/`60ff17a`, migration `backend/migrations/81-add-facto-ordem-unique-constraint.sql`).

**Realistic likelihood in this app's usage pattern:** The frontend (`web/src/app/(dashboard)/processos/[id]/page.tsx:565`) does disable the "Formalizar Processo" button via `formalizarProcesso.isPending`, which reasonably mitigates the naive double-click case. But that guard is client-side and per-tab only — it does not protect against: (a) a network-level retry (proxy/load-balancer timeout causing the browser or an intermediary to resend the POST while the first request is still being processed server-side — the scenario this project's own `.planning/research/PITFALLS.md` Pitfall 4 explicitly calls out as the *higher*-risk trigger, ahead of double-click), (b) two staff members with `processos:manage` on the same tenant opening the same processo in separate sessions/tabs and both clicking formalizar within the race window, or (c) direct API calls (Postman/automation) bypassing the frontend entirely. So while not the most likely everyday occurrence, it is a real, financially-relevant risk (duplicate `Honorario` rows corrupt `Financeiro` totals and `ContaCorrente` balance math per `PITFALLS.md` Pitfall 4's own analysis), and this project has already treated the identical bug shape as worth fixing once found.

**Fix:** Apply the same pattern already established for `Facto` in Phase 81:
```java
// Honorario.java
@Table(name = "t_honorario", uniqueConstraints = @UniqueConstraint(columnNames = "processo_id"))
```
plus a migration script analogous to `backend/migrations/81-add-facto-ordem-unique-constraint.sql`, and in `formalizarProcesso()`:
```java
if (honorarioRepository.findByProcessoId(id).isEmpty()) {
    try {
        Honorario honorario = Honorario.builder().processoId(id).valorTotal(null).dataAcordo(LocalDate.now()).build();
        honorarioRepository.save(honorario);
    } catch (DataIntegrityViolationException ex) {
        // another concurrent formalizar already created it — safe to ignore, the row exists
    }
}
```
Note a straight `UNIQUE(processo_id)` constraint would also block any legitimate future "amendment" Honorario for the same processo if that's ever a supported use case — confirm that's acceptable (per `PITFALLS.md` Pitfall 4 prevention item 2, the auto-created one specifically should be the singleton, which a plain column-level unique constraint achieves only if manual multi-honorario-per-processo is never intended; otherwise a partial/conditional constraint or a `source`/`origem` discriminator column would be needed instead).

## Info

### IN-01: Auto-created Honorario stub has no marker distinguishing it as a pending/auto-created placeholder

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1254-1258`

**Issue:** The stub is created with `descricao` left unset (defaults to `null`), identical in shape to a real, manually-entered `Honorario` except for the null `valorTotal`. `PITFALLS.md` (Pitfall 4, Prevention item 2) explicitly suggested marking it via a `descricao` convention (e.g. "A confirmar") so it's visually distinguishable in lists/breadcrumbs from a deliberately-created honorário. Right now, `web/src/app/(dashboard)/financeiro/[id]/page.tsx:246` renders the breadcrumb as `honorario.data?.descricao ?? "…"`, so the auto-created stub shows a bare ellipsis with no indication of why the fee is unset.
**Fix:** Set a default descriptive placeholder on creation, e.g. `.descricao("Honorário a confirmar (criado automaticamente na formalização)")`, so the record is self-explanatory wherever it surfaces before someone fills in the real value.

---

_Reviewed: 2026-07-07T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
