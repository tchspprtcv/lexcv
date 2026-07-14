---
phase: 96-notf-26-snooze-de-lembrete-de-prazo
verified: 2026-07-14T17:54:19Z
status: human_needed
score: 16/16 code-level must-haves verified (2 live-execution items require human/CI verification)
overrides_applied: 0
human_verification:
  - test: "Live IT execution: NotificacaoRepositoryIT's two new snooze-visibility tests (96-02) against a real postgres:16-alpine Testcontainers instance"
    expected: "countByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_contaNuncaAdiadaEAdiadaJaExpirada asserts count==2 (null-snooze + past-snooze counted, future-snooze excluded); findByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_devolveApenasNuncaAdiada asserts returned list size==1"
    why_human: "Blocked in this sandbox by a reproducible Docker Desktop npipe/Testcontainers 1.20.4 incompatibility (independently reproduced during this verification: `docker info`/`docker ps` fail with 'failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine'). Test code independently confirmed to compile (`mvn test-compile -Dtest=NotificacaoRepositoryIT` = BUILD SUCCESS) and to match the plan's exact fixture/assertion spec. This is the same recurring, documented environment gap affecting Phases 91/93/94/96 — not a code defect. Requires a machine/CI with a working Docker daemon."
  - test: "Live browser/backend E2E: the 5 checks in 96-04-PLAN.md's <how-to-verify> section (snooze control appears with 1/3/7 presets; badge drops + item leaves bell preview; item stays on /notificacoes with 'Adiado até DD/MM'; PRAZO_VENCIDO shows no control and API returns 400; cross-recipient snooze returns 404)"
    expected: "All 5 checks pass as specified in 96-04-PLAN.md"
    why_human: "Blocked by this project's known, pre-existing MINIO_ENDPOINT environment gap (backend/.env has no MINIO_* vars; MinioConfig fails full-context startup with no defaults) documented across Phases 87/89/91/92/93/95/96. The 96-04 checkpoint was auto-passed per parallelization.skip_checkpoints=true (autonomous milestone execution) rather than actually run live — per 96-04-SUMMARY.md's own account. Everything checkable without a live backend (DOM structure, gating logic, category/lida independence) was independently re-verified as code-correct during this verification session (see Goal Achievement section)."
---

# Phase 96: NOTF-26 — Snooze de Lembrete de Prazo Verification Report

**Phase Goal:** Utilizador pode adiar um lembrete de prazo por um período pré-definido, e a notificação reaparece automaticamente como não lida quando esse período termina, sem ser recriada prematuramente pelo job diário.
**Verified:** 2026-07-14T17:54:19Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

