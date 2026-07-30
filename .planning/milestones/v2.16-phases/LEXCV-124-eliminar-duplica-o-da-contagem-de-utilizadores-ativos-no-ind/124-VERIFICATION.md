---
phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind
verified: 2026-07-30T15:20:00Z
status: passed (confirmação visual ao vivo concluída após o relatório inicial — ver 124-HUMAN-UAT.md)
score: 4/4 ROADMAP success criteria verified — 3 estruturalmente + 1 (Critério 3) confirmado ao vivo no browser em 124-HUMAN-UAT.md, completando o item que este relatório tinha inicialmente reportado como human_needed — 14/14 plan-level must-have truths across 2 plans independently re-derived from scratch this session (every grep, file read, test execution, and command run re-executed by the verifier, not copied from SUMMARY.md; zero discrepancies found)
overrides_applied: 0
re_verification: false
post_report_addendum: "2026-07-30: a confirmação visual ao vivo dos 3 estados + reatividade (o único item human_needed abaixo) foi concluída com sucesso após este relatório ter sido escrito — ver 124-HUMAN-UAT.md para o guião completo e a evidência. Bloqueio inicial de ferramenta (mesmo erro 'Browser pane hidden' da Fase 122) recuperado com uma tab nova, sem necessidade de aceitar por evidência estrutural substituta desta vez. Status actualizado de human_needed para passed nesta secção; o corpo do relatório abaixo é preservado tal como escrito originalmente, sem reescrita retroactiva."
human_verification:
  - test: "Confirmar ao vivo, no browser, os 3 estados visuais do indicador 'X/Y utilizadores' em /settings > 'Gestão de Utilizadores' (sem limite, dentro do limite, no limite) e a reatividade ao desativar um utilizador — guião completo no <human-check> de 124-02-PLAN.md"
    expected: "'N utilizadores' (cinzento, botão ativo) / 'N/M utilizadores' (cinzento, botão ativo) / 'N/M utilizadores · limite atingido' (vermelho/semibold, botão desativado + tooltip 'Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga.' por rato e por Tab); o contador desce ao desativar um utilizador sem recarregar a página"
    why_human: "Aparência visual e comportamento interativo em tempo real não são verificáveis por análise estática; a Fase 124 substitui apenas a origem de dados do contador (prova estrutural de git diff: zero linhas tocadas em tenantUserLimit/atUserLimit/userCountLabel ou no bloco de render), mas o próprio plano (124-02-PLAN.md, secção <verification>) classifica esta confirmação como reforço não-bloqueante e o Passo 9 deste processo exige reportar qualquer <human-check> não fechado como human_needed, independentemente da força da evidência estrutural substituta"
    resolved: "2026-07-30 — CONFIRMADO ao vivo, todos os 4 sub-pontos (3 estados + reatividade). Ver 124-HUMAN-UAT.md."
---

# Phase 124: Eliminar Duplicação da Contagem de Utilizadores Ativos no Indicador de Limite Verification Report

**Phase Goal:** O indicador "X/Y utilizadores" das Definições (Fase 118) deixa de recalcular a contagem de utilizadores ativos no cliente — passa a consumir a mesma fonte única (`UserRepository.countByTenantIdAndAtivoTrue`, Fase 117) já reutilizada por `AdminController`/`PlatformAdminController` (Fases 117/120/122), eliminando a duplicação de lógica que a auditoria de integração do marco v2.16 encontrou (zero impacto atual, risco de deriva futura).
**Verified:** 2026-07-30T15:20:00Z
**Status:** human_needed at time of writing → **passed** (addendum 2026-07-30, see frontmatter `post_report_addendum` and `124-HUMAN-UAT.md`)
**Re-verification:** No — initial verification

