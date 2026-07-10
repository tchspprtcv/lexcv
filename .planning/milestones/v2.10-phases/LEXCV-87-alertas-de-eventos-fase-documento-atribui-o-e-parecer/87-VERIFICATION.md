---
phase: LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
verified: 2026-07-09T15:45:00Z
status: human_needed
score: 7/7 must-haves verified programmatically (0 failed, 0 uncertain)
overrides_applied: 0
human_verification:
  - test: "Plan 87-04 Task 4 (checkpoint:human-verify, gate=blocking): with backend (`mvn -f backend/pom.xml spring-boot:run`) and frontend (`pnpm --dir web dev`) running, log in as ADMIN, open an existing processo's ficha, confirm 'Reatribuir' renders next to Responsável (Workflow card) and is absent for a non-manage profile (e.g. ASSISTENTE). Click Reatribuir -> Dialog opens with 'Responsável atual: ...' and a select (submit disabled while selection is empty or equals current, and — per the iteration-3 fix — the current responsável still appears as a synthetic '(inativo)' option if deactivated). Pick a different user -> Reatribuir -> AlertDialog 'Confirmar Reatribuição' names the processo/new responsável. Confirm -> toast success, modals close, responsável name updates with no F5 (proves the `workflow` query-key invalidation). Force a backend error (e.g. stop the backend) and repeat -> inline `text-red-600` message + `toast.error`, AlertDialog stays open. Query `t_notificacao` (or `GET /notificacoes/unread-count` as that user) to confirm a PROCESSO_ATRIBUIDO row exists for the new responsável and for each ADMIN. Finally navigate directly to `/processos/{id}?tab=fases` and confirm the Fases tab is pre-selected on load, while `/processos/{id}` (no query param) still opens on Timeline."
    expected: "All 8 steps in Plan 87-04's <human-check> block pass exactly as scripted; this is the only remaining unexecuted verification surface for the phase — everything else (compile, 20/20 backend unit tests, tsc, full `pnpm build` across all 23 routes) has been independently re-run and passes."
    why_human: "Requires a live browser + a running backend+PostgreSQL session. The orchestrator already attempted this and hit an unrelated, pre-existing environment gap: Spring context fails to initialize because `MINIO_ENDPOINT` is not substituted from `backend/.env` in this session (`IllegalArgumentException: Illegal character in path at index 1: ${MINIO_ENDPOINT}` inside `MinioConfig.s3Client()`), blocking `StorageService`/`S3Client` construction — nothing Phase 85/86/87 touched. User approved deferring this checkpoint, consistent with the project's established pattern (e.g. Phase 81, Phase 86)."
  - test: "Send a PUT to `/api/v1/pareceres/solicitacoes/{id}` (ParecerController.updateSolicitacao) with a body that supplies only `clienteId` (omitting `prazo`), against a solicitação that already has a `prazo` set. Confirm the existing `prazo` is preserved (not wiped to null) and the response is 200, not a 500."
    expected: "`prazo` survives the partial update unchanged; no uncaught exception on flush from a null `prioridade`."
    why_human: "This is the iteration-3 CR-01 fix for a genuine pre-existing data-loss bug (silently wiping a legal deadline field on any partial PUT). It has zero automated test coverage — `backend/src/test/java` contains only `NotificacaoServiceTest.java` and `RiscoPrazoServiceTest.java`; no controller-test harness exists for `ParecerController` in this codebase at all. Tier 1 (re-read) and Tier 2 (compile) confirm the guard is syntactically correct and shaped like the adjacent `clienteId`/`processoId` guards, but no request has actually exercised the runtime path."
  - test: "Fire two near-simultaneous `POST /api/v1/pareceres/solicitacoes/{id}/versoes` requests for the same `solicitacaoId` (e.g. two browser tabs or a small concurrency script) and confirm both succeed with distinct, sequential `numeroVersao` values (no duplicate/skipped version numbers)."
    expected: "The `PESSIMISTIC_WRITE` row lock (`ParecerSolicitacaoRepository.findByIdForUpdate`) serializes the two requests at the DB level; no `numeroVersao` collision occurs."
    why_human: "This iteration-3 WR-04 fix replaces a JVM `synchronized` block (which only serializes threads within one instance and never guaranteed real atomicity) with a genuine DB-level lock. It compiles and matches an existing proven pattern (`SystemSettingRepository.findByIdForUpdate`), but this project has no H2/Testcontainers integration-test setup, so the actual concurrent-transaction behavior against real PostgreSQL has never been exercised."
