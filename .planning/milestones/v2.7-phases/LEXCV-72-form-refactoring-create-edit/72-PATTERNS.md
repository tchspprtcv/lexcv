# Phase 72: Form Refactoring (Create & Edit) - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 2 (both are edit targets, not new files)
**Analogs found:** 2 / 2 (each file is its own best analog for the other — near-identical structure)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | component (form page) | request-response (create) | `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | exact (sibling form, same schema/fields) |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | component (form page) | request-response (update) | `web/src/app/(dashboard)/clientes/novo/page.tsx` | exact (sibling form, same schema/fields) |

Both files share `clienteFormSchema` (react-hook-form + zodResolver), the same `DOCUMENTO_TIPOS`/`toDocumentoTipo` helper, and the same `selectClassName`/`textareaClassName` constants. No other file in the codebase is a closer analog — these two are the only cliente forms and already mirror each other's structure for the fields in scope.

## Pattern Assignments

### `web/src/app/(dashboard)/clientes/novo/page.tsx` (component, request-response/create)

**Analog:** `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (structurally identical for these fields; use as cross-check, not as source of new pattern)

**Current field block — Nome / NIF (lines 173-190):**
```tsx
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="nome">Nome</Label>
                <Input id="nome" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
                {form.formState.errors.nome ? (
                  <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                ) : null}
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="nif">NIF (Legado)</Label>
                  <Input id="nif" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nif")} />
                  {form.formState.errors.nif ? (
                    <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                  ) : null}
                </div>
              </div>
```

Note: NIF is already the field immediately following Nome in `novo/page.tsx` (it sits in its own `grid md:grid-cols-2` block directly after the Nome block, before email/telefone). **No field reordering is needed in `novo/page.tsx`** — only the label text changes (`"NIF (Legado)"` → `"NIF"`) and the Nome label becomes dynamic. Current field order: `tipo` (radio, above Nome) → Nome → NIF → Email/Telefone → Localidade/Morada.

**`tipo` radio + `onTipoChange` handler (lines 47, 68-81, 148-171):**
```tsx
  const [pendingTipo, setPendingTipo] = React.useState<"PARTICULAR" | "EMPRESA" | null>(null);
  ...
  function onTipoChange(newTipo: "PARTICULAR" | "EMPRESA") {
    const currentTipo = form.getValues("tipo");
    if (currentTipo && currentTipo !== newTipo) {
      setPendingTipo(newTipo);
    } else {
      form.setValue("tipo", newTipo, { shouldValidate: true });
    }
  }

  function confirmTipoChange() {
    if (!pendingTipo) return;
    form.setValue("tipo", pendingTipo, { shouldValidate: true });
    setPendingTipo(null);
  }
  ...
              <Controller
                control={form.control}
                name="tipo"
                render={({ field }) => (
                  <RadioGroup
                    className="flex gap-6"
                    value={field.value ?? ""}
                    onValueChange={(value) => onTipoChange(value as "PARTICULAR" | "EMPRESA")}
                  >
```