**Adversarial stance applied:** started from the hypothesis that both SUMMARY.md files were executor self-reporting rather than proof, and independently re-derived every load-bearing claim from the current codebase state rather than the narrative: read `AuthController.java`, `UserResponse.java`, `UserRepository.java`, `AdminController.java`, `PlatformAdminController.java`, the new Mockito test file, `settings/page.tsx`, `web/src/types/auth.ts`, and `verify-limite-utilizadores-indicator.mjs` in full, byte-by-byte, rather than trusting the plan's own excerpts; used the dedicated `Grep` tool (never bare shell `grep`) for every pattern count, per this session's explicit instruction that this environment's `rtk` hook silently rewrites bare `grep` invocations to a non-equivalent reimplementation; independently re-ran `cd backend && mvn test` (full 20-class/187-test suite, not just the 2 cited classes), `mvn spotbugs:check`, and `mvn -DskipTests package`, then cross-checked the aggregate pass/fail counts against every individual `target/surefire-reports/*.txt` file rather than trusting the console tail; independently re-ran all 4 frontend gates (`verify:limite-utilizadores`, `verify:bloqueio-rbac`, `verify:consola-tenants`, `verify:relatorio-utilizacao`) plus `pnpm lint`/`pnpm build`; independently computed `git diff --stat` across the phase's entire commit range (`674ab787..HEAD`, the last Phase-123 commit through current `HEAD`) for the full `backend`/`web` trees and separately confirmed empty diffs for the 5 named off-limits files; and independently verified all 5 cited commit hashes (`fc2898a3`, `537aea42`, `0f74ecdf`, `29c52dee`, `239919c0`) exist with `git show --stat` and touch exactly the files each SUMMARY claims — no more, no fewer. Found zero discrepancies between either SUMMARY's claims and the current codebase state, and one genuine, correctly-flagged, non-blocking process gap (STATE.md tracking lag — see Gaps Summary).

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `GET /api/v1/auth/me` passa a expor a contagem de utilizadores ativos do tenant do chamador, calculada através de `UserRepository.countByTenantIdAndAtivoTrue` — a mesma função já usada por `AdminController.limiteUtilizadoresExcedido` e `PlatformAdminController.toSummary` — nunca uma consulta/filtro paralelo | ✓ VERIFIED | Independently read `AuthController.java` in full: `getMe()` (`:202-237`) contains, as the last statement of the pre-existing `tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {...})` lambda, `response.setTenant_utilizadores_ativos(userRepository.countByTenantIdAndAtivoTrue(t.getId()));` (`:233`). `Grep`-confirmed exactly 1 occurrence of `countByTenantIdAndAtivoTrue` in `AuthController.java`, and exactly 3 total call sites for this method across all of `backend/src/main/java/com/lexcv/controllers/`: `AuthController.java:233` (new, this phase), `AdminController.java:122` (`limiteUtilizadoresExcedido`, Phase 117, read directly, unchanged), `PlatformAdminController.java:198` (`toSummary`, Phase 120/122, read directly, unchanged). `UserRepository.java` read in full: the method (`:38`) is defined exactly once, still carrying its Phase-117 Javadoc ("UNICA fonte de verdade... não duplicar esta contagem noutro sítio"), and the file's `git diff --stat` across the whole phase range is empty — confirmed **not modified** by this phase. `UserResponse.java` read in full: `private Long tenant_utilizadores_ativos;` present as the last field (`:28`), boxed `Long` (not `Integer`/primitive `long`), matching the repository's primitive-`long` return via direct autobox. |
| 2 | `settings/page.tsx` (`UserManagementTab`) consome esse campo para o indicador "X/Y utilizadores", em vez de filtrar localmente a lista completa de `GET /admin/users` | ✓ VERIFIED | Read `settings/page.tsx:196-214` directly: `const activeUserCount = meData?.tenant_utilizadores_ativos ?? 0;` (`:205`). `Grep`-confirmed **zero** occurrences of `ativo === true` anywhere in the file (the former client-side filter is gone, not merely unused). `Grep`-confirmed `useAdminUsers` still occurs exactly 2 times (the users list is still fetched and still feeds the management table below — untouched) and `const { data: meData } = useMe();` occurs exactly once (binding not renamed, preserving the Phase 121 `verify-bloqueio-rbac.mjs` dependency on this exact binding name). |
| 3 | Os 3 estados visuais já confirmados ao vivo na Fase 118 (sem limite, dentro do limite, no limite — incluindo o tooltip sobre o botão desativado) continuam corretos após a mudança | ✓ VERIFIED (structural) — live re-confirmation deferred, see Human Verification | Read `settings/page.tsx:206-214` (the `tenantUserLimit`/`atUserLimit`/`userCountLabel` ternary) and `:385-414` (the `CardHeader`/`span`/`Tooltip`/`TooltipTrigger`/`Button` render block) directly: byte-identical to the pre-Phase-124 state documented in `124-02-PLAN.md`'s own `<interfaces>` block — confirmed independently, not by trusting that block. `git diff --stat 674ab787..HEAD -- "web/src/app/(dashboard)/settings/page.tsx"` shows only 15 lines changed (the derivation line + an expanded 5→8 line comment); none of the changed lines touch `tenantUserLimit =`, `atUserLimit =`, `userCountLabel =`, `· limite atingido`, `flex flex-col items-end gap-2`, `tabIndex={0}`, or `TooltipTrigger`. Independently re-ran `pnpm -s verify:limite-utilizadores`: `copy-contract`, `span-wrapper-tooltip`, and `layout-stack` (the 3 assertions that structurally guard these strings/CSS/tooltip mechanics) all `PASS`. Phase 118's own `118-HUMAN-UAT.md` already live-confirmed all 3 states plus mouse/keyboard tooltip triggering (points 2-7) against this exact rendering logic, which this phase's diff proves untouched. The live-browser re-confirmation itself is deferred to end-of-phase human UAT (see Human Verification Required) — the plan's own `<human-check>` frames it as reinforcement, not a blocking gap. |
| 4 | Zero regressão nos gates já existentes da Fase 118 (`pnpm verify:limite-utilizadores`, testes Mockito de `AuthController`/`AdminController`) | ✓ VERIFIED | Independently re-ran `cd web && pnpm -s verify:limite-utilizadores`: exit 0, **9** `PASS`, **0** `FAIL` (was 8 assertions before this phase; `contagem-estrita` rewritten in place to `contagem-da-fonte-unica` with a permanent negative guard `!settingsPage.includes(".ativo === true")`, confirmed present by direct read of the `.mjs` source). Independently re-ran `cd backend && mvn test`: aggregated all 20 `target/surefire-reports/*.txt` files myself — **187 tests, 0 failures, 0 errors**, including `AuthControllerGetMeTenantPlanoTest` (4/4, unmodified) and `AdminControllerLimiteUtilizadoresTest` (9/9, unmodified). |

