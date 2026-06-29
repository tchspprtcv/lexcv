---
phase: 58-formulario-dinamico
plan: 01
subsystem: web-clientes-foundation
tags: [radix-ui, shadcn, types, zod, clientes]
requires: []
provides:
  - "RadioGroup + RadioGroupItem shadcn wrapper (web/src/components/ui/radio-group.tsx)"
  - "Switch shadcn wrapper (web/src/components/ui/switch.tsx)"
  - "Cliente/ClienteCreateRequest/ClienteUpdateRequest types extended with numero_cliente, avencado, dados_tipo, tipo literal union"
  - "clienteFormSchema (Zod) extended with tipo enum, avencado, dados_tipo, EMPRESA-required superRefine checks"
affects:
  - "web/src/app/(dashboard)/clientes/novo/page.tsx (defaultValues tipo fix only — full form refactor deferred to later plan)"
  - "web/src/app/(dashboard)/clientes/[id]/editar/page.tsx (defaultValues/reset tipo fix only — full form refactor deferred to later plan)"
  - "web/src/app/(dashboard)/clientes/page.tsx (CSV import tipo cast)"
tech-stack:
  added:
    - "@radix-ui/react-radio-group@1.4.1"
    - "@radix-ui/react-switch@1.3.1"
  patterns:
    - "shadcn Radix wrapper: 'use client' + namespace import + data-slot + cn() (copied from dialog.tsx)"
key-files:
  created:
    - web/src/components/ui/radio-group.tsx
    - web/src/components/ui/switch.tsx
  modified:
    - web/src/types/clientes.ts
    - web/src/schemas/clientes.ts
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
    - web/src/app/(dashboard)/clientes/page.tsx
decisions:
  - "avencado kept as z.boolean().optional() instead of .default(false) — .default() made the Zod input type require avencado in defaultValues, breaking existing useForm<ClienteFormValues> consumers that don't yet supply it (deferred to Wave 2 form refactor plans)"
metrics:
  duration: "~25 min"
  completed: "2026-06-29"
---

# Phase 58 Plan 01: Radix Primitives + Type/Schema Foundation Summary

Installed `@radix-ui/react-radio-group` and `@radix-ui/react-switch`, scaffolded their shadcn wrappers exactly matching the `dialog.tsx` pattern, and extended `types/clientes.ts` + `schemas/clientes.ts` with all Phase 57 backend fields (`numero_cliente`, `avencado`, `dados_tipo`, literal-typed `tipo`).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Install Radix primitives and scaffold UI components | ad9f118 | web/src/components/ui/radio-group.tsx, web/src/components/ui/switch.tsx, web/package.json, web/pnpm-lock.yaml |
| 2 | Extend types/clientes.ts and schemas/clientes.ts for Phase 57 fields | 0f90473 | web/src/types/clientes.ts, web/src/schemas/clientes.ts, web/src/app/(dashboard)/clientes/novo/page.tsx, web/src/app/(dashboard)/clientes/[id]/editar/page.tsx, web/src/app/(dashboard)/clientes/page.tsx |

## What Was Built

- `web/src/components/ui/radio-group.tsx` — `RadioGroup` (Root wrapper, `data-slot="radio-group"`, `grid gap-2`) and `RadioGroupItem` (Item wrapper with checked/unchecked, focus-visible, disabled, and dark-mode states; `Indicator` renders a filled dot).
- `web/src/components/ui/switch.tsx` — `Switch` (Root wrapper with track styling for checked/unchecked + dark mode, `Thumb` translates on state change).
- `web/src/types/clientes.ts` — added `DadosTipoParticular` (`idade`, `sexo`, `nacionalidade`) and `DadosTipoEmpresa` (`nome_comercial`, `sede`, `representante_legal`, `cargo`) interfaces. `Cliente.tipo` narrowed from `string` to `"PARTICULAR" | "EMPRESA" | undefined`; added `numero_cliente?: string` (read-only), `avencado?: boolean`, `dados_tipo?: DadosTipoParticular | DadosTipoEmpresa`. Same additions to `ClienteCreateRequest`/`ClienteUpdateRequest` **excluding** `numero_cliente` (backend-generated, must never be sent in requests). `ClientesListFilters.tipo` left as `string` (query param).
- `web/src/schemas/clientes.ts` — `tipo` is now `z.enum(["PARTICULAR", "EMPRESA"]).optional()`; added `avencado: z.boolean().optional()` and a `dados_tipo` nested optional object covering both Particular and Empresa fields. Existing `superRefine` extended (not replaced) with two new checks: when `tipo === "EMPRESA"`, `dados_tipo.nome_comercial` and `dados_tipo.representante_legal` are required (error paths `["dados_tipo", "nome_comercial"]` / `["dados_tipo", "representante_legal"]`).

