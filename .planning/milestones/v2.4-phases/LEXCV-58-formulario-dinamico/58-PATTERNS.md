# Phase 58: Formulário Dinâmico - Pattern Map

**Mapped:** 2026-06-29
**Files analyzed:** 8
**Analogs found:** 7 / 8 (1 no analog — new Radix primitives)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `web/src/components/ui/radio-group.tsx` | component | request-response | `web/src/components/ui/dialog.tsx` | role-match (shadcn Radix wrapper pattern) |
| `web/src/components/ui/switch.tsx` | component | request-response | `web/src/components/ui/dialog.tsx` | role-match (shadcn Radix wrapper pattern) |
| `web/src/types/clientes.ts` | model | transform | `web/src/types/clientes.ts` (self) | exact — extend in place |
| `web/src/schemas/clientes.ts` | utility | transform | `web/src/schemas/clientes.ts` (self) | exact — extend in place |
| `web/src/app/(dashboard)/clientes/page.tsx` | component | CRUD | `web/src/app/(dashboard)/clientes/page.tsx` (self) | exact — add badges to existing rows |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | component | request-response | `web/src/app/(dashboard)/clientes/[id]/page.tsx` (self) | exact — add badges to header |
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | component | CRUD | `web/src/app/(dashboard)/clientes/novo/page.tsx` (self) | exact — refactor form in place |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | component | CRUD | `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (self) | exact — refactor form in place |

---

## Pattern Assignments

### `web/src/components/ui/radio-group.tsx` (component, new Radix primitive wrapper)

**Analog:** `web/src/components/ui/dialog.tsx`

**Imports pattern** (dialog.tsx lines 1-7 — copy the Radix import + cn import style):
```tsx
"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { cn } from "@/lib/utils";
```

**Core shadcn wrapper pattern** (dialog.tsx lines 9-13 — named function exports, NOT const/arrow):
```tsx
// dialog.tsx uses named function exports for complex sub-components
function DialogOverlay({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Overlay>) {
  return <DialogPrimitive.Overlay data-slot="dialog-overlay" className={cn("...", className)} {...props} />;
}
// ...
export { Dialog, DialogClose, DialogContent, ... };
```

**Apply to radio-group.tsx** — use the same pattern with `@radix-ui/react-radio-group`:
```tsx
"use client";
import * as React from "react";
import * as RadioGroupPrimitive from "@radix-ui/react-radio-group";
import { cn } from "@/lib/utils";

export function RadioGroup({ className, ...props }: React.ComponentProps<typeof RadioGroupPrimitive.Root>) {
  return <RadioGroupPrimitive.Root data-slot="radio-group" className={cn("grid gap-2", className)} {...props} />;
}

export function RadioGroupItem({ className, ...props }: React.ComponentProps<typeof RadioGroupPrimitive.Item>) {
  return (
    <RadioGroupPrimitive.Item
      data-slot="radio-group-item"
      className={cn(
        "aspect-square h-4 w-4 rounded-full border border-neutral-300 text-neutral-900 ring-offset-white focus:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-neutral-900 data-[state=checked]:bg-neutral-900 dark:border-neutral-700 dark:ring-offset-neutral-950 dark:focus-visible:ring-neutral-300",
        className,
      )}
      {...props}
    >
      <RadioGroupPrimitive.Indicator className="flex items-center justify-center">
        <span className="h-2 w-2 rounded-full bg-white dark:bg-neutral-950" />
      </RadioGroupPrimitive.Indicator>
    </RadioGroupPrimitive.Item>
  );
}
```

**Note on dark mode:** `dialog.tsx` line 41 uses `dark:bg-[#020617]` for the content background. badge.tsx uses `dark:bg-blue-500/20`, `dark:text-blue-400` for color variants. Apply same dark mode strategy to radio-group item states.

---

### `web/src/components/ui/switch.tsx` (component, new Radix primitive wrapper)

**Analog:** `web/src/components/ui/dialog.tsx` (same wrapper pattern)

**Core pattern** — function export, `data-slot` attribute, `cn()` for className merging:
```tsx
"use client";
import * as React from "react";
import * as SwitchPrimitive from "@radix-ui/react-switch";
import { cn } from "@/lib/utils";

export function Switch({ className, ...props }: React.ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      data-slot="switch"
      className={cn(
        "peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 focus-visible:ring-offset-2 focus-visible:ring-offset-white disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-neutral-900 data-[state=unchecked]:bg-neutral-200 dark:focus-visible:ring-neutral-300 dark:focus-visible:ring-offset-neutral-950 dark:data-[state=checked]:bg-neutral-50 dark:data-[state=unchecked]:bg-neutral-800",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        data-slot="switch-thumb"
        className="pointer-events-none block h-4 w-4 rounded-full bg-white shadow-lg ring-0 transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0 dark:bg-neutral-950"
      />
    </SwitchPrimitive.Root>
  );
}
```

---

### `web/src/types/clientes.ts` (model, transform — EXTEND)

**Analog:** Self (`web/src/types/clientes.ts`)

**Current state** (lines 1-21, 23-40, 42-59, 67-76) — the full interface set to be extended:
- `Cliente` interface: has `tipo?: string`, missing `numero_cliente`, `avencado`, `dados_tipo`
- `ClienteCreateRequest`: has `tipo?: string`, missing `avencado`, `dados_tipo` (never add `numero_cliente` here)
- `ClienteUpdateRequest`: same gaps
- `ClientesListFilters`: has `tipo?: string` (filter value needs update from `SINGULAR`/`COLETIVA` to `PARTICULAR`/`EMPRESA`)

**Pattern to add** — insert after existing fields, before `ClienteContaCorrenteResponse`:
```typescript
// New interfaces to add at top of file (before Cliente interface)
export interface DadosTipoParticular {
  idade?: number;
  sexo?: string;
  nacionalidade?: string;
}

export interface DadosTipoEmpresa {
  nome_comercial?: string;
  sede?: string;
  representante_legal?: string;
  cargo?: string;
}

// In Cliente interface — update tipo and add new fields:
// tipo?: "PARTICULAR" | "EMPRESA";   // was: tipo?: string
// numero_cliente?: string;            // NEW (e.g. "CLI-0001") — read-only
// avencado?: boolean;                 // NEW
// dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;  // NEW

// In ClienteCreateRequest — add (NO numero_cliente):
// tipo?: "PARTICULAR" | "EMPRESA";
// avencado?: boolean;
// dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;

// In ClienteUpdateRequest — same as CreateRequest additions
```

---

### `web/src/schemas/clientes.ts` (utility, transform — EXTEND)

**Analog:** Self (`web/src/schemas/clientes.ts`)

**Current state** (lines 1-56):
- Uses `optionalTrimmedString` helper for optional string fields
- Has existing `superRefine` for `documento_tipo`/`documento_numero` cross-validation
- `tipo: optionalTrimmedString` — to be replaced with `z.enum(["PARTICULAR", "EMPRESA"]).optional()`

**Core pattern to follow** (lines 16-54 — the object + superRefine pattern):
```typescript
// Existing superRefine pattern to extend (not replace):
.superRefine((data, ctx) => {
  if (data.documento_tipo && !data.documento_numero) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Número de documento é obrigatório se o tipo estiver selecionado",
      path: ["documento_numero"],
    });
  }
  // Add new tipo-conditional checks in the SAME superRefine:
  if (data.tipo === "EMPRESA") {
    if (!data.dados_tipo?.nome_comercial?.trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "Nome comercial é obrigatório para Empresa", path: ["dados_tipo", "nome_comercial"] });
    }
    if (!data.dados_tipo?.representante_legal?.trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "Representante legal é obrigatório para Empresa", path: ["dados_tipo", "representante_legal"] });
    }
  }
});
```

**New fields to add to schema object** (after existing fields, before `.superRefine`):
```typescript
tipo: z.enum(["PARTICULAR", "EMPRESA"]).optional(),  // replaces: tipo: optionalTrimmedString
avencado: z.boolean().default(false),
dados_tipo: z.object({
  // Particular
  idade: z.number().int().positive().optional(),
  sexo: optionalTrimmedString,
  nacionalidade: optionalTrimmedString,
  // Empresa
  nome_comercial: optionalTrimmedString,
  sede: optionalTrimmedString,
  representante_legal: optionalTrimmedString,
  cargo: optionalTrimmedString,
}).optional(),
```

**Warning — Zod v4:** Project uses `zod ^4.1.5`. `z.boolean().default(false)` works in v4. `z.enum()` is unchanged. `superRefine` API is unchanged. `optionalTrimmedString` helper (lines 3-7) is reusable for nested string fields.

---

### `web/src/app/(dashboard)/clientes/page.tsx` (component, CRUD — ADD badges)

**Analog:** Self

**Current listing stat cards** (lines 68-69) — update enum values:
```typescript
// CURRENT (wrong enum values after Phase 57):
const totalSingulares = (allClientes.data ?? []).filter((c) => (c.tipo ?? "").toUpperCase() === "SINGULAR").length;
const totalColetivas = (allClientes.data ?? []).filter((c) => (c.tipo ?? "").toUpperCase() === "COLETIVA").length;

// REPLACE WITH:
const totalParticulares = (allClientes.data ?? []).filter((c) => (c.tipo ?? "").toUpperCase() === "PARTICULAR").length;
const totalEmpresas = (allClientes.data ?? []).filter((c) => (c.tipo ?? "").toUpperCase() === "EMPRESA").length;
```

**Current advanced filter dropdown** (lines 309-317) — update option values:
```tsx
// CURRENT:
<option value="SINGULAR">Singular</option>
<option value="COLETIVA">Coletiva</option>

// REPLACE WITH:
<option value="PARTICULAR">Particular</option>
<option value="EMPRESA">Empresa</option>
```

**Mobile card — add badges** after the existing `{c.ativo !== undefined && <Badge>}` block (lines 405-409):
```tsx
// ADD after ativo badge, same flex container:
{c.numero_cliente && (
  <Badge variant="blue" className="rounded-none font-mono font-bold text-[10px] flex-shrink-0">
    {c.numero_cliente}
  </Badge>
)}
{c.avencado && (
  <Badge variant="green" className="rounded-none font-bold text-[10px] flex-shrink-0">
    Avençado
  </Badge>
)}
```

**Desktop TableRow (ClienteRow component)** — `badgeVariant` logic (lines 496-496) — update:
```typescript
// CURRENT:
const badgeVariant = tipo === "SINGULAR" ? "blue" : tipo === "COLETIVA" ? "purple" : "gray";

// REPLACE WITH:
const badgeVariant = tipo === "PARTICULAR" ? "blue" : tipo === "EMPRESA" ? "purple" : "gray";
```

**Desktop TableRow — add numero_cliente and Avençado** in the nome cell (lines 512-516):
```tsx
// In the <div className="min-w-0"> block after the name Link:
// CURRENT line 516:
<div className="text-[11px] font-medium tracking-wider uppercase text-slate-500 dark:text-slate-400 mt-0.5">ID: #{idShort}</div>

// ADD after that div:
{cliente.numero_cliente && (
  <div className="mt-0.5 flex items-center gap-1">
    <Badge variant="blue" className="rounded-none font-mono font-bold text-[10px]">
      {cliente.numero_cliente}
    </Badge>
    {cliente.avencado && (
      <Badge variant="green" className="rounded-none font-bold text-[10px]">
        Avençado
      </Badge>
    )}
  </div>
)}
```

**Import to add** — `Badge` is already imported (line 7).

---

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` (component, request-response — ADD badges in header)

**Analog:** Self

**Current header** (lines 63-84) — the name is in the `<h1>` at line 65 (`Cliente`) and the breadcrumb shows the id. The name is displayed in the data card at line 106.

**Pattern to follow for the data card header** — the `<CardTitle>Dados</CardTitle>` block at line 101 and the `<dl>` row for Nome at lines 105-106:
```tsx
// CURRENT (lines 105-106):
<dt className="text-neutral-500 dark:text-neutral-400">Nome</dt>
<dd className="col-span-2 font-medium">{cliente.data.nome}</dd>

// REPLACE the Nome row with an enhanced display:
<dt className="text-neutral-500 dark:text-neutral-400">Nome</dt>
<dd className="col-span-2 font-medium flex items-center gap-2 flex-wrap">
  {cliente.data.nome}
  {cliente.data.numero_cliente && (
    <Badge variant="blue" className="rounded-none font-mono font-bold text-[10px]">
      {cliente.data.numero_cliente}
    </Badge>
  )}
  {cliente.data.avencado && (
    <Badge variant="green" className="rounded-none font-bold text-[10px]">
      Avençado
    </Badge>
  )}
</dd>
```

**Import to add** — `Badge` from `@/components/ui/badge` (not currently imported in this file — must add to import block at lines 6-26).

---

### `web/src/app/(dashboard)/clientes/novo/page.tsx` (component, CRUD — REFACTOR form)

**Analog:** Self (complete refactor of the form internals)

**Current imports** (lines 1-18) — add new imports:
```tsx
// ADD to existing imports:
import { Controller } from "react-hook-form";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
```

**Current form setup** (lines 33-48) — extend defaultValues:
```tsx
// CURRENT defaultValues:
defaultValues: {
  nome: "", nif: "", tipo: "", email: "", telefone: "",
  localidade: "", morada: "", documento_tipo: "",
  documento_numero: "", ramo_atividade: "", detalhes_adicionais: "",
}

// REPLACE WITH:
defaultValues: {
  nome: "", nif: "", tipo: undefined, avencado: false,
  email: "", telefone: "", localidade: "", morada: "",
  documento_tipo: "", documento_numero: "",
  ramo_atividade: "", detalhes_adicionais: "",
  dados_tipo: {},
}
```

**pendingTipo confirmation state** — add inside the page component, AFTER the `form` declaration:
```tsx
const [pendingTipo, setPendingTipo] = React.useState<"PARTICULAR" | "EMPRESA" | null>(null);
const watchedTipo = form.watch("tipo");

function onTipoChange(newTipo: "PARTICULAR" | "EMPRESA") {
  const current = form.getValues("tipo");
  if (current && current !== newTipo) {
    setPendingTipo(newTipo);  // show confirmation dialog
  } else {
    form.setValue("tipo", newTipo, { shouldValidate: true });
  }
}

function confirmTipoChange() {
  if (!pendingTipo) return;
  // Clear the opposite tipo's fields
  if (pendingTipo === "PARTICULAR") {
    form.setValue("dados_tipo.nome_comercial" as never, "");
    form.setValue("dados_tipo.sede" as never, "");
    form.setValue("dados_tipo.representante_legal" as never, "");
    form.setValue("dados_tipo.cargo" as never, "");
  } else {
    form.setValue("dados_tipo.idade" as never, undefined);
    form.setValue("dados_tipo.sexo" as never, "");
    form.setValue("dados_tipo.nacionalidade" as never, "");
  }
  form.setValue("tipo", pendingTipo, { shouldValidate: true });
  setPendingTipo(null);
}
```

**Tipo RadioGroup — replaces current `<Input id="tipo">` field** (currently lines 127-132 in novo/page.tsx):
```tsx
// REPLACE the tipo Input with:
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
        <div className="flex items-center gap-2">
          <RadioGroupItem value="PARTICULAR" id="tipo-particular" />
          <Label htmlFor="tipo-particular" className="cursor-pointer font-normal">Particular</Label>
        </div>
        <div className="flex items-center gap-2">
          <RadioGroupItem value="EMPRESA" id="tipo-empresa" />
          <Label htmlFor="tipo-empresa" className="cursor-pointer font-normal">Empresa</Label>
        </div>
      </RadioGroup>
    )}
  />
  {form.formState.errors.tipo ? (
    <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
  ) : null}