---

# Phase 87: Alertas de Eventos — Fase, Documento, Atribuição e Parecer — Verification Report

**Phase Goal:** O sistema notifica automaticamente o destinatário certo sempre que um processo muda de fase, um novo documento é adicionado, um processo é atribuído/reatribuído através de um novo formulário dedicado, ou um parecer é atribuído a um advogado.
**Verified:** 2026-07-09T15:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Methodology Note

This phase shipped across 4 plans (87-01 → 87-04, 3 waves) and then went through a **3-iteration adversarial code-review/auto-fix loop** (`87-REVIEW.md`/`.iter2.md`/`.iter3.md` + matching `87-REVIEW-FIX*.md`) that found and fixed 16 findings total (2 Critical in iter1, 1 Critical in iter2, 2 Critical + 5 Warning in iter3) — all materially changing the code *after* the four `*-SUMMARY.md` files were written. Per the task instructions, I treated the SUMMARY.md files as stale narrative and verified the **current committed code on `master`** directly.

`git log --oneline -60` confirms `master`'s HEAD is `b052c14` ("docs(87): finalize code review after 3-iteration auto-fix loop"), with every fix commit cited in `87-REVIEW-FIX.md`/`.iter2.md` present in its linear history (`dd5c1a1`, `601b44f`, `4e43534`, `d17f3f6`, `cc77529`, `3be4d52`, `ce6d1f0`, `8361ad6`, `8043f80`, `9fd80a6`, `015c433`, `396ab8d`, `7bdcf7f`, `b3532c5`, `8122c7d`, `c529b57`). This matters because `87-REVIEW-FIX.iter3.md` itself states the fast-forward to `master` did *not* complete at fix-time due to an unrelated worktree conflict — I confirmed independently via `git log`/`git status` that this was subsequently resolved and all 7 iteration-3 fix commits are now on `master`, not stranded on the `gsd-reviewfix/87-33200` branch.

`git status` shows uncommitted local modifications to `backend/pom.xml`, `UserPrincipal.java`, `ResourceController.java`, `ConflictCheckResponse.java`, `WorkflowResponse.java`, plus an untracked `backend/spotbugs-exclude.xml`. I diffed these: they are SpotBugs `ENTITY_MASS_ASSIGNMENT` remediation (`setId(null)` on unrelated create-endpoints) and exception-logging cleanup, touching none of the four notification trigger points or the reassignment endpoint. They do not affect this phase's verification and were left untouched.

I independently re-ran (not trusted from any report): `mvn -f backend/pom.xml -o -Dtest=NotificacaoServiceTest test` (20/20 green), `mvn -f backend/pom.xml -o -DskipTests compile` (BUILD SUCCESS, full backend), `npx tsc --noEmit -p tsconfig.json` in `web/` (only 3 pre-existing, unrelated `vitest` module-resolution errors), and `pnpm build` in `web/` (BUILD SUCCESS, all 23 routes including the dynamic `/processos/[id]` route that needed the new `<Suspense>` boundary).

## Goal Achievement

