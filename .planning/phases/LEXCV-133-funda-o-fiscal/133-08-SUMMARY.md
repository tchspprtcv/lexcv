---
phase: 133-funda-o-fiscal
plan: 08
subsystem: web-fiscal
tags: [fiscal, efatura, frontend, settings, alert-dialog, e2e]
requires:
  - "133-05: FaturacaoController /api/v1/faturacao"
  - "133-06: use-faturacao hooks, ApiError, types"
  - "133-07: FaturacaoTab shell, FaturacaoDadosForm (onAlteracoesPorGravarChange), FaturacaoSeriesCard"
provides:
  - "FaturacaoAtivacaoCard (Block 4): activate/deactivate AlertDialogs, three server-derived states, inline 409/422 copy"
  - "FaturacaoEmailCard (Block 5): non-optimistic switch, required acknowledgement checkbox, acceptance line"
  - "web/scripts/verify-faturacao.mjs + pnpm verify:faturacao source gate"
  - "Button variant=\"destructive\" (additive)"
affects: [134, 137]
tech-stack:
  added: []
  patterns:
    - "Controlled AlertDialog: confirm calls e.preventDefault() then mutateAsync; closes only on success or on a rule error (409/422) rendered inline"
    - "A destructive confirm is a plain Button, not AlertDialogAction asChild (Slot + tailwind-merge would make the neutral background win)"
key-files:
  created:
    - web/src/app/(dashboard)/settings/faturacao-ativacao-card.tsx
    - web/src/app/(dashboard)/settings/faturacao-email-card.tsx
    - web/scripts/verify-faturacao.mjs
    - .planning/phases/LEXCV-133-funda-o-fiscal/deferred-items.md
  modified:
    - web/src/app/(dashboard)/settings/faturacao-tab.tsx
    - web/src/components/ui/button.tsx
    - web/package.json
decisions:
  - "Activation/email cards read only server flags (ativa, completa, podeDesativar, documentosEmitidos, envioEmail*), never the series list"
  - "Button gains an additive destructive variant (bg-red-600) because the UI-SPEC needs variant=\"destructive\" and the primitive had none"
  - "End-to-end checkpoint auto-verified against a real Postgres + backend + Next dev stack using Playwright; all 10 steps PASS"
metrics:
  duration: 25 min
  completed: 2026-10-04
  tasks: 3
  files: 7
---

# Phase 133 Plan 08: Activation and email cards, verify:faturacao, end-to-end verification Summary

The Faturação tab now has all six blocks. The new "Ativação da faturação" card shows the desligada, ativa and ativa-com-documentos states. Activating goes through a confirmation dialog that explains irreversibility, and deactivating goes through a destructive confirmation. The new "Envio automático por email" card has a switch that never changes optimistically. Turning it on requires ticking "Compreendo que os documentos simulados não têm validade fiscal", and once on it shows "Aceite por {nome} em {data}." A new Node source gate (`pnpm verify:faturacao`) pins the gating, the copy, the read-only series card and the no-rate-constants rule. The full phase was then verified end to end against a real running stack.

## Tasks

| # | Task | Commit |
|---|------|--------|
| 1 | Activation card + email card wired into the tab shell (+ destructive Button variant) | 0fa62cf |
| 2 | verify:faturacao source gate + full frontend gate | 218db60 |
| 3 | End-to-end verification (checkpoint, auto-verified) — fix found in step 7 | f11eff7 |

## Verification

- `pnpm run verify:faturacao`: OK. As a negative check, I added a `// useMutation` comment to faturacao-series-card.tsx; the gate exited 1 with the expected message, and I then reverted the change.
- `pnpm exec vitest run`: 86/86 pass. `tsc --noEmit` is clean. `pnpm lint` reports 0 errors; the 20 warnings were already there and none are in the new files.
- `pnpm build` (with both env vars set): succeeds, and `/settings` prerenders as static.
- Acceptance greps all match: "Ativar faturação?", `variant="destructive"`, the active-locked sentence, the acknowledgement checkbox text, `aceiteDeclaracao: true`, `FaturacaoAtivacaoCard`, `FaturacaoEmailCard` and `onAlteracoesPorGravarChange={setDadosPorGravar}`.

