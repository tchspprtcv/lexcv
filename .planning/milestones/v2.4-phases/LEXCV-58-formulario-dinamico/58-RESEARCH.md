# Phase 58: Formulário Dinâmico - Research

**Researched:** 2026-06-29
**Domain:** React Hook Form + conditional fields, shadcn/ui components, TanStack Query type updates
**Confidence:** HIGH

## Summary

Phase 58 is a pure frontend change. It updates the existing cliente create/edit forms to replace the free-text `tipo` input with a proper RadioGroup selector (Particular/Empresa), adds dynamically rendered field sets per tipo, shows a Dialog confirmation when the user changes tipo mid-form, adds an "Avençado" toggle, and surfaces `numero_cliente` as a badge in the detail and listing views.

The backend shapes (numero_cliente, tipo as enum, avencado, dados_tipo JSON) are delivered by Phase 57. Phase 58 consumes them by extending TypeScript types, the Zod schema, and the React Hook Form defaultValues/reset calls. No new API endpoints are needed — only the request/response types change.

**Critical discovery:** The CONTEXT.md states that RadioGroup and Switch are "already installed" as shadcn components, but inspection of `web/src/components/ui/` shows that `radio-group.tsx` and `switch.tsx` do NOT exist, and `@radix-ui/react-radio-group` and `@radix-ui/react-switch` are NOT in `package.json`. These Radix primitives and their shadcn wrappers must be added in Wave 0 of this phase (install + scaffold component files). Dialog IS present (`dialog.tsx`, `@radix-ui/react-dialog` installed).

**Primary recommendation:** Install missing Radix primitives first, scaffold RadioGroup and Switch shadcn wrappers, then update types/schema/forms/views in sequence.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Selector Particular/Empresa no topo do formulário (RadioGroup ou Select), seguido das secções de campos — tudo numa única página, sem wizard/steps
- **D-02:** Ao mudar de tipo, exibir dialog de confirmação: "Mudar o tipo irá limpar os dados de [Tipo Anterior]. Continuar?" — só limpa após confirmação do utilizador
- **D-03:** Campos de Particular (idade, sexo, nacionalidade) e Empresa (nome comercial, sede, representante legal, cargo) trocam dinamicamente com base no tipo seleccionado; campos comuns (nome, NIF, email, telefone, morada) permanecem visíveis para ambos os tipos
- **D-04:** numero_cliente exibido como badge CLI-0001 no cabeçalho da ficha junto ao nome; na listagem como coluna ou badge; gerado pelo backend, não editável; oculto ou "—" para clientes novos antes de guardar
- **D-05:** Toggle/Switch ou Checkbox com label "Avençado" no formulário de criação/edição
- **D-06:** Na listagem: badge distintivo "Avençado" (verde) visível na linha do cliente
- **D-07:** Na ficha de detalhe: badge junto ao numero_cliente

### Claude's Discretion
- Componente UI específico para o selector de tipo (RadioGroup vs Select vs ToggleGroup)
- Validação Zod dos campos específicos por tipo (obrigatoriedade de campos Empresa vs Particular)
- Animação de transição ao trocar campos

