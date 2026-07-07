---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
fixed_at: 2026-07-03T17:58:08Z
review_path: .planning/phases/LEXCV-74-enum-documento-tipo-bi-nif-restri-o-por-tipo/74-REVIEW.md
iteration: 2
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 74: Code Review Fix Report

**Fixed at:** 2026-07-03T17:58:08Z
**Source review:** .planning/phases/LEXCV-74-enum-documento-tipo-bi-nif-restri-o-por-tipo/74-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 3 (fix_scope: critical_warning — CR-01, WR-01, WR-02)
- Fixed: 3
- Skipped: 0

Note: IN-01 and IN-02 remain open by design (out of scope for `critical_warning`); they were carried forward unaddressed from the previous review round and are documented in 74-REVIEW.md for a future `--fix all` pass.

## Fixed Issues

### CR-01: `updateCliente` can silently null out `cliente.tipo` on a partial PUT

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `0d5c3be`
**Applied fix:** `isDocumentoTipoValidoParaTipo` now rejects any `tipo` that is not `PARTICULAR`/`EMPRESA` up front, independent of whether `documentoTipo`/`documentoNumero` are present — closing the gap where a request with a null or garbage `tipo` passed validation. `updateCliente`'s `cliente.setTipo(payload.getTipo())` is now guarded with `if (payload.getTipo() != null)`, matching the pattern used for the method's other optional fields (`descricaoCaso`, `documentosEntregues`, etc.), so the write can no longer clobber existing data even if the validator's behavior changes in the future. Also switched the `"PARTICULAR"`/`"EMPRESA"` literal comparisons to `TipoCliente.PARTICULAR.name()`/`TipoCliente.EMPRESA.name()`, incidentally addressing part of IN-02's magic-string concern on the touched lines (IN-02 itself remains open as it covers additional surface area outside `critical_warning` scope). Verified no existing caller (`DatabaseSeeder.java`) creates a cliente without `tipo`, so tightening the validator does not break seeding.

### WR-01: `createCliente` accepts a cliente with `tipo` null/unrecognized when no documento_tipo is supplied

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `0d5c3be` (same commit as CR-01 — identical root cause and fix in `isDocumentoTipoValidoParaTipo`)
**Applied fix:** Same validator tightening as CR-01. `POST /clientes` with `tipo: null` or an unrecognized `tipo` and no `documento_tipo`/`documento_numero` is now rejected with 400 before the cliente is persisted.

### WR-02: Edit form can silently drop a legacy invalid `documento_tipo`/`tipo` combo on unrelated save

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx`
**Commit:** `ecdfa09`
**Applied fix:** On load (`useEffect` driven by `cliente.data`), the form now detects when the loaded `documento_tipo` isn't a member of `getDocumentoTipoOptions(tipo)` and stores it in a new `legacyDocumentoTipo` state. The `<select>` renders that raw legacy value as an extra flagged `<option>` (so the native select doesn't silently fall back to "Nenhum" for an unmatched value), and an inline amber warning banner explains the mismatch to the user. `onSubmit` preserves the legacy value verbatim when the user hasn't changed it, instead of routing it through `toDocumentoTipo()` (which would still return `undefined` for it and reintroduce the silent-clear bug). The state is cleared when the user explicitly changes `tipo` and confirms the resulting `documento_tipo` reset via the existing `confirmTipoChange` flow.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-03T17:58:08Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
