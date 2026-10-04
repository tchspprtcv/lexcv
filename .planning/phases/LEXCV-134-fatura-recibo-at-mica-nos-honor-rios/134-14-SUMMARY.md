---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 14
subsystem: verification
tags: [fiscal, efatura, source-gate, e2e, playwright, testcontainers, validation]
requires:
  - "134-10: delete guards + merge re-pointing"
  - "134-12: billing-on payment form, preview dialog, Documento fiscal column"
  - "134-13: fiscal documents list and detail"
provides:
  - "web/scripts/verify-documentos-fiscais.mjs + pnpm verify:documentos-fiscais"
  - "134-VALIDATION.md signed off (nyquist_compliant: true)"
  - "134-HUMAN-UAT.md live E2E record (13/13 PASS + isento/meio supplementary)"
affects: [phase-134-verification, 135, 136]
tech-stack:
  added: []
  patterns:
    - "Literal-substring source gate over raw files (comments included), with negative self-checks"
key-files:
  created:
    - web/scripts/verify-documentos-fiscais.mjs
    - .planning/phases/LEXCV-134-fatura-recibo-at-mica-nos-honor-rios/134-HUMAN-UAT.md
  modified:
    - web/package.json
    - .planning/phases/LEXCV-134-fatura-recibo-at-mica-nos-honor-rios/134-VALIDATION.md
    - web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-dialog.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
    - .planning/REQUIREMENTS.md
decisions:
  - "verify:documentos-fiscais pins the exact read gate (podeLerDocumentosFiscais + PERMISSAO_LEITURA_FISCAL = financeiro:view) instead of can.view(\"financeiro\"), matching what 134-11/13 implemented"
  - "use-financeiro.ts invalidates documentos-fiscais through DOCUMENTOS_FISCAIS_KEY, so the gate checks that symbol instead of the literal path"
  - "The checkpoint:human-verify was auto-verified on a live stack (orchestrator instruction); all 13 steps PASS with evidence, so the plan closes without a human checkpoint"
  - "EMIS-01..12 marked complete only after the supplementary live run also covered the isento branch (EMIS-03) and the method -> eFatura meio codes (EMIS-10)"
metrics:
  duration: "~70 min"
  completed: 2026-10-04
  tasks: 2
  files: 9
---

# Phase 134 Plan 14: Phase gate, VALIDATION sign-off and live E2E Summary

Phase 134 is now closed by evidence:
- A new source gate, `pnpm verify:documentos-fiscais`, pins the fiscal UI invariants.
- Every backend and web gate is green, with all Testcontainers ITs actually executed.
- VALIDATION.md is signed off.
- The whole Fatura-Recibo flow was verified end to end on a real PostgreSQL + Spring Boot + Next dev stack using Playwright, curl and psql: 13/13 steps PASS, plus a supplementary isento / payment-means run.
- Two cosmetic UI defects found during the run were fixed.

## Task 1: Gate, full suite, VALIDATION

`web/scripts/verify-documentos-fiscais.mjs` is plain Node with literal checks on raw file text, comments included. It enforces five things:
- **(a) Gating.** The list page, detail page and Financeiro header use `podeLerDocumentosFiscais(permissions.permissions)`, the hook defines `PERMISSAO_LEITURA_FISCAL = "financeiro:view"` through `hasPermission`, and both pages render `<AccessDeniedState`. The header has the link and label.
- **(b) Immutability.** The detail page contains the immutability line, the simulation mark and the not-found copy. It contains none of `useMutation`, `method: "DELETE" | "PUT" | "PATCH" | "POST"`, `>Apagar`, `>Editar` or `Anular`. The list page and columns contain no `useMutation`.
- **(c) Copy.** The dialog, form and payments-card strings are present, including the key reuse `chaveIdempotencia: chave`. The billing-off branch still has "Adicionar", "Pagamento registado com sucesso." and `pagamentoFormSchema`.
- **(d) Frontend burro.** In the form, dialog, columns, detail, list and payments card there is no `0.15`/`0.20`, no `1.15`, no `* 0.` and no `/ 100`. The schema has no rate literal.
- **(e) Hooks.** The endpoints and `semToastParaStatus` are present and `/api/v1` is absent. `use-financeiro` contains `semToastParaStatus: [409, 422]` and `DOCUMENTOS_FISCAIS_KEY`. `getRandomValues` is in `idempotencia.ts`.

**Negative self-checks:**
- Appending `// useMutation` to the detail page made the gate exit 1 with `contem token proibido "useMutation"`.
- Appending `// x * 0.15` to the dialog made it exit 1 with two failures (rate constant, fraction multiply).
- Both files were restored afterwards and the gate went back to OK.

`web/package.json` gains `"verify:documentos-fiscais": "node scripts/verify-documentos-fiscais.mjs"`. `package-lock.json` is untouched.

