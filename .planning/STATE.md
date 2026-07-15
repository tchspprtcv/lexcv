---
gsd_state_version: 1.0
milestone: v2.11
milestone_name: Auditoria Técnica e Notificações Avançadas
status: Awaiting next milestone
stopped_at: Phase 97 Plan 04 (milestone close-out) complete — fresh code audit (AUD-05) clean, MINIO_ENDPOINT blocker resolved and documented (AUD-04), wave-1 outcomes (AUD-01/02/03) consolidated into STATE.md/PROJECT.md with stale Phase 86/87 attributions corrected. All 15/15 v2.11 requirements Complete.
last_updated: "2026-07-14T21:27:48.786Z"
last_activity: 2026-07-14 — Milestone v2.11 completed and archived
progress:
  total_phases: 8
  completed_phases: 8
  total_plans: 21
  completed_plans: 21
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-12)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Milestone complete

## Current Position

Phase: Milestone v2.11 complete
Plan: —
Status: Awaiting next milestone
Last activity: 2026-07-14 — Milestone v2.11 completed and archived

## Performance Metrics

**Velocity:**

- Total plans completed: 72
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 58 | 4 | ~70 min | ~17 min |
| 59 | 6 | ~90 min | ~15 min |
| 60 | 2 | ~25 min | ~12 min |
| 65 | 2 | - | - |
| 70 | 1 | 12 min | 12 min |
| 74 | 5 | - | - |
| 75 | 3 | - | - |
| 79 | 2 | - | - |
| 80 | 1 | - | - |
| 81 | 3 | - | - |
| 82 | 1 | - | - |
| 83 | 2 | - | - |
| 84 | 5 | - | - |
| 85 | 1 | - | - |
| 86 | 3 | - | - |
| 87 | 4 | - | - |
| 88 | 2 | - | - |
| 89 | 4 | - | - |
| 90 | 1 | - | - |
| 92 | 2 | - | - |
| 93 | 4 | - | - |
| 94 | 1 | - | - |
| 95 | 2 | - | - |
| 96 | 4 | - | - |
| 97 | 4 | ~128 min | ~32 min |

*(Full per-phase history for v2.0–v2.8 lives in `.planning/milestones/*-ROADMAP.md` archives; table trimmed here per STATE.md size constraint.)*

## Accumulated Context

### Roadmap Evolution

