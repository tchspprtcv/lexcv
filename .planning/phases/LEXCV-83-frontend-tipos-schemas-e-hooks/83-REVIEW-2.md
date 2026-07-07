---
phase: LEXCV-83-frontend-tipos-schemas-e-hooks
reviewed: 2026-07-07T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - web/src/hooks/use-processos.ts
  - web/src/types/processos.ts
  - web/src/schemas/processos.ts
  - web/src/lib/processo-juizo-origem-mapping.ts
  - web/package.json
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
status: issues_found
---

# Phase LEXCV-83: Code Review Report (Re-review after fixes)

**Reviewed:** 2026-07-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Re-reviewed the five in-scope files after the fix commits for `83-REVIEW.md`'s CR-01, CR-02, WR-01, WR-02, WR-03, WR-05 (commits `3af6325`, `d9a5985`, `8004460`). All six targeted fixes were verified against the actual git diffs and, where applicable, against the live backend (`ResourceController.listProcessos`/`getProcesso`/`updateProcesso`) as ground truth. `npx tsc --noEmit` is clean except for the three pre-existing, unrelated `vitest`-module errors in `.test.ts` files (baseline, unaffected by these changes).

**Confirmed fixed, no new problems:**

1. **WR-05 (type derivation from Zod, no circular import):** `types/processos.ts` now does `export type TipoDecisao = z.infer<typeof tipoDecisaoSchema>` (and `TipoTestemunha`/`OrigemProcesso` likewise), importing `tipoDecisaoSchema`/`tipoTestemunhaSchema`/`origemProcessoSchema` from `@/schemas/processos` via `import type`. `schemas/processos.ts` has zero imports from `@/types/processos` (only imports `zod`), so there is no import cycle — and even if there were, `import type` is erased at compile time and can't create a runtime cycle. Grepped every consumer of `TipoDecisao`/`TipoTestemunha`/`OrigemProcesso` from `@/types/processos` (`lib/origem-processo.ts`, `lib/tipo-decisao.ts`, `lib/tipo-testemunha.ts`, `lib/processo-juizo-origem-mapping.ts`, `hooks/use-processos.ts`, plus the dashboard pages) — all still compile and their `Record<TipoDecisao, string>`-style exhaustiveness checks still work, because `z.enum([...])`'s inferred type is the same string-literal union as the hand-written unions it replaced. `tsc --noEmit` confirms no regressions.

2. **CR-01 (legalHold/dataRetencao forwarding):** `toProcessoApiPayload` now sends `legalHold: "legal_hold" in payload ? payload.legal_hold : undefined` and the `dataRetencao` equivalent. Verified this is correctly asymmetric: `ProcessoCreateRequest` never declares these keys (so `"in"` is always `false` for create, keeping create payloads clean/unchanged), while the one real call site building `ProcessoUpdateRequest` (`app/(dashboard)/processos/[id]/editar/page.tsx`) always explicitly seeds both keys via `useForm`'s `defaultValues` (`legal_hold: false`, `data_retencao: undefined`) and `form.reset()`, so `"legal_hold" in values` / `"data_retencao" in values` are always `true` at that call site — the update path now genuinely forwards both fields to the backend, fixing the silent legal-hold wipe described in CR-01.

3. **CR-02 (numero_processo/tipo_processo fallback ordering):** Confirmed against the backend that `GET /processos` (`listProcessos`, `ResourceController.java:919-920`) emits snake_case `numero_processo`/`tipo_processo` only, while `GET /processos/{id}` (`getProcesso`, line 986) serializes the JPA entity directly, i.e. camelCase `numeroProcesso`/`tipoProcesso` only. The new fallback chains — `api.numero ?? api.numeroProcesso ?? api.numero_processo` and `api.tipoProcesso ?? api.tipo_processo ?? api.titulo` — correctly try camelCase before snake_case, so the detail endpoint's response is unaffected (still resolved via `numeroProcesso`/`tipoProcesso`, first non-nullish candidate) and the list endpoint's response is now correctly resolved via the new `numero_processo`/`tipo_processo` fallback that didn't exist before. Neither response shape breaks the other.