### ROADMAP Success Criteria (authoritative contract)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Quando um processo entra numa nova fase, o responsável do processo (e ADMIN) recebe uma notificação com link direto para o processo | ✓ VERIFIED | `ResourceController.createProcessoFase` (L1700-1730) calls `notificacaoService.notificarFaseEntrada(processo.getTenantId(), id, processo.getResponsavelId(), processo.getNumeroProcesso(), faseNome, "/processos/" + id + "?tab=fases")` immediately after `processoFaseRepository.save(pf)`. `NotificacaoService.notificarFaseEntrada` (L118-137) null-guards the responsável, isolates that primary call in try/catch (so a stale responsável can't block the fan-out), and unconditionally calls `notificarAdmins(...)`. Proven by passing tests `notificarFaseEntrada_responsavelNaoNulo_...` and `notificarFaseEntrada_responsavelNulo_...`. |
| 2 | Quando um novo documento é adicionado a um processo (ou a um cliente sem processo associado), o destinatário correto recebe uma notificação (mais ADMIN) | ✓ VERIFIED | `ResourceController.uploadDocumento` (L2593-2625), guarded by `replaceId == null` (genuinely new upload only). `if/else if` precedence: `saved.getProcessoId() != null` branch resolves the processo's `responsavelId` and calls `notificarDocumentoNovo`; else `saved.getClienteId() != null` branch resolves the team via tenant-scoped `clienteAdvogadoRepository`/`clienteAdministrativoRepository` lookups. Both branches pass `atorId` (the uploader) for exclusion. `NotificacaoService.notificarDocumentoNovo` (L186-204) dedups via `LinkedHashSet`, excludes `atorId` from both the primary loop and the ADMIN fan-out (8-arg `notificarAdmins` overload), and isolates each destinatario in try/catch. Proven by 3 passing tests (actor exclusion, dedup, admin-as-actor exclusion). |
| 3 | Utilizador com permissão adequada reatribui o responsável de um processo através de um novo formulário/interface dedicado; o backend valida o tenant do novo responsável; o novo responsável (e ADMIN) recebe de imediato uma notificação | ✓ VERIFIED | Backend: `PUT /processos/{id}/atribuir` (`ResourceController.atribuirResponsavel`, L997-1059) gated `@PreAuthorize("hasAuthority('processos:manage')")`, validates processo tenant ownership (404) and new responsável tenant+`ativo` ownership (400), no-op guards a same-user reassignment, writes an `AuditLog` row, then calls `notificarProcessoAtribuido`. Frontend: `ReatribuirResponsavelControl` (page.tsx L2359-2512) wired into the Responsável `dd` gated by `canManageProcessos` (L948-955), implementing the Dialog->AlertDialog two-step flow via `useReatribuirResponsavel` (use-processos.ts L650-670), whose `onSuccess` invalidates `["processos","workflow",processoId]` — the actual source of the rendered name (`getWorkflow`, L1377-1403, is a real DB-backed query, not a stub). |
| 4 | Advogado atribuído a um parecer — na criação ou numa reatribuição posterior — recebe uma notificação de atribuição (mais ADMIN) | ✓ VERIFIED | `ParecerController.createSolicitacao` (L180-183, guarded by `saved.getAdvogadoId() != null`) and `.atribuirAdvogado` (L337-338, after a no-op guard and tenant+`ativo`-validated `advogadoId`) both call `notificarParecerAtribuido`, passing `principal.getUserId()` as the excluded actor. `atribuirAdvogado` never reads the previous `advogadoId` before overwriting it (L320), so the outgoing advogado is never notified, matching REQUIREMENTS.md's Out-of-Scope decision. |

**Score:** 4/4 ROADMAP criteria verified.

### Additional Plan-Level / Review-Hardening Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 5 | `NotificacaoService` is the sole write path for notification creation (no controller/service calls `notificacaoRepository.save/saveAll` directly); all 4 wrappers compose `criar(...)` + `notificarAdmins(...)` | ✓ VERIFIED | `grep -rn "notificacaoRepository\.(save\|saveAll)" backend/src` returns exactly 3 production call sites, all inside `NotificacaoService.java` itself (L65, L251, L261); every other match is a Mockito test stub. |
| 6 | A stale/orphaned recipient (deleted or deactivated user) in any single notification target never rolls back the triggering action nor blocks the rest of the fan-out (best-effort, per-recipient isolation) | ✓ VERIFIED | Confirmed in code: `notificarFaseEntrada`, `notificarDocumentoNovo` (per-destinatario), `notificarAdmins` (per-admin), `notificarProcessoAtribuido`, and `notificarParecerAtribuido` each wrap their primary/loop `criar(...)` call in `try { ... } catch (IllegalArgumentException ex) { log.warn(...); }`. This closes CR-01 (iter1, controller-level try/catch), CR-01 (iter2, moved isolation inside the service + extended to `notificarAdmins`), and CR-02 (iter3, extended to the two methods that were still missing it). All 20 `NotificacaoServiceTest` tests pass, including the 2 new iter3 regression tests exercising an invalid recipient id. |
| 7 | The `?tab=fases` deep-link generated by the FASE_ENTRADA notification actually navigates to the Fases tab, both on first load and on same-route client-side navigation | ✓ VERIFIED | `TAB_KEYS` allow-list validates `searchParams.get("tab")` as the `useState` initializer (page.tsx L233-237) and a `React.useEffect` (L244-250, iter1 WR-03 fix) re-syncs `tab` whenever `searchParams` changes post-mount. `ProcessoDetailPage` wraps `ProcessoDetailContent` in `<Suspense>` (L219-221), which `pnpm build`'s successful generation of the dynamic `/processos/[id]` route confirms satisfies Next.js 16's requirement. |

