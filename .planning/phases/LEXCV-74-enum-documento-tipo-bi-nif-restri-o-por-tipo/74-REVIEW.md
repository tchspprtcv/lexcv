---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
reviewed: 2026-07-03T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/DocumentoTipo.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/migrations/74-cleanup-nif-documento-tipo.sql
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - web/src/types/clientes.ts
  - web/src/lib/cliente-documento-tipo.ts
  - web/src/lib/cliente-documento-tipo.test.ts
  - web/src/schemas/clientes.ts
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
findings:
  critical: 1
  warning: 2
  info: 2
  total: 5
status: issues_found
---

# Phase 74: Code Review Report

**Reviewed:** 2026-07-03T00:00:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Reviewed the `DocumentoTipo` enum clean-cut (`NIF` removed, `BI` added), the backend `tipo`/`documento_tipo` restriction validator in `ResourceController`, the defensive migration script, the seeder, and the frontend shared lookup module + two form pages that consume it.

The core contract — `DocumentoTipo = {BI, CNI, PASSAPORTE, REG_COMERCIAL}`, `getDocumentoTipoOptions`/`toDocumentoTipo` as a single source of truth, per-tipo dropdown filtering, Zod `superRefine` validation, and the mirrored backend `isDocumentoTipoValidoParaTipo` check — is implemented consistently and matches the phase plans. The dual snake_case/camelCase read/write pattern for `documento_tipo`/`documentoTipo` is preserved correctly on both pages, and the shared module's unit tests cover the stated behavior contract.

However, `DatabaseSeeder.java` was modified by this phase's own first commit to satisfy the compiler (swapping the now-illegal `DocumentoTipo.NIF` for `DocumentoTipo.BI`) but was never reconciled with the `tipo`/`documentoTipo` pairing rule that a later commit in the same phase introduces — the seeded demo clients end up with a `tipo`/`documentoTipo` combination that violates the phase's own new business rule and breaks editing those specific seeded records in the UI. There is also a residual validation gap (present both client- and server-side) where `documento_numero` can be submitted/persisted with no `documento_tipo`, which is directly relevant to a phase whose stated purpose is "restrição por tipo."

## Critical Issues

### CR-01: Seeded demo clients violate the new tipo/documentoTipo pairing rule and cannot be re-saved from the edit form

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:96-108, 112-124`
**Issue:** Commit `8a9ed8e` (this phase, task 2/3 of plan 74-01) changed `cliente1.documentoTipo` from the now-deleted `DocumentoTipo.NIF` to `DocumentoTipo.BI` purely to keep the seeder compiling, but left `cliente1.tipo("SINGULAR")` and `cliente2.tipo("COLETIVA")` untouched. A later commit in the same phase (`c93f3e0`, `ResourceController#isDocumentoTipoValidoParaTipo`) introduces the rule that `documentoTipo` is only valid for `tipo="PARTICULAR"` (CNI/BI/PASSAPORTE) or `tipo="EMPRESA"` (REG_COMERCIAL only) — any other `tipo` value with a non-null `documentoTipo` is rejected.

`DatabaseSeeder` writes directly via `clienteRepository.save(...)`, bypassing the controller, so seeding itself does not crash. But the two demo clients it creates (`tipo="SINGULAR"` + `documentoTipo=BI`, `tipo="COLETIVA"` + `documentoTipo=REG_COMERCIAL`) are now inconsistent with the rule this same phase introduces, and this has a concrete user-facing consequence: `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:134` casts `cliente.data.tipo as "PARTICULAR" | "EMPRESA" | undefined` — for `"SINGULAR"`/`"COLETIVA"` this cast is unsound (the runtime value is neither), so `clienteFormSchema`'s required `tipo: z.enum(["PARTICULAR", "EMPRESA"])` fails validation on submit with "Selecione o tipo de cliente" while the RadioGroup shows no option selected, permanently blocking any edit-and-save of these seeded records without first fixing `tipo` through some other channel (there is no such channel exposed in the UI — `tipo` can only be chosen via the same restricted RadioGroup).

**Fix:**
```java
Cliente cliente1 = Cliente.builder()
        .tenantId(tenantId)
        .tipo("PARTICULAR")   // was "SINGULAR" — must match TipoCliente values used by isDocumentoTipoValidoParaTipo
        .nome("João Andrade (PostgreSQL Real)")
        ...
        .documentoTipo(DocumentoTipo.BI)
        ...

Cliente cliente2 = Cliente.builder()
        .tenantId(tenantId)
        .tipo("EMPRESA")      // was "COLETIVA"
        .nome("Empresa Atlântico, SA")
        ...
        .documentoTipo(DocumentoTipo.REG_COMERCIAL)
        ...
```

## Warnings

### WR-01: No validation rejects documento_numero submitted without a documento_tipo (client and server)