4. **WR-01 (verify script wired):** `web/package.json` now has `"verify:juizo-origem": "node scripts/verify-juizo-origem-roundtrip.mjs"`. Ran it directly (`node web/scripts/verify-juizo-origem-roundtrip.mjs` → `PASS`, exit 0) — it is genuinely runnable via `pnpm verify:juizo-origem` now, addressing the original complaint that nothing invoked the file.

5. **WR-02/WR-03 (doc comments):** Both are now present exactly as suggested — `types/processos.ts` on `FactoUpdateRequest.ordem` and `lib/processo-juizo-origem-mapping.ts` above `mapJuizoOrigemToPayload` — and are comment-only changes, so they carry no behavioral risk.

**One residual issue found**, described below.

## Warnings

### WR-06: CR-01's `"in"`-based guard extends the already-flagged WR-03 fragility to `legal_hold`/`data_retencao`, and the round-trip test suite doesn't cover it — a future caller that omits these keys (rather than setting them to `undefined`) silently reintroduces the original CR-01 bug

**File:** `web/src/hooks/use-processos.ts:139-140`, `web/src/hooks/use-processos.round-trip.test.ts:47-56`

**Issue:** The CR-01 fix is structurally sound only because the JS `"in"` operator distinguishes "key present with value `undefined`" from "key absent" — and the *only* live call site (`processos/[id]/editar/page.tsx`) happens to always populate both keys via `useForm`'s `defaultValues`/`form.reset()`. This is the exact same non-type-enforced invariant that `83-REVIEW.md`'s WR-03 already flagged for `origem` (fragile duck-typing over object shape rather than a discriminated union), now extended to two more fields — one of which (`legal_hold`) is explicitly called out in `CR-01`'s own writeup as a compliance-sensitive field ("Legal Hold — Bloquear eliminação de docs").

Concretely, `ProcessoUpdateRequest.legal_hold`/`data_retencao` are optional TS properties, so a perfectly well-typed object literal can omit them entirely (not merely set them to `undefined`) and still satisfy the type:

```ts
const updateRequest: ProcessoUpdateRequest = { juizo: "2º Juízo Cível" }; // valid TS, no legal_hold/data_retencao key at all
"legal_hold" in updateRequest   // false
"data_retencao" in updateRequest // false
```

This isn't hypothetical — the project's own `use-processos.round-trip.test.ts:47-56` constructs exactly this shape (`const updateRequest: ProcessoUpdateRequest = { juizo: "2º Juízo Cível" };`) for its `ProcessoUpdateRequest`/`origem` test, demonstrating the pattern is one a future contributor (e.g. someone writing a "partial update" helper, or a second edit form/bulk-edit flow that doesn't reuse the exact `useForm` defaultValues pattern) could easily reproduce for `legal_hold`/`data_retencao` — silently reintroducing the CR-01 data-loss bug this round of fixes was meant to close. No test in this file (or elsewhere) asserts that `toProcessoApiPayload` forwards `legalHold`/`dataRetencao` for update payloads, despite the file already containing `juizo`/`origem` round-trip assertions for the analogous case.

**Fix:** Add a round-trip test asserting `toProcessoApiPayload({ legal_hold: true, data_retencao: "2026-01-01", ...})` on a `ProcessoUpdateRequest` produces `legalHold: true, dataRetencao: "2026-01-01"` in the payload, and (more importantly) a negative test proving that an update payload which *omits* the keys entirely — mirroring the existing `{ juizo: "..." }`-only literal already in the suite — does *not* forward them, to make the current fragility visible and regression-testable rather than silently relying on one call site's `useForm` defaults. Longer-term, consider the discriminated-union approach already suggested for WR-03 (`{ kind: "create"; ... } | { kind: "update"; ... }`), which would make omission a compile-time-visible choice instead of a runtime `"in"` check.

---

_Reviewed: 2026-07-07T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