</div>
```

**Conditional field sections** — insert AFTER the RadioGroup, before the common fields section (email/telefone grid). Pattern: same `<div className="space-y-4">` + `<div className="space-y-2">` structure as existing fields:
```tsx
{/* Campos específicos por tipo */}
{watchedTipo === "PARTICULAR" && (
  <div className="space-y-4 p-4 border border-neutral-200 dark:border-neutral-800 bg-neutral-50/50 dark:bg-neutral-900/20">
    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Dados Pessoais</h4>
    <div className="grid gap-4 grid-cols-1 md:grid-cols-3">
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.idade">Idade</Label>
        <Input id="dados_tipo.idade" type="number" className="rounded-none" {...form.register("dados_tipo.idade", { valueAsNumber: true })} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.sexo">Sexo</Label>
        <Input id="dados_tipo.sexo" className="rounded-none" {...form.register("dados_tipo.sexo")} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.nacionalidade">Nacionalidade</Label>
        <Input id="dados_tipo.nacionalidade" className="rounded-none" {...form.register("dados_tipo.nacionalidade")} />
      </div>
    </div>
  </div>
)}
{watchedTipo === "EMPRESA" && (
  <div className="space-y-4 p-4 border border-neutral-200 dark:border-neutral-800 bg-neutral-50/50 dark:bg-neutral-900/20">
    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Dados da Empresa</h4>
    <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.nome_comercial">Nome Comercial <span className="text-red-500">*</span></Label>
        <Input id="dados_tipo.nome_comercial" className="rounded-none" {...form.register("dados_tipo.nome_comercial")} />
        {(form.formState.errors.dados_tipo as Record<string, {message?: string}>)?.nome_comercial && (
          <p className="text-sm text-red-600">{(form.formState.errors.dados_tipo as Record<string, {message?: string}>).nome_comercial?.message}</p>
        )}
      </div>
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.sede">Sede</Label>
        <Input id="dados_tipo.sede" className="rounded-none" {...form.register("dados_tipo.sede")} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.representante_legal">Representante Legal <span className="text-red-500">*</span></Label>
        <Input id="dados_tipo.representante_legal" className="rounded-none" {...form.register("dados_tipo.representante_legal")} />
        {(form.formState.errors.dados_tipo as Record<string, {message?: string}>)?.representante_legal && (
          <p className="text-sm text-red-600">{(form.formState.errors.dados_tipo as Record<string, {message?: string}>).representante_legal?.message}</p>
        )}
      </div>
      <div className="space-y-2">
        <Label htmlFor="dados_tipo.cargo">Cargo do Representante</Label>
        <Input id="dados_tipo.cargo" className="rounded-none" {...form.register("dados_tipo.cargo")} />
      </div>
    </div>
  </div>
)}
```

**Avençado Switch** — add before submit buttons, after all field sections. Pattern: use `Controller` (not `register`) because Switch is a controlled Radix component:
```tsx
<div className="flex items-center gap-3">
  <Controller
    control={form.control}
    name="avencado"
    render={({ field }) => (
      <Switch
        id="avencado"
        checked={field.value ?? false}
        onCheckedChange={field.onChange}
      />
    )}
  />
  <Label htmlFor="avencado" className="cursor-pointer">Avençado</Label>
