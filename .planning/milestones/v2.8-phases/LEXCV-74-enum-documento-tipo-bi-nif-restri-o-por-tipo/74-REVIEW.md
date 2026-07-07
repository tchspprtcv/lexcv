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
  warning: 1
  info: 1
  total: 3
status: issues_found
---

# Phase 74: Code Review Report (Re-review, round 3 of 3)

**Reviewed:** 2026-07-03T00:00:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Third and final re-review pass. Per prior rounds, fixes were made for: the seeded-cliente
`tipo`/`documentoTipo` mismatch, the orphaned `documento_numero` without `documento_tipo` gap, the
mutable shared options array, `tipo`-nullability on create/update (round-2 `CR-01`/`WR-01`), and a
legacy-value silent-clear bug on the edit form (round-2 `WR-02`).

Verification of prior-round items:

- **Seeded-cliente tipo mismatch** (orig. round-1 `CR-01`) — confirmed fixed. `DatabaseSeeder.java`
  seeds `cliente1` as `tipo("PARTICULAR")` + `documentoTipo(DocumentoTipo.BI)` and `cliente2` as
  `tipo("EMPRESA")` + `documentoTipo(DocumentoTipo.REG_COMERCIAL)` — both valid combos.
- **Orphaned documento_numero without documento_tipo** (orig. round-1 `WR-01`) — confirmed fixed.
  `isDocumentoTipoValidoParaTipo` (`ResourceController.java:308-319`) rejects a `documentoNumero`
  present without a `documentoTipo`.
- **Mutable shared options array** (orig. round-1 `WR-02`) — confirmed fixed.
  `getDocumentoTipoOptions` returns `[...OPTIONS_BY_TIPO[...]]`, a fresh copy.
- **`tipo`-nullability on create/update, silent null-out on partial PUT** (round-2 `CR-01`/`WR-01`)
  — confirmed fixed. `isDocumentoTipoValidoParaTipo` now rejects any `tipo` that isn't exactly
  `TipoCliente.PARTICULAR.name()` or `TipoCliente.EMPRESA.name()` (lines 309-311) before looking at
  `documentoTipo` at all, closing the "absent tipo + absent documentoTipo passes validation" gap
  the previous round flagged. This also resolves the previous round's `IN-02` (magic string
  literals) as a side effect — the comparisons now go through the `TipoCliente` enum's `.name()`
  instead of raw `"PARTICULAR"`/`"EMPRESA"` literals.
- **Legacy invalid `documento_tipo`/`tipo` combo silently dropped on edit-form save** (round-2
  `WR-02`) — the specific mechanism described in that finding (native `<select>` silently falling
  back to `""` and clearing the field on an unrelated save) has been addressed: the edit page now
  detects a legacy/invalid combo on load (`legacyDocumentoTipo` state), injects an extra `<option>`
  so the raw value stays visibly selected, shows a banner, and the `onSubmit` handler special-cases
  the legacy value to preserve it verbatim when unchanged. However, this round finds that fix is
  **not actually reachable** — see `CR-01` below, which is effectively a new manifestation of the
  same underlying user-facing problem (can't save an edit without the legacy value being force-
  changed), just moved one layer up into the shared Zod schema that was added alongside the fix.
- **`IN-01` (missing doc-comment on `DocumentoTipo` explaining the NIF removal)** — still open,
  carried forward below (unchanged from round 2, not addressed in round 3's diff).

## Critical Issues

### CR-01: Legacy `documento_tipo` banner/carve-out on the edit form is unreachable — Zod schema blocks submission before `onSubmit` runs

**File:** `web/src/schemas/clientes.ts:66-75` (interacts with `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:192-218`)

**Issue:**
The edit page's `legacyDocumentoTipo` mechanism is designed so a user can save the form
*without touching* an invalid/legacy `documento_tipo` value and have it preserved verbatim (see
the comment at `editar/page.tsx:195-198` and the carve-out at lines 199-202). The intent is
spelled out explicitly in the on-screen banner at lines 378-384: *"Selecione um tipo de
documento válido para corrigir, ou **guarde sem alterar este campo para manter o valor
legado**."*

That carve-out logic lives entirely inside the component's `onSubmit` callback — but
`onSubmit` is only invoked by `form.handleSubmit(onSubmit)` **after** the `zodResolver`
validates the form against `clienteFormSchema`. The shared schema's `superRefine` unconditionally
rejects any `documento_tipo` that isn't in `getDocumentoTipoOptions(data.tipo)`:

```ts
if (
  data.documento_tipo &&
  !getDocumentoTipoOptions(data.tipo).some((option) => option.value === data.documento_tipo)
) {
  ctx.addIssue({
    code: z.ZodIssueCode.custom,
    message: "Tipo de documento inválido para o tipo de cliente selecionado",
    path: ["documento_tipo"],
  });
}
```

Because the legacy value is (by definition) not in the current tipo's option set, this
`superRefine` check fires on every submit attempt where the user leaves the legacy value in
place — exactly the scenario the banner tells the user is safe. React Hook Form's
`handleSubmit` short-circuits on validation failure: the page's `onSubmit` (containing the
`legacyDocumentoTipo` preservation branch) is **never called**, the form shows a red "Tipo de
documento inválido para o tipo de cliente selecionado" error under the select, and the user is
blocked from saving *any* change to this cliente record — not just documento_tipo — until they
clear or fix the field themselves. This directly contradicts the banner's stated escape hatch
and reintroduces a variant of the original defect: the user cannot save without touching the
field, because the schema treats "leave it alone" as a validation error instead of a no-op.

