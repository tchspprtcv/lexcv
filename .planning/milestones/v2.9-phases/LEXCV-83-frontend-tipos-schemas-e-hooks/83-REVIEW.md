---
phase: LEXCV-83-frontend-tipos-schemas-e-hooks
reviewed: 2026-07-07T23:18:54Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - web/src/types/processos.ts
  - web/src/schemas/processos.ts
  - web/src/lib/tipo-decisao.ts
  - web/src/lib/tipo-testemunha.ts
  - web/src/lib/origem-processo.ts
  - web/src/lib/processo-juizo-origem-mapping.ts
  - web/src/hooks/use-processos.ts
  - web/scripts/verify-juizo-origem-roundtrip.mjs
findings:
  critical: 2
  warning: 5
  info: 3
  total: 10
status: issues_found
---

# Phase LEXCV-83: Code Review Report

**Reviewed:** 2026-07-07T23:18:54Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Reviewed the frontend types/schemas/hooks for the new Decisão/Facto/Testemunha entities and the `juizo`/`origem` fields on Processo, cross-checking every request/response field name against the live backend (`backend/src/main/java/com/lexcv/controllers/ResourceController.java` and the `Decisao`/`Testemunha`/`Facto`/`Processo` JPA entities, read as ground truth even though not part of the change set).

The five items the task asked to specifically verify all check out correctly:

1. `useUpdateDecisao`/`useAddTestemunha`/`useUpdateTestemunha` send exactly the keys the backend's `Map<String,Object>`-based handlers do `payload.get(...)` for (`data`/`tipo`/`resumo`; `nome`/`tipo`/`contacto`/`notas`). Confirmed by reading `updateDecisao` (ResourceController.java:1767-1811), `createTestemunha`/`updateTestemunha` (1855-1930).
2. `useAddFacto` never sends `ordem` (`FactoCreateRequest` has no `ordem` field); `useUpdateFacto` does send it (`FactoUpdateRequest.ordem: number`, required). Matches `createFacto`/`updateFacto` (1959-2016), which is entity-typed (`@RequestBody Facto`), not Map-based.
3. `useAddDecisao`'s `FormData` keys (`data`, `tipo`, `resumo`, `file`) match `createDecisao`'s `@RequestParam` names exactly (1693-1698).
4. `juizo`/`origem` round-trip through `mapJuizoOrigemFromApi`/`mapJuizoOrigemToPayload` is correct for both response shapes — but only because `juizo`/`origem` happen to be single-word field names where camelCase and snake_case coincide. See CR-02 below: the exact same "manual translation layer" pattern this module was built to fix is *already broken* for other fields in the same file (`numero`/`tipo_processo`), which the phase's own `PITFALLS.md` (Pitfall 1) explicitly warns about and this phase did not backport the fix to.
5. `web/scripts/verify-juizo-origem-roundtrip.mjs` genuinely imports the real shared module (`../src/lib/processo-juizo-origem-mapping.ts`) — verified by executing it (`node scripts/verify-juizo-origem-roundtrip.mjs` → `PASS`), not a reimplementation. However see WR-01: it is not wired into any npm script or CI step, so it provides no automatic regression protection.

Beyond the scoped verification, tracing `toProcessoApiPayload`/`ProcessoApiPayload` all the way to the real, already-wired `processos/[id]/editar/page.tsx` page uncovered a genuine silent-data-loss bug (CR-01): every processo edit through the existing UI wipes `legal_hold` and `data_retencao` to `false`/`null` server-side, regardless of what the user set, because the outgoing JSON payload never includes those keys. This is the exact class of bug documented in this project's own `PITFALLS.md` Pitfall 1, just on different fields than the ones this phase was watching.

## Critical Issues

### CR-01: `toProcessoApiPayload` silently drops `legal_hold` and `data_retencao` on every processo update — wipes an active legal hold on unrelated edits

**File:** `web/src/hooks/use-processos.ts:71-83, 119-132`

**Issue:** `ProcessoApiPayload` (the wire-format type sent to the backend) has no `legalHold`/`dataRetencao` keys, and `toProcessoApiPayload()` never maps `payload.legal_hold`/`payload.data_retencao` into the outgoing object, even though `ProcessoUpdateRequest` declares both fields (`types/processos.ts:72-73`) and the real edit form collects and submits them (`web/src/app/(dashboard)/processos/[id]/editar/page.tsx:88-89,96`: `legal_hold: processo.data.legal_hold ?? false`, `await update.mutateAsync(values satisfies ProcessoUpdateRequest)`).