</div>
```

**Confirmation Dialog** — add before closing `</div>` of the page, outside the form. Pattern matches `dialog.tsx` exports (lines 102-113):
```tsx
<Dialog open={!!pendingTipo} onOpenChange={(open) => { if (!open) setPendingTipo(null); }}>
  <DialogContent>
    <DialogHeader>
      <DialogTitle>Mudar tipo de cliente</DialogTitle>
      <DialogDescription>
        Mudar o tipo irá limpar os dados de {pendingTipo === "PARTICULAR" ? "Empresa" : "Particular"}. Continuar?
      </DialogDescription>
    </DialogHeader>
    <DialogFooter>
      <Button variant="outline" onClick={() => setPendingTipo(null)}>Cancelar</Button>
      <Button onClick={confirmTipoChange}>Continuar</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

**onSubmit payload** (lines 57-70) — strip `numero_cliente` (never sent), include new fields:
```tsx
const payload: ClienteCreateRequest = {
  ...values,
  tipo: values.tipo,
  avencado: values.avencado,
  dados_tipo: values.dados_tipo,
  documentoTipo: values.documento_tipo || undefined,
  documentoNumero: values.documento_numero || undefined,
  ramoAtividade: values.ramo_atividade || undefined,
  detalhesAdicionais: values.detalhes_adicionais || undefined,
  // numero_cliente is NEVER included — backend-generated
};
```

