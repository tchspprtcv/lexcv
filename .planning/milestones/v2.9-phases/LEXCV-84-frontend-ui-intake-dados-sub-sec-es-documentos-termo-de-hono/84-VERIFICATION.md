---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
verified: 2026-07-08T02:30:00Z
status: human_needed
score: 10/10 must-haves verified (code-level); live browser walkthrough outstanding
re_verification: false
human_verification:
  - test: "Complete the intake wizard end-to-end: attempt to submit step 1 without selecting Origem (expect inline Zod error, no POST fired), then select an Origem, finish intake, formalize the processo, and open its ficha"
    expected: "Step 1 cannot be submitted with Origem left on the placeholder (inline red error under the select, no network call to /processos/intake); after formalização, the ficha's Dados card shows the chosen Origem read-only (no edit affordance anywhere)"
    why_human: "No executor in this phase (nor its two review rounds) drove the wizard through a real browser — all evidence is static (Zod schema binding, JSX structure). Deferred explicitly in 84-01-PLAN.md's <verification> section."
  - test: "Open /processos/{id}/editar for a processo that already has a juizo value, confirm the Juízo input pre-fills, edit it, save, then reload /processos/{id} and check the Dados card"
    expected: "Juízo input shows the existing value on load; after save, the ficha's read-only Juízo row reflects the new value"
    why_human: "Round-trip through a live TanStack Query mutation + refetch was never exercised in a browser; only the register/reset wiring was read statically. Deferred explicitly in 84-01-PLAN.md."
  - test: "Open /processos/{id}/termo-honorarios twice: once for a processo whose Honorário has valorTotal=null, once after setting a valorTotal. Also trigger the browser print preview (Ctrl+P / window.print())."
    expected: "First visit: Imprimir button is visually disabled, red message with a working 'Financeiro' link shows. Second visit: Imprimir is enabled, Valor Total renders as a formatted CVE currency string (e.g. '150 000,00 CVE'), not a raw number. Print preview hides the toolbar/back-link/aside via PRINT_CSS and paginates the four sections cleanly on A4."
    why_human: "window.print() rendering and @media print behavior cannot be verified by static analysis; disabled-button CSS/interaction and actual print pagination require a real browser. Deferred explicitly in 84-02-PLAN.md."
  - test: "On a processo's ficha, open Partes 'Adicionar Parte', type a name, click Cancelar, reopen the dialog; repeat for Fases 'Adicionar Fase'. Separately, on the Fases tab, change a row's status dropdown without touching another row's, then click that other row's 'Guardar'."
    expected: "Reopening either dialog after Cancelar shows a blank form, not the previously-typed text (WR-02 fix). Clicking 'Guardar' on an untouched row saves that row's current status unchanged, not an empty/undefined PUT body (CR-01 fix)."
    why_human: "Both are React-state-lifecycle bugs whose fixes were verified by reading the diff/source, not by clicking through the UI against a live backend. Deferred explicitly in 84-03-PLAN.md."
  - test: "Create a Decisão with an attached PDF file via the Decisões tab's 'Adicionar Decisão' dialog, then open the Documentos tab and confirm the same file appears there (via the shared documentoId link); separately, open 'Editar' on an existing Testemunha and confirm the dialog pre-fills its current values."
    expected: "The uploaded Decisão attachment shows up as a Documento in the Documentos tab without a separate upload step; the Testemunha edit dialog opens pre-populated with nome/tipo/contacto/notas matching the row clicked, not blank or another row's data."
    why_human: "Cross-entity linkage (documentoId) and dialog pre-population were verified by reading the mutation/hook wiring, not by observing the actual multipart upload response and the Documentos list refetch in a browser. Deferred explicitly in 84-04-PLAN.md."
  - test: "Reorder two Factos via the 'Editar Facto' dialog's Ordem field and confirm the table re-sorts; upload a document via the processo's Documentos tab and confirm it also appears on the existing generic /documentos list page (shared backend list)."
    expected: "After saving a new Ordem value, the Factos table row order updates to match; a document uploaded from the processo Documentos tab appears in the generic /documentos page with the correct processo association."
    why_human: "Client-side re-sort after a live mutation, and cross-page consistency with the shared /documentos list, both require a running app + backend to observe. Deferred explicitly in 84-05-PLAN.md."
  - test: "Log in as a user with processos:view but not processos:edit, and separately as a user with processos:edit but not documentos:edit; visit the ficha and every tab (Partes/Fases/Decisões/Factos/Testemunhas/Documentos)"
    expected: "The processos:view-only user sees all lists but no 'Adicionar'/'Editar'/'✕' controls anywhere, including the Fases status <select> (now disabled per WR-03); the processos:edit-without-documentos:edit user sees full CRUD on Partes/Fases/Decisões/Factos/Testemunhas but only a read-only Documentos list (no upload/delete)"
    why_human: "All permission gates were confirmed by grep/read (canEditProcessos / canEditDocumentos wrapping every mutating control), but no executor logged in as a restricted role to observe the rendered UI live."