All code-level must-haves are independently verified against the actual source (not SUMMARY.md claims). `mvn -DskipTests package` compiles cleanly, the full backend unit suite (65+ tests, including 39 in `NotificacaoServiceTest`) passes with 0 failures/errors, and `pnpm build`/production build succeed. The two remaining items (real-Postgres IT execution and live-browser E2E) are blocked by the same pre-existing, well-documented sandbox environment gaps (Docker npipe incompatibility; `MINIO_ENDPOINT` missing) that have affected every phase this milestone — independently reproduced during this verification (see below), not a code defect.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | (SC1) User can snooze a prazo reminder for a pre-defined duration (1/3/7 days) via a UI control | ✓ VERIFIED | `notificacao-snooze-control.tsx`: Popover trigger + `RadioGroup` with exactly 3 `RadioGroupItem`s (values "1"/"3"/"7"), no date picker; confirm button calls `useSnoozeNotificacao().mutate({id, dias})`. |
| 2 | (SC2a) Snoozed notification disappears from the bell badge/unread list during the snooze period | ✓ VERIFIED | Backend: `countByTenantIdAndDestinatarioIdAndLidaFalse`/`findByTenantIdAndDestinatarioIdAndLidaFalse` JPQL both carry `AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :agora)`, called with `LocalDateTime.now()` from `contarNaoLidas`/`marcarTodasLidas`. Frontend: `notification-bell.tsx` computes `visibleNotificacoes` filtering out `snoozedUntil > now` before both the empty-state check and the rendered/sliced list. |
| 3 | (SC2b) Snoozed notification stays visible/searchable on `/notificacoes` | ✓ VERIFIED | `buscarPorFiltros` (native, history query) confirmed byte-for-byte unchanged — no `snoozed` token anywhere inside it. `notificacoes/page.tsx`'s `useNotificacoes(...)` call/filters untouched; `NotificacaoRow` renders an "Adiado até DD/MM" `Badge` (`variant="gray"`) when `snoozedUntil` is a future timestamp. |
| 4 | (SC3) Snooze reappears automatically as unread once the period elapses, without user action | ✓ VERIFIED | The visibility predicate is a pure time comparison (`<= :agora`) re-evaluated on every read — no write/job needed for reappearance. `marcarTodasLidas` loads via the filtered query, so a future-snoozed row is never flipped to `lida=true` while snoozed, meaning it is still `lida=false` the instant `snoozedUntil` elapses. |
| 5 | (SC4) Running the daily job during the snooze period does not recreate/duplicate the snoozed notification | ✓ VERIFIED | `AlertasDiariosJob.java` has a byte-for-byte-empty diff from the first Phase-96 commit to HEAD (`git diff b5b8f9c HEAD -- .../AlertasDiariosJob.java` = empty); its last git-history touch is a Phase-94 commit. Its existence-check (`existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria`) ignores `snoozedUntil` entirely, so a snoozed row still counts as "exists" and blocks recreation by construction. |
| 6 | PATCH `/notificacoes/{id}/snooze` validates `dias∈{1,3,7}`, is dual-scoped (tenant+destinatario, 404 on mismatch), and blocks PRAZO_VENCIDO (400) | ✓ VERIFIED | `NotificacaoService.snooze()` source read directly; order of checks matches spec exactly. 4 Mockito tests (`snooze_presetValido...`, `snooze_diasInvalido...`, `snooze_naoPertenceAoDestinatario...`, `snooze_categoriaPrazoVencido...`) all present and passing (39/39 in `NotificacaoServiceTest`, confirmed via `mvn test -Dtest=NotificacaoServiceTest` run in this session, not just SUMMARY claim). |
| 7 | The snooze control is NOT nested inside the row's `<Link>` anchor in the bell dropdown (the plan-checker-caught blocker) | ✓ VERIFIED (independently re-checked) | Read `notification-bell.tsx` directly: `<Link>` (line 130) closes at line 138 wrapping only `<NotificacaoConteudo n={n} />`; `<NotificacaoSnoozeControl notificacao={n} />` is at line 156, inside a sibling `flex-shrink-0` `<div>` (opened at line 143) that is a sibling of — not a descendant of — the `<Link>`. No `<button>`/Popover trigger is a descendant of `<a>`. |
| 8 | Snooze control visibility is independent of `lida` (both bell and history) | ✓ VERIFIED | `NotificacaoSnoozeControl`'s only early-return gate is `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS.includes(notificacao.categoria)` — no reference to `lida` anywhere in the component. In the bell, it renders unconditionally at line 156 (only the sibling Check button at 144-155 is gated by `!n.lida`). In `notificacoes/page.tsx`, it renders at line 321, outside the `{!lida && (...)}` block at 307-320. |
| 9 | PRAZO_VENCIDO is blocked from snooze end-to-end (backend rejects 400, UI hides control) | ✓ VERIFIED | Backend: `CategoriaNotificacao.PRAZO_VENCIDO(false)` is the sole `silenciavel=false` entry; `isSilenciavelCategoria("PRAZO_VENCIDO")` returns `false`, tripping `snooze()`'s `IllegalArgumentException` → controller 400 (unit-tested). Frontend: `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS = ["PRAZO_VENCIDO"]` (from Phase 93, reused verbatim) is the component's sole hide condition — exact string match to the backend enum name. |
| 10 | Real-Postgres proof (Testcontainers) that the visibility predicate hides future-snoozed rows and reveals past-snoozed rows | ⚠ CODE-VERIFIED / EXECUTION BLOCKED | `NotificacaoRepositoryIT` gained `persistirComSnooze(...)` + 2 new tests matching the plan's exact fixture/assertion spec (count==2 test, find-size==1 test). `mvn test-compile -Dtest=NotificacaoRepositoryIT` = BUILD SUCCESS (independently run this session). Actual execution blocked: `docker info`/`docker ps` fail in this sandbox with `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine` (independently reproduced, not just quoted from SUMMARY) — same Testcontainers 1.20.4/Docker Desktop npipe gap as Phases 91/93/94. → **human_needed**. |
| 11 | Live browser/backend E2E of the full snooze flow (5 checks in 96-04-PLAN.md) | ⚠ NOT RUN | 96-04 is a `checkpoint:human-verify` task, auto-passed per `parallelization.skip_checkpoints=true` per its own SUMMARY — it was never actually exercised against a running app. Blocked by the project's recurring `MINIO_ENDPOINT` gap for full-context/live verification. → **human_needed**. |