**Combined score: 7/7 must-haves verified.**

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | 4 `notificar*` wrappers + 8-arg `notificarAdmins` overload with actor exclusion, per-recipient isolation | ✓ VERIFIED | All 4 methods present (L118, L142, L186, L211); 7-arg `notificarAdmins` delegates to 8-arg (L86-90); try/catch isolation present at every recipient touchpoint. |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` | Mockito tests proving fan-out, exclusion, null-guard, dedup, isolation | ✓ VERIFIED | 20 `@Test` methods, all passing (re-ran independently: `Tests run: 20, Failures: 0, Errors: 0`). |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | 3 notification triggers + reassignment endpoint + injected `NotificacaoService` | ✓ VERIFIED | Field L73; triggers at L990 (createProcesso), L1055 (atribuirResponsavel), L1724 (createProcessoFase), L2605/L2618 (uploadDocumento, 2 branches); endpoint L997-1059. |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` | 2 PARECER_ATRIBUIDO triggers + injected `NotificacaoService` | ✓ VERIFIED | Field L49; triggers at L181 (createSolicitacao) and L337 (atribuirAdvogado). |
| `backend/src/main/java/com/lexcv/dtos/UserSummaryResponse.java` | Minimal `{id, nome, ativo}` projection for tenant-scoped pickers | ✓ VERIFIED | Present with all 3 fields, `ativo` added per WR-01 iter2 fix. |
| `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` | `findByIdForUpdate` with `PESSIMISTIC_WRITE` lock | ✓ VERIFIED | Present (L22-24), used by `createVersao` (L475), which is `@Transactional` (L465). |
| `web/src/hooks/use-processos.ts` | `useReatribuirResponsavel` mutation hook | ✓ VERIFIED | L650-670: calls `PUT /processos/{id}/atribuir`, invalidates `list`+`detail`+`workflow` query keys exactly as specified. |
| `web/src/hooks/use-users.ts` | `useTenantUsers` hook, tenant-scoped, SSR-guarded | ✓ VERIFIED | New file; `enabled = typeof window !== "undefined"` guard present (WR-02 iter2 fix); `ativo?: boolean` on `TenantUserOption`. |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | `ReatribuirResponsavelControl` + `?tab=` deep-link init | ✓ VERIFIED | Component at L2359-2512, wired at L948-955 gated by `canManageProcessos`; tab logic at L233-250; `Suspense` boundary at L219-221. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ResourceController.createProcessoFase` | `NotificacaoService.notificarFaseEntrada` | Direct call after `.save()`, wrapped in controller-level try/catch | ✓ WIRED | L1723-1728 |
| `ResourceController.uploadDocumento` | `NotificacaoService.notificarDocumentoNovo` | Two guarded branches (processo/cliente precedence) inside `if (replaceId == null)` | ✓ WIRED | L2595-2624 |
| `ResourceController.createProcesso` / `.atribuirResponsavel` | `NotificacaoService.notificarProcessoAtribuido` | Direct call after `.save()` | ✓ WIRED | L989-991, L1055-1056 |
| `ResourceController.atribuirResponsavel` | tenant + `ativo` validation | `userRepository.findById` + `Boolean.FALSE.equals(responsavel.getAtivo())` guard | ✓ WIRED | L1021-1030 |
| `ParecerController.createSolicitacao` / `.atribuirAdvogado` | `NotificacaoService.notificarParecerAtribuido` | Direct call after `.save()` + AuditLog | ✓ WIRED | L180-183, L337-338 |
| `useReatribuirResponsavel` | `PUT /processos/{id}/atribuir` | `apiFetch` with method PUT, body `{responsavelId}` | ✓ WIRED | use-processos.ts L654-662 |
| `useReatribuirResponsavel.onSuccess` | query key `["processos","workflow",processoId]` | `invalidateQueries` | ✓ WIRED | use-processos.ts L668 |
| `ReatribuirResponsavelControl` | Responsável `dd` (Workflow card) | Conditional render on `canManageProcessos` | ✓ WIRED | page.tsx L948-955 |
| `searchParams.get("tab")` | `tab` initial + re-synced state | `TAB_KEYS` allow-list validation, `useState` initializer + `useEffect` | ✓ WIRED | page.tsx L233-250 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| Responsável name in Workflow card | `workflow.data.responsavelNome` | `GET /processos/{id}/workflow` → `ResourceController.getWorkflow` (L1377-1403): `processoRepository.findById` + `userRepository.findById`, both real JPA queries, tenant-checked | Yes | ✓ FLOWING |
| `t_notificacao` rows created by all 4 triggers | `Notificacao` entity | `NotificacaoService.criar()` → `notificacaoRepository.save(n)` (real JPA persist, entity built from caller-supplied real ids/strings, not static/empty) | Yes | ✓ FLOWING |
| "Reatribuir"/"Novo Prazo" user pickers | `tenantUsers.data` | `GET /users` → `ResourceController.listTenantUsers` (L1071-1077): `userRepository.findByTenantId(tenantId)`, real DB query, tenant-scoped | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `NotificacaoService` wrappers behave correctly (fan-out, exclusion, null-guard, dedup, per-recipient isolation) under real execution, not just static reading | `mvn -f backend/pom.xml -o -Dtest=NotificacaoServiceTest test` | `Tests run: 20, Failures: 0, Errors: 0` | ✓ PASS |
| Full backend (all Phase 87 + 16 review-fix changes) compiles cleanly | `mvn -f backend/pom.xml -q -o -DskipTests compile` | Exit 0, empty output (clean success) | ✓ PASS |
| Frontend type-checks with no new errors from Phase 87 files | `npx tsc --noEmit -p tsconfig.json` (in `web/`) | Only 3 pre-existing, unrelated `vitest` module-resolution errors (baseline, confirmed by file path — none touch `use-processos.ts`, `use-users.ts`, or `processos/[id]/page.tsx`) | ✓ PASS |
| Frontend production build succeeds, including the Suspense-gated `/processos/[id]` route required for `useSearchParams()` | `pnpm build` (in `web/`) | `✓ Compiled successfully`, all 23 routes generated, exit 0 | ✓ PASS |
| Single-write-path invariant (no notification bypass of `NotificacaoService`) | `grep -rn "notificacaoRepository\.(save\|saveAll)" backend/src` | 3 matches, all inside `NotificacaoService.java`; remainder are test mocks | ✓ PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` convention exists in this project (no `scripts/` directory at all), and neither the PLAN nor SUMMARY files for this phase declare any probe scripts.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|--------------|--------|----------|
| NOTF-15 | 87-01, 87-02, 87-04 | Responsável do processo é alertado quando o processo entra numa nova fase | ✓ SATISFIED | ROADMAP SC1; `notificarFaseEntrada` + `createProcessoFase` trigger + working `?tab=fases` deep-link |
| NOTF-16 | 87-01, 87-02 | Responsável do processo (ou equipa do cliente) é alertado quando um novo documento é adicionado | ✓ SATISFIED | ROADMAP SC2; `notificarDocumentoNovo` + `uploadDocumento` if/else-if precedence |
| NOTF-17 | 87-02, 87-04 | Utilizador com permissão adequada pode reatribuir o responsável de um processo através de um novo fluxo dedicado | ✓ SATISFIED | ROADMAP SC3; `PUT /processos/{id}/atribuir` (manage-gated) + `ReatribuirResponsavelControl` Dialog→AlertDialog flow |
| NOTF-18 | 87-01, 87-02 | Utilizador é alertado quando é definido ou reatribuído como responsável de um processo | ✓ SATISFIED | ROADMAP SC3; `notificarProcessoAtribuido` called from both `createProcesso` and `atribuirResponsavel` |
| NOTF-19 | 87-01, 87-03 | Advogado é alertado quando lhe é atribuído um parecer | ✓ SATISFIED | ROADMAP SC4; `notificarParecerAtribuido` called from both `createSolicitacao` and `atribuirAdvogado` |