---

### `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (component, CRUD — REFACTOR form)

**Analog:** `web/src/app/(dashboard)/clientes/novo/page.tsx` (same structure post-refactor) + Self

**All patterns from novo/page.tsx apply identically**, with two additional concerns:

**form.reset in useEffect** (lines 72-87) — extend to populate new fields:
```tsx
React.useEffect(() => {
  if (!cliente.data) return;
  form.reset({
    nome: cliente.data.nome ?? "",
    nif: cliente.data.nif ?? "",
    tipo: (cliente.data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined,
    avencado: cliente.data.avencado ?? false,
    email: cliente.data.email ?? "",
    telefone: cliente.data.telefone ?? "",
    localidade: cliente.data.localidade ?? "",
    morada: cliente.data.morada ?? "",
    documento_tipo: cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? "",
    documento_numero: cliente.data.documento_numero ?? cliente.data.documentoNumero ?? "",
    ramo_atividade: cliente.data.ramo_atividade ?? cliente.data.ramoAtividade ?? "",
    detalhes_adicionais: cliente.data.detalhes_adicionais ?? cliente.data.detalhesAdicionais ?? "",
    dados_tipo: cliente.data.dados_tipo ?? {},
  });
}, [cliente.data, form]);
```

**Page title area** (lines 118-124) — add numero_cliente badge if present:
```tsx
// In the title div, after <h1 className="text-2xl font-semibold">Editar cliente</h1>:
{cliente.data?.numero_cliente && (
  <Badge variant="blue" className="rounded-none font-mono font-bold text-[10px] w-fit">
    {cliente.data.numero_cliente}
  </Badge>
)}
```