## End-to-end checkpoint (Task 3) — auto-verified, 10/10 PASS

**Stack:**
- PostgreSQL 16 in Docker.
- Backend via `mvn -Dmaven.compiler.release=21 spring-boot:run` with `SEED_ENABLED=true` and a throwaway JWT secret.
- Web via `pnpm dev`.
- Every request went through the web origin `:3000`, using the `/api/v1` rewrite.
- MinIO could not be pulled (`pull access denied`). It is not needed: the backend only logs a warning at startup, and none of these flows touch storage.
- The env files are git-ignored (`backend/.gitignore *.env`, `web/.gitignore .env*`). They were deleted afterwards, and every process and container was stopped.

**Driver:** Playwright with the preinstalled Chromium (`/tmp/claude-0/e2e.cjs`), plus curl and psql.

| Step | Result | Evidence |
|------|--------|----------|
| 1 | PASS | Tab order is "… Controlo de Acesso (RBAC) \| Auditoria \| Faturação \| Notificações". The "Faturação desligada" badge and the simulation notice are both visible. |
| 2 | PASS | For assistente@lexcv.cv, whose permissions do not include `financeiro:*`, Definições shows no Faturação tab and an in-browser `fetch('/api/v1/faturacao/configuracao')` returns 403. Via curl, GET series/motivos and POST ativar/desativar return 403, and so do PUT configuracao and PUT email-automatico when sent with valid bodies. (A PUT with an empty `{}` body returns 400 first, because bean validation runs before method security; no data is exposed.) |
| 3 | PASS | 012345678 and 12345678 both show "O NIF deve ter 9 dígitos e começar por um algarismo de 1 a 9." on blur; 512345679 clears the error. |
| 4 | PASS | A 101-character morada shows the morada error. Choosing Isento reveals the motivo select with 21 options, from "1 — Regime da margem de lucro…" to "21 — Isenções do Orçamento do Estado". Saving without a motivo shows "Escolha o motivo de isenção."; switching back to Normal hides the select. Saving shows the toast "Dados fiscais guardados.", and after a reload NIF, firma, morada, localidade, email and regime have all persisted. |
| 5 | PASS | Before saving, the button is disabled and shows the hint. With an unsaved edit it is disabled again with the hint. After saving it becomes enabled. The dialog shows the irreversibility text. Confirming shows "Faturação ativada.", the badge changes to "Faturação ativa", and the card shows "A faturação está ativa." with an outline "Desativar faturação" button. Also tested: when the database copy is made incomplete after the page loads, clicking activate gets a 422 from the server, the dialog closes, and the card shows the inline incomplete-data copy with no toast. |
| 6 | PASS | Before activation the switch is disabled with its hint. After activation, "Ligar envio automático" stays disabled until the checkbox is ticked. Cancel and Escape both leave the switch off. The checkbox resets when the dialog is reopened. Confirming shows "Envio automático ligado." and the line "Aceite por Administrador (PostgreSQL Real) em 04/10/2026, 13:24:09."; the database has `envio_email_automatico=true` with the user id and timestamp recorded. Turning it off happens immediately with no dialog and shows "Envio automático desligado." |
| 7 | PASS (after fix) | The destructive dialog shows the spec text and focus starts on "Cancelar". Confirming shows "Faturação desativada.", the badge changes to desligada, and the email switch goes off and disabled. In the first run the confirm button was not red; this was fixed in f11eff7 and re-verified with the `bg-red-600` class present. |
| 8 | PASS | Before any insert, the series card shows "Sem séries de numeração". After inserting a `t_serie_fiscal` row (FR/2026/SIMULADO/SIM-FR-2026/1) and reloading, the NIF is disabled with the locked-NIF copy, and the series row reads "Fatura-Recibo 2026 SIM-FR-2026 1 Simulado". After activating, the deactivate button is gone and the active-locked sentence is shown. A hand-built `POST /desativar` returns `409 FATURACAO_JA_EMITIU`. In a separate run where a document appeared while the page was open, confirming deactivation closed the dialog and showed the inline 409 copy. The row was deleted afterwards. |
| 9 | PASS | `POST /api/v1/pagamentos` (the endpoint the Financeiro UI calls) returned 201 both with billing off (id 3) and with billing on (id 5). The honorário totalPago went up as before. With billing on, `t_serie_fiscal.ultimo_numero` stayed at 1, so no fiscal path ran. This is CFG-03. |
| 10 | PASS | The dark class was applied with `colorScheme: dark`. At a 375px width the page has 0px horizontal overflow in both light and dark. The series table sits in an `overflow-x: auto` wrapper and scrolls horizontally. The forms are single column on mobile. I reviewed the per-card screenshots (`card-{dark-desktop,dark-mobile,light-mobile}-*.png`): all text is readable in both themes. One cosmetic observation: in dark mode the unchecked Switch track (neutral-800 on the dark card) is subtle. That styling comes from the shared Switch primitive, which other settings tabs also use, so it is not new to this plan. |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added a destructive Button variant**
- **Found during:** Task 1.
- **Issue:** The UI-SPEC and the acceptance criteria require `variant="destructive"`, but `buttonVariants` had no such variant, so tsc would reject it.
- **Fix:** Added `destructive` (bg-red-600, with a dark: pair) to `web/src/components/ui/button.tsx`. The change is additive and no other caller is affected.
- **Commit:** 0fa62cf

