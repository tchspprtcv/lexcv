---
phase: LEXCV-70-backend-refactoring-seeder-alignment
reviewed: 2026-07-02T00:08:55Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/DocumentoTipo.java
  - backend/src/main/java/com/lexcv/models/Cliente.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
findings:
  critical: 2
  warning: 1
  info: 1
  total: 4
status: issues_found
---

# Phase 70: Code Review Report

**Reviewed:** 2026-07-02T00:08:55Z
**Depth:** standard
**Files Reviewed:** 4 (source files reviewed; `DadosTipo.java`/`DadosTipoConverter.java` confirmed deleted, not reviewable)
**Status:** issues_found

## Summary

The mechanical part of this refactor is done correctly and matches the plan: `REG_COMERCIAL` was added cleanly to `DocumentoTipo`, the `dados_tipo` `@Convert` field/import/setter calls were removed from `Cliente` and `ResourceController` exactly as scoped, `DadosTipo`/`DadosTipoConverter` are deleted with zero remaining backend references, and the Empresa seed client now uses `DocumentoTipo.REG_COMERCIAL` while the Singular client is untouched. `mvn -DskipTests package` is reported to succeed.

However, tracing the change across the stack (backend entity/controller + frontend forms) surfaces two blocker-level regressions that the plan's narrow file scope did not anticipate, plus a latent business-logic gap that the new `REG_COMERCIAL` enum value now makes reachable:

1. The frontend still fully depends on a `dados_tipo` object (nome_comercial, representante_legal, sede, cargo, idade, sexo, nacionalidade) that no backend field now persists or returns — this silently discards data on create/update and blocks re-saving existing Empresa clients due to now-unfillable required-field validation.
2. `createCliente`/`updateCliente`'s NIF-derivation logic only populates `nif` when `documentoTipo == NIF`; any client created via the API (not the seeder, which sets `.nif(...)` directly) with `documentoTipo = REG_COMERCIAL` will have `nif == null`, breaking NIF-based search, cliente↔processo matching, and merge pre-fill for genuine Empresa clients — the primary use case `REG_COMERCIAL` was introduced for.

These are flagged as Critical because they represent real user-facing data loss / broken save flows triggered directly by this phase's change, even though the touched files are outside the plan's stated `files_modified` list — the plan's scope boundary does not exempt correctness of the resulting system behavior.

## Critical Issues

### CR-01: `dados_tipo` removal breaks frontend Cliente create/edit forms (data loss + validation deadlock)

**File:** `backend/src/main/java/com/lexcv/models/Cliente.java` (field removed) — consumed by `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:150` and `web/src/app/(dashboard)/clientes/novo/page.tsx:97-127`, validated by `web/src/schemas/clientes.ts:22-34,78-90`

**Issue:** The frontend `clienteFormSchema` still declares a required `dados_tipo` object for EMPRESA clients (`nome_comercial`, `representante_legal` are validated as required, see `web/src/schemas/clientes.ts:78+`), and both the "novo" and "editar" pages build and submit a `dados_tipo` payload on every create/update (`novo/page.tsx:96-117`, `editar/page.tsx:187-207`). The edit page also pre-fills the form from `cliente.data.dados_tipo` on load (`editar/page.tsx:150`).

