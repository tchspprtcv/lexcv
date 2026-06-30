---
phase: "59"
plan: "06"
subsystem: frontend-cliente-edit
tags: [clientes, intake, procuracao, react-hook-form, dialog]
dependency-graph:
  requires: ["59-04"]
  provides: ["Cliente edit page intake UI (descricaoCaso, honorariosPropostos, list sections)"]
  affects: ["web/src/app/(dashboard)/clientes/[id]/editar/page.tsx", "web/src/types/clientes.ts", "web/src/schemas/clientes.ts"]
tech-stack:
  added: []
  patterns: ["useState-managed JSON list arrays synced into react-hook-form PUT payload on submit", "Radix Dialog add-item modal with 2-3 inputs per Risk 6 (RESEARCH.md)"]
key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
    - web/src/types/clientes.ts
    - web/src/schemas/clientes.ts
decisions:
  - "Worktree branched before 59-04 merged its types/clientes.ts and schemas/clientes.ts changes — re-applied the same DocumentoEntregue/DocumentoATratar/Deslocacao/HonorariosPropostos fields here (Rule 3: blocking issue, missing referenced types) rather than waiting, since 59-04 is already merged on main per its SUMMARY."
  - "pnpm build could not run — no node_modules in this isolated worktree. Verified via grep-based acceptance criteria checks per all 7 criteria in the plan instead."
metrics:
  duration: "~25m"
  completed: "2026-06-30"
---

# Phase 59 Plan 06: Cliente Edit Page Intake Fields Summary

Extended `clientes/[id]/editar/page.tsx` with a "Intake do Caso" section: a `descricaoCaso` textarea, an inline Honorários Propostos form, and three add-via-modal/remove list sections (Documentos Entregues, Documentos a Tratar, Deslocações) — all submitted together via the existing `useUpdateCliente` PUT mutation.

## What Was Built

### Task 1: descricaoCaso field + honorariosPropostos inline form (commit a5f570d, combined with Task 2)
- Added `descricao_caso` and `honorarios_propostos` to form `defaultValues` and the `form.reset()` effect (mapped from `cliente.data`).
- New "Intake do Caso" section (`<hr/>`-separated, matching existing page pattern) with a `<textarea>` labeled "Descrição do Caso" registered via `form.register("descricao_caso")`.
- Inline "Honorários Propostos" sub-section (bordered box matching the existing `dados_tipo` card style) with three fields: Total (`type="number" step="0.01"`, `valueAsNumber: true`), Valor por Extenso (text), Previsão (text).
- `onSubmit` payload includes `descricaoCaso: values.descricao_caso || undefined` and `honorariosPropostos: values.honorarios_propostos` (camelCase keys matching backend Jackson serialization, per plan instructions).

### Task 2: Three JSON list sections with add-modal and remove (commit a5f570d, combined with Task 1)
- Added `useState` for `documentosEntregues`, `documentosATratar`, `deslocacoes` arrays, initialized from `cliente.data` in the existing reset effect.
- Each list has its own modal-open state and "new item" draft state (`newDocEntre`, `newDocATratar`, `newDeslocacao`), kept separate from react-hook-form per the plan's design (modal forms are simple, 2-3 inputs).
- Three `Dialog` sections rendered: "Documentos Entregues" (descrição + data inputs), "Documentos a Tratar" (descrição only), "Deslocações" (descrição + local + data). Each has an "Adicionar" `DialogTrigger` button and list rows with an "✕" remove button calling `setX(prev => prev.filter((_, i) => i !== index))`.
- `onSubmit` payload includes `documentosEntregues`, `documentosATratar`, `deslocacoes` (camelCase, matching backend expectations).

### Prerequisite fix (commit b131e39)
This worktree's base branch predates the 59-04 plan's merge of intake types into `web/src/types/clientes.ts` and `web/src/schemas/clientes.ts` (the worktree had the pre-Phase-57/59 minimal `Cliente`/`ClienteUpdateRequest` shapes, no `DocumentoEntregue`/`DocumentoATratar`/`Deslocacao`/`HonorariosPropostos`). Re-applied the same field additions documented in `59-04-SUMMARY.md`:
- `types/clientes.ts`: added the four interfaces, extended `Cliente` with `descricao_caso`/`documentos_entregues`/`documentos_a_tratar`/`deslocacoes`/`honorarios_propostos`, extended `ClienteUpdateRequest` with both snake_case (`descricao_caso`) and camelCase (`descricaoCaso`, `documentosEntregues`, `documentosATratar`, `deslocacoes`, `honorariosPropostos`) variants so the page's payload (which sends camelCase per backend convention) type-checks.
- `schemas/clientes.ts`: added `descricao_caso` (optional trimmed string) and `honorarios_propostos` (optional object: total/totalPorExtenso/previsao) to `clienteFormSchema`.

This is classified as Rule 3 (auto-fix blocking issue — missing referenced types/schema fields needed to complete the task), not an architectural change, since it mirrors fields already merged to main by 59-04 and does not introduce new design decisions.

## Verification

- `pnpm build` could not run in this worktree — `node_modules` is absent (pnpm workspace did not propagate to the isolated git worktree). This matches the documented limitation in 59-04's SUMMARY for the same reason.
- Verified via grep against the plan's acceptance criteria, all passing:
  - `"Descrição do Caso"` label: 1 match
  - `"honorarios_propostos.totalPorExtenso"` field registration: 3 matches (label htmlFor, input id, register call)
  - `"Honorários Propostos"` heading: 1 match
  - `descricaoCaso:` in submit payload: 1 match
  - `"Documentos Entregues"` heading: 2 matches (section heading + dialog title)
  - `"Documentos a Tratar"` heading: 2 matches
  - `"Deslocações"` heading: 2 matches
  - Three `<Dialog open=` components: 3 matches
  - `documentosEntregues:` in submit payload: 1 match

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking issue] Worktree missing 59-04's type/schema additions**
- Found during: Task 1 (read_first step on `types/clientes.ts`/`schemas/clientes.ts`)
- Issue: This worktree's base commit predates 59-04's merge of `DocumentoEntregue`/`DocumentoATratar`/`Deslocacao`/`HonorariosPropostos` types and `descricao_caso`/`honorarios_propostos` schema fields, even though 59-04-SUMMARY.md documents them as already complete on main.
- Fix: Re-applied the identical field additions to this worktree's copies of `types/clientes.ts` and `schemas/clientes.ts`.
- Files modified: `web/src/types/clientes.ts`, `web/src/schemas/clientes.ts`
- Commit: b131e39

## Known Stubs

None — all fields wire directly into the existing `useUpdateCliente` PUT mutation; no placeholder/mock data introduced.

## Threat Flags

None — no new trust boundaries beyond what 59-06's own threat_model already covers (T-59-11 descricao_caso XSS — accepted, React auto-escapes; T-59-12 honorariosPropostos.total type coercion — mitigated via `valueAsNumber`).

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/[id]/editar/page.tsx (confirmed via Write tool success)
- FOUND: web/src/types/clientes.ts (confirmed via Edit tool success)
- FOUND: web/src/schemas/clientes.ts (confirmed via Edit tool success)
- FOUND: commit b131e39 (git log)
- FOUND: commit a5f570d (git log)
