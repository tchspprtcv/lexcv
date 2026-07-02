---
phase: 72-form-refactoring-create-edit
reviewed: 2026-07-02T00:15:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: clean
---

# Phase 72: Code Review Report

**Reviewed:** 2026-07-02T00:15:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** clean

## Summary

Reviewed the full diff introduced by commits `0dffd35` (novo/page.tsx) and `fd36a9a` (editar/page.tsx), plus the surrounding component logic in both files to check for regressions or interaction bugs with existing state (`tipo` radio group, `onTipoChange`/`confirmTipoChange`, async `form.reset()` in the edit form, and the Zod schema in `web/src/schemas/clientes.ts`).

The change is exactly as scoped: three derived constants (`tipoValue`, `nomeLabel`, `moradaLabel`) inserted immediately before each component's `return (`, and three `<Label>` children swapped from string literals to the derived values. Nothing else in either file was touched — verified via `git show` on both commits, which show only the 7-line insertions/3-line label swaps described in the plan.

Correctness checks performed:
- **Edit-form timing:** `form.watch("tipo")` is read after the async `useEffect`-driven `form.reset()` sets `tipo` from `cliente.data`. Since `form.watch` is evaluated on every render (not memoized/cached), and the `useEffect` triggers a re-render via `form.reset`, the label correctly reflects the loaded value — no stale-label risk.
- **Undefined `tipo` (new client, nothing selected yet):** both `nomeLabel`/`moradaLabel` ternaries default to `"Nome"`/`"Morada"`, matching the spec's "ou por defeito" requirement.
- **Hooks-order safety in `novo/page.tsx`:** `form.watch("tipo")` is called after a conditional early `return` (`AccessDeniedState`) in the component body. This is *not* a Rules-of-Hooks violation because `form.watch()` is a plain method call on the already-constructed `form` object (from `useForm()` called earlier, unconditionally) — it does not itself register a new hook, so hook call count/order is unaffected across renders. No bug.
- **Dialog copy (`confirmTipoChange` flow):** the "Mudar tipo de cliente" confirmation dialog text was not touched and still reads `Mudar o tipo irá limpar os dados de {Empresa|Particular}` — this is pre-existing copy, unaffected by and consistent with the new dynamic labels; no accidental mismatch introduced (e.g., dialog doesn't reference "Nome Comercial"/"Sede", so no diff-induced inconsistency there).
- **NIF field label vs. schema:** `nif` remains a required, always-visible field per `clienteFormSchema` (`z.string().trim().regex(nifPattern, ...)`, no `.optional()`) regardless of `tipo`. Renaming the label to plain "NIF" is consistent with the schema (NIF is not actually legado/optional) and does not touch validation.
- **`REG_COMERCIAL` option:** confirmed present, unchanged, in both `documento_tipo` `<select>` elements (`novo/page.tsx:250`, `editar/page.tsx:354`).
- **No new inputs, endpoints, or data flow paths** were introduced — pure derived-label text, consistent with the phase's own threat model disposition (accept, no new attack surface).

No Critical or Warning findings. One minor Info-level observation below (does not block, matches existing codebase-wide pattern per the phase summary's noted "React Compiler: incompatible library" warning on other `form.watch()` call sites).

## Info

### IN-01: Duplicated derived-label logic across two files

**File:** `web/src/app/(dashboard)/clientes/novo/page.tsx:127-129` and `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:214-216`
**Issue:** The identical three-line block (`tipoValue`/`nomeLabel`/`moradaLabel`) is duplicated verbatim across both forms. This was an explicit, deliberate choice per the plan/patterns doc (no shared hook extracted), and is a reasonable trade-off for a two-call-site duplication of this size. Flagging only as a forward-looking note: if a third cliente-tipo-aware form is added, consider extracting a `useTipoLabels(tipoValue)` helper (e.g., in `web/src/hooks/`) to avoid a third copy drifting out of sync.
**Fix:** No action required now. If/when a third call site appears:
```ts
// web/src/hooks/use-tipo-labels.ts
export function useTipoLabels(tipo: "PARTICULAR" | "EMPRESA" | undefined) {
  return {
    nomeLabel: tipo === "EMPRESA" ? "Nome Comercial" : "Nome",
    moradaLabel: tipo === "EMPRESA" ? "Sede" : "Morada",
  };
}
```

---

_Reviewed: 2026-07-02T00:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