Since `Cliente.java` no longer has a `dadosTipo` field (and Spring's default Jackson config does not set `FAIL_ON_UNKNOWN_PROPERTIES`, so the extra JSON key is silently dropped rather than erroring), the backend:
- Never persists `nome_comercial`/`representante_legal`/`sede`/`cargo` (or the PARTICULAR `idade`/`sexo`/`nacionalidade` fields) submitted at create time — the data the user just typed is silently discarded.
- Never returns this data on `GET /clientes/{id}`, so `editar/page.tsx:150` always initializes `dados_tipo` to `{}`.
- Because `nome_comercial`/`representante_legal` are required-if-EMPRESA in the Zod schema, editing *any* existing Empresa client whose `dados_tipo` blob was populated before this phase now fails client-side validation on save unless the user manually retypes company details that already existed in the (now orphaned) `dados_tipo` DB column — an unrecoverable-via-UI data loss/deadlock for those records.

**Fix:** Either (a) restore a backend field/DTO that maps `dados_tipo` to real columns/JSON storage consistent with the flat-column model (e.g. persist `nome_comercial`/`representante_legal`/etc. as flat `Cliente` columns or a still-mapped JSON blob), or (b) if `dados_tipo` capture is being intentionally retired, remove the corresponding fields/validation/payload code from `web/src/schemas/clientes.ts`, `web/src/app/(dashboard)/clientes/novo/page.tsx`, and `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` in the same change so client and server stay in sync. This phase should not have been marked complete/scoped-out without addressing (or explicitly deferring with a tracked follow-up) this frontend/backend contract break.

```ts
// web/src/schemas/clientes.ts — if dados_tipo is retired, drop the required-field
// superRefine checks at lines ~78-90 and the `dados_tipo` object at lines 22-34,
// and stop sending it from novo/page.tsx and editar/page.tsx.
```

### CR-02: NIF-derivation logic never fires for `REG_COMERCIAL` clients created via the API, silently nulling `nif`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:225-227` (createCliente), `:284-288` (updateCliente)

**Issue:**
```java
if (cliente.getDocumentoTipo() == DocumentoTipo.NIF) {
    cliente.setNif(cliente.getDocumentoNumero());
}
```
and
```java
if (payload.getDocumentoTipo() == DocumentoTipo.NIF) {
    cliente.setNif(payload.getDocumentoNumero());
} else if (payload.getNif() != null) {
    cliente.setNif(payload.getNif());
}
```
`nif` is a functionally load-bearing field elsewhere in this controller: client search (`ResourceController.java:181,191`), cliente↔processo NIF matching (`:1018-1044`), and client merge pre-fill (`:743-744`). Prior to this phase, `REG_COMERCIAL` didn't exist, so every non-NIF client was CNI/PASSAPORTE (individuals, where `nif` legitimately being unset was acceptable). Now that `REG_COMERCIAL` exists specifically for company clients (per CLI-09, the very use case `nif` search/matching most needs), a company created through `POST /clientes` or edited through `PUT /clientes/{id}` with `documentoTipo = REG_COMERCIAL` and no explicit `nif` in the payload will have `nif == null` — breaking NIF search and processo-association matching for that client. The seeded Empresa client only avoids this because the seeder sets `.nif("512345678")` directly, bypassing this controller logic entirely — masking the bug in the one place it's exercised.

The frontend's own "novo" page NIF-sync only fires `if (values.documento_tipo === "NIF")` (`novo/page.tsx:125-127`), so it does not compensate either — a REG_COMERCIAL submission from the current UI (once the enum is exposed there) would also omit `nif`.

**Fix:** Extend the derivation to cover `REG_COMERCIAL` (and any other document type intended to double as the tax/registration identifier), or explicitly require the frontend to submit `nif` alongside `documentoNumero` for REG_COMERCIAL and validate that server-side:
```java
if (payload.getDocumentoTipo() == DocumentoTipo.NIF
        || payload.getDocumentoTipo() == DocumentoTipo.REG_COMERCIAL) {
    cliente.setNif(payload.getDocumentoNumero());
} else if (payload.getNif() != null) {
    cliente.setNif(payload.getNif());
}
```
(Mirror the same change in `createCliente`.) Confirm with product/domain whether `REG_COMERCIAL` numbers are always NIF-equivalent in Cape Verde company registration before applying this fix as-is.

## Warnings

### WR-01: Frontend `documento_tipo` select does not offer `REG_COMERCIAL`, making the new enum value unreachable from the UI

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:365-368`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:409-421` (same `<select>` pattern)

**Issue:** The `documento_tipo` `<select>` options are hardcoded to `NIF`, `CNI`, `PASSAPORTE`. `REG_COMERCIAL` was added to the backend enum (CLI-09) but there is no UI path to select it, so the only way a `Cliente` ever gets `documentoTipo = REG_COMERCIAL` today is via `DatabaseSeeder`. This isn't a backend bug, but it means the phase's stated purpose ("enables company-registration identification") is not actually usable end-to-end yet — worth flagging so it isn't mistaken for a completed feature.

**Fix:** Add a `<option value="REG_COMERCIAL">Registo Comercial</option>` to both selects (and update the accompanying Zod NIF-format validation at `web/src/schemas/clientes.ts:68` to not force the 9-digit NIF format check when `documento_tipo === "REG_COMERCIAL"`), likely as a follow-up phase.

## Info

### IN-01: Orphaned `dados_tipo` DB column left silently unmapped

**File:** `backend/src/main/java/com/lexcv/models/Cliente.java` (field removed, DB column not dropped)

**Issue:** The plan explicitly and correctly chose not to drop the `dados_tipo` column to avoid destructive DDL on shared dev DBs under `ddl-auto=update`. This is a reasonable call, but combined with CR-01 above, existing rows' `dados_tipo` JSON now become permanently inaccessible dead data via the API (no code path reads that column anymore) unless a future migration/backfill re-maps it into the flat columns before the column is eventually dropped.

**Fix:** No action required now; track a follow-up to either (a) backfill `nome_comercial`/`representante_legal`/etc. from the orphaned `dados_tipo` JSON into new flat columns before the column is dropped in a later migration, or (b) confirm this data was disposable and schedule the column drop.

---

_Reviewed: 2026-07-02T00:08:55Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