---

## Shared Patterns

### Badge usage
**Source:** `web/src/components/ui/badge.tsx`
**Apply to:** clientes/page.tsx, clientes/[id]/page.tsx, clientes/[id]/editar/page.tsx

Available variants (lines 7-28): `default`, `secondary`, `outline`, `blue`, `green`, `amber`, `red`, `purple`, `gray`.
- `numero_cliente` badge → `variant="blue"` + `className="rounded-none font-mono font-bold text-[10px]"`
- `avencado` badge → `variant="green"` + `className="rounded-none font-bold text-[10px]"`

### React Hook Form — `register` vs `Controller`
**Source:** `web/src/app/(dashboard)/clientes/novo/page.tsx` (lines 111, 183)
**Apply to:** RadioGroup and Switch in both novo and editar pages

- All `<Input>` fields use `form.register("fieldName")` (spread pattern)
- RadioGroup and Switch MUST use `<Controller control={form.control} name="..." render={({ field }) => ...}>` because they use `onValueChange`/`onCheckedChange` instead of standard `onChange(event)`

### Error display pattern
**Source:** `web/src/app/(dashboard)/clientes/novo/page.tsx` (lines 113-115)
**Apply to:** All new form fields

```tsx
{form.formState.errors.fieldName ? (
  <p className="text-sm text-red-600">{form.formState.errors.fieldName.message}</p>
) : null}
```