**How to compute the dynamic label:** `tipo` is a react-hook-form field (`form.control`, registered via `Controller`, no native `<input>` — so `form.watch("tipo")` is the correct live-reactive read, since `onTipoChange`/`confirmTipoChange` only call `form.setValue(...)` and there is no separate local `tipo` state to read from). Recommended pattern inside the component body, before the JSX return:
```tsx
  const tipoValue = form.watch("tipo");
  const nomeLabel = tipoValue === "EMPRESA" ? "Nome Comercial" : "Nome";
  const moradaLabel = tipoValue === "EMPRESA" ? "Sede" : "Morada";
```
Then use `<Label htmlFor="nome">{nomeLabel}</Label>` and `<Label htmlFor="morada">{moradaLabel}</Label>`. This does not require touching `onTipoChange` — it already updates `tipo` via `form.setValue(..., { shouldValidate: true })`, and `form.watch` re-renders on any `setValue` call reactively (react-hook-form's documented behavior for `watch`).

**Morada field (lines 217-223, inside `grid md:grid-cols-2` alongside Localidade):**
```tsx
                <div className="space-y-2">
                  <Label htmlFor="morada">Morada</Label>
                  <Input id="morada" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("morada")} />
                  {form.formState.errors.morada ? (
                    <p className="text-sm text-red-600">{form.formState.errors.morada.message}</p>
                  ) : null}
                </div>
```
Only the `Label` text needs to become `{moradaLabel}` — no structural change, no reordering needed (already after Nome/NIF/Email/Telefone as established field order, and CONTEXT.md's "Claude's Discretion" leaves other-field order untouched).

**`documento_tipo` select — REG_COMERCIAL confirmed present (lines 236-247):**
```tsx
                    <select
                      id="documento_tipo"
                      className={selectClassName}
                      {...form.register("documento_tipo")}
                    >
                      <option value="">Nenhum</option>
                      <option value="NIF">NIF</option>
                      <option value="CNI">CNI</option>
                      <option value="PASSAPORTE">Passaporte</option>
                      <option value="REG_COMERCIAL">Registo Comercial</option>
                    </select>
```
Already current — Phase 71's review fix is in place here. No change needed for CLI-05/CLI-09/CLI-10 in this file beyond what's already shipped.

---

### `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (component, request-response/update)

**Analog:** `web/src/app/(dashboard)/clientes/novo/page.tsx` (apply the identical dynamic-label technique)

**Current field block — Nome / NIF / Tipo (lines 249-294) — NOTE FIELD ORDER DIFFERS FROM `novo/page.tsx`:**
```tsx
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="nome">Nome</Label>
                  <Input id="nome" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
                  {form.formState.errors.nome ? (
                    <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                  ) : null}
                </div>

                <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="nif">NIF (Legado)</Label>
                    <Input id="nif" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nif")} />
                    {form.formState.errors.nif ? (
                      <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                    ) : null}
                  </div>

                </div>

                <div className="space-y-2">
                  <Label>Tipo de Cliente</Label>
                  <Controller
                    control={form.control}
                    name="tipo"
                    render={({ field }) => (
                      <RadioGroup
                        value={field.value ?? ""}
                        onValueChange={(val) => onTipoChange(val as "PARTICULAR" | "EMPRESA")}
                        className="flex gap-6"
                      >
```

**Important structural difference from `novo/page.tsx`:** in `editar/page.tsx`, the `tipo` radio group is rendered **after** Nome/NIF, not before (in `novo/page.tsx` it's before Nome). NIF is already directly under Nome here too (own `grid md:grid-cols-2` row, empty second column) — same as `novo/page.tsx`. **No reordering needed here either** — only label text/dynamism changes. Confirmed field order in `editar/page.tsx`: Nome → NIF → Tipo de Cliente (radio) → Email/Telefone → Localidade/Morada.

**`tipo` radio + `onTipoChange` handler (lines 95-110, 271-290) — identical logic to `novo/page.tsx`:**
```tsx
  const [pendingTipo, setPendingTipo] = React.useState<"PARTICULAR" | "EMPRESA" | null>(null);

  function onTipoChange(newTipo: "PARTICULAR" | "EMPRESA") {
    const current = form.getValues("tipo");
    if (current && current !== newTipo) {
      setPendingTipo(newTipo);
    } else {
      form.setValue("tipo", newTipo, { shouldValidate: true });
    }
  }

  function confirmTipoChange() {
    if (!pendingTipo) return;
    form.setValue("tipo", pendingTipo, { shouldValidate: true });
    setPendingTipo(null);
  }
```
Additionally, `editar/page.tsx` seeds `tipo` (and all other fields) asynchronously via `form.reset({...})` inside a `React.useEffect` once `cliente.data` loads (lines 130-155) — `form.watch("tipo")` will correctly reflect this once `reset` fires, no extra wiring needed for the dynamic label to work post-load.

**Same `tipoValue`/label derivation to add** (place in `ClienteEditContent`, before the JSX return, same as `novo/page.tsx`):
```tsx
  const tipoValue = form.watch("tipo");
  const nomeLabel = tipoValue === "EMPRESA" ? "Nome Comercial" : "Nome";
  const moradaLabel = tipoValue === "EMPRESA" ? "Sede" : "Morada";
```

**Morada field (lines 321-327, inside `grid md:grid-cols-2` alongside Localidade) — same structure as `novo/page.tsx`:**
```tsx
                  <div className="space-y-2">
                    <Label htmlFor="morada">Morada</Label>
                    <Input id="morada" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("morada")} />
                    {form.formState.errors.morada ? (
                      <p className="text-sm text-red-600">{form.formState.errors.morada.message}</p>
                    ) : null}
                  </div>
```

**`documento_tipo` select — REG_COMERCIAL confirmed present (lines 341-351):**
```tsx
                      <select
                        id="documento_tipo"
                        className={selectClassName}
                        {...form.register("documento_tipo")}
                      >
                        <option value="">Nenhum</option>
                        <option value="NIF">NIF</option>
                        <option value="CNI">CNI</option>
                        <option value="PASSAPORTE">Passaporte</option>
                        <option value="REG_COMERCIAL">Registo Comercial</option>
                      </select>
```
Identical to `novo/page.tsx` — already current from Phase 71's review fix. Confirmed: **both files already have REG_COMERCIAL** in `documento_tipo`; no change needed there for this phase.

---

## Shared Patterns

### Dynamic label via `form.watch`
**Source:** N/A (new pattern for this codebase — first use of `form.watch` for conditional label text in a cliente form)
**Apply to:** Both `novo/page.tsx` and `editar/page.tsx`, identically
```tsx
const tipoValue = form.watch("tipo");
const nomeLabel = tipoValue === "EMPRESA" ? "Nome Comercial" : "Nome";
const moradaLabel = tipoValue === "EMPRESA" ? "Sede" : "Morada";
```
Reason to prefer `form.watch` over reading `onTipoChange`'s target state: `tipo` has no parallel local `React.useState` mirror in either file — it lives solely in react-hook-form state, set via `field.value` (Controller) and `form.setValue`. `form.watch("tipo")` is therefore the only live-reactive read of the current value without introducing new state.

### Label rename ("NIF (Legado)" → "NIF")
**Source:** Both files, same literal string `NIF (Legado)` at `novo/page.tsx:184` and `editar/page.tsx:260`
**Apply to:** Both files — pure text change, no JSX restructuring, no schema change (schema already treats `nif` as unconditionally required per Phase 71 per CONTEXT.md).

### Field positioning ("NIF under Nome")
**Source:** Both files already satisfy this — no move required.
**Apply to:** N/A — CONTEXT.md's instruction to "move NIF under Nome" describes the *already-current* structure in both files (NIF's `grid` row directly follows Nome's block in both). Planner should treat this as a confirmation, not a to-do, unless a review later finds the visual order differs from source order (it does not, in either file).

### `selectClassName` / `textareaClassName` module-level constants
**Source:** `novo/page.tsx:36-40`, `editar/page.tsx:57-61` (verbatim duplicates)
**Apply to:** No change needed for this phase — not in scope, but planner should know both files already define these independently (no shared import) if any refactor temptation arises; CONTEXT.md's "Claude's Discretion" does not call for deduplication.

## No Analog Found

None. Both files in scope have each other as adequate analogs, and all patterns needed (dynamic label via `form.watch`, label rename, confirming existing structure) are demonstrated within the two files themselves.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/clientes/` (both target files read in full; no external search needed since phase scope is exactly these 2 files editing each other's mirrored fields)
**Files scanned:** 2 (both fully read, no Grep/Glob needed — CONTEXT.md pre-identified the exact files and CLAUDE.md/AGENTS.md confirm no other cliente forms exist)
**Pattern extraction date:** 2026-07-01
</content>