### Deferred Ideas (OUT OF SCOPE)
- Procuração (upload obrigatório) → Phase 59
- Campos de intake (advogados, documentos, deslocações, honorários propostos) → Phase 59
- Ficha imprimível → Phase 60
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-02 | Utilizador vê o `numero_cliente` na listagem de clientes e na ficha individual | Badge component (already in `components/ui/badge.tsx`, variant "blue" or "default") rendered from `cliente.numero_cliente` field added to TS types |
| PERF-03 | Utilizador selecciona o tipo de cliente (Particular ou Empresa) no formulário de criação/edição | RadioGroup shadcn component (needs install), controlled via RHF Controller, enum values `PARTICULAR`/`EMPRESA` from Phase 57 backend |
| PERF-04 | Utilizador indica se o cliente é "Avençado" (flag booleano) visível na ficha e na listagem | Switch shadcn component (needs install) in form; Badge variant "green" in listing and detail |
| EMP-02 | Campos de entidade coletiva substituem os campos demográficos no formulário (formulário dinâmico por tipo) | Conditional rendering via `watch("tipo")` in RHF; Dialog from `components/ui/dialog.tsx` for tipo-change confirmation |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tipo selector (RadioGroup) | Frontend (browser) | — | Pure UI state — controlled via React Hook Form watch |
| Dynamic field swap | Frontend (browser) | — | Conditional rendering based on watched `tipo` value |
| Tipo-change confirmation dialog | Frontend (browser) | — | Prevents accidental data loss; no backend involvement |
| Avençado toggle | Frontend (browser) | API (persisted) | Toggle UI state maps to boolean field sent on submit |
| numero_cliente badge display | Frontend (browser) | API (source) | Read-only field from API response — never editable |
| Zod schema update | Frontend (browser) | — | `.superRefine` for type-conditional required fields |
| TypeScript type extensions | Frontend (browser) | — | Add `numero_cliente`, `avencado`, `dados_tipo` fields |

## Standard Stack

### Core (existing — no new installs for logic)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| react-hook-form | 7.62.0 | Form state management | Already in use; `watch("tipo")` drives dynamic fields |
| zod | 4.1.5 | Schema validation | Already in use; extend with `.superRefine` for tipo-conditional fields |
| @tanstack/react-query | 5.87.4 | Server state / API calls | Already in use; `useCliente` / `useUpdateCliente` hooks |

### New Dependencies (must install)
| Package | Registry | Purpose | Verified |
|---------|----------|---------|---------|
| @radix-ui/react-radio-group | npm | Accessible RadioGroup primitive | [VERIFIED: npm registry — npm view @radix-ui/react-radio-group version] |
| @radix-ui/react-switch | npm | Accessible Switch/Toggle primitive | [VERIFIED: npm registry — npm view @radix-ui/react-switch version] |

### Existing shadcn components (no reinstall needed)
| Component | File | Use in Phase 58 |
|-----------|------|-----------------|
| Badge | `components/ui/badge.tsx` | `numero_cliente` + "Avençado" badges; variants: blue, green |
| Dialog | `components/ui/dialog.tsx` | Tipo-change confirmation dialog |
| Card, CardContent, CardHeader | `components/ui/card.tsx` | Form section containers |
| Input, Label | existing | Common fields |

