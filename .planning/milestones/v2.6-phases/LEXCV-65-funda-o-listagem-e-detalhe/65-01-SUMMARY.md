---
phase: 65-funda-o-listagem-e-detalhe
plan: 01
subsystem: frontend (pareceres data foundation)
tags: [pareceres, tanstack-query, types, zod, nav]
requires: []
provides:
  - "web/src/types/pareceres.ts (ParecerStatus, ParecerPrioridade, ParecerSolicitacao, ParecerVersao)"
  - "web/src/schemas/pareceres.ts (parecerStatusSchema, parecerPrioridadeSchema)"
  - "web/src/hooks/use-pareceres.ts (usePareceres, useParecer, useParecerVersoes, useDownloadParecerAnexo)"
  - "Pareceres sidebar nav item gated by pareceres:view"
affects:
  - "Phases 66-69 (all depend on these types/hooks)"
tech-stack:
  added: []
  patterns:
    - "Pure camelCase 1:1 type mapping (no snake_case bridge, no normalize* wrapper) — matches use-documentos.ts convention, not use-processos.ts's ProcessoApi bridge"
key-files:
  created:
    - web/src/types/pareceres.ts
    - web/src/schemas/pareceres.ts
    - web/src/hooks/use-pareceres.ts
  modified:
    - web/src/components/shared/dashboard-shell.tsx
decisions:
  - "Read-only phase: only enum Zod schemas exported (parecerStatusSchema, parecerPrioridadeSchema) — full create/update schemas deferred to Phases 66-67"
  - "No dual-field (camelCase/snake_case) type bridge — backend entities have zero @JsonProperty overrides, so Jackson emits exact camelCase"
metrics:
  duration: "~15 minutes"
  completed: 2026-07-01
---

# Phase 65 Plan 01: Fundação de Dados para Parecer Jurídico Summary

Pure-camelCase TypeScript types, Zod enum schemas, and four TanStack Query hooks for the Parecer Jurídico module, plus a permission-gated "Pareceres" sidebar nav item — the read-only data foundation all later phases (66-69) build on.

## What Was Built

### Task 1: Types + Zod schema stubs
- `web/src/types/pareceres.ts`: `ParecerStatus`, `ParecerPrioridade` unions; `ParecerSolicitacao` and `ParecerVersao` interfaces, fields exactly matching the Java entities (`ParecerSolicitacao.java`, `ParecerVersao.java`) field-for-field, pure camelCase, no snake_case alternate keys, no normalization bridge.
- `web/src/schemas/pareceres.ts`: `parecerStatusSchema` (4-value enum) and `parecerPrioridadeSchema` (3-value enum). Full create/update schemas deferred to Phases 66-67 per the read-only scope of this phase.

### Task 2: TanStack Query hooks
- `web/src/hooks/use-pareceres.ts` exports:
  - `usePareceres(filters)` — list, `GET /pareceres/solicitacoes` with clienteId/advogadoId/status query params, queryKey `["pareceres","list",...]`, staleTime 30s
  - `useParecer(id)` — detail, `GET /pareceres/solicitacoes/{id}`, queryKey `["pareceres","detail",id]`, staleTime 30s
  - `useParecerVersoes(solicitacaoId)` — `GET /pareceres/solicitacoes/{id}/versoes`, queryKey `["pareceres","versoes",id]`, staleTime 15s
  - `useDownloadParecerAnexo(solicitacaoId, versaoId)` — presigned-URL mutation, mirrors `useDownloadDocumento` shape verbatim
- All paths passed without `/api/v1` prefix (apiFetch prepends it), no normalization wrapper.

### Task 3: Sidebar nav item
- Added `ScrollText` to the lucide-react import and appended `{ href: "/pareceres", label: "Pareceres", icon: ScrollText, requiredPermission: "pareceres:view" }` to the `NAV` array in `dashboard-shell.tsx`. Existing `NAV.filter((item) => hasPermission(...))` at both the desktop aside and mobile Sheet render sites gates visibility — no render-logic changes needed. `bottom-nav.tsx` untouched (still 5 fixed items).

## Verification

- `pnpm exec tsc --noEmit` — clean, no errors referencing pareceres files or dashboard-shell.tsx
- `grep -rn "normalize" web/src/hooks/use-pareceres.ts web/src/types/pareceres.ts` — no matches
- `grep -n "_id" web/src/types/pareceres.ts` — no matches (no snake_case keys)
- `grep -c "pareceres:view" web/src/components/shared/dashboard-shell.tsx` — 1 match

## Deviations from Plan

None - plan executed exactly as written.

## Commits

- `aaeaf9e` — feat(65-01): add pareceres types and zod enum schemas
- `350c676` — feat(65-01): add TanStack Query hooks for pareceres
- `62137f0` — feat(65-01): add Pareceres nav item gated by pareceres:view

## Known Stubs

None — this plan produces no UI rendering; it's a pure data-layer + nav-item addition consumed by Phase 65 Plan 02 (list/detail pages) and beyond.

## Threat Flags

None — no new network surface introduced beyond what the plan's own threat model (T-65-01, T-65-02, T-65-SC) already accounts for.

## Self-Check: PASSED

- FOUND: web/src/types/pareceres.ts
- FOUND: web/src/schemas/pareceres.ts
- FOUND: web/src/hooks/use-pareceres.ts
- FOUND: web/src/components/shared/dashboard-shell.tsx (modified)
- FOUND: aaeaf9e in git log
- FOUND: 350c676 in git log
- FOUND: 62137f0 in git log
