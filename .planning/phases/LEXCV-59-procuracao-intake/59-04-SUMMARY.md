---
phase: "59"
plan: "04"
subsystem: frontend-types-hooks
tags: [clientes, procuracao, advogados, administrativos, react-query, zod]
dependency-graph:
  requires: ["59-03"]
  provides: ["Cliente type+hook contracts for Wave 4 UI plans"]
  affects: ["web/src/types/clientes.ts", "web/src/hooks/use-clientes.ts", "web/src/schemas/clientes.ts"]
tech-stack:
  added: []
  patterns: ["TanStack Query useMutation/useQuery with apiFetch", "FormData upload via apiFetch (no Content-Type override)"]
key-files:
  created: []
  modified:
    - web/src/types/clientes.ts
    - web/src/hooks/use-clientes.ts
    - web/src/schemas/clientes.ts
decisions:
  - "Used /clientes/... relative paths (matching apiFetch + API_BASE convention already used by all other hooks in the file) rather than /api/v1/clientes/... — apiFetch prefixes API_BASE which already includes /api/v1."
metrics:
  duration: "~20m"
  completed: "2026-06-30"
---

# Phase 59 Plan 04: Frontend Types, Hooks & Schema for Procuração Intake Summary

Extended `Cliente` TypeScript types, added 9 new TanStack Query hooks for procuração upload/download/delete and advogados/administrativos assignment, and extended the Zod `clienteFormSchema` with `descricao_caso` and `honorarios_propostos` fields — the typed contract layer Wave 4 UI plans build against.

## What Was Built

### Task 1: TypeScript types (commit dd73497)
- Added `DocumentoEntregue`, `DocumentoATratar`, `Deslocacao`, `HonorariosPropostos`, `ClienteAdvogadoUser` interfaces to `web/src/types/clientes.ts`.
- Extended `Cliente` with Phase 57 back-fills (`avencado`, `numero_cliente`, `dados_tipo`) and Phase 59 intake fields (`procuracao_key`, `descricao_caso`, `documentos_entregues`, `documentos_a_tratar`, `deslocacoes`, `honorarios_propostos`).
- Extended `ClienteUpdateRequest` with the same intake fields (all optional).
- The worktree's pre-existing `clientes.ts` did NOT yet have Phase 57 fields (no drift was present at this base commit), so all fields including Phase 57 back-fills were added fresh per plan instructions.

### Task 2: Hooks and schema (commit f8b5169)
Added to `web/src/hooks/use-clientes.ts`:
- `useUploadProcuracao(clienteId)` — POST FormData to `/clientes/{id}/procuracao`
- `useDownloadProcuracao()` — mutation taking `clienteId`, returns presigned `{ url, expiresIn }`
- `useDeleteProcuracao(clienteId)` — DELETE `/clientes/{id}/procuracao`
- `useClienteAdvogados(clienteId)` — query `["clientes", clienteId, "advogados"]`
- `useAddAdvogado(clienteId)` / `useRemoveAdvogado(clienteId)` — POST/DELETE `/clientes/{id}/advogados/{userId}`
- `useClienteAdministrativos(clienteId)` — query `["clientes", clienteId, "administrativos"]`
- `useAddAdministrativo(clienteId)` / `useRemoveAdministrativo(clienteId)` — POST/DELETE `/clientes/{id}/administrativos/{userId}`

All mutations follow the existing file's pattern (`useMutation` + `apiFetch` + `invalidateQueries` on success). FormData upload omits explicit `Content-Type` per the file's documented convention.

Extended `clienteFormSchema` in `web/src/schemas/clientes.ts` with `descricao_caso` (optional trimmed string) and `honorarios_propostos` (optional object: `total`, `totalPorExtenso`, `previsao`).

## Verification

- `grep -c` for all 9 new hook names in `use-clientes.ts` returns 9 (matches plan's expected verification command).
- `pnpm tsc --noEmit` could not be run — `web/node_modules` is not installed in this worktree (`node_modules/.bin/tsc` absent, `pnpm exec tsc` reports command not found). This is an environment limitation of the isolated worktree, not a code defect. Types were hand-verified against existing file conventions (optional fields, consistent naming, correct import additions).

## Deviations from Plan

None — plan executed as written. One clarifying note: paths use `/clientes/...` (not `/api/v1/clientes/...`) consistent with every other hook in the file, since `apiFetch`'s `API_BASE` already includes the `/api/v1` prefix.

## Known Stubs

None. All hooks call real endpoints per the 59-03 backend plan (already merged: procuração upload/download/delete, advogados/administrativos sub-resources).

## Threat Flags

None — this plan only adds typed client-side contracts; no new trust boundaries beyond what 59-03's backend already exposes (see plan's own threat_model section, accepted dispositions).