This also affects the **create** page indirectly: `clienteFormSchema` is shared between
`novo/page.tsx` and `editar/page.tsx`, so any future legacy-tolerant path added only to one
page's component logic will silently fail the same way unless the schema is made aware of it.

**Fix:** Give the schema visibility into the legacy value so it can exempt it, e.g. thread the
originally-loaded `documento_tipo` through as a schema-builder parameter, or move the
"is this combination valid for the current tipo" check out of the shared Zod schema and into
each page's `onSubmit` (where the legacy-aware logic already lives), leaving only the pure
"documento_numero requires documento_tipo and vice versa" pairing check in the schema. Example:

```ts
// clientes.ts
export function buildClienteFormSchema(allowedLegacyDocumentoTipo?: string | null) {
  return z.object({ /* ...same fields... */ }).superRefine((data, ctx) => {
    // ...existing pairing checks...
    const isLegacyExempt =
      allowedLegacyDocumentoTipo != null && data.documento_tipo === allowedLegacyDocumentoTipo;
    if (
      data.documento_tipo &&
      !isLegacyExempt &&
      !getDocumentoTipoOptions(data.tipo).some((option) => option.value === data.documento_tipo)
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Tipo de documento inválido para o tipo de cliente selecionado",
        path: ["documento_tipo"],
      });
    }
  });
}

// editar/page.tsx
const form = useForm<ClienteFormValues>({
  resolver: zodResolver(buildClienteFormSchema(legacyDocumentoTipo)),
  ...
});
```

`legacyDocumentoTipo` is set asynchronously (inside the `cliente.data` effect), so the resolver
must be re-created/updated once it's known. Add a regression test (component or integration
level) that loads a cliente with an out-of-set `documento_tipo`, submits the form unchanged, and
asserts the PUT request fires with the legacy value intact instead of a validation error being
shown — this is exactly the gap `cliente-documento-tipo.test.ts` doesn't cover, since it only
tests the pure helper functions, not the Zod schema/form integration where the regression lives.

## Warnings

### WR-01: `getDocumentoTipoOptions` fallback to PARTICULAR can mask a real "no tipo selected" state as valid on the create form

**File:** `web/src/lib/cliente-documento-tipo.ts:35-39`, used at `web/src/app/(dashboard)/clientes/novo/page.tsx:242`

**Issue:** `getDocumentoTipoOptions(undefined)` returns the PARTICULAR set as a UI fallback "to
avoid an empty dropdown before selection" (per the doc comment). On the create page, `tipo`
starts as `undefined`, and nothing prevents the user from opening the `documento_tipo` select
before choosing `tipo`. If the user picks a `documento_tipo` (e.g. `CNI`) while `tipo` is still
unset, then later selects `EMPRESA`, the previously-picked `documento_tipo` is not reset or
re-validated at selection time — it's only caught by `clienteFormSchema`'s `superRefine` at
submit. This is a minor UX inconsistency rather than a data-integrity issue (the backend's
`isDocumentoTipoValidoParaTipo` is authoritative and unconditional), but the dropdown can
present/retain options that are wrong for the user's eventual tipo choice with no visual cue
until submit fails.

**Fix:** Consider disabling/hiding the `documento_tipo` select until `tipo` is chosen, or
resetting `documento_tipo` whenever `tipo` changes on the create form. The edit form already has
tipo-change handling via `onTipoChange`/`confirmTipoChange`, but the create form's initial
`undefined → value` transition isn't covered by that dialog flow since `onTipoChange` only
guards `currentTipo && currentTipo !== newTipo` (i.e. it never fires on the very first
selection).

## Info

### IN-01: `DocumentoTipo.java` still has no doc-comment explaining the NIF removal / clean-cut decision

**File:** `backend/src/main/java/com/lexcv/models/DocumentoTipo.java:1-8`
**Issue:** Carried forward from round 2 — still unaddressed in this round's diff. The migration
script and commit history both explain, at length, that `NIF` was deliberately removed as a
`documentoTipo` value (superseded by the dedicated `Cliente.nif` field) and that this was a
clean cut with no data migration. None of that context lives next to the enum itself, so a
future contributor reading only `DocumentoTipo.java` has no signal that re-adding `NIF` here
would be a regression of a considered decision.
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

---

_Reviewed: 2026-07-03T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
