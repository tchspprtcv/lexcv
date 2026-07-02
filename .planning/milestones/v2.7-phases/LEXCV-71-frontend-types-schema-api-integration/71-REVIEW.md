---
phase: 71-frontend-types-schema-api-integration
reviewed: 2026-07-01T00:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - web/src/types/clientes.ts
  - web/src/schemas/clientes.ts
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
findings:
  critical: 2
  warning: 4
  info: 3
  total: 9
status: issues_found
---

# Phase 71: Code Review Report

**Reviewed:** 2026-07-01
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

The type-flattening and Zod-schema work (`web/src/types/clientes.ts`, `web/src/schemas/clientes.ts`) is clean and matches the plan: `dados_tipo`/`DadosTipoParticular`/`DadosTipoEmpresa` are fully removed, `DocumentoTipo` includes `REG_COMERCIAL`, and `nifPattern` correctly enforces exactly 9 digits.

However, the consumer-page adaptation in plan 71-02 introduced a genuine data-loss bug in the edit page's NIF-sync logic, and both create/edit pages ship a confusing dual-source-of-truth for `nif` that was only half-guarded. The undocumented CSV bulk-import fix in `clientes/page.tsx` also skips the same `nifPattern` validation the rest of the app now enforces, silently sending malformed NIFs to the backend instead of catching them client-side. None of these were caught by the plan's `tsc --noEmit` gate because they are logic bugs, not type errors.

## Critical Issues

### CR-01: Edit page's NIF-sync assignment can silently null out a valid NIF on update

**File:** `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:199-202`
**Issue:** In `onSubmit`, `payload` is first built from `...values` (which includes the Zod-validated, mandatory 9-digit `nif` field). The subsequent sync block:
```ts
if (values.documento_tipo === "NIF") {
  payload.nif = values.documento_numero;
}
```
runs whenever `documento_tipo === "NIF"`, regardless of whether `documento_numero` is populated. `documento_numero` is `optionalTrimmedString` (`string | undefined`) — if the user has `documento_tipo` set to `"NIF"` but leaves `documento_numero` blank (a valid, unvalidated combination unless the "documento_numero is required if documento_tipo is set" superRefine issue is currently displayed and ignored, or the user clears the field after selecting NIF), `payload.nif` is overwritten with `undefined`. Because `ClienteUpdateRequest.nif` is optional, this compiles fine and passes `tsc --noEmit`, but at runtime it strips the client's previously-valid, form-validated NIF from the PUT payload — either dropping the field entirely (if the backend treats `undefined` as "no change", benign) or, worse, if the backend's PUT/PATCH semantics treat present-but-`undefined`-serialized-as-omitted differently from explicit blanking, this is a latent data-integrity risk. At minimum it silently discards the user's actual, valid NIF input in the dedicated "NIF (Legado)" field in favor of an unrelated, possibly-empty field — this is a genuine behavioral regression, not merely a type-safety gap.

Compare with `novo/page.tsx:104-106`, which correctly guards this exact assignment:
```ts
if (values.documento_tipo === "NIF" && values.documento_numero) {
  payload.nif = values.documento_numero;
}
```
The 71-02 plan explicitly told the executor this counterpart "is type-safe as written and must be left unchanged" (71-02-PLAN.md line 173-174) — but "type-safe" is not the same as "logically correct"; the plan's reasoning only addressed the TS compile error, not the underlying null-out bug that existed even before this phase.

**Fix:**
```ts
// Sincronizar NIF se tipo for NIF
if (values.documento_tipo === "NIF" && values.documento_numero) {
  payload.nif = values.documento_numero;
}
```

### CR-02: CSV bulk-import bypasses the app's own NIF format validation, sending non-conformant NIFs to the API