---

# Phase LEXCV-84: Frontend — UI (Intake, Dados, Sub-secções, Documentos, Termo de Honorários) Verification Report

**Phase Goal:** O utilizador consegue registar e consultar Juízo/Origem, gerir Decisões/Factos/Testemunhas, aceder a uma aba de Documentos dedicada, e gerar o Termo de Honorários impresso — tudo a partir da ficha do processo.
**Verified:** 2026-07-08T02:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | O passo 1 do intake exige Origem (Petição Inicial \| Notificações Avulsas); depois de formalizado o campo é visível na ficha mas não editável (SC1) | ✓ VERIFIED | `processos/novo/page.tsx`: `intakeForm` bound to `zodResolver(processoIntakeFormSchema)` (required `.enum()`), `<select id="origem" {...intakeForm.register("origem")}>` with inline Zod error render. `processos/[id]/editar/page.tsx`: zero `origem` references (grep confirms). `processos/[id]/page.tsx:725-728`: Origem rendered read-only in Dados `dl`, no edit affordance anywhere. |
| 2 | Juízo é visível e editável no card Dados, ao lado de Tribunal/Área Jurídica (SC2) | ✓ VERIFIED | `processos/[id]/page.tsx:722-723`: read-only Juízo row directly after Tribunal. `processos/[id]/editar/page.tsx:72,91,208-216`: `juizo` in `defaultValues`, in the `form.reset()` load effect, and a registered `Input` with inline error — matches this codebase's existing pattern where the ficha is read-only `dl`/`dd` and `/editar` is the sole editable surface for every Processo field (Tribunal/Área Jurídica included), so this is architecturally consistent, not a deviation. |
| 3 | 4 novas abas (Decisões/Factos/Testemunhas/Documentos) no toggle group, cada uma com listar/criar/editar/remover (SC3) | ✓ VERIFIED | `TabKey` extended to 8 values; tab-button group has all 4 new buttons (line ~1220-1241); ternary chain fully wired at lines 1740 (`decisoes`), 1907 (`factos`), 2058 (`testemunhas`), 2235 (`documentos`) — no `null` placeholders remain (confirmed by reading the full ternary through line 2293). Each tab has Dialog Adicionar + per-row Editar (Decisões/Factos/Testemunhas) + delete-with-`window.confirm` (all 4 tabs); Documentos has upload/list/download/delete instead of edit (matches its different nature). |
| 4 | Nova rota `[id]/termo-honorarios` gera documento imprimível combinando Cliente+Processo+Honorário (SC4) | ✓ VERIFIED | `processos/[id]/termo-honorarios/page.tsx` (209 lines) exists: permission-gated (`AccessDeniedState` when `!canViewProcessos`), sources `useProcesso`/`useCliente`/`useHonorarios` with `isLoading`/`isError` as the OR of all three, renders 4 `SectionTitle`+`Field` groups (Identificação do Cliente, Identificação do Processo, Honorários, Data e Assinaturas) only after all three resolve. Route appears in `pnpm build` output (`ƒ /processos/[id]/termo-honorarios`). |
| 5 | Gerar o Termo bloqueia/avisa quando valorTotal é null, em vez de imprimir campos vazios (SC5) | ✓ VERIFIED | `termo-honorarios/page.tsx:77,92`: `isBlocked = !honorario \|\| honorario.valorTotal === null`; Imprimir `Button` has a real `disabled={isBlocked}` prop (not just CSS); `isBlocked && honorario` renders a red inline message with a working `Link` to `/financeiro/{honorario.id}`; `!honorario` (no Honorário at all) renders a distinct error message instead of a blank document; `fmtMoney` renders `___________` (not "0" or empty) for a null valorTotal. |
| 6 | Explicit scope extension: Partes/Fases refactored to the same Dialog "Adicionar" pattern (requested in 84-CONTEXT.md, not a ROADMAP SC) | ✓ VERIFIED | `lg:grid-cols-2` no longer appears anywhere in the file (grep, 0 matches); Partes/Fases both use `Dialog open={addXModal}` + `DialogTrigger asChild` + `DialogContent`, gated by `canEditProcessos`, matching the 4 new tabs' pattern exactly. |
| 7 | Round-1 code review findings (1 Critical + 4 Warnings) are actually fixed on disk, not just claimed in reports | ✓ VERIFIED | Independently re-read the current file (not the review report): CR-01 `onUpdateFaseStatus(faseId, currentStatus)` with `faseDraftStatus[faseId] ?? currentStatus` fallback (line 471-472) + call site `onUpdateFaseStatus(f.id, f.status)` (line 1726). WR-01: zero `mov(Form\|imentac)` matches except the unrelated Timeline `"movimentacao"` tipo-filter literal (a different feature, `useTimeline`); top-level gate is `processo/clientes/partes/fases` only (line 276-277), `movimentacoes` fully removed. WR-02: `onOpenAddParte`/`onOpenAddFase` (lines 429, 450) reset the form + server-error state and are wired to the trigger buttons' `onClick` (lines 1528, 1636). WR-03: Fases `<select>` has `disabled={!canEditProcessos}` (line 1714). WR-04: `onChange` clamps via `Math.max(1, Math.trunc(Number(e.target.value) \|\| 1))` (line 1968-1970). |
| 8 | Round-2 independent re-review (84-REVIEW-2.md) claims of zero new Critical/Warning are consistent with the code | ✓ VERIFIED | Cross-checked all 5 fix-by-fix claims in 84-REVIEW-2.md against the current file state myself (not just trusting the report) — all matched line-for-line (see Truth 7). Only remaining item is IN-01 (pre-existing unused `textareaClassName` constant, confirmed dead since before this phase per the review's own `git show` history check) — a pure Info-level nit, not a functional gap. |
| 9 | `tsc --noEmit` is clean for phase-84 code | ✓ VERIFIED | Ran independently: 3 errors, all `Cannot find module 'vitest'` in `*.test.ts` files unrelated to this phase (`use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`) — matches the documented pre-existing baseline exactly, zero errors in any phase-84 file. |
| 10 | `pnpm run build` succeeds, including the new termo-honorarios route | ✓ VERIFIED | Ran independently: "✓ Compiled successfully", all 23 routes generated, `/processos/[id]/termo-honorarios` present in the route table, zero build errors. |

**Score:** 10/10 truths verified at the code level. 0 failed. All live-browser-only behaviors are routed to human verification below (not treated as failures), consistent with every prior phase in this milestone (80/81/82) which also could not obtain live browser/authenticated-session access in this environment.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/processos/novo/page.tsx` | Origem field bound to `processoIntakeFormSchema` | ✓ VERIFIED | Required select + inline Zod error, imports switched from `processoFormSchema` |
| `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` | Editable Juízo input | ✓ VERIFIED | Registered, defaulted, reset from `processo.data.juizo`; zero `origem` refs |
| `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx` | New printable route, ≥150 lines | ✓ VERIFIED | 209 lines, 3-hook gate, 4 sections, currency-safe `fmtMoney`, hard print-block |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | TabKey extended, Dados card Juízo/Origem/Termo button, Partes/Fases→Dialog, 4 new tab bodies | ✓ VERIFIED | 2547 lines; all sub-elements confirmed individually above |
| `web/src/types/processos.ts` | `FactoUpdateRequest.ordem` stale docblock corrected | ✓ VERIFIED | No longer contains "no form collects this field yet"; now documents the Phase 84 editable mechanism |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `processos/novo/page.tsx` | `processoIntakeFormSchema` | `zodResolver` | ✓ WIRED | `useForm<ProcessoIntakeFormValues>({ resolver: zodResolver(processoIntakeFormSchema) })` |
| `processos/[id]/editar/page.tsx` | `ProcessoUpdateRequest.juizo` | `form.register("juizo")` + `satisfies` | ✓ WIRED | Round-trips through `useUpdateProcesso` |
| `processos/[id]/termo-honorarios/page.tsx` | `useProcesso`/`useCliente`/`useHonorarios` | combined `isLoading`/`isError` gate | ✓ WIRED | All 3 hooks OR'd for both flags before rendering the document body |
| `processos/[id]/termo-honorarios/page.tsx` | `honorario.valorTotal === null` | `disabled={isBlocked}` on Imprimir | ✓ WIRED | Real `disabled` prop, plus distinct "no honorário" vs "null valorTotal" messaging |
| `processos/[id]/page.tsx` | `origemProcessoToLabel` | Dados card `dd` | ✓ WIRED | Used exactly once, read-only |
| `processos/[id]/page.tsx` | `/processos/{id}/termo-honorarios` | `Link` gated on `estado === "ATIVO"` | ✓ WIRED | `target="_blank"`, `rel="noopener noreferrer"` |
| `processos/[id]/page.tsx` | `useAddDecisao` | multipart `FormData` w/ optional file | ✓ WIRED | Confirmed by both review rounds and hook contract read |
| `processos/[id]/page.tsx` | `useUpdateTestemunha` | PUT JSON, pre-filled Editar dialog | ✓ WIRED | `onOpenEditTestemunha` resets form with row data before opening |
| `processos/[id]/page.tsx` | `useUpdateFacto` | PUT JSON, `ordem` from local state (not Zod form) | ✓ WIRED | `factoOrdemDraft`, clamped positive integer, merged at submit only |
| `processos/[id]/page.tsx` | `useUploadDocumentoComProgresso` | `DocumentoUploadPayload` w/ `processo_id` | ✓ WIRED | Confirmed `processo_id: processoId` in `onConfirmarUpload` |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PROC-13 | 84-05 | Aba "Documentos" dedicada (upload/listagem/download/remoção) | ✓ SATISFIED | `ProcessoDocumentosTab`/`ProcessoDocumentoRow`, scoped by `processo_id`, gated by `documentos:edit` |
| PROC-15 | 84-02, 84-03 | Ação para gerar/imprimir Termo de Honorários combinando Cliente+Processo+Honorário | ✓ SATISFIED | `termo-honorarios/page.tsx` + Dados card "Gerar Termo de Honorários" button (ATIVO-gated) |
| PROC-16 | 84-02 | Bloqueia/avisa quando valorTotal está em branco | ✓ SATISFIED | Hard `disabled` block + distinct messaging for null-valorTotal vs. no-Honorário |

**Additional requirement IDs declared across this phase's plans (frontend UI completion of earlier-phase backend work, per REQUIREMENTS.md's own note that Phase 83/84 are integration phases building on Phase 80/81):** PROC-01, PROC-02, PROC-03, PROC-04, PROC-05 (84-01/84-03 — Origem/Juízo UI), PROC-08 (84-04 — Decisões UI), PROC-10 (84-05 — Factos UI), PROC-12 (84-04 — Testemunhas UI). REQUIREMENTS.md's traceability table attributes these to Phases 80/81 (their backend/first-implementation phase); this phase's plans re-declare them because the frontend surface for each was completed here. No orphaned requirements found — REQUIREMENTS.md's Phase 84 row (PROC-13, PROC-15, PROC-16) is fully covered by this phase's plans, and every requirement ID appearing in any 84-0X-PLAN.md frontmatter exists in REQUIREMENTS.md.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `processos/[id]/page.tsx` | 141 | Unused `textareaClassName` constant (pre-existing, predates Phase 84, confirmed via `git show` history in 84-REVIEW-2.md) | ℹ️ Info | Dead code, zero functional impact; independently confirmed not touched by this phase's commits |

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any phase-84-modified file (grep across `processos/[id]/page.tsx`, `processos/novo/page.tsx`, `processos/[id]/editar/page.tsx`, `processos/[id]/termo-honorarios/page.tsx`, `types/processos.ts` — zero matches).

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TypeScript compiles cleanly for phase-84 code | `cd web && npx tsc --noEmit` | 3 pre-existing unrelated `vitest`-module errors only | ✓ PASS |
| Production build compiles all routes incl. new print route | `cd web && pnpm run build` | "✓ Compiled successfully", 23/23 routes, `/processos/[id]/termo-honorarios` present | ✓ PASS |
| No dangling Movimentação references after WR-01 removal | `grep -n -iE "mov(Form\|imentac)"` on `processos/[id]/page.tsx` | Only unrelated Timeline `"movimentacao"` tipo-filter literal and Portuguese UI strings | ✓ PASS |
| No `lg:grid-cols-2` remains anywhere in the file | `grep -n "lg:grid-cols-2"` | 0 matches | ✓ PASS |
| All 4 delete confirmations present | `grep -n "window.confirm"` | 4 matches (decisão/testemunha/facto/documento) | ✓ PASS |

Live-server/browser behaviors (print rendering, dialog reset UX, RBAC-gated rendering, upload progress, live reorder) could not be spot-checked without a running dev server + authenticated session — routed to Human Verification below, consistent with phases 80/81/82 in this milestone.

### Probe Execution

No `scripts/*/tests/probe-*.sh` probes exist for this phase (frontend UI phase, not a migration/tooling phase) — SKIPPED, no runnable probes declared or found.

### Human Verification Required

See `human_verification` in the frontmatter (7 items) — covering the intake wizard's blocking validation, the Juízo edit round-trip, the Termo de Honorários print flow (button disabled state + `@media print` rendering), the Partes/Fases dialog-reset and Fases-Guardar fixes, the Decisão-file→Documentos-tab cross-link and Testemunha edit pre-fill, the Factos live-reorder and cross-page Documentos consistency, and RBAC-gated rendering for restricted roles. None of these could be exercised against a running dev server + authenticated session in this environment, matching the constraint documented in every prior phase of this milestone (80/81/82 all produced `HUMAN-UAT.md` for the same reason).

### Gaps Summary

No code-level gaps found. All 5 ROADMAP success criteria, the explicitly-requested Partes/Fases Dialog-pattern scope extension, and all 3 phase-owned requirements (PROC-13/15/16) are implemented and wired correctly on disk — independently re-verified, not merely trusted from SUMMARY.md/REVIEW.md claims. Both rounds of code review (1 Critical + 4 Warnings found and fixed; round-2 re-review found zero new Critical/Warning) were independently spot-checked against the current file state and confirmed accurate. `tsc --noEmit` and `pnpm run build` were both re-run in this verification pass and are clean. The only open items are live-browser behaviors that no executor in this milestone has been able to exercise (no running dev server / authenticated session available in this environment) — these are routed to human verification, not treated as failures, per this milestone's established pattern.

---

_Verified: 2026-07-08T02:30:00Z_
_Verifier: Claude (gsd-verifier)_
