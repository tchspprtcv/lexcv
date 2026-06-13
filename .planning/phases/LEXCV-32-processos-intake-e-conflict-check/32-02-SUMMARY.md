---
phase: 32-processos-intake-e-conflict-check
plan: "02"
subsystem: frontend
tags: [conflict-check, types, zod, tanstack-query, hooks, intake]
dependency_graph:
  requires:
    - Phase 32 Plan 01 (backend endpoints: /processos/intake, /conflict-check, /conflict-check/decisao, /formalizar)
  provides:
    - ConflictNivel type (sem_conflito|potencial|sanavel|impeditivo) in web/src/types/processos.ts
    - ConflictMatch, ConflictCheckResponse, ConflictCheckDecisao (camelCase), ConflictCheckDecisaoRequest interfaces
    - conflictNivelEnum and conflictCheckDecisaoFormSchema (Zod, conditional justificativa) in web/src/schemas/processos.ts
    - conflictNivelToVariant and conflictNivelToLabel single source of truth in web/src/lib/conflict-check.ts
    - useCreateIntake, useRunConflictCheck, useRegistarDecisaoConflito, useConflictCheckDecisao, useFormalizarProcesso hooks in web/src/hooks/use-processos.ts
  affects:
    - web/src/types/processos.ts
    - web/src/schemas/processos.ts
    - web/src/hooks/use-processos.ts
    - web/src/lib/conflict-check.ts
tech_stack:
  added: []
  patterns:
    - TanStack Query useMutation + useQuery with invalidateQueries/setQueryData
    - Zod superRefine for cross-field conditional validation (justificativa required for potencial/sanavel)
    - Single source of truth utility module (lib/conflict-check.ts) shared across wizard steps and detail page
    - camelCase TypeScript interfaces matching Spring Boot Jackson default serialization
key_files:
  created:
    - web/src/lib/conflict-check.ts
  modified:
    - web/src/types/processos.ts
    - web/src/schemas/processos.ts
    - web/src/hooks/use-processos.ts
decisions:
  - "ConflictCheckDecisao interface uses camelCase (tenantId, processoId, createdAt) to match Spring Boot Jackson default serialization — snake_case would produce undefined at runtime"
  - "conflictNivelToVariant and conflictNivelToLabel placed in lib/conflict-check.ts as single source of truth shared by Step 2, Step 3, and detail page (per CONTEXT.md specifics)"
  - "No manual toasts added to hooks — apiFetch already handles non-401/403 errors; UI (Plan 03) handles inline display via try/catch"
  - "useConflictCheckDecisao uses staleTime 15_000 to allow re-fetch on decisao registration without excessive network calls"
metrics:
  duration: "~20 minutes"
  completed_date: "2026-06-13"
  tasks_completed: 2
  files_created: 1
  files_modified: 3
---

# Phase 32 Plan 02: Frontend Data Layer — Types, Schema, Hooks, and Conflict Helper Summary

**One-liner:** Frontend contracts for conflict check: camelCase TypeScript types matching Jackson serialization, Zod schema with conditional justificativa enforcement, single-source-of-truth nivel helper, and 5 TanStack Query hooks covering intake/conflict-check/decisao/formalizar endpoints.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Tipos e schema Zod do conflict check | `0b7e160` | web/src/types/processos.ts, web/src/schemas/processos.ts |
| 2 | Helper de nivel (fonte unica) e hooks TanStack Query | `61df7b1` | web/src/lib/conflict-check.ts (new), web/src/hooks/use-processos.ts |

## What Was Built

### Types (Task 1)
- `ConflictNivel` union type: `"sem_conflito" | "potencial" | "sanavel" | "impeditivo"`
- `ConflictMatch` interface: `entidadeId`, `entidadeTipo` ("cliente" | "parte"), `nome`, `nif?`, `nivelConflito`, `motivo?`
- `ConflictCheckResponse` interface: `matches: ConflictMatch[]`, `nivelSugerido: ConflictNivel`
- `ConflictCheckDecisao` interface: all fields in camelCase (`tenantId`, `processoId`, `createdAt`, `decisorId`, `dataDecisao`, `referenciaEvidencia?`) — critical alignment with Spring Boot Jackson default serialization
- `ConflictCheckDecisaoRequest` interface: `nivel`, `justificativa?`, `referenciaEvidencia?` — decisorId intentionally omitted (T-32-07: backend derives from SecurityContext)

### Schema (Task 1)
- `conflictNivelEnum`: `z.enum(["sem_conflito","potencial","sanavel","impeditivo"])` — rejects any other value
- `conflictCheckDecisaoFormSchema`: `superRefine` requiring `justificativa` when `nivel === "potencial" || nivel === "sanavel"` — emits issue at `path: ["justificativa"]`; justificativa is not required for `sem_conflito` or `impeditivo`
- `ConflictCheckDecisaoFormValues` exported inferred type

### Conflict Helper (Task 2)
- `web/src/lib/conflict-check.ts` exports:
  - `conflictNivelToVariant`: sem_conflito->"green", potencial->"amber", sanavel->"blue", impeditivo->"red"
  - `conflictNivelToLabel`: sem_conflito->"SEM CONFLITO", potencial->"POTENCIAL", sanavel->"SANÁVEL", impeditivo->"IMPEDITIVO"
- Exact match with UI-SPEC lines 73-84 and 171-175

### Hooks (Task 2)
- `useCreateIntake()` — mutation POST `/processos/intake`, normalizes via `normalizeProcesso`, invalidates `["processos","list"]`
- `useRunConflictCheck(processoId)` — mutation POST `/processos/{id}/conflict-check`, returns `ConflictCheckResponse`, invalidates `["processos","conflict-check",processoId]`
- `useRegistarDecisaoConflito(processoId)` — mutation POST `/processos/{id}/conflict-check/decisao`, returns `ConflictCheckDecisao`, invalidates both `conflict-check` and `detail` query keys
- `useConflictCheckDecisao(processoId)` — query GET `/processos/{id}/conflict-check/decisao`, enabled when `typeof window !== "undefined" && Boolean(processoId)`, staleTime 15s
- `useFormalizarProcesso(processoId)` — mutation POST `/processos/{id}/formalizar`, normalizes response, invalidates list and sets detail cache

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan delivers only types, schema, and hooks. No UI rendering; no data flows through to any component stub.

## Threat Flags

No new threat surfaces introduced.

T-32-07 mitigation confirmed: `ConflictCheckDecisaoRequest` does not include `decisorId` — the frontend never sends identity; the backend derives it from `SecurityContext`.

## Self-Check: PASSED

Files verified:
- FOUND: web/src/lib/conflict-check.ts (new)
- FOUND: web/src/types/processos.ts (modified — ConflictNivel, ConflictCheckDecisao, etc.)
- FOUND: web/src/schemas/processos.ts (modified — conflictNivelEnum, conflictCheckDecisaoFormSchema)
- FOUND: web/src/hooks/use-processos.ts (modified — 5 new hooks)

Commits verified (web submodule):
- 0b7e160: feat(32-02): add ConflictNivel types, ConflictCheckDecisao interface (camelCase), and conflictCheckDecisaoFormSchema
- 61df7b1: feat(32-02): add conflict-check helper (single source of truth) and 5 intake/conflict/formalizar hooks

Build: `pnpm exec tsc --noEmit` -> no errors
Lint: `pnpm lint` -> 0 errors, 5 pre-existing warnings (unchanged from baseline)