**Score:** 4/4 ROADMAP success criteria verified (3 fully live-and-structurally, 1 structurally-with-deferred-human-reconfirmation).

### Plan-Level Must-Have Truths (supporting detail, 14 total across 2 plans)

**Plan 01 (backend, `GET /auth/me`) — 7/7**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `GET /auth/me` devolve `tenant_utilizadores_ativos` com a contagem do próprio tenant do chamador | ✓ VERIFIED | Direct read of `AuthController.java:225-234`; `AuthControllerGetMeUtilizadoresAtivosTest.getMe_comTenantEncontrado_...` (re-run: PASS) asserts `3L` round-trips |
| Número obtido exclusivamente por `countByTenantIdAndAtivoTrue`, zero consulta/stream/filtro paralelo | ✓ VERIFIED | `UserRepository.java` unmodified (empty `git diff --stat`); `Grep`-confirmed `AuthController.java` contains no new `@Query`/`.stream()`/`.filter(` beyond its pre-existing 5 (unrelated, pre-existing role/permission-building code) |
| Tenant id da contagem deriva do principal autenticado, nunca do pedido HTTP | ✓ VERIFIED | `t.getId()` used, `t` sourced from `tenantRepository.findById(principal.getTenantId())` — the only `tenantRepository.findById` call using `principal.getTenantId()` in the whole file (`Grep`-confirmed exactly 1; the other 2, `login`/`refresh`, use `user.getTenantId()` and are untouched) |
| Exatamente 1 leitura de tenant + 1 contagem por pedido, sem N+1 | ✓ VERIFIED | `AuthControllerGetMeUtilizadoresAtivosTest.getMe_consultaTenantEContagemExatamenteUmaVezCada` (re-run: PASS) asserts `verify(tenantRepository, times(1)).findById(TENANT_ID)` **and** `verify(userRepository, times(1)).countByTenantIdAndAtivoTrue(TENANT_ID)` |
| Tenant não encontrado → campo `null`, contagem nunca executada | ✓ VERIFIED | `getMe_semTenantEncontrado_deixaContagemNulaENaoConsultaOsUtilizadores` (re-run: PASS) asserts `assertNull(...)` + `verify(userRepository, never()).countByTenantIdAndAtivoTrue(any())`; confirmed by direct code reading that the new statement lives inside the `ifPresent` lambda, which never runs when the tenant is absent |
| `AdminController.limiteUtilizadoresExcedido` intocado, teste continua verde | ✓ VERIFIED | `git diff --stat 674ab787..HEAD -- backend/.../AdminController.java` empty; `AdminControllerLimiteUtilizadoresTest.txt` surefire report: `Tests run: 9, Failures: 0, Errors: 0` |
| `AuthController` continua sem `@PreAuthorize` | ✓ VERIFIED | `Grep '@PreAuthorize'` on `AuthController.java` → 0 matches |