**File:** `web/src/app/(dashboard)/clientes/page.tsx:156-157`
**Issue:** The undocumented CSV-import fix added a `!nif` guard to skip rows with an empty NIF cell, but it does not validate NIF format:
```ts
const nif = idxNif >= 0 ? (r[idxNif] ?? "").trim() : "";
if (!nome || !nif) {
  failed++;
  continue;
}
```
Any non-empty string (e.g. `"abc"`, `"123"`, `"123456789012"`) passes this guard and is sent directly to `createCliente.mutateAsync({ ..., nif, ... })`, i.e. straight to the backend `POST /clientes`. This is inconsistent with the newly-added `nifPattern` (`/^\d{9}$/`) that the manual create/edit forms now enforce client-side — bulk import silently accepts malformed NIF data that the single-record forms would reject with a clear inline error. Because the backend does not yet enforce NIF format either (per 71-01's own threat model, T-71-01: "Backend does NOT yet enforce NIF format — this is a PRE-EXISTING gap"), a malformed NIF from CSV import will likely be persisted as-is, producing bad data that bypasses the very validation this phase was built to add. This directly undermines CLI-05's intent.

**Fix:**
```ts
import { nifPattern } from "@/schemas/clientes";
// ...
const nif = idxNif >= 0 ? (r[idxNif] ?? "").trim() : "";
if (!nome || !nifPattern.test(nif)) {
  failed++;
  continue;
}
```

## Warnings

### WR-01: Dual source of truth for `nif` confuses users and risks overwriting explicit input

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:184, 104-106`; `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:260-265, 199-202`
**Issue:** Both pages present a standalone, labeled "NIF (Legado)" input bound to the Zod-validated `nif` field, and separately a "Tipo de Documento" / "Número do Documento" pair. If the user picks `documento_tipo = "NIF"` and enters a `documento_numero` that differs from what they typed in "NIF (Legado)", the dedicated NIF field is silently overwritten on submit with no visual indication to the user that their entry was replaced. This is confusing UX and a maintenance trap — a future edit to either code path can easily reintroduce CR-01-style bugs. The "(Legado)" label suggests this field is meant to be phased out, but it's still the schema's canonical, mandatory NIF source per CLI-05, while the sync logic quietly treats `documento_numero` as authoritative when `documento_tipo === "NIF"`.
**Fix:** Either remove the sync entirely (since the dedicated `nif` field is now mandatory and independently validated, there is no need to derive it from `documento_numero`), or make the two fields explicitly linked in the UI (e.g. disable/mirror one when the other is set) so the precedence is visible to the user. This is a good candidate to flag for the Phase 72 form rebuild.

### WR-02: `DocumentoTipo` union includes `REG_COMERCIAL` but no form exposes it

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:242-246`; `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:346-350`
**Issue:** Plan 71-01 added `REG_COMERCIAL` to the `DocumentoTipo` union specifically to support EMPRESA-type documents (per the Phase 70 backend enum). Both the create and edit `<select id="documento_tipo">` elements only render `NIF`, `CNI`, `PASSAPORTE` options — `REG_COMERCIAL` is unreachable from the UI. An EMPRESA client can never have this document type set from the form, even though `toDocumentoTipo`'s allow-list (`DOCUMENTO_TIPOS`) includes it and the backend presumably expects it to be usable. This looks like a gap between the type work and the (admittedly deferred-to-Phase-72) form UI, but worth flagging now since it silently limits functionality this phase claims to enable.
**Fix:** Add `<option value="REG_COMERCIAL">Registo Comercial</option>` to both selects, or explicitly document this as deferred to Phase 72 in the SUMMARY (it currently is not called out).

### WR-03: `documento_tipo`/`documento_numero` NIF-format check is now redundant with, and can diverge from, the dedicated `nif` field's validation

**File:** `web/src/schemas/clientes.ts:57-66`
**Issue:** The `superRefine` block still validates `documento_numero` as exactly-9-digits when `documento_tipo === "NIF"`, duplicating the new mandatory `nif` field's `nifPattern` regex validation. These two validations are independent and can disagree (e.g. `nif` valid, `documento_numero` invalid, or vice versa), producing two different error states for what a user perceives as "my NIF." Given CR-01/WR-01 above, this duplication is a root cause of the sync-logic confusion.
**Fix:** No immediate code change required, but flag for Phase 72: consider deriving `documento_numero`'s NIF validation from `nifPattern` directly (`nifPattern.test(...)` instead of the inline digit-count check) to keep the two paths from drifting, or collapse them into one field.

### WR-04: `useCreateCliente`/CSV import failure reporting doesn't distinguish validation failures from server errors

**File:** `web/src/app/(dashboard)/clientes/page.tsx:161-174`
**Issue:** The per-row `try { await createCliente.mutateAsync(...) } catch { failed++; }` swallows the actual error (network failure, validation failure, tenant conflict, etc.) with no logging or detail retained. Combined with CR-02, a user who imports a CSV with malformed NIFs will see a generic "Import parcial: N criado(s), M falhou/invalid" with no indication *why* rows failed (missing name/NIF vs. a rejected NIF format vs. a server-side conflict). This was true before this phase's CSV fix too, but the newly-added NIF-required skip makes silent failures more likely to occur, worsening the existing gap.
**Fix:** Track failure reasons (e.g. `{ row: i, reason: "nif inválido" }`) and surface a summary or downloadable failed-rows report, at minimum log `console.warn` per failed row with the caught error for diagnosability. Not blocking for this phase, but worth tracking.

## Info

### IN-01: `ClienteUpdateRequest.nif` optionality is inconsistent with the "NIF is mandatory" requirement (CLI-05) for existing clients

**File:** `web/src/types/clientes.ts:89`
**Issue:** Per 71-01's own design note, `nif` was intentionally kept optional on `ClienteUpdateRequest` for "PATCH-style partial-update semantics." This is a reasonable type-level choice, but combined with CR-01, it means the type system provides no compile-time protection against accidentally omitting/blanking a mandatory business field on update — the only enforcement is the Zod schema at form-submission time, and (as CR-01 shows) a post-schema sync step can still bypass it. This is an accepted, documented tradeoff, not a bug in itself, but worth flagging as residual risk for reviewers of Phase 72/73 who touch this payload-assembly code again.
**Fix:** No action required now; consider a runtime assertion or a narrower `Omit<ClienteUpdateRequest, "nif"> & { nif?: string }` intent-revealing comment near the `payload.nif` sync sites so future edits don't reintroduce CR-01.

### IN-02: `toDocumentoTipo` allow-list duplicated verbatim across two files

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:30-34`; `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:30-34`
**Issue:** `DOCUMENTO_TIPOS` and `toDocumentoTipo` are byte-for-byte identical in both pages (introduced as an undocumented Rule-3 auto-fix in 71-02, per the SUMMARY). This is minor duplication but is exactly the kind of narrowing helper that belongs in a shared module (e.g. `web/src/types/clientes.ts` or a small `web/src/lib/documento-tipo.ts`) so future additions to the `DocumentoTipo` union don't require updating two copies in sync.
**Fix:** Extract to a shared helper, e.g. export `toDocumentoTipo` from `web/src/types/clientes.ts` alongside the `DocumentoTipo` union.

### IN-03: Ficha page's Empresa NIF field bypasses the `isBlank` styling logic that other blanked fields use

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:187-191`
**Issue:** For EMPRESA clients, `<Field label="NIF" value={fmt(cliente.nif)} />` renders the real NIF (correct, since `nif` is now mandatory and always populated), while the four sub-fields below it (`Nome Comercial`, `Sede`, `Representante Legal`, `Cargo`) are all hard-coded to `fmt(undefined)` → always blank. This is intentional per the plan (deferred to Phase 73) and not a bug, but the visual result is a "Ficha" for EMPRESA clients that looks broken/incomplete (4 blank fields in a row) with no indication to the printed-document reader that this is a known temporary limitation rather than missing data entry. Worth a heads-up for Phase 73 planning, not a defect in this phase's scope.
**Fix:** None required in this phase; consider a subtle placeholder note (e.g. "dados a preencher — indisponível nesta versão") if Phase 73 is not imminent.

---

_Reviewed: 2026-07-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