**2. [Rule 1 - Bug] The deactivate confirm button was not red**
- **Found during:** Task 3, step 7.
- **Issue:** Wrapping the destructive `Button` in `AlertDialogAction asChild` let Radix Slot merge the Action's `bg-neutral-900` classes into the Button, and tailwind-merge let them win.
- **Fix:** Switched to a plain `Button variant="destructive"`. Closing is already controlled through `desativarAberto`.
- **Files modified:** faturacao-ativacao-card.tsx
- **Commit:** f11eff7

**3. Additive behaviour.** The email card also shows the inline `mensagem` for 409/422 responses (for example FATURACAO_DESLIGADA in a race), because the hook suppresses the toast for those statuses. While a request is pending, the dialogs ignore Escape and overlay clicks.

### Verification-environment workarounds (no product change)
- The first-run wizard fails on a fresh database, a pre-existing bug logged in `deferred-items.md` #1. I seeded the demo tenant instead by truncating the tenant and user tables, marking `system_settings` as initialized and restarting with `SEED_ENABLED=true`. The default admin `admin@lexcv.cv` / `Pa$$w0rd` then worked.
- MinIO was unavailable, which is irrelevant to these flows.

## Deferred Issues

Both are logged in `.planning/phases/LEXCV-133-funda-o-fiscal/deferred-items.md`; neither is in this plan's scope.

1. `POST /api/v1/setup/initialize` returns 500 (UnsupportedOperationException) on a fresh install, because of `Set.of(...)` roles combined with merge in SetupService. This predates Phase 133.
2. In `ddl-auto: update` mode, every boot after the first logs a DDL error for `t_serie_fiscal.ambiente`, caused by `columnDefinition = "varchar(32) not null"` on a converter-typed column. The error is non-fatal and the schema is correct. This comes from Plan 133-01.

## Threat Mitigations

- **T-133-32:** The checkbox is required on the client and the backend's `aceiteDeclaracao` check is the authority. The run confirmed the database stores who accepted and when.
- **T-133-33:** The UI hides deactivate once documents exist, and a hand-built POST got 409 FATURACAO_JA_EMITIU (step 8).
- **T-133-34:** The simulation notice is always visible, and turning on email requires the explicit acknowledgement.
- **T-133-35:** The tab gating is pinned by verify:faturacao, and the backend returned 403 to a non-manager (step 2).

## Known Stubs

None. The email switch only records the user's choice; actual sending arrives in Phase 137, and the UI copy says so.

## Requirements

CFG-02, CFG-03 and CFG-06 were verified end to end (steps 5, 7, 8, 9 and 6) and are marked complete.

## Self-Check: PASSED

- All of these exist on disk: faturacao-ativacao-card.tsx, faturacao-email-card.tsx, verify-faturacao.mjs and deferred-items.md.
- Commits 0fa62cf, 218db60 and f11eff7 are present in `git log`.