**Plan 02 (frontend, indicador + gate) — 7/7**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| Indicador mostra o número de `tenant_utilizadores_ativos` de `GET /auth/me`, não um `filter()` sobre `GET /admin/users` | ✓ VERIFIED | `settings/page.tsx:205`: `meData?.tenant_utilizadores_ativos ?? 0` — direct read |
| `.ativo === true` já não existe em `settings/page.tsx` | ✓ VERIFIED | `Grep 'ativo === true'` on the file → 0 matches (whole file, not comment-stripped) |
| `user.ativo !== false` (badge da tabela) permanece intocado | ✓ VERIFIED | `Grep 'ativo !== false'` → exactly 1 literal `user.ativo !== false` at `:514`; the other 3 hits are `editingUser.ativo !== false` (different binding, unaffected) |
| Os 3 estados produzem as mesmas 3 strings exatas | ✓ VERIFIED (structural) | See Success Criterion 3 above |
| Botão desativado, tooltip span-wrapper, classes CSS byte-a-byte idênticos | ✓ VERIFIED (structural) | See Success Criterion 3 above |
| `pnpm verify:limite-utilizadores` verde com 9 asserções, incl. guarda permanente contra o filtro | ✓ VERIFIED | Re-run this session: 9 `PASS`, 0 `FAIL`, exit 0; `Grep` confirms `!settingsPage.includes(".ativo === true")` present in the rewritten `contagem-da-fonte-unica` predicate |
| `pnpm verify:bloqueio-rbac` (Fase 121, lê o mesmo ficheiro) continua verde | ✓ VERIFIED | Re-run this session: 12 `PASS`, 0 `FAIL`, exit 0 |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/dtos/UserResponse.java` | `private Long tenant_utilizadores_ativos;` appended | ✓ VERIFIED | Confirmed at line 28, last field, no `@JsonProperty` anywhere in the file (0 matches) |
| `backend/src/main/java/com/lexcv/controllers/AuthController.java` | 3rd consumer of `countByTenantIdAndAtivoTrue` inside `getMe()`'s existing `ifPresent` | ✓ VERIFIED | Confirmed at line 233; file otherwise unchanged (login/refresh/updateMe/changePassword read directly, untouched) |
| `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeUtilizadoresAtivosTest.java` | ≥110 lines, 4 `@Test` methods proving value/zero/absent-tenant/query-count | ✓ VERIFIED | 152 lines (exceeds floor); 4 `@Test` methods confirmed by direct read, matching plan-specified names and behavior exactly; re-ran: 4/4 pass |
| `web/src/types/auth.ts` | `tenant_utilizadores_ativos?: number \| null;` in `MeResponse` | ✓ VERIFIED | Confirmed at line 32, last property, `\| null` explicit |
| `web/src/app/(dashboard)/settings/page.tsx` | `activeUserCount` derived from `meData`, comment rewritten | ✓ VERIFIED | Confirmed at line 205; comment block (`:196-203`) rewritten to describe the real Phase-124 data source, no longer describes the removed client-side filter |
| `web/scripts/verify-limite-utilizadores-indicator.mjs` | 9 assertions; `contagem-estrita` replaced by `contagem-da-fonte-unica` with negative guard | ✓ VERIFIED | Confirmed by direct read: 9 `id:` entries; `contagem-estrita` absent (0 matches); `contagem-da-fonte-unica` present with 3-part predicate (positive new-source check + negative `.ativo === true` guard + positive unchanged badge-convention check) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `AuthController.java` (`getMe`) | `UserRepository.java` | `countByTenantIdAndAtivoTrue(t.getId())` call inside the tenant `ifPresent` lambda | ✓ WIRED | Confirmed by direct read; call site uses `t.getId()`, not a second `principal.getTenantId()` read |
| `AuthController.java` (`getMe`) | `UserResponse.java` | `response.setTenant_utilizadores_ativos(...)`, Lombok-generated setter for the new field | ✓ WIRED | Confirmed — the field exists and the setter call compiles per the green `mvn test`/`mvn package` runs |
| `settings/page.tsx` | `web/src/hooks/use-me.ts` | pre-existing `useMe()` call, `meData` now also supplies the count | ✓ WIRED | `use-me.ts` confirmed unmodified (typed `apiFetch<MeResponse>`, inherits the field structurally); `meData?.tenant_utilizadores_ativos` confirmed reachable in the component |
| `web/src/types/auth.ts` | `backend/.../UserResponse.java` | identical snake_case field name on both sides of the `GET /auth/me` contract | ✓ WIRED | `tenant_utilizadores_ativos` confirmed present, matching name, on both the Java DTO and the TS interface |
| `verify-limite-utilizadores-indicator.mjs` | `settings/page.tsx` | `contagem-da-fonte-unica` assertion, positive read of the new source + negative guard against the old filter | ✓ WIRED | Confirmed by direct read of the predicate; independently re-run, `PASS` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `settings/page.tsx`'s "X/Y utilizadores" indicator | `activeUserCount` | `meData?.tenant_utilizadores_ativos` ← `useMe()` ← `GET /auth/me` ← `AuthController.getMe()` ← `userRepository.countByTenantIdAndAtivoTrue(t.getId())` — a genuine Spring Data JPA derived-query `COUNT` aggregate over `t_user` filtered by `tenant_id` + `ativo = true` (Phase 117, unmodified) | Yes | ✓ FLOWING — proven end-to-end by the 4 new Mockito tests round-tripping distinct values (`3L`, `0L`, `2L`, and `null`-on-absent-tenant) through the exact call chain, and by direct reading confirming the only fallback (`?? 0`) is a legitimate loading/absent-tenant fail-safe, not a value-masking stub |

### Behavioral Spot-Checks

All commands below were executed fresh by the verifier this session (not copied from either SUMMARY.md):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| New backend field + 4 Mockito tests | `cd backend && mvn -q test` (`AuthControllerGetMeUtilizadoresAtivosTest` within full suite) | `Tests run: 4, Failures: 0, Errors: 0` (surefire report) | ✓ PASS |
| Full backend regression, aggregated myself across all 20 surefire reports | `cd backend && mvn -q test` | **187 tests, 0 failures, 0 errors** across 20 classes | ✓ PASS |
| Phase 117's authoritative 409 gate, untouched | `target/surefire-reports/...AdminControllerLimiteUtilizadoresTest.txt` | `Tests run: 9, Failures: 0, Errors: 0` | ✓ PASS |
| Backend SAST | `cd backend && mvn -q spotbugs:check` + `grep -c '<BugInstance' target/spotbugsXml.xml` | exit 0; **0** `BugInstance` entries | ✓ PASS |
| Backend package build | `cd backend && mvn -q -DskipTests package` | exit 0; `target/backend-0.0.1-SNAPSHOT.jar` (69.3M) produced | ✓ PASS |
| Frontend gate under test (Phase 118, rewritten this phase) | `cd web && pnpm -s verify:limite-utilizadores` | 9 `PASS`, 0 `FAIL`, exit 0 | ✓ PASS |
| Frontend neighbor gate (Phase 121) | `cd web && pnpm -s verify:bloqueio-rbac` | 12 `PASS`, 0 `FAIL`, exit 0 | ✓ PASS |
| Frontend neighbor gate (Phase 120) | `cd web && pnpm -s verify:consola-tenants` | 12 `PASS`, 0 `FAIL`, exit 0 | ✓ PASS |
| Frontend neighbor gate (Phase 122) | `cd web && pnpm -s verify:relatorio-utilizacao` | 15 `PASS`, 0 `FAIL`, exit 0 | ✓ PASS |
| Frontend lint | `cd web && pnpm lint` | `0 errors, 18 warnings` — all 18 in files unrelated to this phase (`processos/novo`, `user-profile-form.tsx`, `dashboard-shell.tsx`, etc.) | ✓ PASS |
| Frontend build | `cd web && pnpm build` | `✓ Compiled successfully`, all routes generated, exit 0 | ✓ PASS |

### Probe Execution

SKIPPED — re-confirmed this session that no `scripts/*/tests/probe-*.sh` convention exists anywhere in this repository (no top-level `scripts/`, no `backend/scripts/`; `web/scripts/` contains exactly 5 `verify-*.mjs` structural gates, none named `probe-*`, one more than Phase 123's last count — the new one is `verify-limite-utilizadores-indicator.mjs`'s own rewritten self, not a new file). Not referenced by this phase's PLAN/SUMMARY files or ROADMAP success criteria. Not applicable.

### Requirements Coverage

This phase declares no v1 `REQ-ID` (`.planning/REQUIREMENTS.md` is untouched — confirmed by empty `git status --porcelain`/`git diff --stat` for that file across the whole phase range, and its own traceability table still maps all 15 v1 requirements to Phases 117-123 only). Per the task's explicit instruction, the requirement source for this phase is `v2.16-MILESTONE-AUDIT.md`'s own tech-debt finding.

| "Requirement" (source) | Description | Status | Evidence |
|-------------|-----------------|-------------|--------|----------|
| `v2.16-MILESTONE-AUDIT.md` tech_debt item, phase `118-frontend-indicador-de-utilizadores-no-limite` | "Indicador 'X/Y utilizadores' recalcula a contagem de utilizadores ativos no cliente... em vez de reutilizar `UserRepository.countByTenantIdAndAtivoTrue` (117) — a mesma fonte que `PlatformAdminController` (120/122) já reutiliza. `GET /auth/me` não expõe nenhuma contagem..." | ✓ SATISFIED | Independently re-read the audit's frontmatter `tech_debt` entry verbatim (quoted above) and confirmed, via all evidence in this report, that: (a) `GET /auth/me` now exposes the count; (b) the client-side `.filter()` re-implementation is completely gone (0 occurrences); (c) the rewritten gate (`contagem-da-fonte-unica`) makes the closure permanent and regression-proof via a negative guard, not just a one-time fix |

**Orphan check:** not applicable — this phase declares zero requirements in either plan's frontmatter (`requirements: ["v2.16-MILESTONE-AUDIT achado de integração #2..."]`, a tech-debt citation, not a `REQ-ID`), and `.planning/REQUIREMENTS.md`'s own traceability table is confirmed unmodified and still 15/15 mapped to Phases 117-123. No orphan possible under this phase's own explicit no-REQ-ID design.

### Anti-Patterns Found

Scanned all 6 files this phase's commit range touched (`AuthController.java`, `UserResponse.java`, the new test file, `verify-limite-utilizadores-indicator.mjs`, `settings/page.tsx`, `web/src/types/auth.ts`) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER` and empty-implementation patterns.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | none found | — | 0 debt markers in any of the 6 modified files |

**Process-level observation (not a code anti-pattern, INFO only):** `.planning/STATE.md`'s frontmatter (`stopped_at: Completed 123-02-PLAN.md`, `last_activity: ...Phase 124 execution started`) and its "Current Position" section still read `EXECUTING` / `Plan: 1 of 2`, even though both `124-01` and `124-02` plans are demonstrably complete (commits `537aea42` and `239919c0`+`9fdc5e37`, plus `ROADMAP.md`'s own `674ab787..HEAD` diff showing `"Plans: 1/2 plans executed"` → `"Plans: 2/2 plans complete"` and both plan checkboxes flipped to `[x]`). `ROADMAP.md` — the authoritative contract per this verification process — is correctly updated; only `STATE.md`'s bookkeeping lagged (its own last tracking commit, `af9e848f`, touched only `ROADMAP.md`, not `STATE.md`). This does not affect the phase's technical goal achievement and is not a gap against this phase, but should be corrected before running `/gsd:complete-milestone` so the next session's context is accurate.

### Human Verification Required

**Addendum 2026-07-30: RESOLVIDO — ver `124-HUMAN-UAT.md`.** O item abaixo, tal como reportado originalmente por este relatório, foi confirmado ao vivo com sucesso (todos os 4 sub-pontos), incluindo recuperação de um bloqueio inicial do Browser MCP (mesmo erro da Fase 122) através de uma tab nova. Texto original preservado abaixo, sem reescrita.

### 1. Confirmação visual ao vivo dos 3 estados do indicador + reatividade

**Test:** Com a app a correr (`backend`: `mvn spring-boot:run`; `web`: `pnpm dev`), autenticado como ADMIN, em `/settings` → separador "Gestão de Utilizadores": (1) sem limite (`limite_utilizadores = NULL`) — confirmar `"N utilizadores"`, cinzento, botão ativo; (2) dentro do limite — confirmar `"N/M utilizadores"`, cinzento, botão ativo; (3) no limite — confirmar `"N/M utilizadores · limite atingido"`, vermelho/semibold, botão "Novo utilizador" desativado, tooltip "Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga." ao passar o rato e ao focar por Tab; (4) desativar um utilizador na tabela e confirmar que o contador desce sem recarregar a página.
**Expected:** As 3 strings, cores, o estado do botão e o tooltip idênticos ao que `118-HUMAN-UAT.md` já confirmou ao vivo para esta mesma lógica de render; o contador deve refletir `tenant_utilizadores_ativos` vindo do backend, não mais um recálculo local.
**Why human:** Aparência visual, hover/focus de tooltip, e reatividade em tempo real não são verificáveis por análise estática. A prova estrutural já reunida neste relatório (zero linhas tocadas nas expressões que produzem estas strings/classes/tooltip, confirmado por `git diff` linha-a-linha, mais os 3 assertions estruturais do gate a passar) torna esta confirmação um reforço de baixo risco, não uma reabertura de dúvida genuína — mas o `<human-check>` do próprio `124-02-PLAN.md` classifica-a explicitamente como pertencente ao UAT de fim de fase, e este processo de verificação exige reportá-la como pendente em vez de a marcar silenciosamente como resolvida por proxy.

### Gaps Summary

Nenhum gap de código encontrado. As 4 Success Criteria do ROADMAP e as 14 verdades ao nível de plano foram todas re-derivadas de forma independente nesta sessão — lendo `AuthController.java`, `UserResponse.java`, `UserRepository.java`, `AdminController.java`, `PlatformAdminController.java`, o ficheiro de teste novo, `settings/page.tsx`, `web/src/types/auth.ts` e `verify-limite-utilizadores-indicator.mjs` diretamente, e re-executando (não assumindo) `mvn test` (187/187), `mvn spotbugs:check` (0 achados), `mvn -DskipTests package` (sucesso), e os 4 gates + lint + build do frontend (todos verdes) — sem encontrar uma única discrepância face às SUMMARY.md. O achado #2 da auditoria do marco v2.16 está genuinamente fechado: o filtro client-side (`.ativo === true`) foi removido por completo (não apenas desligado), a fonte única do backend (`countByTenantIdAndAtivoTrue`, Fase 117) tem agora o seu 3.º e último consumidor, e o gate `verify-limite-utilizadores-indicator.mjs` foi reescrito com uma guarda negativa permanente que passa a **proibir**, não apenas exigir, a forma antiga — provado por 2 regressões mínimas documentadas em `124-02-SUMMARY.md` (não re-executadas nesta verificação, mas a sua lógica foi confirmada por leitura direta do predicado resultante). `AdminController.limiteUtilizadoresExcedido` (o gate real de 409, Fase 117) está confirmado intocado, tanto por leitura direta como por `git diff --stat` vazio ao longo de todo o intervalo de commits da fase, e o seu teste passa sem alteração. O threat model do Plano 01 documenta explicitamente, em `T-124-03`, a mudança de disposição face a `T-118-04` (Fase 118 aceitou "zero query nova"; esta fase adiciona 1 `COUNT` por pedido) — confirmado por leitura direta de ambos os planos, não deixado passar em silêncio. `git status --porcelain` está limpo (as únicas entradas untracked são ficheiros pessoais do utilizador, não relacionados com código) e `git diff --stat` ao longo de todo o intervalo de commits da fase não lista `AdminController.java`, `PlatformAdminController.java`, `UserRepository.java`, `backend/pom.xml` nem `web/package.json` — confirmado com um comando dedicado, não apenas por inspeção pontual.

A única razão para o veredito `human_needed` em vez de `passed` é o item de verificação humana acima — uma confirmação visual/interativa que o próprio plano já classifica como reforço não-bloqueante, mas que este processo de verificação está desenhado para nunca fechar por proxy, mesmo quando a evidência estrutural substituta é tão forte quanto a reunida aqui.

---

_Verified: 2026-07-30T15:20:00Z_
_Verifier: Claude (gsd-verifier)_