The backend's `updateProcesso` (`ResourceController.java:990-1012`) is a full-replace handler over an entity-typed `@RequestBody Processo payload`:
```java
processo.setLegalHold(payload.getLegalHold() != null ? payload.getLegalHold() : false);
processo.setDataRetencao(payload.getDataRetencao());
```
Since the frontend never sends `legalHold`/`dataRetencao` in the JSON body, Jackson deserializes those fields as `null` on every single call, and the backend then **unconditionally resets `legal_hold` to `false` and `data_retencao` to `null`** — on every save, including edits that only touch e.g. `tribunal` or `descricao`. "Legal Hold" is a compliance feature (UI label: "Bloquear eliminação de docs" — block document deletion); silently disabling it on unrelated edits is a real data-integrity/compliance regression, not a cosmetic bug.

**Fix:**
```ts
type ProcessoApiPayload = {
  // ...existing fields...
  legalHold?: boolean;
  dataRetencao?: string;
};

export function toProcessoApiPayload(payload: ProcessoCreateRequest | ProcessoUpdateRequest): ProcessoApiPayload {
  return {
    // ...existing fields...
    legalHold: "legal_hold" in payload ? payload.legal_hold : undefined,
    dataRetencao: "data_retencao" in payload ? payload.data_retencao : undefined,
    ...mapJuizoOrigemToPayload(payload),
  };
}
```
(`ProcessoCreateRequest` doesn't have `legal_hold`/`data_retencao` today, so the guard mirrors the existing `mapJuizoOrigemToPayload` pattern — adjust if `legal_hold` should also be settable at creation.)

### CR-02: List endpoint (`GET /processos`) returns snake_case `numero_processo`/`tipo_processo`, but `normalizeProcesso`/`ProcessoApi` only read camelCase — case number and type never populate in the processos list

**File:** `web/src/hooks/use-processos.ts:39-69, 95-117`

**Issue:** `ResourceController.listProcessos` hand-builds its response map for `GET /processos` with **snake_case** keys for these two fields specifically (`ResourceController.java:919-920`):
```java
m.put("numero_processo", p.getNumeroProcesso());
m.put("tipo_processo", p.getTipoProcesso());
```
while `GET /processos/{id}` (`getProcesso`, line 981-986) returns the JPA entity directly, which Jackson serializes with **camelCase** (`numeroProcesso`, `tipoProcesso`) since no snake_case naming strategy is configured.

`ProcessoApi` declares `numeroProcesso?: string; numero?: string;` and `tipoProcesso?: string; titulo?: string;` — note there is **no** `numero_processo`/`tipo_processo` key on the type at all (unlike `area_juridica`/`data_inicio`/`data_fim`/etc., which correctly declare both casings and are read with `??` fallbacks). `normalizeProcesso` does:
```ts
numero: api.numero ?? api.numeroProcesso,
titulo: api.titulo ?? api.tipoProcesso,
tipo_processo: api.tipoProcesso ?? api.titulo,
```
For the list endpoint's actual response shape, none of `api.numero`, `api.numeroProcesso`, `api.titulo`, `api.tipoProcesso` exist — every processo in `useProcessos()` gets `numero: undefined` and `tipo_processo/titulo: undefined`.

This is not theoretical: `web/src/app/(dashboard)/processos/page.tsx:344` renders `{p.numero || p.titulo || "Sem número"}` for every row in the list — confirming this is a real, user-visible defect (every case in the processos list shows "Sem número" instead of its case number), even though the same field displays correctly on the detail page (`useProcesso(id)` hits the entity-serialized detail endpoint, where `numeroProcesso` does exist).

**Fix:** Add the missing snake_case keys to `ProcessoApi` and read them, matching the pattern already used for `area_juridica`/`data_inicio`/etc.:
```ts
type ProcessoApi = {
  // ...
  numeroProcesso?: string;
  numero_processo?: string;
  numero?: string;
  tipoProcesso?: string;
  tipo_processo?: string;
  titulo?: string;
  // ...
};

export function normalizeProcesso(api: ProcessoApi): Processo {
  return {
    // ...
    numero: api.numero ?? api.numeroProcesso ?? api.numero_processo,
    titulo: api.titulo ?? api.tipoProcesso ?? api.tipo_processo,
    tipo_processo: api.tipoProcesso ?? api.tipo_processo ?? api.titulo,
    // ...
  };
}
```
Longer-term, consider making the backend `listProcessos` map consistent (camelCase) with `getProcesso`'s entity serialization to remove the need for dual-casing entirely — this exact "manual translation layer, no type-level exhaustiveness check" pattern is called out as Pitfall 1 in this project's own `.planning/research/PITFALLS.md`, and this bug is a live instance of it.

## Warnings

### WR-01: `verify-juizo-origem-roundtrip.mjs` is not wired into any npm script or CI step

**File:** `web/scripts/verify-juizo-origem-roundtrip.mjs`, `web/package.json`

**Issue:** The script's own header comment frames it as "prova automatizada e executável" (automated, executable proof) guarding against Pitfall 1 regressions. It does genuinely work (`node scripts/verify-juizo-origem-roundtrip.mjs` → `PASS`), and it does import the real shared module rather than reimplementing it. But `web/package.json`'s `scripts` block only has `dev`/`build`/`start`/`lint` — nothing invokes this file, and there is no CI workflow referencing it either. A companion Vitest spec (`use-processos.round-trip.test.ts`) explicitly says it isn't runnable today because no test runner is installed. As it stands, this "regression proof" only runs if a human remembers to invoke it manually — it provides zero protection against the exact class of bug (CR-02) found elsewhere in this same review.

**Fix:** Add a `pnpm verify:juizo-origem` script (`"verify:juizo-origem": "node scripts/verify-juizo-origem-roundtrip.mjs"`) and call it from `pnpm build`/CI, or fold it into a real test runner once one is installed.

### WR-02: `FactoUpdateRequest.ordem` is required, but `factoFormSchema` never collects it — the future consuming form must thread it through by hand

**File:** `web/src/types/processos.ts:187-191`, `web/src/schemas/processos.ts:143-148`

**Issue:** `factoFormSchema` only has `descricao`/`data`. `FactoUpdateRequest` requires `ordem: number`. No component in this phase consumes `useUpdateFacto` yet, so this doesn't manifest as a runtime bug today, but it is an undocumented contract gap: whoever builds the edit-Facto UI must remember to pass the existing `facto.ordem` value alongside the form's `descricao`/`data` output, with no schema-level reminder that it's needed. Given this project's documented history of exactly this kind of "the type says X is required but nothing enforces supplying it correctly" gap, it's worth flagging now rather than after the next phase silently omits it (TS will catch a missing key, but not a wrong/stale one, e.g. reusing a stale `ordem` after a concurrent reorder).

**Fix:** Add a code comment on `FactoUpdateRequest.ordem` noting it must be sourced from the current `Facto.ordem`, not user input, or consider a dedicated `useReorderFacto`/separate PATCH-style hook that doesn't require the full entity's `ordem` to be reconstructed manually.

### WR-03: `mapJuizoOrigemToPayload`'s create/update disambiguation relies on the runtime `"in"` operator over duck-typed objects, not a discriminated union

**File:** `web/src/lib/processo-juizo-origem-mapping.ts:25-32`

**Issue:** `"origem" in payload ? payload.origem : undefined` correctly returns `undefined` for real `ProcessoUpdateRequest` literals today (verified by the round-trip test/script), because `ProcessoUpdateRequest` doesn't declare an `origem` property. But this safety currently depends entirely on call sites constructing genuinely-typed `ProcessoUpdateRequest` objects rather than spreading a full `Processo` (which does carry `origem`) into an update payload — e.g. a future `update.mutateAsync({ ...processo, tribunal: "..." })` would silently leak `origem` back into the update JSON body, because object spread copies the property regardless of the target type's declared shape. The backend currently tolerates this (`origem is intentionally excluded` in `updateProcesso`), so it's not exploitable today, but it's a fragile, non-type-enforced invariant that a reviewer wouldn't catch via `tsc`.

**Fix:** Consider a discriminated union (`{ kind: "create"; ...} | { kind: "update"; ... }`) instead of structural presence-checking, or at minimum a code comment warning against spreading `Processo`/`ProcessoApi` shapes into update payloads.

### WR-04: `DecisaoCreateRequest.file: File` vs. `decisaoFormSchema.file: FileList` — type shapes diverge across the schema/type boundary

**File:** `web/src/types/processos.ts:138-143`, `web/src/schemas/processos.ts:121-130`

**Issue:** `decisaoFormSchema` (built for a native `<input type="file">` via react-hook-form) types `file` as `FileList`, while `DecisaoCreateRequest.file` (consumed by `useAddDecisao`) types it as a single `File`. No component in this phase wires the two together yet, so `tsc` will catch the mismatch when that integration is written (`values.file` would need `values.file?.[0]` extraction), but this is exactly the "two independently-typed intermediate shapes that must be kept in sync by hand" pattern this project's `PITFALLS.md` flags as its recurring failure mode. Not a live bug, but worth calling out for whoever wires the Decisão upload form next.

**Fix:** No code change required now; when building the consuming form, extract `values.file?.[0]` before calling `useAddDecisao`.

### WR-05: Enum value lists are duplicated between `types/processos.ts` and `schemas/processos.ts` with no shared source of truth

**File:** `web/src/types/processos.ts:9-17`, `web/src/schemas/processos.ts:27-30, 48-55`

**Issue:** `TipoDecisao`/`TipoTestemunha`/`OrigemProcesso` (TS union types) and `tipoDecisaoSchema`/`tipoTestemunhaSchema`/`origemProcessoSchema` (Zod enums) list the same literal values twice, by hand, in two files. `tsc` will not catch drift if a value is added to one and not the other (a Zod enum missing a valid TS union member fails silently at runtime validation, not at compile time). Low risk today (the values are stable, per `PITFALLS.md` Pitfall 8), but this is the same category of un-enforced dual-maintenance that caused this project's prior field-mismatch bugs.

**Fix:** Derive the TS union type from the Zod enum (`type TipoDecisao = z.infer<typeof tipoDecisaoSchema>`) instead of maintaining both independently, or vice versa.

## Info

### IN-01: Label-mapping helpers silently fall back to the first enum value for unrecognized input, masking bad data instead of surfacing it

**File:** `web/src/lib/tipo-decisao.ts:13`, `web/src/lib/tipo-testemunha.ts:11`, `web/src/lib/origem-processo.ts:11`

**Issue:** `return map[tipo] ?? "Despacho"` (and the `TipoTestemunha`/`OrigemProcesso` equivalents) is dead code for well-typed TS callers (`Record<TipoDecisao, string>` guarantees every valid key maps to a string), but if the backend ever returns an unexpected/corrupted enum value (e.g. a future enum member added server-side before the frontend is updated), it silently displays as "Despacho"/"Autor"/"Petição Inicial" instead of indicating something is wrong.

**Fix:** Consider `map[tipo] ?? \`Desconhecido (${tipo})\`` so unrecognized values are visibly flagged rather than silently mislabeled as a specific, misleading value.

### IN-02: `toProcessoApiPayload` still sends `estado` on `PUT /processos/{id}` even though the backend explicitly ignores it there

**File:** `web/src/hooks/use-processos.ts:127`

**Issue:** `estado: payload.estado` is included in every outgoing update payload, but `updateProcesso` (`ResourceController.java:1003`) explicitly comments `// estado is intentionally excluded: changes must go through /transicao or /formalizar` and never calls `setEstado`. Harmless (backend ignores it), but it's a dead/misleading field in the payload that could confuse a future reader into thinking state transitions work through this path.

**Fix:** Drop `estado` from `ProcessoApiPayload`/`toProcessoApiPayload`'s update path, or add a comment noting it's intentionally sent-but-ignored for parity with `ProcessoCreateRequest`.

### IN-03: `TestemunhaCreateRequest` and `TestemunhaUpdateRequest` are structurally identical

**File:** `web/src/types/processos.ts:160-172`

**Issue:** Both interfaces declare the exact same four fields (`nome`, `tipo`, `contacto`, `notas`) with identical optionality. Pure duplication; not wrong, but unnecessary.

**Fix:** `export type TestemunhaUpdateRequest = TestemunhaCreateRequest;` (or keep separate only if they're expected to diverge soon).

---

_Reviewed: 2026-07-07T23:18:54Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
