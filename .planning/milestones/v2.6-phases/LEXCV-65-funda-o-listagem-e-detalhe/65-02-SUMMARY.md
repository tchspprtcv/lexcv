---
phase: 65-funda-o-listagem-e-detalhe
plan: 02
subsystem: frontend (pareceres UI surface)
tags: [pareceres, list-page, detail-page, dual-view, rbac]
requires:
  - "web/src/types/pareceres.ts (Plan 01)"
  - "web/src/hooks/use-pareceres.ts (Plan 01)"
provides:
  - "web/src/app/(dashboard)/pareceres/page.tsx — list page (dual-view, filters, status badges)"
  - "web/src/app/(dashboard)/pareceres/[id]/page.tsx — detail page (metadata + version timeline)"
affects:
  - "Phases 66-68 (create/version/entrega actions will extend these pages)"
tech-stack:
  added: []
  patterns:
    - "Dual-view table/card fork ported from clientes/page.tsx"
    - "Draft-state + committed-filters split ported from processos/page.tsx"
    - "Timeline dot+connector visual language ported from processos/[id]/page.tsx, single fixed dot style"
key-files:
  created:
    - web/src/app/(dashboard)/pareceres/page.tsx
    - web/src/app/(dashboard)/pareceres/[id]/page.tsx
  modified: []
decisions:
  - "Advogado filter select scoped client-side to users whose roles include ADVOGADO, via useAdminUsers()"
  - "Cliente name resolution reuses the same useClientes({}) call already fetched for the filter select (no duplicate request)"
  - "Error branch on detail page renders no metadata/timeline blocks — satisfies T-65-03 IDOR mitigation for foreign-tenant ids"
  - "No create/entregar/edit action buttons present — phase is read-only per CONTEXT.md"
metrics:
  duration: "~25 minutes"
  completed: 2026-07-01
---

# Phase 65 Plan 02: Fundação — Listagem e Detalhe (UI) Summary

Read-only `/pareceres` list page (dual-view table/cards, status badges, status/advogado/cliente filters) and `/pareceres/[id]` detail page (metadata card + immutable, actor-attributed version timeline with anexo download), both gated behind `pareceres:view`, consuming the Plan 01 types/hooks.

## What Was Built

### Task 1: List page `/pareceres`
- `web/src/app/(dashboard)/pareceres/page.tsx`: permission-gated (`can.view("pareceres")` → `AccessDeniedState`), H1 "Pareceres Jurídicos", no CTA (Phase 66 scope).
- Filter bar (Card + form, draft/committed split): status select (4 values), advogado select (from `useAdminUsers()` filtered to `roles.includes("ADVOGADO")`), cliente select (from `useClientes({})`).
- Dual-view results: `md:hidden` stacked cards + `hidden md:block` table, both linking to `/pareceres/${id}`.
- Status badge variant ternary: `PENDENTE→gray`, `EM_ELABORACAO→blue`, `EM_REVISAO→amber`, `CONCLUIDO→green`.
- Cliente name resolved via a `clienteNomeById` map built from the same `useClientes({})` result used for the filter select; falls back to raw id if unresolved.
- Loading/error/empty states with the exact UI-SPEC Portuguese copy.

### Task 2: Detail page `/pareceres/[id]`
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx`: `React.use(params)`, permission-gated with `AccessDeniedState backHref="/pareceres"`.
- Metadata Card (`dl`/`dd` grid `grid-cols-3`): cliente, advogado (resolved via `userNomeById` map from `useAdminUsers()`, "—" if null), status badge, prioridade, prazo (`formatDate`), createdAt (`formatDateTime`).
- Version timeline as the dominant content block: dot+connector visual (single fixed blue dot, no type-branching since all entries are the same type), each entry shows `numeroVersao`, resolved author, `createdAt`, `conteudo`, and an `AnexoLink` sub-component wired to `useDownloadParecerAnexo(id, versao.id)` consumed via `mutateAsync()` + `window.open(r.url)`.
- Empty-versões state: "Nenhuma versão ainda" / "Aguarda elaboração pelo advogado atribuído."
- Error branch (`parecer.isError`) renders neither metadata nor timeline — no partial/leaked data on a foreign-tenant 404.
- No create/entregar/edit buttons anywhere in the file (read-only phase).

## Verification

- `pnpm exec tsc --noEmit` — no errors referencing `pareceres/page` or `pareceres/[id]/page`
- `grep "hidden md:block\|md:hidden"` in list page — both present (dual-view fork confirmed)
- `grep "can.view(\"pareceres\")\|usePareceres\|useParecerVersoes"` — all present in the expected files

## Deviations from Plan

None - plan executed exactly as written.

## Commits

- `1b3bfea` — feat(65-02): add pareceres list page with dual-view and filters
- `bebeed2` — feat(65-02): add pareceres detail page with metadata and version timeline

## Known Stubs

None — both pages are fully wired to Plan 01 hooks; no hardcoded empty/mock data.

## Threat Flags

None — no new network surface introduced beyond what the plan's own threat model (T-65-03 through T-65-06, T-65-SC) already accounts for. IDOR mitigation (T-65-03) explicitly implemented: error branch on detail page renders no data blocks.

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/pareceres/page.tsx
- FOUND: web/src/app/(dashboard)/pareceres/[id]/page.tsx
- FOUND: 1b3bfea in git log
- FOUND: bebeed2 in git log