**Installation (Wave 0):**
```bash
pnpm add @radix-ui/react-radio-group @radix-ui/react-switch
```
Then scaffold `web/src/components/ui/radio-group.tsx` and `web/src/components/ui/switch.tsx` from shadcn patterns (see Code Examples below).

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| @radix-ui/react-radio-group | npm | 4+ yrs | Very high (official Radix UI) | github.com/radix-ui/primitives | [ASSUMED — slopcheck unavailable] | Approved — official Radix UI package matching pattern of all other @radix-ui/* already in project |
| @radix-ui/react-switch | npm | 4+ yrs | Very high (official Radix UI) | github.com/radix-ui/primitives | [ASSUMED — slopcheck unavailable] | Approved — same Radix UI org, same pattern |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

*slopcheck was unavailable at research time. Both packages are from the official `@radix-ui` scope already present in package.json (6 other @radix-ui/* packages installed). The planner should add a `checkpoint:human-verify` step before the pnpm install task as a safety gate.*

## Architecture Patterns

### System Architecture Diagram

```
User selects tipo (RadioGroup)
        │
        ├── tipo unchanged → update RHF value directly
        │
        └── tipo changed from previous value
                │
                └── Dialog confirmation
                        ├── Cancel → revert RadioGroup to previous tipo
                        └── Confirm → clear tipo-specific fields, set new tipo
                                │
                                └── Render conditional section
                                        ├── tipo === "PARTICULAR" → idade, sexo, nacionalidade fields
                                        └── tipo === "EMPRESA"    → nome_comercial, sede, representante_legal, cargo fields
                                │
                                └── Common fields always visible (nome, NIF, email, telefone, morada)
                                │
                                └── Avençado Switch (always visible)
                                │
                                └── Submit → PUT/POST with dados_tipo + avencado
                                        │
                                        └── Backend returns Cliente with numero_cliente
                                                │
                                                └── Badge rendered in detail + listing
```

### Recommended File Changes
```
web/src/
├── components/ui/
│   ├── radio-group.tsx     ← NEW (scaffold from Radix)
│   └── switch.tsx          ← NEW (scaffold from Radix)
├── types/
│   └── clientes.ts         ← EXTEND: add numero_cliente, avencado, dados_tipo
├── schemas/
│   └── clientes.ts         ← EXTEND: add avencado, dados_tipo fields + superRefine
├── hooks/
│   └── use-clientes.ts     ← NO CHANGE (schema-driven; types update propagates)
└── app/(dashboard)/clientes/
    ├── page.tsx             ← ADD: numero_cliente badge + Avençado badge per row
    ├── [id]/page.tsx        ← ADD: numero_cliente badge + Avençado badge in header
    ├── novo/page.tsx        ← REFACTOR: RadioGroup tipo selector + conditional fields + Switch
    └── [id]/editar/page.tsx ← REFACTOR: same as novo + populate from client data
```

### Pattern 1: Controlled tipo change with confirmation Dialog

The challenge: RadioGroup fires `onChange` immediately, but we need to confirm before clearing fields. The pattern is to intercept the change via a `pendingTipo` state, show Dialog, and only commit or reject on user action.

```tsx
// Source: [ASSUMED — React Hook Form controlled pattern, training knowledge]
const [pendingTipo, setPendingTipo] = React.useState<string | null>(null);
const watchedTipo = form.watch("tipo");

function onTipoChange(newTipo: string) {
  const current = form.getValues("tipo");
  if (current && current !== newTipo) {
    // There was a previous tipo — ask for confirmation
    setPendingTipo(newTipo);
  } else {
    form.setValue("tipo", newTipo, { shouldValidate: true });
  }
}

function confirmTipoChange() {
  if (!pendingTipo) return;
  // Clear tipo-specific fields
  if (pendingTipo === "PARTICULAR") {
    form.setValue("dados_tipo.nome_comercial", "");
    form.setValue("dados_tipo.sede", "");
    form.setValue("dados_tipo.representante_legal", "");
    form.setValue("dados_tipo.cargo", "");
  } else {
    form.setValue("dados_tipo.idade", undefined);
    form.setValue("dados_tipo.sexo", "");
    form.setValue("dados_tipo.nacionalidade", "");
  }
  form.setValue("tipo", pendingTipo, { shouldValidate: true });
  setPendingTipo(null);
}
```

### Pattern 2: Zod superRefine for tipo-conditional required fields

```typescript
// Source: [ASSUMED — Zod superRefine pattern, training knowledge]
export const clienteFormSchema = z.object({
  tipo: z.enum(["PARTICULAR", "EMPRESA"]).optional(),
  avencado: z.boolean().default(false),
  dados_tipo: z.object({
    // Particular fields
    idade: z.number().int().positive().optional(),
    sexo: z.string().optional(),
    nacionalidade: z.string().optional(),
    // Empresa fields
    nome_comercial: z.string().optional(),
    sede: z.string().optional(),
    representante_legal: z.string().optional(),
    cargo: z.string().optional(),
  }).optional(),
  // ... existing fields
}).superRefine((data, ctx) => {
  if (data.tipo === "EMPRESA") {
    if (!data.dados_tipo?.nome_comercial?.trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "Nome comercial é obrigatório para Empresa", path: ["dados_tipo", "nome_comercial"] });
    }
    if (!data.dados_tipo?.representante_legal?.trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "Representante legal é obrigatório para Empresa", path: ["dados_tipo", "representante_legal"] });
    }
  }
  // Particular fields are all optional per requirements (PART-01 listed in Phase 57, not 58)
});
```

### Pattern 3: numero_cliente badge (read-only, display only)

```tsx
// In detail header — Source: existing badge.tsx pattern
{cliente.data.numero_cliente && (
  <Badge variant="blue" className="rounded-none font-mono text-xs">
    {cliente.data.numero_cliente}
  </Badge>
)}

// In listing row (ClienteRow) — below existing nome link
<div className="text-[11px] font-medium tracking-wider text-slate-500 mt-0.5">
  {cliente.numero_cliente ?? "—"}
</div>
```

### Pattern 4: Avençado badge in listing

```tsx
// Add to mobile card and desktop TableRow — reuses existing Badge variant "green"
{c.avencado && (
  <Badge variant="green" className="rounded-none font-bold text-[10px]">
    Avençado
  </Badge>
)}
```

### Anti-Patterns to Avoid
- **Clearing fields silently on tipo change:** D-02 requires a confirmation dialog. Never `setValue("tipo", ...)` directly without checking if there was a previous tipo and prompting first.
- **Making numero_cliente editable:** It is backend-generated (Phase 57). The form must never include it as an editable field — only display it in detail/listing views.
- **Nested Zod object without `.optional()`:** `dados_tipo` should be optional at the object level since new clients start with no tipo selected.
- **Using `form.register` for RadioGroup/Switch:** These shadcn components use `<Controller>` from react-hook-form, not `register`, because they are controlled components with non-input DOM structure.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Accessible radio buttons | Custom div with click handlers | `@radix-ui/react-radio-group` via shadcn RadioGroup | Keyboard nav, aria-checked, focus management handled |
| Toggle/switch | Custom checkbox styled | `@radix-ui/react-switch` via shadcn Switch | Accessible, animates natively, aria-checked |
| Confirmation dialog | Custom modal | Existing `dialog.tsx` (AlertDialog or Dialog) | Already installed, matches app pattern |
| Badge variants | New CSS classes | Existing `badge.tsx` variants (blue, green) | `variant="blue"` for numero_cliente, `variant="green"` for Avençado |

## Common Pitfalls

### Pitfall 1: RadioGroup and Switch need Controller, not register
**What goes wrong:** `form.register("tipo")` returns `ref`, `onChange`, `onBlur` — but Radix Radio/Switch components don't forward these correctly to their internal input.
**Why it happens:** Radix primitives expose `onValueChange` / `onCheckedChange`, not a standard `onChange(event)`.
**How to avoid:** Use `<Controller control={form.control} name="tipo" render={({ field }) => <RadioGroup value={field.value} onValueChange={field.onChange}>` pattern.
**Warning signs:** `field.value` stays undefined; no validation errors even when blank.

### Pitfall 2: dados_tipo is a nested object — RHF dot notation
**What goes wrong:** Trying to register `dados_tipo.nome_comercial` as a flat key; TypeScript complains.
**Why it happens:** RHF uses dot notation for nested objects: `form.register("dados_tipo.nome_comercial")`.
**How to avoid:** Define `dados_tipo` as a nested Zod object, use `form.register("dados_tipo.nome_comercial")` with dot notation, and in `defaultValues` provide `dados_tipo: {}`.

### Pitfall 3: Edit form must map dados_tipo from API response to nested RHF values
**What goes wrong:** The `useEffect` that calls `form.reset()` in the edit form currently flattens all fields. With `dados_tipo` as a JSON column in the API response, the reset must map it properly.
**Why it happens:** The API returns `dados_tipo` as a JSON object; the form expects `dados_tipo.nome_comercial` etc.
**How to avoid:** In `form.reset({ ..., dados_tipo: cliente.data.dados_tipo ?? {} })`.

### Pitfall 4: tipo enum mismatch between listing filter and form
**What goes wrong:** The listing currently filters by `SINGULAR`/`COLETIVA` but Phase 57 changes the enum to `PARTICULAR`/`EMPRESA`. The listing advanced filter dropdown must also be updated.
**Why it happens:** The listing `page.tsx` has hardcoded `<option value="SINGULAR">` and `<option value="COLETIVA">`.
**How to avoid:** Update the Tipo filter dropdown to `PARTICULAR`/`EMPRESA`. Also update the stats cards that count by tipo (`totalSingulares`, `totalColetivas`).

### Pitfall 5: Zod v4 syntax differences
**What goes wrong:** The project uses `zod ^4.1.5`. Zod v4 changed some APIs (e.g. `.default()` behavior on optional fields).
**Why it happens:** zod 4.x is a major version with breaking changes vs zod 3.x. Most training data covers zod 3.
**How to avoid:** Use `z.boolean().default(false)` for `avencado`; test that `superRefine` still works as expected (API is unchanged in v4 for superRefine).

## Code Examples

### RadioGroup shadcn wrapper scaffold
```tsx
// web/src/components/ui/radio-group.tsx
// Source: [ASSUMED — standard shadcn/ui scaffold pattern]
"use client";
import * as React from "react";
import * as RadioGroupPrimitive from "@radix-ui/react-radio-group";
import { cn } from "@/lib/utils";

export function RadioGroup({ className, ...props }: React.ComponentProps<typeof RadioGroupPrimitive.Root>) {
  return <RadioGroupPrimitive.Root className={cn("grid gap-2", className)} {...props} />;
}

export function RadioGroupItem({ className, ...props }: React.ComponentProps<typeof RadioGroupPrimitive.Item>) {
  return (
    <RadioGroupPrimitive.Item
      className={cn(
        "aspect-square h-4 w-4 rounded-full border border-neutral-300 text-neutral-900 ring-offset-white focus:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-neutral-900 data-[state=checked]:bg-neutral-900",
        className,
      )}
      {...props}
    >
      <RadioGroupPrimitive.Indicator className="flex items-center justify-center">
        <span className="h-2 w-2 rounded-full bg-white" />
      </RadioGroupPrimitive.Indicator>
    </RadioGroupPrimitive.Item>
  );
}
```

### Switch shadcn wrapper scaffold
```tsx
// web/src/components/ui/switch.tsx
// Source: [ASSUMED — standard shadcn/ui scaffold pattern]
"use client";
import * as React from "react";
import * as SwitchPrimitive from "@radix-ui/react-switch";
import { cn } from "@/lib/utils";

export function Switch({ className, ...props }: React.ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        "peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-neutral-900 data-[state=unchecked]:bg-neutral-200",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        className="pointer-events-none block h-4 w-4 rounded-full bg-white shadow-lg ring-0 transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0"
      />
    </SwitchPrimitive.Root>
  );
}
```

### Tipo selector with Controller + confirmation flow
```tsx
// Source: [ASSUMED — React Hook Form Controller pattern]
import { Controller } from "react-hook-form";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";

<Controller
  control={form.control}
  name="tipo"
  render={({ field }) => (
    <RadioGroup
      value={field.value ?? ""}
      onValueChange={(val) => onTipoChange(val, field.value)}
    >
      <div className="flex items-center gap-2">
        <RadioGroupItem value="PARTICULAR" id="tipo-particular" />
        <Label htmlFor="tipo-particular">Particular</Label>
      </div>
      <div className="flex items-center gap-2">
        <RadioGroupItem value="EMPRESA" id="tipo-empresa" />
        <Label htmlFor="tipo-empresa">Empresa</Label>
      </div>
    </RadioGroup>
  )}
/>
```

### TypeScript type additions for Cliente
```typescript
// web/src/types/clientes.ts additions
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

// Update Cliente interface:
export interface Cliente {
  // ... existing fields
  tipo?: "PARTICULAR" | "EMPRESA";       // was: string
  avencado?: boolean;                    // NEW
  numero_cliente?: string;               // NEW (e.g. "CLI-0001")
  dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;  // NEW
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `tipo` as free-text Input | RadioGroup with enum values `PARTICULAR`/`EMPRESA` | Phase 57/58 | Eliminates invalid values; enables conditional field rendering |
| No Avençado concept | Boolean `avencado` flag | Phase 57 backend + Phase 58 UI | Surfaces in form, badge in listing/detail |
| No client number | `numero_cliente` (backend-generated, CLI-0001 format) | Phase 57/58 | Visible as badge in detail + listing |
| Flat form fields | Nested `dados_tipo` JSON object | Phase 57 backend stores JSON | RHF dot-notation for nested fields; tipo-specific validation |

**Legacy values to handle in listing:**
- Existing clients may have `tipo` values `SINGULAR`/`COLETIVA` (old enum) or plain strings. The listing stats counter uses these; after Phase 57 migration, these will be `PARTICULAR`/`EMPRESA`. The listing filter must update its option values too.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Phase 57 backend adds `numero_cliente`, `avencado`, `dados_tipo` to the API response for GET /clientes and GET /clientes/{id} | Types section, Code Examples | If Phase 57 uses different field names or nesting structure, the TS types and form reset logic must be adjusted |
| A2 | Phase 57 uses enum values `PARTICULAR` and `EMPRESA` (not `SINGULAR`/`COLETIVA` or other) | Types, listing filter pitfall | If enum values differ, all frontend string comparisons must update |
| A3 | `dados_tipo` is returned as a flat JSON object from the backend (e.g. `{ nome_comercial: "..." }`) | Schema, edit form reset | If backend wraps it differently, the form reset mapping changes |
| A4 | shadcn scaffold patterns for RadioGroup/Switch are compatible with the project's existing Tailwind + dark mode setup | Code Examples | If the project has customized Tailwind config that conflicts, minor CSS tweaks needed |
| A5 | The listing stat cards ("Pessoas Singulares", "Entidades Coletivas") should be renamed to match new enum values | Pitfall 4, listing page | If the client wants to keep old labels, only internal filter values change |

## Open Questions

1. **Phase 57 API shape for dados_tipo**
   - What we know: CONTEXT.md says backend adds `dados_tipo JSON` to Cliente entity
   - What's unclear: Whether GET /clientes/{id} returns it as a nested object or as flattened columns; whether the list endpoint `/clientes` returns `dados_tipo` or only the detail endpoint
   - Recommendation: Planner should add a task "Verify Phase 57 API shape — check GET /clientes/{id} response for dados_tipo, avencado, numero_cliente fields before updating TypeScript types"

2. **Validation strictness for tipo-specific fields**
   - What we know: D-03 says these fields swap dynamically
   - What's unclear: Whether idade/sexo/nacionalidade are required for Particular, or whether Empresa fields (nome_comercial, representante_legal) are required — requirements say PART-01 and EMP-01 are "Phase 57" work, not Phase 58
   - Recommendation: Claude's discretion per CONTEXT.md — make Empresa fields `nome_comercial` and `representante_legal` required when tipo=EMPRESA, all Particular fields optional

3. **Listing enum mismatch with legacy data**
   - What we know: Existing clientes have `tipo` as free-text or old values (`SINGULAR`/`COLETIVA`)
   - What's unclear: Whether Phase 57 includes a data migration to convert old values
   - Recommendation: Planner should add a note that listing filter must handle both old and new enum values until migration is confirmed complete

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| pnpm | Package install | Assumed yes (already used) | locked via pnpm-lock.yaml | — |
| @radix-ui/react-radio-group | RadioGroup component | No (not in package.json) | — | Use native `<select>` for tipo if install fails (degraded UX) |
| @radix-ui/react-switch | Switch component | No (not in package.json) | — | Use `<input type="checkbox">` with label styling (degraded UX) |
| @radix-ui/react-dialog | Confirmation dialog | Yes (1.1.16 in package.json) | 1.1.16 | — |
| shadcn badge.tsx | Badges | Yes (in components/ui) | — | — |

**Missing dependencies with no fallback:** none (degraded UX alternatives exist)
**Missing dependencies requiring install:** `@radix-ui/react-radio-group`, `@radix-ui/react-switch`

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | None detected (no jest.config, no vitest.config, no test/ directory found) |
| Config file | none |
| Quick run command | `pnpm lint` (ESLint — only linting available) |
| Full suite command | `pnpm build` (type-check via tsc) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PERF-02 | numero_cliente badge renders in listing and detail | manual-only | — | N/A |
| PERF-03 | RadioGroup tipo selector selects PARTICULAR/EMPRESA | manual-only | — | N/A |
| PERF-04 | Avençado switch toggles; badge visible in listing/detail | manual-only | — | N/A |
| EMP-02 | Empresa fields appear when tipo=EMPRESA; Particular fields when tipo=PARTICULAR | manual-only | — | N/A |

**Note:** No test infrastructure exists in this project. Verification is by TypeScript type-check (`pnpm build`) and manual browser testing. The planner should include a verification task: "Run `pnpm build` — zero type errors" as the automated gate.

### Sampling Rate
- **Per task commit:** `pnpm lint` (ESLint)
- **Per wave merge:** `pnpm build` (TypeScript type check)
- **Phase gate:** `pnpm build` zero errors + manual browser verification of all 4 requirements

### Wave 0 Gaps
- [ ] `web/src/components/ui/radio-group.tsx` — needed for PERF-03
- [ ] `web/src/components/ui/switch.tsx` — needed for PERF-04
- [ ] Install: `pnpm add @radix-ui/react-radio-group @radix-ui/react-switch`

## Security Domain

### Applicable ASVS Categories (ASVS Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Auth is handled upstream (JWT cookie already in place) |
| V3 Session Management | no | No session changes |
| V4 Access Control | yes | Permission check already in place (`permissions.can.edit("clientes")`) — no new endpoints |
| V5 Input Validation | yes | Zod schema with superRefine validates all new fields before submit |
| V6 Cryptography | no | No new crypto |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Sending `numero_cliente` in POST/PUT payload | Tampering | Never include `numero_cliente` in ClienteCreateRequest or ClienteUpdateRequest — it is backend-generated and should be stripped from form submit payload |
| Unvalidated enum value for tipo | Tampering | Zod schema uses `z.enum(["PARTICULAR", "EMPRESA"])` — rejects any other value before API call |
| XSS via dados_tipo display | XSS | React renders strings as text nodes by default — no dangerouslySetInnerHTML used |

## Sources

### Primary (HIGH confidence)
- Codebase inspection (`web/src/components/ui/`, `web/package.json`) — confirmed RadioGroup/Switch absence, Dialog/Badge presence
- `web/src/schemas/clientes.ts` — confirmed missing fields, existing superRefine pattern
- `web/src/types/clientes.ts` — confirmed missing avencado, numero_cliente, dados_tipo
- `web/src/app/(dashboard)/clientes/` all pages — confirmed current form structure and listing rendering

### Secondary (MEDIUM confidence)
- `.planning/phases/LEXCV-58-formulario-dinamico/58-CONTEXT.md` — locked decisions
- `.planning/REQUIREMENTS.md` — requirement descriptions

### Tertiary (LOW / ASSUMED confidence)
- shadcn RadioGroup/Switch scaffold patterns — based on training knowledge of shadcn conventions; verify against `web/node_modules/next/dist/docs/` if Next.js 16 has framework-specific caveats

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified by package.json inspection
- Architecture: HIGH — all source files read directly
- Missing components: HIGH — confirmed by `ls components/ui/` showing no radio-group.tsx or switch.tsx
- API shape from Phase 57: LOW — Phase 57 not yet implemented; using CONTEXT.md descriptions

**Research date:** 2026-06-29
**Valid until:** 2026-07-29 (stable frontend domain; Phase 57 API shape is the main uncertainty)