**Cross-reference with REQUIREMENTS.md:** all 5 requirement IDs mapped to Phase 87 (`NOTF-15` through `NOTF-19`, lines 65-69) are claimed by at least one of the 4 plans' `requirements:` frontmatter, and the union of all 4 plans' declared requirements is exactly `{NOTF-15, NOTF-16, NOTF-17, NOTF-18, NOTF-19}` — no orphaned requirements, no unclaimed IDs. (REQUIREMENTS.md's own checkbox/status column still shows "Pending" for all 5 — that column is milestone-level bookkeeping updated at milestone close, not a phase-verification concern.)

### Anti-Patterns Found

None. Scanned all phase-modified files (`NotificacaoService.java`, `NotificacaoServiceTest.java`, `ResourceController.java`, `ParecerController.java`, `UserSummaryResponse.java`, `ParecerSolicitacaoRepository.java`, `use-processos.ts`, `use-users.ts`, `processos/[id]/page.tsx`) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` (word-boundary pattern, to avoid false positives) — zero matches. (An initial case-insensitive substring scan without word boundaries falsely flagged Portuguese words "todo"/"método", which contain "todo" as a substring; re-scanned with `\bTODO\b` etc. and confirmed no real markers.) No empty-return stubs, no hardcoded-empty-data props, no console.log-only handlers found in any reviewed trigger/component code.

`deferred-items.md` documents two legitimate, well-reasoned out-of-scope items (SpotBugs/FindSecBugs cannot run against Java 23 bytecode — pre-existing, project-wide tooling gap; 22 pre-existing frontend lint issues in unrelated files) — both correctly out of this phase's scope, neither blocking.

### Human Verification Required

See frontmatter `human_verification` for full detail. Three items:

1. **Plan 87-04 Task 4 — full live E2E walkthrough** (Reatribuir flow, notification rows in `t_notificacao`, `?tab=fases` deep-link). This is the phase's own explicit `checkpoint:human-verify` gate, not yet executed due to an unrelated environment gap (`MINIO_ENDPOINT` not substituted in this session, blocking Spring context startup) — deferred with user approval, consistent with this project's established pattern.
2. **`ParecerController.updateSolicitacao` partial-update behavior** (the iteration-3 `CR-01` fix for a real pre-existing data-loss bug on `prazo`/`prioridade`). Zero automated test coverage exists for any controller in this codebase — worth one manual partial-PUT request to confirm the runtime behavior matches the guard's intent.
3. **Concurrent `numeroVersao` assignment** (the iteration-3 `WR-04` fix replacing a JVM `synchronized` block with a DB-level `PESSIMISTIC_WRITE` lock). No H2/Testcontainers setup exists in this project to exercise real concurrent transactions; worth a quick two-tab manual test.

### Gaps Summary

No code gaps were found. Every ROADMAP Success Criterion, every PLAN-level must-have truth across all 4 plans, and every one of the 16 code-review findings fixed across the 3-iteration review loop are present, correctly wired, and independently confirmed via a fresh test run (20/20), a fresh full-backend compile, a fresh `tsc` pass, and a fresh production `pnpm build` — not by trusting any SUMMARY.md or REVIEW-FIX.md narrative. The only reason this phase is not marked `passed` is that Plan 87-04's own blocking human-verification checkpoint (live browser walkthrough) has never actually been executed, for a documented and unrelated environment reason (MinIO env var substitution), plus two review-fixed defects (a real data-loss bug and a concurrency-correctness change) that have zero automated test coverage in this codebase and would benefit from a quick manual pass. None of these three items point to a defect found in the code during this verification — they are genuine coverage gaps in *verification depth*, not evidence of unmet functionality.

---

*Verified: 2026-07-09T15:45:00Z*
*Verifier: Claude (gsd-verifier)*
