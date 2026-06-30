---
phase: LEXCV-58-formulario-dinamico
reviewed: 2026-06-30T00:00:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - web/src/components/ui/radio-group.tsx
  - web/src/components/ui/switch.tsx
  - web/src/types/clientes.ts
  - web/src/schemas/clientes.ts
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 2
  warning: 6
  info: 4
  total: 12
status: issues_found
---

# Phase LEXCV-58: Code Review Report

**Reviewed:** 2026-06-30
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Reviewed the dynamic client form (Particular/Empresa), the tipo-switch confirmation dialog, the Avençado switch, and the numero_cliente/avencado badges added to listing/detail/edit pages. The new Radix-based `RadioGroup`/`Switch` primitives are implemented correctly and are accessible by default (Radix handles `role`, `aria-checked`, keyboard nav). The core defect class found is **data integrity around `dados_tipo`**: the Zod schema merges Particular and Empresa fields into a single flat object instead of a discriminated union, and neither the create nor the edit submit handler strips the fields that don't belong to the currently selected `tipo` before sending the payload to the backend. Stale/cross-type data can be silently persisted. There is also a real type-safety regression in the edit page where `form.setValue` is cast to `unknown` to suppress a compiler error instead of fixing it.

## Critical Issues

### CR-01: `dados_tipo` is never pruned to match selected `tipo` before submit — stale/cross-type data can be persisted

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:96-106`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:127-137`

**Issue:** `clienteFormSchema.dados_tipo` (schemas/clientes.ts:20-32) is a single flat object containing **both** the Particular fields (`idade`, `sexo`, `nacionalidade`) and the Empresa fields (`nome_comercial`, `sede`, `representante_legal`, `cargo`). Stale values are only cleared in `confirmTipoChange`, which runs **only when the user actively switches `tipo` via the radio group while a previous `tipo` was already set** (`if (currentTipo && currentTipo !== newTipo)`).

