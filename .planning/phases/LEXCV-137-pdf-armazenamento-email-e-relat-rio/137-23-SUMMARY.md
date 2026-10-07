---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 23
subsystem: web/verification-gates
tags: [static-analysis, verification-gate, node-script, security-assertions, rbac-assertions, forbidden-wording]
requires: [137-21, 137-22]
provides:
  - verify-entrega-fiscal.mjs (static assertion runner validating UI contract, gates, copy and safety)
  - pnpm verify:entrega-fiscal script in package.json
affects: [137-24]
tech-stack:
  added: []
  patterns: [zero-dependency Node static analysis, exact substring checks, seeded failure test via scratch directory]
key-files:
  created:
    - web/scripts/verify-entrega-fiscal.mjs
  modified:
    - web/package.json
    - web/src/app/(dashboard)/clientes/[id]/documentos-fiscais-tab.tsx
    - web/src/hooks/use-faturacao.ts
    - web/src/lib/entrega-email.ts
decisions:
  - "Static gate enforces exact financeiro:edit/view RBAC and zero fallback"
  - "Forbids deceptive delivery wording (Entregue, Recebido, Lido) and premature tax authority approval claims in all new Phase 137 files"
  - "Enforces that CSV generation is solely backend-driven with no client-side formula escaping"
metrics:
  duration: ~15min
  completed: 2026-10-07
  tasks: 2
  files: 5
---

# Phase 137 Plan 23: Static UI Verification Gate Summary

Implemented `verify-entrega-fiscal.mjs` and registered `verify:entrega-fiscal` in `package.json`.

## Key Deliverables

1. **`web/scripts/verify-entrega-fiscal.mjs`**:
   - Machine-checks exact RBAC gates (`PERMISSAO_REENVIAR_EMAIL = "financeiro:edit"` with `hasPermission`, `podeLerDocumentosFiscais`).
   - Verifies binding copy verbatim (6 badge states, 5 fixed descriptions, count-aware FALHOU description fragments, CTA labels, toasts, client-tab helper, settings helper, notification category).
   - Enforces visual neutrality on delivery badges (no accent or success green/amber classes).
   - Prohibits forbidden wording (`Entregue`, `Recebido`, `Lido`, `Autorizado`, `Aprovado`, `Validado pela DNRE`) in all files created in Phase 137.
   - Forbids `dangerouslySetInnerHTML`, common document mutations in the client tab, and client-side CSV synthesis in the monthly export dialog.
   - Checks notification category registration (`EMAIL_FISCAL_FALHOU`).

2. **`package.json` Script**:
   - Added `"verify:entrega-fiscal": "node scripts/verify-entrega-fiscal.mjs"`.

3. **Seeded Violation Test**:
   - Verified that seeding forbidden token `"Entregue"` in a copy of `entrega-email.ts` triggers immediate non-zero exit:
     ```
     verify:entrega-fiscal FALHOU (1):
       - ...\lib\entrega-email.ts: contem linguagem de confirmacao enganadora ou de autorizacao "Entregue"
     ```

## Verification

- `pnpm verify:entrega-fiscal`: **PASSED**.
- `pnpm verify:documentos-fiscais`: **PASSED**.
- `pnpm verify:faturacao`: **PASSED**.
- `pnpm test`: **353/353 tests passed**.
- Lockfiles unchanged.

## Self-Check: PASSED
