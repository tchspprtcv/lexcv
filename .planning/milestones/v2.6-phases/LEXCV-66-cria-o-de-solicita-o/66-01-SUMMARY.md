---
phase: 66-cria-o-de-solicita-o
plan: 01
subsystem: frontend (parecer jurídico create form)
tags: [pareceres, react-hook-form, zod, tanstack-query]
requires:
  - "web/src/types/pareceres.ts (ParecerSolicitacao, ParecerPrioridade)"
  - "web/src/hooks/use-pareceres.ts read hooks (Phase 65)"
provides:
  - "web/src/schemas/pareceres.ts#parecerCreateFormSchema + ParecerCreateFormValues"
  - "web/src/hooks/use-pareceres.ts#useCreateParecer + ParecerCreateRequest"
  - "web/src/app/(dashboard)/pareceres/nova/page.tsx create form"
  - "'Nova Solicitação' CTA on /pareceres list header"
affects:
  - "Phases 67-69 (version/entrega/aprovação build on solicitações created here)"
tech-stack:
  added: []
  patterns:
    - "Per-page selectClassName/textareaClassName duplication (matches processos/novo/page.tsx, no shared component extracted)"
    - "zodResolver(...) as any + eslint-disable on the resolver line to bridge z.default() input/output mismatch with RHF (matches existing prazoForm pattern in processos/[id]/page.tsx)"
key-files:
  created:
    - web/src/app/(dashboard)/pareceres/nova/page.tsx
  modified:
    - web/src/schemas/pareceres.ts
    - web/src/hooks/use-pareceres.ts
    - web/src/app/(dashboard)/pareceres/page.tsx
decisions:
  - "descricao validation uses superRefine to emit two distinct copies (empty vs. too-short) per UI-SPEC copywriting contract, instead of a single .min(10) message"
  - "processoId select is NOT disabled when clienteId is empty — useProcessos({}) returns the flat tenant list, matching CONTEXT.md's Claude's-discretion note"
metrics:
  duration: "~25 minutes"
  completed: 2026-07-01
---

# Phase 66 Plan 01: Criação de Solicitação de Parecer Summary

First write path for the Parecer Jurídico module: a single-step create form (cliente required, processo/prazo/advogado optional) wired to a new `useCreateParecer` mutation, plus the "Nova Solicitação" CTA on the list page — delivering PARC-13.

## What Was Built

### Task 1: parecerCreateFormSchema (web/src/schemas/pareceres.ts)
- Added `optionalTrimmedString` helper (trim → empty becomes `undefined` → optional).
- Added `parecerCreateFormSchema`: `clienteId` (required, min 1), `processoId`/`prazo`/`advogadoId` (optional trimmed strings), `descricao` (required, `superRefine` emits "Descreva o pedido de parecer." for empty input and "A descrição deve ter pelo menos 10 caracteres." for 1-9 char input — matches the UI-SPEC's two distinct copy requirements), `prioridade` (reuses existing `parecerPrioridadeSchema`, defaults to `"MEDIA"`).
- Exported `ParecerCreateFormValues` type.

### Task 2: useCreateParecer (web/src/hooks/use-pareceres.ts)
- Added `useQueryClient` to the existing react-query import and `ParecerPrioridade` to the existing types import.
- Exported `ParecerCreateRequest` type (six allowlisted fields) and `useCreateParecer()`: POSTs to `/pareceres/solicitacoes`, invalidates `["pareceres","list"]` onSuccess. No normalize/snake_case bridge, consistent with pareceres' pure-camelCase convention from Phase 65.

### Task 3: Create form page + list CTA
- New `web/src/app/(dashboard)/pareceres/nova/page.tsx`: outer `ParecerCreatePage` gates on `permissions.can.create("pareceres")` with `AccessDeniedState` fallback; inner `ParecerCreateFormContent` renders a single Card ("Dados da Solicitação") with cliente (required select), processo (optional select, client-filtered by selected cliente via `useProcessos({cliente_id})`), descrição (textarea), prazo (native date input), prioridade (select, MEDIA pre-selected), advogado responsável (optional select filtered to role `"ADVOGADO"` via `useAdminUsers`). Submit calls `createParecer.mutateAsync(values)`, then `toast.success(...)` + `router.push('/pareceres/{id}')`; failure sets an inline red-600 error banner + `toast.error`. Submit/Cancel button pair matches the `processos/novo` pattern exactly.
- Edited `web/src/app/(dashboard)/pareceres/page.tsx`: added a second `usePermissions()` call inside `ParecerPageContent` (cheap — TanStack Query dedupes) and a `canCreatePareceres` gate around a new "Nova Solicitação" button (`Button asChild` + `Link` to `/pareceres/nova`) in the list header, alongside the existing H1.

## Verification

- `pnpm exec tsc --noEmit` — clean (no errors in any of the four touched files)
- `pnpm lint --quiet` — clean for all touched files; 5 pre-existing lint errors remain in unrelated files (`documentos/novo/page.tsx`, `dashboard-shell.tsx`, `use-toast.ts`) — out of scope per the plan's scope boundary, not introduced by this plan

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - blocking issue] `zodResolver` type mismatch with `z.default()` on `prioridade`**
- **Found during:** Task 3, `tsc --noEmit`
- **Issue:** `parecerCreateFormSchema.prioridade` uses `.default("MEDIA")`, which makes zod's input type `optional` but output type required. RHF's `useForm<ParecerCreateFormValues>` (the output/inferred type) is incompatible with the resolver's expected input type, causing a `tsc` error on both the `resolver` line and the `onSubmit` handler.
- **Fix:** Cast `zodResolver(parecerCreateFormSchema) as any` on the resolver line only, with an inline `eslint-disable-next-line @typescript-eslint/no-explicit-any` comment — this exactly matches the established pattern already in use for the same issue in `web/src/app/(dashboard)/processos/[id]/page.tsx`'s `prazoForm` (`prazoFormSchema` also uses `.default("MEDIA")`).
- **Files modified:** web/src/app/(dashboard)/pareceres/nova/page.tsx
- **Commit:** 57bd084

None of the other deviations were needed — plan executed largely as written.

## Known Stubs

None — the form is fully wired to live data sources (`useClientes`, `useProcessos`, `useAdminUsers`) and a real mutation (`useCreateParecer`); no placeholder/mock data paths introduced.

## Threat Flags

None — all three threats identified in the plan's threat model (T-66-01 elevation of privilege, T-66-02 tampering/IDOR, T-66-03 mass-assignment) were mitigated exactly as specified: CTA/page gated by `pareceres:create` matching backend `@PreAuthorize`; all select values sourced from tenant-scoped hooks, no hand-typed ids; `ParecerCreateRequest` restricts the POST body to the six allowlisted fields. No new network surface beyond the plan's own threat register.

## Commits

- `9450b44` — feat(66-01): add parecerCreateFormSchema with descricao superRefine
- `3f28913` — feat(66-01): add useCreateParecer mutation hook
- `57bd084` — feat(66-01): add /pareceres/nova create form and list-page CTA

## Self-Check: PASSED

- FOUND: web/src/schemas/pareceres.ts (parecerCreateFormSchema)
- FOUND: web/src/hooks/use-pareceres.ts (useCreateParecer)
- FOUND: web/src/app/(dashboard)/pareceres/nova/page.tsx
- FOUND: web/src/app/(dashboard)/pareceres/page.tsx (modified)
- FOUND: 9450b44 in git log
- FOUND: 3f28913 in git log
- FOUND: 57bd084 in git log
