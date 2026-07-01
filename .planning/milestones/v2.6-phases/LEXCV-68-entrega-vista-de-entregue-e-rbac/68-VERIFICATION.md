---
phase: 68-entrega-vista-de-entregue-e-rbac
verified: 2026-07-01T00:00:00Z
status: human_needed
score: 6/6 must-haves verified (static analysis)
overrides_applied: 0
---

# Phase 68: Entrega, Vista de Entregue e RBAC Verification Report

**Phase Goal:** Utilizador autorizado consegue concluir o ciclo do parecer marcando uma versão como entrega final — com clareza total sobre a irreversibilidade — e qualquer pessoa autorizada consegue depois consultar o parecer entregue como um documento final e íntegro, com todas as ações da interface a respeitar exatamente as mesmas regras RBAC do backend.
**Verified:** 2026-07-01
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Advogado responsável ou ADMIN vê o botão "Entregar Parecer" apenas quando status != CONCLUIDO, e confirma via AlertDialog que enfatiza irreversibilidade | ✓ VERIFIED | `showEntregarTrigger` (page.tsx:155-160) = `!permissions.isLoading && !versoes.isLoading && canEditPareceres && isResponsavelOuAdmin && !isConcluido`. `EntregarParecerDialog` (page.tsx:415-512) renders `AlertDialogDescription` with exact irreversibility copy "Esta ação é irreversível..." (page.tsx:471-474). Matches backend `entregarSolicitacao` gate: `@PreAuthorize("hasAuthority('pareceres:edit')")` (ParecerController.java:353) + `isAdmin \|\| isResponsavel` instance check (lines 375-380). |
| 2 | O diálogo de entrega obriga a seleccionar qual versão é entregue como final | ✓ VERIFIED | `<select>` (page.tsx:479-491) populated exclusively from the `versoes` prop (already-fetched `versoes.data`, no free-text input); `defaultVersaoId` pre-selects most recent, `selectedVersaoId` overridable; `AlertDialogAction` disabled when `!selectedVersaoId` (page.tsx:499). Backend independently re-validates `versao.getSolicitacaoId().equals(id)` → 404 otherwise (ParecerController.java:368-371), defense-in-depth confirmed. |
| 3 | Após confirmar, a solicitação passa a CONCLUIDO e a UI reflecte isso sem reload | ✓ VERIFIED | `useEntregarParecer.onSuccess` (use-pareceres.ts:155-160) invalidates `["pareceres","detail",id]` and `["pareceres","list"]` via `Promise.all`. This flips `parecer.data.status`, causing the `isConcluido` branch (page.tsx:284-291) to render `ParecerEntregueBlock` instead of `showEntregarTrigger`/`showNovaVersaoForm` without a manual reload (TanStack Query re-render on cache update). |
| 4 | Uma solicitação CONCLUIDO expõe o bloco "Parecer Entregue" com a versão final, autor e data, e link de anexo | ✓ VERIFIED | `ParecerEntregueBlock` (page.tsx:514-563) looks up `versaoFinal = versoes?.find(v => v.id === versaoFinalId)` (no new fetch), renders "Versão {numeroVersao}", "Elaborado por {resolveUserNome(criadoPorId)} em {formatDateTime(createdAt)}" (derived only from the version's own fields, no fabricated entregue-por/em field — matches CONTEXT.md constraint), green `Badge`, and `<AnexoLink>`. Handles loading vs. genuinely-missing version states distinctly (post WR-03 fix). |
| 5 | Uma solicitação CONCLUIDO é só-leitura na UI: sem botão de entrega e sem form de nova versão, independentemente do papel | ✓ VERIFIED | Render precedence (page.tsx:284-298): `isConcluido` branch takes priority and renders `ParecerEntregueBlock`; `showNovaVersaoForm` and `showEntregarTrigger` both include `!isConcluido` in their boolean derivation (page.tsx:153-154, 160), so neither can render when status is CONCLUIDO, regardless of role. No dead/disabled buttons — affordances are fully omitted ("no dead buttons" convention honored). |
| 6 | Todos os CardTitle sob /pareceres têm text-lg font-bold; o marcador de timeline usa cor neutra | ✓ VERIFIED | Grep-confirmed 4 instances of `CardTitle className="text-lg font-bold"` in `pareceres/[id]/page.tsx` (Dados line 180, Versões line 213, Nova Versão line 345, Parecer Entregue line 532) + 1 in `nova/page.tsx` (line 121, "Dados da Solicitação"). Zero bare `<CardTitle>` or `className="font-bold"`-only instances remain. Timeline dot (page.tsx:240) is `bg-slate-400 dark:bg-slate-500`, zero remaining `bg-blue-600` on the dot span. |

**Score:** 6/6 truths verified (static analysis — see Human Verification section for the live-flow confirmation these truths still require)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/hooks/use-pareceres.ts` | `useEntregarParecer` mutation hook (PUT query-param, no body, cascading invalidation) | ✓ VERIFIED | Lines 146-162: `PUT` to `.../entregar?versaoFinalId=...`, no `body`/`JSON.stringify`, invalidates `detail` + `list` only (not `versoes`, matching CONTEXT.md decision). |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` | Entrega AlertDialog trigger + version selector + Parecer Entregue block + fixes | ✓ VERIFIED | All sub-features present and wired (see truths 1-6 above). |
| `web/src/app/(dashboard)/pareceres/nova/page.tsx` | CardTitle typography fix | ✓ VERIFIED | Line 121: `<CardTitle className="text-lg font-bold">Dados da Solicitação</CardTitle>`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `pareceres/[id]/page.tsx` | `useEntregarParecer` | `mutateAsync({ versaoFinalId: selectedVersaoId })` | ✓ WIRED | page.tsx:435, hook imported line 32, invoked line 429 (`useEntregarParecer(solicitacaoId)`). |
| `useEntregarParecer` | `PUT /pareceres/solicitacoes/{id}/entregar` | `apiFetch` query param, no JSON body | ✓ WIRED | use-pareceres.ts:150-154; confirmed against `ParecerController.entregarSolicitacao` signature `@RequestParam UUID versaoFinalId` (ParecerController.java:356) — query param, not JSON body, matches exactly. |
| entrega trigger visibility | backend `isAdmin \|\| isResponsavel` | `canEditPareceres && isResponsavelOuAdmin && !isConcluido` | ✓ WIRED | page.tsx:155-160 boolean expression mirrors ParecerController.java:375-380 exactly; `canEditPareceres` maps to the `pareceres:edit` `@PreAuthorize` at line 353. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `ParecerEntregueBlock` | `versaoFinal` | `versoes.data` (from `useParecerVersoes`, real GET query, not static) | Yes — real DB-backed data via `ParecerController` version listing endpoint | ✓ FLOWING |
| `EntregarParecerDialog` `<select>` | `versoes` prop | Same `useParecerVersoes(id)` query as timeline | Yes — reuses already-fetched real data, no hardcoded/empty fallback | ✓ FLOWING |
| entrega mutation | `parecer.data.status` post-invalidate | Backend `PUT .../entregar` response (200 OK with updated entity, DB-persisted `status`/`versaoFinalId`) | Yes — real persisted mutation, not a static return (`solicitacao.setStatus("CONCLUIDO")`, `parecerSolicitacaoRepository.save(solicitacao)` at ParecerController.java:383-386) | ✓ FLOWING |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PARC-14 | 68-01-PLAN.md | Entrega irreversível com diálogo de confirmação | ✓ SATISFIED | AlertDialog + irreversibility copy + version selector, backend-mirrored RBAC gate (truths 1-2). |
| PARC-15 | 68-01-PLAN.md | Vista dedicada "Parecer Entregue" (fecha gap PARC-09) | ✓ SATISFIED | `ParecerEntregueBlock`, derives from `versaoFinalId` lookup, no fabricated fields (truth 4). |
| PARC-16 | 68-01-PLAN.md | Ações da UI espelham `@PreAuthorize` do backend, incl. instance-check não-uniforme | ✓ SATISFIED | `showEntregarTrigger`/`showNovaVersaoForm` both gate on scope + instance check; read-only enforcement confirmed (truths 1, 5). Code review (68-REVIEW.md) independently confirmed RBAC gate correctness against backend line numbers. |

No orphaned requirements — REQUIREMENTS.md maps exactly PARC-14/15/16 to Phase 68, all three claimed in PLAN frontmatter and satisfied above.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER debt markers found in the three changed files | — | Clean — grep for debt markers returned only legitimate HTML `placeholder=` attributes, not code-debt comments. |

Code review (68-REVIEW.md, deep depth) found 0 Critical, 3 Warning, 3 Info issues — all 3 Warnings (WR-01, WR-02, WR-03) were subsequently fixed per 68-REVIEW-FIX.md, and independently re-verified in this pass by reading the current file state:
- WR-01 (empty version list race): fixed — `showEntregarTrigger` now includes `!versoes.isLoading` (page.tsx:157).
- WR-02 (Escape-key dismiss mid-mutation): fixed — `onOpenChange` guard (page.tsx:451-454) + `onEscapeKeyDown` guard on `AlertDialogContent` (page.tsx:465-467). The originally-proposed `onPointerDownOutside` handler was correctly removed in a follow-up commit (615166b) because this project's Radix `AlertDialogContent` props type explicitly omits that prop (confirmed: `tsc --noEmit` runs clean with zero errors on the current code, verified in this pass).
- WR-03 (ambiguous loading vs. missing version): fixed — `ParecerEntregueBlock` now takes an explicit `isLoading` prop and branches distinctly (page.tsx:535-538).

Info-level items (IN-01, IN-02, IN-03) were explicitly out of scope for the fix pass and remain as documented, non-blocking hardening opportunities.

### Behavioral Spot-Checks

`pnpm exec tsc --noEmit` run directly in this verification pass (not trusting REVIEW-FIX.md's claim) — **completed with zero errors/output**, confirming the post-correction code (after commit `615166b` removed the non-type-checking `onPointerDownOutside` prop) is currently typecheck-clean.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full project typecheck | `cd web && pnpm exec tsc --noEmit` | Empty output, exit success | ✓ PASS |
| No debt markers in changed files | grep TODO/FIXME/XXX/HACK/PLACEHOLDER | Only legitimate JSX `placeholder=` attrs | ✓ PASS |
| Backend `@PreAuthorize` + instance check matches frontend gate | Manual cross-read of `ParecerController.java:353-380` vs. `page.tsx:155-160` | Boolean logic identical | ✓ PASS |
| Git commits referenced in SUMMARY/REVIEW-FIX exist | `git log --oneline` | fd29066, 2b58a48, d180b35, f3a7b00, 615166b, 4315167 all present | ✓ PASS |

No server/browser available in this environment — live-flow behavioral checks (dialog opening, actual submit round-trip, visual rendering) are deferred to Human Verification below.

### Probe Execution

Step 7c: SKIPPED (no `scripts/*/tests/probe-*.sh` files found; this is a UI phase with no probe-based verification declared in PLAN/SUMMARY).

## Human Verification Required

Static analysis and code review confirm all wiring, RBAC gating, data derivation, and typography/color fixes are correctly implemented and typecheck-clean. However, this is a UI-heavy phase whose core value (irreversibility clarity, live status transition, visual rendering) cannot be fully confirmed without a running app and browser. The following require human testing before full confidence in the delivered UX:

### 1. Entrega irreversibility clarity in the live dialog

**Test:** Log in as the advogado responsável (or ADMIN) for a parecer solicitação with `status = EM_ELABORACAO` (or similar pre-CONCLUIDO state) and at least one version. Click "Entregar Parecer".
**Expected:** AlertDialog opens showing the exact irreversibility copy, a version selector pre-populated with real versions (most recent pre-selected), and a destructive-styled "Confirmar Entrega" button. Escape key and clicking outside do NOT close the dialog while the request is in flight.
**Why human:** Requires visual confirmation of dialog rendering, keyboard interaction timing, and the actual pending-state UX — code correctness was confirmed statically but the felt experience of "clareza total sobre a irreversibilidade" is a UX judgment call.

### 2. Live status transition without reload

**Test:** Confirm entrega in the dialog above and observe the page after the mutation resolves.
**Expected:** Toast "Parecer entregue com sucesso." appears; dialog closes; the entrega trigger disappears; the "Parecer Entregue" block appears immediately with correct version/author/date/anexo data — all without a manual page reload.
**Why human:** Cache invalidation logic was verified statically (correct query keys), but the actual React re-render timing and visual transition can only be confirmed by observing the live app.

### 3. Read-only enforcement across roles

**Test:** As a non-responsável, non-ADMIN user with `pareceres:view` only, open a CONCLUIDO solicitação. Separately, as the responsável/ADMIN, open the same CONCLUIDO solicitação.
**Expected:** In both cases, no "Entregar Parecer" button and no "Nova Versão" form are visible — only the "Parecer Entregue" block and version timeline. No dead/disabled buttons.
**Why human:** Requires testing with multiple real user sessions/roles against a running backend — RBAC boolean logic was verified in code but real end-to-end role behavior (including backend 403 responses for API-level bypass attempts) needs a live environment.

### 4. Visual/typography QA of mandated cross-cutting fixes

**Test:** Visually inspect `/pareceres`, `/pareceres/[id]`, and `/pareceres/nova` pages.
**Expected:** All CardTitle headings render visibly larger/bolder (18px/700) than body text; timeline dots are neutral gray, not blue; the "Entregar Parecer" button and "Parecer Entregue" badge use the correct destructive/green colors per UI-SPEC.
**Why human:** Class names were grep-confirmed in source, but actual rendered visual weight/contrast (especially dark mode) requires a screenshot or live browser check, per this project's established convention of deferring visual QA to human review.

## Gaps Summary

No blocking gaps found. All 6 must-have truths, all 3 required artifacts, all 3 key links, and the data-flow trace pass static verification. Code review issues (WR-01/02/03) were fixed and the fix was independently re-verified against the current file state in this pass, including confirming that the corrective follow-up commit (`615166b`, removing the non-type-checking `onPointerDownOutside` prop) left the codebase in a currently `tsc --noEmit`-clean state — not just per REVIEW-FIX.md's claim, but re-run directly here.

Status is `human_needed` rather than `passed` solely because this phase's goal hinges on live, observable UX qualities (irreversibility "clareza total", real-time status transition, cross-role read-only behavior, visual typography/color correctness) that cannot be responsibly certified through static analysis alone in an environment without a running dev server or browser. No code changes are required before human sign-off — these are confirmation checks, not fixes.

---

_Verified: 2026-07-01_
_Verifier: Claude (gsd-verifier)_