## Verification

`pnpm build` (from `web/`) exits 0 with zero TypeScript errors across all routes, including the newly typed `radio-group.tsx`, `switch.tsx`, `types/clientes.ts`, and `schemas/clientes.ts`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking type error] `avencado: z.boolean().default(false)` broke existing form pages**
- **Found during:** Task 2, running `pnpm build` verification
- **Issue:** Zod's `.default()` makes the field's *input* type required (non-optional) on the resolver's input schema, which made `useForm<ClienteFormValues>` (used in `clientes/novo/page.tsx` and `clientes/[id]/editar/page.tsx`, not yet refactored in this plan) fail to type-check because their existing `defaultValues` objects don't include `avencado`.
- **Fix:** Changed `avencado: z.boolean().default(false)` to `avencado: z.boolean().optional()`. Behavior is equivalent for current consumers (undefined treated as falsy downstream); the Wave 2 form refactor plans (58-02/58-03/58-04 per ROADMAP) will explicitly wire `avencado: false` into `defaultValues` via `Controller` + `Switch`.
- **Files modified:** web/src/schemas/clientes.ts
- **Commit:** 0f90473

**2. [Rule 3 - Blocking type error] `tipo: ""` defaults no longer match new literal union type**
- **Found during:** Task 2, running `pnpm build` verification
- **Issue:** `Cliente.tipo` / schema `tipo` changed from `string` to `"PARTICULAR" | "EMPRESA" | undefined`. Existing `clientes/novo/page.tsx` (`defaultValues.tipo: ""`) and `clientes/[id]/editar/page.tsx` (`defaultValues.tipo: ""` and `form.reset({ tipo: cliente.data.tipo ?? "" })`) used the empty string as their "no selection" sentinel, which is no longer a valid value for the narrowed type.
- **Fix:** Changed both occurrences to `undefined` instead of `""`. This is a minimal type-compatibility fix only — it does NOT include the RadioGroup/Controller/Dialog refactor described in 58-PATTERNS.md for these pages; that full refactor is explicitly scoped to later Wave 2 plans per the phase plan's `depends_on` structure.
- **Files modified:** web/src/app/(dashboard)/clientes/novo/page.tsx, web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
- **Commit:** 0f90473

**3. [Rule 3 - Blocking type error] CSV import in clientes/page.tsx assigns free-text string to `tipo`**
- **Found during:** Task 2, running `pnpm build` verification
- **Issue:** The clientes list page's CSV bulk-import handler builds a `ClienteCreateRequest` from raw CSV cell text and assigned the trimmed string directly to `tipo`, which is no longer compatible with the narrowed `"PARTICULAR" | "EMPRESA" | undefined` type.
- **Fix:** Cast the trimmed CSV value to `"PARTICULAR" | "EMPRESA" | undefined`. This preserves existing CSV-import behavior (no runtime validation of the cell value was happening before this change either); a stricter validation of imported `tipo` values is out of scope for this plan and not flagged by 58-CONTEXT.md or 58-PATTERNS.md.
- **Files modified:** web/src/app/(dashboard)/clientes/page.tsx
- **Commit:** 0f90473

## Known Stubs

None — no UI components reference fields with empty/mock placeholder data; the RadioGroup/Switch components are unused until Wave 2 form refactor plans wire them in (this plan's `key_links` define the intended import-only, not full usage).

## Threat Flags

None — no new network endpoints, auth paths, or trust-boundary changes. `numero_cliente` is confirmed absent from `ClienteCreateRequest`/`ClienteUpdateRequest` (verified via grep, matching T-58-01 mitigation).

## Self-Check: PASSED

- FOUND: web/src/components/ui/radio-group.tsx
- FOUND: web/src/components/ui/switch.tsx
- FOUND: web/src/types/clientes.ts (numero_cliente?: string present)
- FOUND: web/src/schemas/clientes.ts (z.enum(["PARTICULAR", "EMPRESA"]), dados_tipo present)
- FOUND: commit ad9f118
- FOUND: commit 0f90473
- `pnpm build` exits 0