This leaves at least two ways for cross-type data to leak into the persisted payload:
1. **Edit page, pre-existing data:** `form.reset()` (editar/page.tsx:106-123) loads `dados_tipo: cliente.data.dados_tipo ?? {}` verbatim from the server. If the backend record for a PARTICULAR client happens to contain leftover `nome_comercial`/`representante_legal` keys (e.g. from a prior bug, a bulk import, or a tipo change done before this confirmation flow existed), those values are never stripped — `onSubmit` spreads `dados_tipo: values.dados_tipo` unmodified back to the server on every save, silently re-persisting the cross-type junk forever.
2. **Both pages:** if a user selects a `tipo` for the first time (no previous `tipo`, so `onTipoChange`'s `else` branch fires `form.setValue("tipo", newTipo)` directly, bypassing `confirmTipoChange` entirely — see WR-01), any `dados_tipo` already present in `defaultValues`/`reset` (e.g. from a draft, browser autofill of the uncontrolled `register()` inputs, or future client-side persistence) is never cleared either, since clearing only happens inside `confirmTipoChange`.

**Fix:** Make pruning unconditional at submit time, not just on the confirmation dialog path:
```ts
const onSubmit = async (values: ClienteFormValues) => {
  const dados_tipo =
    values.tipo === "PARTICULAR"
      ? { idade: values.dados_tipo?.idade, sexo: values.dados_tipo?.sexo, nacionalidade: values.dados_tipo?.nacionalidade }
      : values.tipo === "EMPRESA"
        ? {
            nome_comercial: values.dados_tipo?.nome_comercial,
            sede: values.dados_tipo?.sede,
            representante_legal: values.dados_tipo?.representante_legal,
            cargo: values.dados_tipo?.cargo,
          }
        : undefined;

  const payload: ClienteCreateRequest = { ...values, dados_tipo, /* ...rest */ };
  ...
};
```
Better still, model `dados_tipo` as a Zod discriminated union keyed on `tipo` (see WR-02) so the schema itself rejects cross-type fields.

### CR-02: `clienteFormSchema.tipo` is optional, contradicting the form's "required" UX and the rest of the validation logic

**File:** `web/src/schemas/clientes.ts:18`

**Issue:** `tipo: z.enum(["PARTICULAR", "EMPRESA"]).optional()`. Nothing in the schema (no `superRefine` branch) requires `tipo` to be set. A client can therefore be created/saved with `tipo` left `undefined` — no conditional section is shown, none of the Particular/Empresa validation in `superRefine` (lines 76-91) runs, and the resulting `Cliente.tipo` is persisted as `undefined`. Downstream, list/detail pages render `"—"` for tipo and totals (`totalParticulares`/`totalEmpresas` in `page.tsx:68-69`) silently exclude these records, masking the fact that required classification data is missing. If `tipo` is meant to be mandatory for new clients (the radio group strongly implies this, and the confirmation dialog UX assumes a meaningful prior value), the schema should enforce it.

**Fix:**
```ts
tipo: z.enum(["PARTICULAR", "EMPRESA"], { required_error: "Selecione o tipo de cliente" }),
```
If `tipo` is genuinely optional for legacy/imported records, this should at minimum be called out as an intentional decision (and the radio group should not visually imply both options are equally "select one" without an explicit "none" option), but as implemented it reads as an unintentional gap given the rest of the conditional-validation logic assumes `tipo` is meaningful.

## Warnings

### WR-01: Tipo-switch confirmation dialog is bypassed on the very first selection, so the "clears the other type's data" guarantee is inconsistent

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:65-72`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:81-88`

**Issue:** `onTipoChange` only shows the confirmation dialog (and therefore only clears stale `dados_tipo` fields via `confirmTipoChange`) when `currentTipo` is truthy and different from the new value. The first time a user picks a `tipo` (`currentTipo` is `undefined`), `form.setValue("tipo", newTipo, { shouldValidate: true })` is called directly with **no clearing of `dados_tipo`**. This is usually harmless on the create page (where `dados_tipo` starts as `{}`), but combine this with CR-01 (edit page loading a `dados_tipo` object with mixed/legacy fields and `tipo` initially `undefined` on a freshly-typed record) and stale data survives the very flow meant to police it.

**Fix:** Either always clear the "other side" of `dados_tipo` on any `tipo` change (not just switches between two non-null values), or — preferably — fix this at the data layer per CR-01 so the clearing logic isn't load-bearing for correctness.

### WR-02: `dados_tipo` modeled as a flat merged object instead of a discriminated union allows invalid combinations to pass validation

**File:** `web/src/schemas/clientes.ts:20-32`, `web/src/types/clientes.ts:49,77,98`

**Issue:** The Zod object accepts any combination of Particular and Empresa keys simultaneously (e.g. both `idade` and `nome_comercial` set) — `superRefine` only checks that the fields *required for the current tipo* are present, it never rejects fields that *belong to the other tipo*. The TS types (`DadosTipoParticular | DadosTipoEmpresa`) also don't prevent this in practice since `ClienteFormValues["dados_tipo"]` (the Zod-inferred type) is the flat merged shape, not a real union — so `values.dados_tipo` typed in `onSubmit` is wider than either interface and TypeScript won't catch a future regression that sends both halves.

**Fix:** Use `z.discriminatedUnion("tipo", [...])` at the top level, or at minimum a `.refine()` that rejects the presence of fields belonging to the non-selected tipo.

### WR-03: Edit page suppresses type-checking on `form.setValue` via an `unknown` cast instead of fixing the type error

**File:** `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:93-100`

**Issue:**
```ts
(form.setValue as (name: string, value: unknown) => void)("dados_tipo.nome_comercial", "");
```
This pattern (repeated 7 times) casts away `react-hook-form`'s typed `setValue` overloads entirely, which means typos in field paths (e.g. `"dados_tipo.nome_comercial"` vs a future rename) will no longer be caught at compile time anywhere in this function. The create page's `confirmTipoChange` (novo/page.tsx:74-88) does the equivalent calls **without** this cast and compiles fine, which strongly suggests the cast in the edit page is papering over an unrelated type error (likely from `ClienteFormValues` widening — see WR-02) rather than a genuine API limitation.

**Fix:** Remove the cast and resolve the underlying type mismatch (likely fixed automatically once WR-02 is addressed and `dados_tipo` has a precise discriminated type).

### WR-04: CSV import casts arbitrary user-supplied strings to the `tipo` union type without validation

**File:** `web/src/app/(dashboard)/clientes/page.tsx:163-165`

**Issue:**
```ts
tipo: idxTipo >= 0
  ? ((r[idxTipo] ?? "").trim() || undefined) as "PARTICULAR" | "EMPRESA" | undefined
  : undefined,
```
Any string in the CSV's `tipo` column (e.g. `"Pessoa Fisica"`, `"empresa"` lowercase, a typo) is blindly cast and sent to `createCliente.mutateAsync`. The `as` assertion gives a false sense of type safety — the backend will either reject the whole row (counted as `failed`, acceptable) or, if it's lenient, silently store an invalid `tipo` value that the radio-group based UI never produces, which would then break the dynamic-section logic (`watchedTipo === "PARTICULAR"`/`"EMPRESA"`) when that record is later opened for edit, rendering neither conditional section.

**Fix:** Validate/normalize against the known enum before sending:
```ts
const rawTipo = idxTipo >= 0 ? (r[idxTipo] ?? "").trim().toUpperCase() : "";
const tipo = rawTipo === "PARTICULAR" || rawTipo === "EMPRESA" ? rawTipo : undefined;
```

### WR-05: `RadioGroup` "Tipo de Cliente" label is not programmatically associated with the radio group

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:156-180`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:210-234`

**Issue:** `<Label>Tipo de Cliente</Label>` has no `htmlFor`/`id` link to the `RadioGroup` root (which has no `aria-labelledby` either). Each individual `RadioGroupItem` is correctly labelled ("Particular"/"Empresa"), but a screen reader landing on the group via Tab will announce only "Particular, radio button, 1 of 2" with no group context, since Radix's `role="radiogroup"` has nothing pointing back to the visible heading label.

**Fix:**
```tsx
<Label id="tipo-cliente-label">Tipo de Cliente</Label>
<RadioGroup aria-labelledby="tipo-cliente-label" ...>
```

### WR-06: `idade` field has no upper bound, allowing nonsensical values to pass validation

**File:** `web/src/schemas/clientes.ts:23`

**Issue:** `idade: z.number().int().positive().optional()` accepts any positive integer, e.g. `999999`. Combined with the `<Input type="number">` having no `max` attribute (novo/page.tsx:190-195, editar/page.tsx:242), there's no client-side guard against obviously invalid ages.

**Fix:** `z.number().int().min(0).max(130).optional()` (or whatever the domain's reasonable bound is), mirrored with `max={130}` on the input.

## Info

### IN-01: `documento_numero` NIF format check duplicated with no shared constant

**File:** `web/src/schemas/clientes.ts:66-75`

**Issue:** The 9-digit NIF check is inlined in `superRefine`. If this rule needs to change (e.g. Cabo Verde NIF format updates), it's only defined here, but the legacy top-level `nif` field has no equivalent check at all, so the two "NIF" concepts in the same form have inconsistent validation strength. Worth a comment or a shared validator if `nif` is ever re-enabled for direct entry.

### IN-02: Repeated `selectClassName`/`textareaClassName` string literals duplicated verbatim across create and edit pages

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:30-34`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:45-49`

**Issue:** Identical Tailwind class strings are copy-pasted between the two pages (and likely diverge silently over time, e.g. one page already differs slightly in dialog usage). Low risk, but a shared `web/src/components/ui/select.tsx` (or just exporting the constant from a shared module) would prevent drift.

### IN-03: `dadosTipoErrors` cast pattern repeated with slightly different inline casts between create and edit pages

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:132-134`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:262-263,273-274`

**Issue:** The create page hoists one `dadosTipoErrors` cast at the top of the component and reuses it; the edit page inlines the same `as Record<string, { message?: string }>` cast twice per field at the call site instead, which is more verbose and easier to get out of sync (e.g. `idade`/`sexo`/`nacionalidade` error messages are not even rendered in the edit page's Particular section — see IN-04).

### IN-04: Edit page's Particular section does not render field-level validation errors

**File:** `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:240-252`

**Issue:** Unlike the create page (novo/page.tsx:196-220), the edit page's `idade`/`sexo`/`nacionalidade` inputs have no `{dadosTipoErrors?.idade && <p>...}` blocks following them — only the Empresa section's `nome_comercial`/`representante_legal` show errors (lines 262-264, 273-275). Since the Particular schema branch doesn't currently raise any `superRefine` issues (no required Particular fields), this is latent rather than currently user-visible, but if Particular-side required-field validation is ever added (paralleling the Empresa rules), the edit page would silently fail to surface it.

**Fix:** Add the same error-rendering blocks under the Particular inputs in the edit page for parity with the create page.

---

_Reviewed: 2026-06-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