**Full gate:**

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **800**, 0 failures, 0 errors, 0 skipped. Failsafe **94**, 0 / 0 / 0. |
| Phase 134 ITs (Testcontainers, executed) | MigracaoFiscal134IT 6, DocumentoFiscalRepositoryIT 13, PagamentoFaturadoServiceIT 10, PagamentoFaturadoConcorrenciaIT 4, GuardasDocumentoFiscalConcorrenciaIT 6, all with 0 skipped |
| `mvn -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 |
| web `vitest run` / `tsc --noEmit` / `lint` | 157/157 / clean / 0 errors (20 pre-existing warnings) |
| `verify:faturacao` / `verify:documentos-fiscais` | OK / OK |
| `pnpm build` | success; `/financeiro/documentos-fiscais` (static) and `/[id]` (dynamic) listed |

**134-VALIDATION.md:**
- Every row's "File Exists?" cell is now ✅ with the real test class (all of them were confirmed on disk).
- The Wave 0 and Sign-Off checklists are ticked.
- Frontmatter is `status: approved`, `nyquist_compliant: true`, `wave_0_complete: true`, with "Approval: approved (automated)" and the gate counts.

## Task 2: Live E2E (checkpoint auto-verified)

The full record with per-step evidence is in `134-HUMAN-UAT.md`. Highlights:
- **Step 2, billing off:** unchanged. The button reads "Adicionar", no dialog opens, the legacy toast appears, rows show "Sem documento fiscal", and 0 documents are written.
- **Step 4, preview:** the figures are exact: Base 104 347$83, IVA (15%) 15 652$17, Retenção (20%) - 20 869$57, Total 120 000$00, Líquido 99 130$43 (pt-CV CVE format). The preview wrote nothing (0 documents, series counter unchanged), and focus lands on the dialog title.
- **Step 5, emission:** the toast reads "Pagamento registado e fatura-recibo SIM-FR-2026/1 emitida.". The row links to the document and has no delete button. Conta corrente +120 000.
- **Step 6, idempotency:** 201 then 200 with the same pagamento id 5 and the same `documentoFiscal.id`. The same key with a different value → 409 `CHAVE_REUTILIZADA`. Only one extra document was created.
- **Step 7, refusals:** a retroactive date shows the under-field error; an invalid client NIF shows the banner with "Abrir cliente". Extra check: a network abort keeps the dialog open and the retry reuses the same key; reopening generates a new key.
- **Step 8, guards:** 409 `PAGAMENTO_FATURADO`. The honorário 409 appears inline with "Fechar" and no toast. The cliente 409 appears as a toast and the cliente remains.
- **Step 9, list:** filters, URL state, page reset on filter change, server pagination, and the empty and period-error states all behave as specified.
- **Step 10, detail:** shows the snapshot, values and links. A later cliente edit does not change the snapshot. Unknown and non-UUID ids show "não encontrado".
- **Step 11, merge:** the document's `cliente_id` moves to the primary and the buyer snapshot is unchanged.
- **Step 12, access:** the assistente gets 403 and the access-denied UI. Tenant B gets an empty list and 404 for tenant A's ids.
- **Step 13, cleanup:** done; see below.
- **Supplementary, EMIS-03:** an ISENTO office (motivo 5) shows "Isento" and "5 — Outras isenções", with base = total and IVA 0.
- **Supplementary, EMIS-10:** DINHEIRO 10, TRANSFERENCIA 30, CHEQUE 20, CARTAO 48 and OUTRO ZZZ are snapshotted; an unknown or missing método → 422 `METODO_PAGAMENTO_INVALIDO`.

**Environment workarounds (no product change):**
- The pre-existing `Set.of` bug (133 deferred-items #1) breaks the setup wizard. I seeded the demo tenant the same way 133-08 did.
- The same bug breaks `POST /platform/tenants`, so I created the second tenant for step 12 with SQL.
- Everything was stopped afterwards and both env files deleted. `git status --porcelain` is clean and no container remains.

**Requirements:** EMIS-01..12 are marked complete in REQUIREMENTS.md, both the checkboxes and the traceability rows. The automated tests and the live run both cover each of them.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | fa2b612 | verify:documentos-fiscais gate + package.json script + VALIDATION sign-off |
| 2 (fix) | e6d5e2d | Preview dialog: remove doubled vertical spacing |
| 2 (fix) | 63d99eb | Fiscal document pages: header wraps on small screens |
| 2 | 682142e | 134-HUMAN-UAT.md live E2E record |

## Deviations from Plan

**1. [Rule 2 - Correctness] Gate (a) checks the exact read gate, not `can.view("financeiro")`**
- **Issue:** Plans 11/13 use `podeLerDocumentosFiscais` (exact `financeiro:view`, as the orchestrator required). A `can.view` check would assert the wrong, weaker gate.
- **Fix:** The gate asserts the helper call, the constant and the `AccessDeniedState`.
- **Commit:** fa2b612

**2. [Rule 3] Gate (e) checks `DOCUMENTOS_FISCAIS_KEY` in use-financeiro.ts**
- **Issue:** The hook invalidates documentos-fiscais through the shared key constant, so the plan's literal `"documentos-fiscais"` string does not appear in that file.
- **Commit:** fa2b612

**3. [Rule 1 - Bug] Preview dialog spacing doubled (found live, step 4 screenshot)**
- **Issue:** The 134-12 plan asked for `space-y-4` on `DialogContent`, but that primitive is already `grid gap-4`, so the blocks sat about 32px apart.
- **Fix:** Removed the class. Re-measured gaps are 16px.
- **Commit:** e6d5e2d

**4. [Rule 1 - Bug] Breadcrumb squeezed at 375px (found live, mobile/dark check)**
- **Fix:** Headers use `flex-wrap`. Re-checked by screenshot; 0px overflow.
- **Commit:** 63d99eb

**5. Extra verification beyond the plan:** the network-retry key reuse (D-10), the isento branch, and per-method meio codes. Added so that every EMIS requirement was observed live before being marked complete.

## Observations (follow-up candidates, not in scope)

- The legacy "Método" column of the honorário payments table shows the stored enum name (for example `TRANSFERENCIA`) for billing-on payments. The document detail page shows the label.
- `SetupService` `Set.of` bug: the setup wizard and platform tenant provisioning both 500 (pre-existing, 133 deferred-items #1).

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: web/scripts/verify-documentos-fiscais.mjs, 134-HUMAN-UAT.md, 134-VALIDATION.md (nyquist_compliant: true)
- FOUND: fa2b612, e6d5e2d, 63d99eb, 682142e
