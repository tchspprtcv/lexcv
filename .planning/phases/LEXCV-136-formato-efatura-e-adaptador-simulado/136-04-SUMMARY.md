---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 04
subsystem: web-contracts
tags: [fiscal, efatura, web, rbac, polling, tdd]
requires: []
provides:
  - "EstadoComunicacaoFiscal = PENDENTE | ACEITE_SIMULADO | REJEITADO | ERRO; ComunicacaoFiscal on DocumentoFiscalDetalhe; EstadoEmissao.modoComunicacao; ReprocessarComunicacaoResposta"
  - "lib/comunicacao-fiscal: ESTADOS_COMUNICACAO, apresentacaoEstadoComunicacao (neutral 'Estado desconhecido'), descricaoEstadoComunicacao, intervaloAtualizacaoComunicacao, mostrarReprocessar, interpretarErroReprocessar, ROTULO_AMBIENTE, copy constants"
  - "hooks: PERMISSAO_REPROCESSAR_COMUNICACAO, podeReprocessarComunicacao (exact financeiro:edit), useReprocessarComunicacao, 15 s polling only while PENDENTE"
  - "COMUNICACAO_FISCAL_FALHOU registered on the web (label, red badge, non-silenceable)"
  - "FIRMA_MAX = 150"
affects: [136-08, 136-11, 136-09]
tech-stack:
  added: []
  patterns:
    - "React-free presentation lib: icons as string keys, components map them to lucide icons"
    - "TanStack Query v5 refetchInterval as a function of query.state.data, refetchIntervalInBackground false"
key-files:
  created:
    - web/src/lib/comunicacao-fiscal.ts
    - web/src/lib/comunicacao-fiscal.test.ts
  modified:
    - web/src/types/faturacao.ts
    - web/src/types/notificacoes.ts
    - web/src/lib/notificacao-categoria.ts
    - web/src/lib/notificacao-categoria.test.ts
    - web/src/hooks/use-faturacao.ts
    - web/src/schemas/faturacao.ts
    - web/src/schemas/faturacao.test.ts
decisions:
  - "Reprocess gate is exact hasPermission(financeiro:edit); a manage-only user does not see the button (checker clarification), pinned by a test against hasScopedPermission"
  - "COMUNICACAO_FISCAL_FALHOU is non-silenceable on the web, like the backend (research_resolutions supersede the UI-SPEC 'Silenciável yes')"
  - "Reprocess mutation suppresses toasts for 404/409/422/500/502/503/504 (handled inline); 401/403 keep apiFetch behaviour; onSettled invalidates documentos-fiscais and notificacoes"
metrics:
  duration: "~35 min (across two sessions)"
  completed: 2026-10-06
  tasks: 2
  files: 9
---

# Phase 136 Plan 04: Web contracts for fiscal communication Summary

The web side now has the types, permission gate, reprocess mutation, polling rule and presentation module needed for the Phase 136 screens. Components (136-08) and pages (136-11) can be built on top of these without adding new logic.

- The four communication states are typed, and the detail includes a `comunicacao` object. `EstadoEmissao` reports `modoComunicacao`. The literal `AUTORIZADO` appears nowhere.
- `lib/comunicacao-fiscal.ts` maps each state to its label, badge variant, icon key and long description, following the UI-SPEC. An unknown state, including `AUTORIZADO`, shows a neutral "Estado desconhecido". No state uses the accent or success style.
- `interpretarErroReprocessar` turns every backend error into fixed copy, so backend text is never shown.
- The reprocess button is gated on `financeiro:edit` exactly. A user with only `financeiro:manage` does not see it.
- `useReprocessarComunicacao` posts to `/documentos-fiscais/{id}/comunicacao/reprocessar`.
- The document detail and the list refresh every 15 s, but only while a visible state is `PENDENTE` and the tab is in the foreground.
- `COMUNICACAO_FISCAL_FALHOU` shows as "Falha de comunicação fiscal" with a red badge and cannot be muted.
- The firma field is capped at 150 characters.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | Types, lib/comunicacao-fiscal, notification category registrations | 5244322 (RED), 2e6f47d (GREEN) |
| 2 | Exact reprocess gate, reprocess mutation, PENDENTE-only polling, firma 150 | 3150537 (RED), 64a370e (GREEN) |

## Verification

- Full vitest run: 14 files, 318 tests, all green.
- `tsc --noEmit` is clean.
- `pnpm lint` reports 0 errors. Its 20 warnings were already there.
- `verify:faturacao` and `verify:documentos-fiscais` pass.
- **Acceptance greps in `use-faturacao.ts`:**
  - `PERMISSAO_REPROCESSAR_COMUNICACAO = "financeiro:edit"` appears once.
  - `hasPermission(permissions, PERMISSAO_REPROCESSAR_COMUNICACAO)` appears once.
  - `comunicacao/reprocessar` appears once.
  - `/api/v1` appears 0 times.
  - `refetchIntervalInBackground: false` appears twice.
- **Acceptance grep in `schemas/faturacao.ts`:** `FIRMA_MAX = 150` appears once.

## Deviations from Plan

None. The plan was executed as written. Two comments in `use-faturacao.ts` were reworded so that the path literal `comunicacao/reprocessar` appears only once, as the acceptance grep requires.

## TDD Gate Compliance

- **Task 1:** RED 5244322, then GREEN 2e6f47d.
- **Task 2:** RED 3150537. Three tests failed: the gate function was missing and firma 151 was still accepted. GREEN 64a370e.

## Notes for downstream plans

- **136-08:** map the icon keys `Clock`, `Info`, `CircleSlash` and `TriangleAlert` to lucide icons. Use `mostrarReprocessar(podeReprocessarComunicacao(perms), comunicacao, modoComunicacao)` to decide whether to show the button.
- **136-09:** the backend firma limit (`@Size` and the column) must become 150 to match.

## Threat Flags

None. T-136-12 (exact gate), T-136-13 (no AUTORIZADO, neutral fallback), T-136-14 (PENDENTE-only foreground polling) and T-136-15 (fixed error copy) are mitigated as planned.

## Self-Check: PASSED

- FOUND: web/src/lib/comunicacao-fiscal.ts, web/src/lib/comunicacao-fiscal.test.ts
- FOUND: commits 5244322, 2e6f47d, 3150537, 64a370e
