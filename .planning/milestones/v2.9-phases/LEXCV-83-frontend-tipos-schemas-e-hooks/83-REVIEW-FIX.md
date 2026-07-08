---
phase: LEXCV-83-frontend-tipos-schemas-e-hooks
fixed_at: 2026-07-07T23:34:10Z
review_path: .planning/phases/LEXCV-83-frontend-tipos-schemas-e-hooks/83-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 6
skipped: 1
status: partial
---

# Phase LEXCV-83: Code Review Fix Report

**Fixed at:** 2026-07-07T23:34:10Z
**Source review:** .planning/phases/LEXCV-83-frontend-tipos-schemas-e-hooks/83-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (2 critical, 5 warning; IN-01/IN-02/IN-03 excluded per default critical_warning scope)
- Fixed: 6
- Skipped: 1

## Fixed Issues

### CR-01: `toProcessoApiPayload` silently drops `legal_hold` and `data_retencao` on every processo update

**Files modified:** `web/src/hooks/use-processos.ts`
**Commit:** 3af6325
**Applied fix:** Added `legalHold?: boolean` and `dataRetencao?: string` to `ProcessoApiPayload`, and mapped them in `toProcessoApiPayload` using the same `"legal_hold" in payload ? payload.legal_hold : undefined` / `"data_retencao" in payload ? payload.data_retencao : undefined` guard pattern already used for `origem` (since `ProcessoCreateRequest` doesn't declare these fields, only `ProcessoUpdateRequest` does). Added an inline comment explaining why the guard exists and what silently breaks without it.

### CR-02: List endpoint (`GET /processos`) returns snake_case `numero_processo`/`tipo_processo`, but `normalizeProcesso`/`ProcessoApi` only read camelCase

**Files modified:** `web/src/hooks/use-processos.ts`
**Commit:** 3af6325
**Applied fix:** Added `numero_processo?: string` and `tipo_processo?: string` to the `ProcessoApi` type, and extended the `??` fallback chains in `normalizeProcesso` (`numero`, `titulo`, `tipo_processo`) to also read these snake_case keys, matching the existing dual-casing pattern already used for `area_juridica`/`data_inicio`/etc.

### WR-01: `verify-juizo-origem-roundtrip.mjs` is not wired into any npm script

**Files modified:** `web/package.json`
**Commit:** 9683e8c
**Applied fix:** Added `"verify:juizo-origem": "node scripts/verify-juizo-origem-roundtrip.mjs"` to the `scripts` block, matching the existing formatting/quoting style. Verified `pnpm verify:juizo-origem` runs and prints `PASS`.

### WR-02: `FactoUpdateRequest.ordem` is required but no form collects it yet

**Files modified:** `web/src/types/processos.ts`
**Commit:** d9a5985
**Applied fix:** Added a code comment above the `ordem` field in `FactoUpdateRequest` noting it must be sourced from the current `Facto.ordem` value, not user input, and warning against reusing a stale value after a concurrent reorder. Documentation-only, no behavior change.

### WR-03: `mapJuizoOrigemToPayload`'s create/update disambiguation via `"origem" in payload` is fragile

**Files modified:** `web/src/lib/processo-juizo-origem-mapping.ts`
**Commit:** d9a5985
**Applied fix:** Added a code comment above `mapJuizoOrigemToPayload` warning against spreading `Processo`/`ProcessoApi` shapes into update payloads passed to this function, since object spread would carry `origem` along regardless of the target type's declared shape. Documentation-only, no refactor to a discriminated union (explicitly left as a "consider" alternative in the review, not required).

### WR-05: `TipoDecisao`/`TipoTestemunha`/`OrigemProcesso` TS union types duplicate the Zod enum literals by hand

**Files modified:** `web/src/types/processos.ts`
**Commit:** 8004460
**Applied fix:** Changed `TipoDecisao`, `TipoTestemunha`, and `OrigemProcesso` in `types/processos.ts` to `z.infer<typeof tipoDecisaoSchema>` / `z.infer<typeof tipoTestemunhaSchema>` / `z.infer<typeof origemProcessoSchema>`, importing the schemas from `@/schemas/processos`. Verified no circular import (`schemas/processos.ts` only imports from `zod`) and that all consumers (`lib/tipo-decisao.ts`, `lib/tipo-testemunha.ts`, `lib/origem-processo.ts`, which use `Record<TipoDecisao, string>` etc.) still type-check unchanged, since the derived union has the identical literal shape.

## Skipped Issues

### WR-04: `DecisaoCreateRequest.file: File` vs. `decisaoFormSchema.file: FileList` type mismatch

**File:** `web/src/types/processos.ts:138-143`, `web/src/schemas/processos.ts:121-130`
**Reason:** Explicitly out of scope — the review's own Fix section states "No code change required now; when building the consuming form, extract `values.file?.[0]` before calling `useAddDecisao`." No component in this phase wires the two together yet, so left untouched per the review's own guidance.
**Original issue:** `decisaoFormSchema.file` types as `FileList` (native `<input type="file">` shape) while `DecisaoCreateRequest.file` types as a single `File`. `tsc` will catch the mismatch when the consuming form is eventually written.

## Verification

- `cd web && npx tsc --noEmit` — passes cleanly after all fixes; only the 3 pre-existing, unrelated `vitest`-module-not-found errors remain (`src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, `src/schemas/clientes.legacy-documento-tipo.test.ts`), confirmed identical before and after the fix commits (no test runner installed yet, pre-existing on `master`).
- `node web/scripts/verify-juizo-origem-roundtrip.mjs` (and `pnpm verify:juizo-origem` from `web/`) — prints `PASS` after the CR-01/CR-02 changes to the same file (`use-processos.ts`), confirming the round-trip mapping for `juizo`/`origem` is unaffected.

---

_Fixed: 2026-07-07T23:34:10Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