### Permission guard pattern
**Source:** `web/src/app/(dashboard)/clientes/novo/page.tsx` (lines 29-32, 80-87)
**Apply to:** Both form pages — no change needed, pattern already in place

```tsx
const permissions = usePermissions();
const canCreate = permissions.can.create("clientes");
// ...
if (!permissions.isLoading && !canCreate) {
  return <AccessDeniedState description="..." backHref="..." />;
}
```

### Shadcn component file conventions
**Source:** `web/src/components/ui/dialog.tsx` (entire file)
**Apply to:** radio-group.tsx, switch.tsx

1. `"use client"` directive at top
2. Import order: React, Radix primitive (as namespace import), `cn` from `@/lib/utils`
3. Named function exports (not arrow functions or const)
4. `data-slot="component-name"` attribute on root element
5. `className={cn("...tailwind classes...", className)}` pattern
6. `...props` spread for all remaining props
7. Named exports at bottom (or inline `export function`)

---

## No Analog Found

No files are fully without analog — all new UI primitives (RadioGroup, Switch) follow the pattern established by `dialog.tsx`.

---

## Metadata

**Analog search scope:** `web/src/components/ui/`, `web/src/app/(dashboard)/clientes/`, `web/src/types/`, `web/src/schemas/`
**Files read:** 8 source files
**Pattern extraction date:** 2026-06-29
