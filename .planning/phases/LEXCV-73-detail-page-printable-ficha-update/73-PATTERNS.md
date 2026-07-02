# Phase 73: Detail Page & Printable Ficha Update - Pattern Map

**Mapped:** 2026-07-02
**Files analyzed:** 2 (both modified, no new files)
**Analogs found:** 2 / 2 — both files are self-referential analogs (the pattern to copy already exists in the same file, or in a sibling Phase 72 file)

This phase makes two small, surgical, presentation-only edits. No new files are created, no new components/hooks/services are needed, and no external analog search was required — the exact pattern to replicate (dynamic ternary label) already exists verbatim in the same component tree (Phase 72's `novo/page.tsx` / `editar/page.tsx`) and the removal target is fully self-contained within `ficha/page.tsx`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|---------------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | component (detail view, read-only `<dl>`) | request-response (client-side render of fetched data) | Phase 72 dynamic-label ternary pattern (`tipo === "EMPRESA" ? "X" : "Y"`); local `cliente.data.tipo` field already read at line 159 | exact (pattern already used for other fields in same `dl`) |
| `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` | component (printable/static view) | request-response (client-side render of fetched data) | `isEmpresa` ternary already used at lines 185-199 for the Identificação block | exact (pattern already present in same file, just needs re-application to the Contactos `Field label="Morada"`) |

## Pattern Assignments

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` (component, request-response)

**Analog:** itself — the file already reads `cliente.data.tipo` for another field, and the CONTEXT.md decisions explicitly specify the exact ternary to use, modeled on the Phase 72 pattern used in `novo/page.tsx`/`editar/page.tsx`.

**Current state — target lines (170-171):**
```tsx
                  <dt className="text-neutral-500 dark:text-neutral-400">Morada</dt>
                  <dd className="col-span-2">{cliente.data.morada ?? "—"}</dd>
```

**Surrounding context for placement reference (lines 158-171)** — shows the `<dl>` grid pattern (`<dt>`/`<dd>` pairs, `text-neutral-500 dark:text-neutral-400` label styling, `col-span-2` value styling, `?? "—"` fallback) that must be preserved:
```tsx
                  <dt className="text-neutral-500 dark:text-neutral-400">Tipo</dt>
                  <dd className="col-span-2">{cliente.data.tipo ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Email</dt>
                  <dd className="col-span-2">{cliente.data.email ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Telefone</dt>
                  <dd className="col-span-2">{cliente.data.telefone ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Localidade</dt>
                  <dd className="col-span-2">{cliente.data.localidade ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Morada</dt>
                  <dd className="col-span-2">{cliente.data.morada ?? "—"}</dd>
```

**Required change:** replace the static `Morada` text node in the `<dt>` on line 170 with a ternary expression, per CONTEXT.md decision:
```tsx
                  <dt className="text-neutral-500 dark:text-neutral-400">
                    {cliente.data.tipo === "EMPRESA" ? "Sede" : "Morada"}
                  </dt>
                  <dd className="col-span-2">{cliente.data.morada ?? "—"}</dd>
```
Note: `cliente.data.tipo` is already read elsewhere in this same `<dl>` (line 159: `{cliente.data.tipo ?? "—"}`), confirming the field is available on the loaded object with no additional fetch/type work needed. This is a plain read (no `form.watch`, no controlled input) — CONTEXT.md explicitly notes this simplification vs. the Phase 72 form pattern.

---

### `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` (component, request-response)

**Analog:** itself — `isEmpresa` boolean (line 148) and the `Field` component (lines 125-133) are the two local patterns to reuse; no external file needed.

**`Field` component definition (lines 125-133)** — reused as-is, no changes needed to this helper:
```tsx
function Field({ label, value }: { label: string; value: string }) {
  const isBlank = value === BLANK;
  return (
    <div className="grid grid-cols-2 gap-2 py-1 text-sm">
      <span className="text-gray-600">{label}</span>
      <span className={isBlank ? "font-mono underline" : ""}>{value}</span>
    </div>
  );
}
```

**`isEmpresa` computed flag (line 148)** — already exists, reuse directly for the Contactos "Morada"/"Sede" ternary:
```tsx
  const isEmpresa = cliente.tipo === "EMPRESA";
```

**Current state — Identificação block, `isEmpresa` branch to be trimmed (lines 185-199):**
```tsx
      {isEmpresa ? (
        <>
          <Field label="NIF" value={fmt(cliente.nif)} />
          <Field label="Nome Comercial" value={fmt(undefined)} />
          <Field label="Sede" value={fmt(undefined)} />
          <Field label="Representante Legal" value={fmt(undefined)} />
          <Field label="Cargo" value={fmt(undefined)} />
        </>
      ) : (
        <>
          <Field label="Idade" value={fmt(idade)} />
          <Field label="Sexo" value={fmt(sexo)} />
          <Field label="Nacionalidade" value={fmt(nacionalidade)} />
        </>
      )}
```

**Required change:** remove the three lines `Field label="Nome Comercial"`, `Field label="Representante Legal"`, `Field label="Cargo"` entirely (they always render `fmt(undefined)` → `BLANK` today — dead visual clutter per CONTEXT.md decision and REQUIREMENTS.md "Out of Scope"). Also remove the `Field label="Sede" value={fmt(undefined)}` line from this block — CONTEXT.md is explicit that Sede/Morada must not be duplicated; it moves entirely to the Contactos section below with a dynamic label. Result:
```tsx
      {isEmpresa ? (
        <>
          <Field label="NIF" value={fmt(cliente.nif)} />
        </>
      ) : (
        <>
          <Field label="Idade" value={fmt(idade)} />
          <Field label="Sexo" value={fmt(sexo)} />
          <Field label="Nacionalidade" value={fmt(nacionalidade)} />
        </>
      )}
```

**Current state — Contactos section, Morada field to become dynamic (lines 201-205):**
```tsx
      <SectionTitle>Contactos</SectionTitle>
      <Field label="Morada" value={fmt(cliente.morada)} />
      <Field label="Localidade" value={fmt(cliente.localidade)} />
      <Field label="Telefone" value={fmt(cliente.telefone)} />
      <Field label="Email" value={fmt(cliente.email)} />
```

**Required change:** compute the label dynamically using the existing `isEmpresa` flag, per CONTEXT.md decision (mirrors the `[id]/page.tsx` ternary exactly, and the same convention as `isEmpresa` itself):
```tsx
      <SectionTitle>Contactos</SectionTitle>
      <Field label={isEmpresa ? "Sede" : "Morada"} value={fmt(cliente.morada)} />
      <Field label="Localidade" value={fmt(cliente.localidade)} />
      <Field label="Telefone" value={fmt(cliente.telefone)} />
      <Field label="Email" value={fmt(cliente.email)} />
```

---

## Shared Patterns

### Dynamic label ternary (cross-file convention, established Phase 72)
**Source pattern:** `tipo === "EMPRESA" ? "X" : "Y"` (referenced in CONTEXT.md as already used in `web/src/app/(dashboard)/clientes/novo/page.tsx` and `editar/page.tsx`)
**Apply to:**
- `[id]/page.tsx` line 170 `<dt>`: `cliente.data.tipo === "EMPRESA" ? "Sede" : "Morada"`
- `ficha/page.tsx` line 202 `Field label` prop: `isEmpresa ? "Sede" : "Morada"` (using the pre-existing `isEmpresa` local const instead of re-deriving from `tipo`, since `ficha/page.tsx` already has that boolean computed at line 148)

### `fmt()` blank-value formatter (ficha/page.tsx only)
**Source:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` lines 37-42
```tsx
const BLANK = "___________";

function fmt(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return BLANK;
  return String(value);
}
```
**Apply to:** No change needed — `fmt()` is untouched by this phase. Confirmed it already handles `undefined` gracefully (which is why the removed fields silently rendered blank underscores rather than erroring) and continues to be used for all retained `Field` values including `cliente.morada`.

### `?? "—"` fallback (page.tsx only)
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`, used consistently throughout the `<dl>` (e.g. line 156 `cliente.data.nif ?? "—"`, line 171 `cliente.data.morada ?? "—"`)
**Apply to:** No change to the `<dd>` value expression for Morada — only the `<dt>` label text changes. Keep `{cliente.data.morada ?? "—"}` exactly as-is.

## No Analog Found

None — both edits are fully self-contained within the two target files, reusing patterns already present in those same files (or, for the ternary itself, an already-referenced sibling pattern from Phase 72 forms). No new component, hook, service, or type is introduced by this phase.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` (both read in full — 902 and 243 lines respectively)
**Files scanned:** 2 (both target files; no external search needed as CONTEXT.md fully specified the analog locations)
**Pattern extraction date:** 2026-07-02