**Score:** 9/9 fully-verifiable-now truths VERIFIED; 2 truths are code-correct but require live execution (Docker/browser) not available in this sandbox.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Notificacao.java` | nullable `snoozedUntil` column, `@Setter`, mirrors `lida` mutability, not in `uk_notificacao_dedup` | ✓ VERIFIED | Field present at line 62-64; unique constraint (line 14-16) lists only original 5 columns — `snoozed_until` absent, as required. |
| `backend/migrations/96-add-notificacao-snoozed-until.sql` | manual production migration | ✓ VERIFIED | Present; contains `ALTER TABLE t_notificacao ADD COLUMN snoozed_until TIMESTAMP;` with required-manual-migration header comment matching the 93 precedent style. |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | snoozedUntil predicate on the two `LidaFalse` queries only | ✓ VERIFIED | Both queries converted to explicit `@Query` JPQL with `agora` param and the predicate; `buscarPorFiltros` and `findByIdAndTenantIdAndDestinatarioId` confirmed unchanged. |
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | `snooze()` method, find-then-mutate-then-save, 404-empty-Optional, PRAZO_VENCIDO block | ✓ VERIFIED | `public Optional<Notificacao> snooze(UUID, UUID, UUID, int)` present at line 426, matches spec exactly including the required forward-risk code comment. |
| `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` | `PATCH /{id}/snooze`, dual tenant+destinatario scoped | ✓ VERIFIED | Present at line 156-171, `hasAuthority('notificacoes:view')`-gated, tenant/destinatario only from `getTenantId()`/`getUserId()`. |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` | 4 Mockito tests | ✓ VERIFIED | All 4 present (valid preset, invalid dias, not-owned, PRAZO_VENCIDO), all passing (39/39 total in this test class, this session's own `mvn test` run). |
| `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` | Testcontainers snooze-visibility tests | ✓ VERIFIED (compiles) / ⚠ execution blocked | `persistirComSnooze` helper + 2 tests present, matching spec; `test-compile` succeeds; live run blocked by Docker environment (see Truth #10). |
| `web/src/types/notificacoes.ts` | `snoozedUntil: string \| null` | ✓ VERIFIED | Present at line 22. |
| `web/src/hooks/use-notificacoes.ts` | `useSnoozeNotificacao` mutation | ✓ VERIFIED | Present at line 81-94, PATCHes `.../snooze` with `{dias}`, invalidates `["notificacoes"]`. |
| `web/src/components/shared/notificacao-snooze-control.tsx` | reusable Popover+RadioGroup control | ✓ VERIFIED | Present, self-hides only for `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS`, no `lida` reference. |
| `web/src/components/shared/notification-bell.tsx` | snooze control wired outside Link + presentation filter | ✓ VERIFIED | Confirmed via direct read; see Truths #2, #7, #8. |
| `web/src/app/(dashboard)/notificacoes/page.tsx` | snooze control (independent of lida) + "Adiado até" badge | ✓ VERIFIED | Confirmed via direct read; see Truths #3, #8. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `NotificacaoController.snooze` | `NotificacaoService.snooze` | dual-scoped delegation | ✓ WIRED | `notificacaoService.snooze(getTenantId(), getUserId(), id, dias)` at controller line 163. |
| `NotificacaoService.snooze` | `NotificacaoRepository.findByIdAndTenantIdAndDestinatarioId` | tenant+destinatario ownership load | ✓ WIRED | Called at service line 430; empty → `Optional.empty()`, never saved. |
| `NotificacaoService.snooze` | `CategoriaNotificacao.isSilenciavelCategoria` | PRAZO_VENCIDO snooze block | ✓ WIRED | Called at service line 444; throws before `setSnoozedUntil`/`save`. |
| `NotificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse` / `findBy...` | snoozedUntil visibility predicate | JPQL | ✓ WIRED | Predicate present in both queries; both call sites (`contarNaoLidas`, `marcarTodasLidas`) pass `LocalDateTime.now()`. |
| `NotificacaoSnoozeControl` | `useSnoozeNotificacao` | `mutate({id, dias})` on confirm | ✓ WIRED | `handleConfirm` calls `snooze.mutateAsync({id: notificacao.id, dias})` at line 41. |
| `NotificacaoSnoozeControl` | `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS` | hide for PRAZO_VENCIDO | ✓ WIRED | `if (NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS.includes(notificacao.categoria)) return null;` at line 34. |
| `notification-bell` dropdown list | `snoozedUntil` | client-side presentation filter | ✓ WIRED | `visibleNotificacoes` filter at bell line 61-63, reused for both empty-state check and the sliced/mapped list. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `notification-bell.tsx` (`visibleNotificacoes`) | `list.data.content` (from `useNotificacoes`) | `GET /notificacoes` (unfiltered, real DB via `buscarPorFiltros`) | Yes — real query, no static stub | ✓ FLOWING |
| `notification-bell.tsx` (badge `count`) | `unread.data.count` (from `useNotificacoesUnreadCount`) | `GET /notificacoes/unread-count` → `countByTenantIdAndDestinatarioIdAndLidaFalse` (real DB, snoozedUntil-filtered) | Yes | ✓ FLOWING |
| `notificacoes/page.tsx` (`NotificacaoRow.snoozedUntil`) | `notificacao.snoozedUntil` (from `useNotificacoes` list) | `GET /notificacoes` → `buscarPorFiltros` (real DB, unfiltered) | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with all NOTF-26 changes | `mvn -f backend/pom.xml -DskipTests package` | BUILD SUCCESS, no output/errors | ✓ PASS |
| `NotificacaoServiceTest` (incl. 4 new snooze tests) passes | `mvn -f backend/pom.xml test -Dtest=NotificacaoServiceTest` | `Tests run: 39, Failures: 0, Errors: 0` | ✓ PASS |
| Full backend unit suite passes (no regressions) | `mvn -f backend/pom.xml test` | Exit 0; surefire reports show 0 failures/errors across all test classes | ✓ PASS |
| `NotificacaoRepositoryIT` (incl. 2 new snooze tests) compiles | `mvn -f backend/pom.xml test-compile -Dtest=NotificacaoRepositoryIT` | BUILD SUCCESS | ✓ PASS |
| Frontend lints clean on phase-96 files | `cd web && pnpm lint` | Exit 1 overall (6 pre-existing errors in `dashboard-shell.tsx` / `processos/[id]/page.tsx`, both predating Phase 96 by weeks — git-blamed to 2026-06-21 and 2026-07-09 commits); **zero** errors/warnings in any of the 5 phase-96 files | ✓ PASS (scoped to phase files) |
| Frontend production build succeeds | `cd web && pnpm build` | `✓ Compiled successfully`, all 24 routes generated including `/notificacoes` | ✓ PASS |
| `AlertasDiariosJob.java` untouched by Phase 96 | `git diff b5b8f9c HEAD -- .../AlertasDiariosJob.java` | Empty diff; last touching commit is Phase 94 (`9572030`) | ✓ PASS |
| `uk_notificacao_dedup` untouched | grep on `@Table(... uniqueConstraints ...)` | Still lists only `tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria` — no `snoozed_until` | ✓ PASS |

### Probe Execution

No `scripts/*/tests/probe-*.sh` probes declared or discovered for this phase. SKIPPED (no probes applicable — this phase uses Maven tests + Mockito + Testcontainers + `pnpm lint`/`build`, all covered above).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|-------------|-------------|--------|----------|
| NOTF-26 | 96-01, 96-02, 96-03, 96-04 | Utilizador pode adiar (snooze) um lembrete de prazo por um período pré-definido, reaparecendo automaticamente depois do período | ✓ SATISFIED (code) / pending live human confirmation | Full backend write path (96-01), read-side visibility (96-01 code, 96-02 IT code), frontend UX (96-03) all directly verified in source; live E2E (96-04) not yet executed — see human_verification. |

No orphaned requirements: REQUIREMENTS.md maps NOTF-26 → Phase 96 exclusively, and all 4 plans declare `requirements: [NOTF-26]`.

### Anti-Patterns Found

None. Scanned all 12 phase-96-modified files for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER` (case-sensitive) and lowercase `placeholder|coming soon|not yet implemented|not available`. Two grep hits were false positives, manually confirmed: `TODOS` (Portuguese "all", substring-matches `TODO`) in a code comment in `NotificacaoService.java`, and the word "placeholders" used in its ordinary English sense (describing MinIO env-var placeholders) in a `NotificacaoRepositoryIT.java` Javadoc comment — neither is a debt marker.

### Human Verification Required

### 1. Real-Postgres Testcontainers execution (96-02)

**Test:** Run `mvn -f backend/pom.xml verify -Dit.test=NotificacaoRepositoryIT` (or the full `mvn verify`) on a machine/CI with a working Docker daemon.
**Expected:** `countByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_contaNuncaAdiadaEAdiadaJaExpirada` asserts count==2; `findByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_devolveApenasNuncaAdiada` asserts list size==1 — both passing against real PostgreSQL.
**Why human:** This sandbox's Docker Desktop cannot be reached via npipe by Testcontainers 1.20.4 (`docker info`/`docker ps` fail with `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`, independently reproduced during this verification). Test code is confirmed to compile and match spec; only live execution is blocked.

### 2. Live browser/backend E2E of the snooze flow (96-04)

**Test:** Follow the 5 checks in `96-04-PLAN.md`'s `<how-to-verify>` section against a running backend + frontend with a real Postgres/MinIO stack: (1) snooze control shows 1/3/7 presets, (2) snoozing drops the bell badge and removes the item from the bell preview, (3) the item stays on `/notificacoes` with an "Adiado até DD/MM" badge, (4) PRAZO_VENCIDO shows no control and a direct PATCH returns 400, (5) a cross-recipient snooze attempt returns 404.
**Expected:** All 5 checks pass as specified.
**Why human:** Requires a live browser + a fully-booted backend (MinIO configured) — this project's recurring `MINIO_ENDPOINT` sandbox gap prevents a full-context Spring Boot startup here, and the 96-04 checkpoint was auto-passed by config rather than actually exercised (per its own SUMMARY.md). All statically-checkable parts of this claim (DOM structure not nesting the trigger inside `<Link>`, the control's independence from `lida`, the client-side snooze filter, the backend's 400/404 logic) were independently re-verified as code-correct in this session — only the live, integrated runtime behavior remains open.

### Gaps Summary

No code-level gaps. Every artifact, key link, and observable truth that can be verified without a live Docker daemon or a live browser/backend was independently re-derived from source (not taken from SUMMARY.md) and confirmed correct — including the two properties this verification was specifically asked to double-check: (1) `AlertasDiariosJob.java`/`uk_notificacao_dedup` are provably untouched (empty git diff since the phase's first commit), and (2) the snooze control in `notification-bell.tsx` is genuinely NOT nested inside a `<Link>` anchor (the plan-checker's blocker is durably fixed, re-confirmed by direct source inspection, not by trusting the SUMMARY's narrative). The only open items are live-execution verifications (Testcontainers IT run, and the 96-04 live E2E checklist) blocked by pre-existing, well-documented sandbox environment gaps common to this entire milestone, not by any defect in Phase 96's code.

---

*Verified: 2026-07-14T17:54:19Z*
*Verifier: Claude (gsd-verifier)*