**File:** `web/src/schemas/clientes.ts:51-68`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java:306-317`
**Issue:** The `superRefine` in `clienteFormSchema` only checks the forward direction (`documento_tipo` set → `documento_numero` required, and `documento_tipo` must be in the allowed set for `tipo`). It never checks the reverse: a user who previously filled `documento_numero`, then resets `documento_tipo` back to "Nenhum" (empty string), can submit with `documento_numero` populated and `documento_tipo` empty — this passes both branches of `superRefine` since both conditions are guarded by `data.documento_tipo &&`.

Server-side, `isDocumentoTipoValidoParaTipo` in `ResourceController.java` has the identical gap: `if (documentoTipo == null) { return true; }` unconditionally passes regardless of `documentoNumero`'s value. The end result is a cliente record with an orphaned document number and no document type, which is exactly the kind of inconsistent state a phase about "restrição por tipo" for document identification should prevent, and it also silently reintroduces ambiguity about what `documento_numero` even represents once `documento_tipo` is cleared (in `novo/page.tsx` this can only happen via a manual field edit since `confirmTipoChange` only clears the number when the *type* becomes invalid for the new tipo, not when the type is cleared to empty by the user directly changing the select to "Nenhum" while leaving the number field untouched).

**Fix:**
```typescript
// web/src/schemas/clientes.ts — add a companion branch in superRefine
if (data.documento_numero && !data.documento_tipo) {
  ctx.addIssue({
    code: z.ZodIssueCode.custom,
    message: "Selecione o tipo de documento correspondente ao número introduzido",
    path: ["documento_tipo"],
  });
}
```
```java
// ResourceController.java — mirror server-side (defense in depth, since Zod is UX-only per T-74-06)
private boolean isDocumentoTipoValidoParaTipo(String tipo, DocumentoTipo documentoTipo, String documentoNumero) {
    if (documentoTipo == null) {
        return documentoNumero == null || documentoNumero.isBlank();
    }
    ...
}
```

### WR-02: Shared OPTIONS_BY_TIPO arrays are returned by reference, not cloned

**File:** `web/src/lib/cliente-documento-tipo.ts:35-39`
**Issue:** `getDocumentoTipoOptions` returns the live array stored in the module-level `OPTIONS_BY_TIPO` record rather than a copy. Any caller that mutates the returned array (e.g. `.sort()`, `.push()`, `.splice()`) would permanently corrupt the shared lookup table for every subsequent caller across the app for the remainder of the module's lifetime, which is especially risky given the doc-comment explicitly frames this module as the single "fonte única de verdade" consumed by multiple pages. Current callers only `.map()` over the result, so there is no live bug today, but the function's public contract (returning a mutable array) invites this class of defect the moment a future caller sorts or filters the array in place instead of deriving a new one.
**Fix:**
```typescript
export function getDocumentoTipoOptions(
  tipo: ClienteTipo | undefined,
): DocumentoTipoOption[] {
  return [...OPTIONS_BY_TIPO[tipo ?? "PARTICULAR"]];
}
```

## Info

### IN-01: DocumentoTipo.java has no doc-comment explaining the NIF removal / clean-cut decision

**File:** `backend/src/main/java/com/lexcv/models/DocumentoTipo.java:1-8`
**Issue:** The migration script and commit message both explain, at length, that `NIF` was deliberately removed as a `documentoTipo` value (superseded by the dedicated `Cliente.nif` field) and that this was a clean-cut with no data migration. None of that context lives next to the enum itself, so a future contributor reading only `DocumentoTipo.java` has no signal that re-adding `NIF` here would be a regression of a considered decision.
**Fix:**
```java
package com.lexcv.models;

/**
 * Tipo de documento de identificação do cliente.
 * NIF is intentionally NOT a value here — Cliente.nif is the sole source of
 * truth for NIF (see backend/migrations/74-cleanup-nif-documento-tipo.sql).
 * Do not reintroduce NIF as a DocumentoTipo constant.
 */
public enum DocumentoTipo {
    BI,
    CNI,
    PASSAPORTE,
    REG_COMERCIAL
}
```

### IN-02: isDocumentoTipoValidoParaTipo relies on tipo being one of two magic string literals with no shared constant

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:306-317`
**Issue:** `Cliente.tipo` is a plain `String` (not the existing `TipoCliente` enum defined in `backend/src/main/java/com/lexcv/models/TipoCliente.java`), and this method compares it against the literals `"PARTICULAR"` / `"EMPRESA"` inline. Nothing ties these literals to `TipoCliente`'s constants, so the two can drift silently (e.g. a future rename of `TipoCliente.EMPRESA` would not be caught by the compiler here). This is pre-existing (the `tipo` field predates this phase) but this phase's new method compounds the magic-string surface area.
**Fix:** Use `TipoCliente.EMPRESA.name()` / `TipoCliente.PARTICULAR.name()` instead of the raw literals, or migrate `Cliente.tipo` to the `TipoCliente` enum type (larger, separate change) so the compiler enforces the relationship.

---

_Reviewed: 2026-07-03T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