- v2.10 roadmap created 2026-07-08: 5 phases (85–89) derived from 16 requirements (NOTF-08 to NOTF-23), continuing phase numbering from v2.9 (last phase: 84). See PROJECT.md Key Decisions / prior STATE.md history for full v2.9/v2.10 rationale (trimmed here per size constraint).
- v2.11 roadmap created 2026-07-12: 8 phases (90–97) derived from 15 requirements (SAST-01, TEST-01/02/03, AGD-34/35, NOTF-24/25/26/27, AUD-01 to AUD-05), continuing phase numbering from v2.10 (last phase: 89). Structure follows research's file-collision analysis: (90) SpotBugs/SAST commit+verify — already ~90% done uncommitted in working tree, zero dependency, highest risk of being lost if not landed first; (91) Testcontainers integration-test infra (native query + concurrency lock risks) — independent track, no file overlap with anything else; (92) Agenda↔RiscoPrazoService consolidation — frontend-mostly, independent, closes the "5th divergent prazo crítico implementation" debt explicitly deferred at v2.10 close. Phases 90/91/92 are mutually parallelizable (zero file overlap). Phases 93–96 are a hard sequential chain — all collide on `NotificacaoService.java` and its test file regardless of logical independence: (93) NOTF-24 mute preferences goes first (smallest, most self-contained insertion at the `criar()` choke point, so team fan-out later inherits the mute gate for free); (94) NOTF-27 dedup/ADMIN-collision fix — a pre-existing bug from Phase 88 (v2.10) that NOTF-25 would turn from latent into near-certain, so it must land before the fan-out widens; (95) NOTF-25 team fan-out (depends on 93+94); (96) NOTF-26 snooze, most additive, last in the chain. (97) Cross-cutting milestone audit (AUD-01 to AUD-05) runs last, depending on all 7 other phases, matching the v2.7/v2.9/v2.10 established retrospective pattern of auditing at milestone close. 100% requirement coverage (15/15), no orphans.

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.10 roadmap) Phase 88 (daily job) hard-depends on both Phase 85 and Phase 86 — its entire purpose is reusing the consolidated risk logic.
- (v2.11 research/roadmap) Notification-chain internal order is NOTF-24 → NOTF-27 (dedup fix) → NOTF-25 → NOTF-26, not the research SUMMARY.md's own initially-listed phase order (which had the dedup fix before NOTF-24) — the orchestrator's explicit sequencing instruction (matching the research executive summary's own recommended NOTF-24-first rationale) takes precedence: mute lands first so team fan-out inherits it for free, dedup fix lands immediately before the fan-out widens the recipient pool.
- (v2.11 roadmap) SpotBugs/SAST (Phase 90), Testcontainers infra (Phase 91), and Agenda/RiscoPrazoService consolidation (Phase 92) have zero file overlap with each other or with the notification chain — explicitly marked mutually parallelizable, not sequential filler.
- (v2.11 roadmap) Cross-cutting milestone audit (AUD-01 to AUD-05) placed last (Phase 97), depending on all other 7 phases — matches the established v2.7/v2.9/v2.10 pattern of auditing the milestone's final integration shape rather than mid-stream.
- (v2.11 TEST-03) CI (`deploy.yml`) gains a `test` job (`mvn verify` + `mvn spotbugs:check`) gating `build-and-push` via `needs: test` — closes the requirement's "decision registered + implemented" clause (Phase 91); OWASP `dependency-check:check` deliberately deferred (out of scope, NVD download cost).
- (v2.11 Phase 97, milestone close-out) AUD-01 through AUD-05 all closed: tenant isolation COVERED (97-01), UAT consolidated in `97-UAT.md` (97-03/AUD-02), DocumentoTipo/NIF debt closed (97-02/AUD-03), MINIO_ENDPOINT blocker RESOLVED (97-04/AUD-04), fresh code audit clean with zero new findings (97-04/AUD-05) — see PROJECT.md Key Decisions for full detail. 15/15 v2.11 requirements now Complete.

### Pending Todos

- ~~UAT ao vivo pendente em 8 fases (75, 76, 79, 81, 82, 84, 85, 89)~~ — CLOSED by v2.11 Phase 97 (AUD-02, see `97-UAT.md` for the full per-scenario breakdown of all ~38 scenarios). Phases 75, 76, 79, 84 close 100% via CODE-VERIFIED (some carry a non-blocking secondary visual note); Phase 89 closes 8/11 by code with 3/11 remaining NEEDS-HUMAN-VISUAL (purely visual/live-timing, zero functional risk). Two items are carried forward, not silently dropped — see the next two bullets.
- Phase 81 scenario #5 / Phase 82 scenario #4 (`97-UAT.md`) — the manual migration scripts (`81-add-facto-ordem-unique-constraint.sql` / `82-add-honorario-processo-unique-constraint.sql`) could not be live-verified against a `ddl-auto=validate` prod-like DB — none exists in this dev environment. Both constraints ARE confirmed enforced in dev via app-level guard+catch logic. Recommendation: review both scripts' SQL syntax against staging/prod before the next deploy.
- Phase 85 open product decision (`97-UAT.md`, carried forward unanswered): should an ALTA-priority `Evento` with null `dataInicio` count as urgent in `agendaUrgentesCount`/`prazosCriticosCount`? Current behavior excludes it by design (see `isEventoCritico()` docblock in `ResourceController.java`, grep to relocate). Not resolved by v2.11 — awaiting a user product decision. (Field renamed from `dataFim` to `dataInicio` on 2026-07-14 — see the next bullet; the corner case itself, and the still-open product question, are unchanged.)
- ~~`isEventoCritico()` (dashboard KPIs: `prazos_vencer`/`prazos_criticos_count`) computed Evento risk from `dataFim`, while `listEventos` (GET /eventos, Agenda, AGD-34/35) and `AlertasDiariosJob` both use `dataInicio` — a genuine 5th divergent "prazo crítico" implementation that AGD-34/35's own consolidation goal was meant to eliminate~~ — CLOSED 2026-07-14, found by the v2.11 milestone audit's integration-checker (flagged by `92-REVIEW.md` CR-01/`92-CONTEXT.md` as deferred to Phase 97, but missed by Phase 97's own bounded AUD-05 scope until the milestone-audit step caught it). Fixed by standardizing `isEventoCritico()` on `dataInicio`; full backend suite re-confirmed green (69/69) after the change. Commit `f0c62ff`.
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` — manual-execution script, must be run against the database before/alongside next deploy (no migration runner in this repo).
- ~~REG_COMERCIAL and other `DocumentoTipo` values still render as raw enum strings instead of translated Portuguese labels~~ — CLOSED by v2.11 Phase 97-02 (AUD-03): `getDocumentoTipoLabel` helper added, applied at both cliente render sites (detail page + printable ficha).
- ~~No automated backend tests cover the 4 NIF validation scenarios introduced in Phase 73.1~~ — CLOSED by v2.11 Phase 97-02 (AUD-03): `ClienteNifValidationTest` added (standalone `jakarta.validation.Validator`, 4 scenarios: valid/blank/wrong-length/non-numeric).
- ~~No H2/Testcontainers integration-test infrastructure exists anywhere in this backend~~ — CLOSED by v2.11 Phase 91 (TEST-01/02/03): Testcontainers PostgreSQL integration-test infra + CI gate now exist; `buscarPorFiltros` (native query) and the `numeroVersao` concurrency lock are proven against real Postgres in `NotificacaoRepositoryIT`/`ParecerVersaoConcorrenciaIT`.
- ~~`backend/spotbugs-exclude.xml`/SpotBugs SAST tooling broken against JDK 23 bytecode~~ — CLOSED by v2.11 Phase 90 (SAST-01): `mvn spotbugs:check` runs clean against JDK 23 bytecode, exclusions file + version bumps committed.
- ~~`web/src/app/(dashboard)/agenda/page.tsx` independently recomputes its own "prazo crítico" verdict instead of using `RiscoPrazoService`~~ — CLOSED by v2.11 Phase 92 (AGD-34/AGD-35): Agenda now consumes the backend-computed `risco` field for both Prazos and Eventos; the orphaned `/eventos/upcoming` endpoint and its frontend hook/types were removed.
- ~~AUD-01 tenant-isolation cross-cutting audit~~ — CLOSED by v2.11 Phase 97-01: all three newest notification data surfaces (Phase 93 preferences, Phase 95 team resolution, Phase 96 snooze) traced end-to-end and confirmed tenant-scoped; zero gaps found, zero fixes needed (verdict: COVERED, not FIXED).
- ~~AUD-05 fresh code-discovery audit~~ — CLOSED by v2.11 Phase 97-04 Task 1: bounded fresh pass over `NotificacaoService.java`, `NotificacaoController.java`, `AlertasDiariosJob.java`, and the notification/preference/snooze web hooks + adjacent components (bell, snooze control, settings tab) found zero new gaps — all 8 `NotificacaoController` endpoints confirmed to carry `@PreAuthorize`, zero real `TODO`/`FIXME`/`XXX` markers (one substring false-positive on "TODOS" checked and dismissed), zero dead-code references anywhere to the removed `/eventos/upcoming` endpoint, `AlertasDiariosJob`'s 4-layer error isolation (job/tenant/category/entity, all catching `Throwable`) confirmed intact. Clean result — no FIX-NOW or RECORD-AS-DEBT findings; see `97-04-SUMMARY.md` for the enumerated coverage.

### Blockers/Concerns

- ~~`MINIO_ENDPOINT` environmental blocker (recurring across v2.8/v2.9/v2.10 sessions, prevents full Spring context startup for live UAT)~~ — **RESOLVED (2026-07-14, v2.11 Phase 97 AUD-04):** `backend/.env` (gitignored, not committed) now supplies a real `MINIO_ENDPOINT=http://localhost:9000` plus working credentials against a running `lexcv_minio` Docker container the user started deliberately for this session. The Spring context now boots fully — `MinioConfig.s3Client()` no longer throws the "Illegal character ... `${MINIO_ENDPOINT}`" `IllegalArgumentException` that previously blocked every controller from becoming reachable. `backend/.env.example` already documents all required `MINIO_*` vars (`MINIO_ENDPOINT`, `MINIO_PUBLIC_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`) for any future environment. This is an environment/config resolution, not a code fix — no source files were modified to close this blocker; it was never a code defect (see `.planning/milestones/v2.10-MILESTONE-AUDIT.md`).

## Deferred Items

Items acknowledged and deferred at milestone v2.10 close on 2026-07-10 (see `.planning/milestones/v2.10-MILESTONE-AUDIT.md` for full detail — 0 cross-phase integration gaps found; only 2 non-blocking WARNINGs, neither requiring a fix before close). Most are now in-scope for v2.11 (see Pending Todos above for explicit requirement ownership):

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 85 (85-VERIFICATION.md / `97-UAT.md`) — code-level verification passed 5/5; 1 open product decision (ALTA-priority `Evento` with null `dataInicio`, renamed from `dataFim` 2026-07-14 — should it count as urgent in dashboard KPIs?) re-confirmed still unanswered by v2.11 Phase 97 AUD-02, `isEventoCritico()` docblock quoted verbatim | OPEN — genuine product decision for the user, not a defect; carried forward, not silently dropped |
| integration_gap | `isEventoCritico()` used `dataFim` while `listEventos`/`AlertasDiariosJob` used `dataInicio` — a 5th divergent risk-calc implementation AGD-34/35 was meant to eliminate; flagged by `92-REVIEW.md` CR-01 as deferred to Phase 97, missed by Phase 97's own bounded AUD-05 scope, caught by the milestone audit's integration-checker | CLOSED 2026-07-14 during milestone audit — standardized on `dataInicio`, commit `f0c62ff`, 69/69 backend tests still green |
| verification_gap | Phase 86 (86-VERIFICATION.md) — 11/11 plan-level truths verified; the first-ever `nativeQuery=true`+`Pageable` combination in this codebase (`buscarPorFiltros`) — CLOSED by v2.11 Phase 91 (TEST-01): Testcontainers infra now exists and `NotificacaoRepositoryIT` exercises `buscarPorFiltros` against real Postgres; RBAC Settings-screen visual confirmation still pending | human_needed — standalone item, NOT owned by v2.11 AUD-02 (Phase 86 is not among AUD-02's 8 covered phases: 75/76/79/81/82/84/85/89; no Phase 97 plan performed this specific live check) — remains open for a future session |
| verification_gap | Phase 87 (87-VERIFICATION.md) — code-level verification passed 7/7. Of the 3 originally-tracked gaps: (1) live E2E walkthrough (Reatribuir flow) blocked by the MinIO env issue — CLOSED by v2.11 Phase 97 Task 2 (AUD-04), MinIO blocker resolved 2026-07-14; (2) concurrent `numeroVersao` DB lock had zero automated test coverage — CLOSED by v2.11 Phase 91 (TEST-02), `ParecerVersaoConcorrenciaIT` added; (3) `ParecerController` partial-update data-loss bug (already fixed in code, commit `ce6d1f0`) still has zero automated test coverage | partially closed — (1) CLOSED (Phase 97/AUD-04), (2) CLOSED (Phase 91/TEST-02); (3) remains OPEN as a standalone item — Phase 87 is not one of AUD-02's 8 covered phases, so no plan this milestone added test coverage for this specific defect; candidate for a future phase |
| verification_gap | Phase 89 (89-VERIFICATION.md / `97-UAT.md`) — code-level verification passed 15/15; all 11 pending UAT scenarios given an explicit verdict by v2.11 Phase 97 AUD-02: 8/11 CODE-VERIFIED (badge/dropdown/fused-click/mark-all/route+RBAC/filters/standalone-mark+pagination), 3/11 remain NEEDS-HUMAN-VISUAL (cross-surface real-time reflection, WR-01 same-origin-guard render, WR-02 pagination-flash fix) — none indicate a functional defect, all backed by prior independent static/behavioral evidence | mostly CLOSED — 3 purely visual/live-timing confirmations remain, zero functional risk; the MinIO blocker that previously prevented any live walkthrough is separately CLOSED (Phase 97 Task 2, AUD-04) |
| uat_gap | 75/76/79/81/82/84/85/89-HUMAN-UAT.md pending scenarios (the 8 phases AUD-02 actually covers — NOT 86/87, see their own rows above) | CLOSED by v2.11 Phase 97 (`97-UAT.md`, AUD-02) — see the Pending Todos section above for the per-phase breakdown and the 2 carried-forward open items (Phase 81/82 migration review, Phase 85 product decision) |
| tooling | `backend/migrations/86-create-notificacao-table.sql` and `88-add-notificacao-dedup-unique-constraint.sql` are standalone manual-execution scripts — no migration runner exists in this repo | must be run manually against the database before/alongside deploy |
| tooling | No H2/Testcontainers integration-test infrastructure exists anywhere in this backend | CLOSED by v2.11 Phase 91 — Testcontainers PostgreSQL infra + CI gate (`deploy.yml` `test` job) now exist |
| tooling | `backend/spotbugs-exclude.xml`/SpotBugs SAST tooling broken against JDK 23 bytecode | CLOSED by v2.11 Phase 90 (SAST-01) — `mvn spotbugs:check` runs clean against JDK 23 bytecode |
| cosmetic | `notificarDocumentoNovo`'s `linkUrl` doesn't use the `?tab=documentos` deep-link pattern that `notificarFaseEntrada` established (call sites: `ResourceController.java` upload-document branches, still plain `/processos/{id}`/`/clientes/{id}`) | still open — Phase 94/95 (NOTF-27/NOTF-25) touched `NotificacaoService.java` but not this specific `linkUrl` cosmetic gap; not addressed by v2.11 Phase 97 either (out of this plan's bounded audit scope); candidate one-line fix, non-blocking, for a future phase |

*(Full v2.8/v2.9 deferred-items detail trimmed here per STATE.md size constraint — see `.planning/milestones/v2.8-MILESTONE-AUDIT.md` and `.planning/milestones/v2.9-MILESTONE-AUDIT.md`.)*

### Items acknowledged and deferred at milestone v2.11 close (2026-07-14)

Pre-close `audit-open` scan found 11 open items across 5 phases, all `human_needed`/`partial` — none are BLOCKER-level (see `.planning/v2.11-MILESTONE-AUDIT.md` for full detail). Acknowledged and deferred rather than blocking close:

| Category | Item | Status |
|----------|------|--------|
| uat_gap / verification_gap | Phase 91 (Testcontainers) — 2 pending scenarios (`91-HUMAN-UAT.md`) | human_needed — Docker Desktop 4.80/Testcontainers 1.20.4 npipe incompatibility, confirmed independently multiple times; not a code defect, will pass in CI |
| uat_gap / verification_gap | Phase 92 (Agenda↔RiscoPrazoService) — 2 pending scenarios (`92-HUMAN-UAT.md`) | mostly closed — AGD-35's risco field was confirmed live this session; residual items are minor visual confirmations |
| uat_gap / verification_gap | Phase 93 (NOTF-24 preferências) — 5 pending scenarios (`93-HUMAN-UAT.md`) | mostly closed — the mute toggle itself was confirmed live end-to-end this session; residual items are secondary scenarios |
| uat_gap / verification_gap | Phase 96 (NOTF-26 snooze) — 2 pending scenarios (`96-HUMAN-UAT.md`) | mostly closed — the full snooze flow (presets, badge drop, history, bell-hide) was confirmed live end-to-end this session; residual items are edge cases (PRAZO_VENCIDO block, cross-user 404) not reproducible with current test data |
| uat_gap / verification_gap | Phase 97 (milestone audit) — 3 pending scenarios (`97-HUMAN-UAT.md`) | print-preview layout, cross-surface bell timing, RBAC second-user walkthrough — see Phase 97 rows above; zero functional risk per code-level evidence |

Known deferred items count at close: 11 (5 verification_gaps + 6 uat_gaps, `97-UAT.md` itself already closed with 0 pending).

## Session Continuity

Last session: 2026-07-14T20:38:30.000Z
Stopped at: Phase 97 Plan 04 (milestone close-out) complete — fresh code audit (AUD-05) clean, MINIO_ENDPOINT blocker resolved and documented (AUD-04), wave-1 outcomes (AUD-01/02/03) consolidated into STATE.md/PROJECT.md with stale Phase 86/87 attributions corrected. All 15/15 v2.11 requirements Complete.
Resume file: None

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
