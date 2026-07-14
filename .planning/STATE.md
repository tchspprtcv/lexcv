---
gsd_state_version: 1.0
milestone: v2.11
milestone_name: Auditoria Técnica e Notificações Avançadas
status: executing
stopped_at: ROADMAP.md and REQUIREMENTS.md traceability written for v2.11 (8 phases, 15/15 requirements, no orphans)
last_updated: "2026-07-14T14:52:03.773Z"
last_activity: 2026-07-14 -- Phase 95 execution started
progress:
  total_phases: 8
  completed_phases: 5
  total_plans: 13
  completed_plans: 11
  percent: 63
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-12)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 95 — NOTF-25 — Notificar Toda a Equipa do Processo

## Current Position

Phase: 95 (NOTF-25 — Notificar Toda a Equipa do Processo) — EXECUTING
Plan: 1 of 2
Status: Executing Phase 95
Last activity: 2026-07-14 -- Phase 95 execution started

Progress: [░░░░░░░░░░] 0% (0/8 phases da milestone v2.11)

## Performance Metrics

**Velocity:**

- Total plans completed: 58
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

### Pending Todos

- Carried from v2.8 close: 3 phases (75, 76, 79) have static verification 100% complete but live browser/backend UAT pending — now explicitly owned by v2.11's AUD-02/AUD-04 (Phase 97).
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` — manual-execution script, must be run against the database before/alongside next deploy (no migration runner in this repo).
- REG_COMERCIAL and other `DocumentoTipo` values still render as raw enum strings instead of translated Portuguese labels — now explicitly owned by v2.11's AUD-03 (Phase 97).
- No automated backend tests cover the 4 NIF validation scenarios introduced in Phase 73.1 — now explicitly owned by v2.11's AUD-03 (Phase 97).
- ~~No H2/Testcontainers integration-test infrastructure exists anywhere in this backend~~ — CLOSED by v2.11 Phase 91 (TEST-01/02/03): Testcontainers PostgreSQL integration-test infra + CI gate now exist; `buscarPorFiltros` (native query) and the `numeroVersao` concurrency lock are proven against real Postgres in `NotificacaoRepositoryIT`/`ParecerVersaoConcorrenciaIT`.
- `backend/spotbugs-exclude.xml`/SpotBugs SAST tooling broken against JDK 23 bytecode — now explicitly owned by v2.11's SAST-01 (Phase 90).
- `web/src/app/(dashboard)/agenda/page.tsx` independently recomputes its own "prazo crítico" verdict instead of using `RiscoPrazoService` — now explicitly owned by v2.11's AGD-34/AGD-35 (Phase 92).

### Blockers/Concerns

- `MINIO_ENDPOINT` environmental blocker (recurring across v2.8/v2.9/v2.10 sessions, prevents full Spring context startup for live UAT) — explicitly owned by v2.11's AUD-04 (Phase 97), which must resolve or document a workaround.

## Deferred Items

Items acknowledged and deferred at milestone v2.10 close on 2026-07-10 (see `.planning/milestones/v2.10-MILESTONE-AUDIT.md` for full detail — 0 cross-phase integration gaps found; only 2 non-blocking WARNINGs, neither requiring a fix before close). Most are now in-scope for v2.11 (see Pending Todos above for explicit requirement ownership):

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 85 (85-VERIFICATION.md) — code-level verification passed 5/5; 1 open product decision (should an ALTA-priority Evento with null dataFim count as urgent in dashboard KPIs?) never answered across 3 review iterations | human_needed — owned by v2.11 AUD-02 |
| verification_gap | Phase 86 (86-VERIFICATION.md) — 11/11 plan-level truths verified; the first-ever `nativeQuery=true`+`Pageable` combination in this codebase (`buscarPorFiltros`) — CLOSED by v2.11 Phase 91 (TEST-01): Testcontainers infra now exists and `NotificacaoRepositoryIT` exercises `buscarPorFiltros` against real Postgres; RBAC Settings-screen visual confirmation still pending | human_needed — RBAC visual confirmation only, owned by v2.11 AUD-02 |
| verification_gap | Phase 87 (87-VERIFICATION.md) — code-level verification passed 7/7, live E2E walkthrough (Reatribuir flow) blocked by MinIO env issue; 2 code-review-fixed defects (ParecerController partial-update data-loss bug, concurrent numeroVersao DB lock) have zero automated test coverage | human_needed — owned by v2.11 TEST-02/AUD-02/AUD-04 |
| verification_gap | Phase 89 (89-VERIFICATION.md) — code-level verification passed 15/15, live E2E walkthrough blocked by the same MinIO env issue as Phase 87; 2 code-review-fixed defects (same-origin URL guard, pagination clamp) flagged for a final visual confirmation despite passing independent behavioral/lint re-verification | human_needed — owned by v2.11 AUD-02/AUD-04 |
| uat_gap | 85/86/87/89-HUMAN-UAT.md pending scenarios | partial — owned by v2.11 AUD-02 (Phase 97) |
| tooling | `backend/migrations/86-create-notificacao-table.sql` and `88-add-notificacao-dedup-unique-constraint.sql` are standalone manual-execution scripts — no migration runner exists in this repo | must be run manually against the database before/alongside deploy |
| tooling | No H2/Testcontainers integration-test infrastructure exists anywhere in this backend | CLOSED by v2.11 Phase 91 — Testcontainers PostgreSQL infra + CI gate (`deploy.yml` `test` job) now exist |
| tooling | `backend/spotbugs-exclude.xml`/SpotBugs SAST tooling broken against JDK 23 bytecode | now v2.11 Phase 90 |
| cosmetic | `notificarDocumentoNovo`'s `linkUrl` doesn't use the `?tab=documentos` deep-link pattern that `notificarFaseEntrada` established | candidate one-line fix, non-blocking — reassess during v2.11 Phase 94/95 (same file being touched) |

*(Full v2.8/v2.9 deferred-items detail trimmed here per STATE.md size constraint — see `.planning/milestones/v2.8-MILESTONE-AUDIT.md` and `.planning/milestones/v2.9-MILESTONE-AUDIT.md`.)*

## Session Continuity

Last session: 2026-07-12T15:10:00.000Z
Stopped at: ROADMAP.md and REQUIREMENTS.md traceability written for v2.11 (8 phases, 15/15 requirements, no orphans)
Resume file: None

## Operator Next Steps

- Run `/gsd:plan-phase 90` to start planning (Phases 90, 91, 92 have zero file overlap and are mutually parallelizable; Phases 93→94→95→96 are a hard sequential chain on `NotificacaoService.java`; Phase 97 runs last).
