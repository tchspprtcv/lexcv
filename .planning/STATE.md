---
gsd_state_version: 1.0
milestone: v2.10
milestone_name: Notificações e Alertas
status: executing
stopped_at: Phase 87 UI-SPEC approved
last_updated: "2026-07-09T07:19:12.250Z"
last_activity: 2026-07-09 -- Phase 87 execution started
progress:
  total_phases: 5
  completed_phases: 2
  total_plans: 8
  completed_plans: 4
  percent: 40
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-08)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 87 — Alertas de Eventos — Fase, Documento, Atribuição e Parecer

## Current Position

Phase: 87 (Alertas de Eventos — Fase, Documento, Atribuição e Parecer) — EXECUTING
Plan: 1 of 4
Status: Executing Phase 87
Last activity: 2026-07-09 -- Phase 87 execution started

## Performance Metrics

**Velocity:**

- Total plans completed: 37
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

*(Full per-phase history for v2.0–v2.8 lives in `.planning/milestones/*-ROADMAP.md` archives; table trimmed here per STATE.md size constraint.)*
| Phase 80 P01 | 15min | 3 tasks | 10 files |
| Phase 81 P01 | 12min | 2 tasks | 1 files |
| Phase 81 P02 | 8min | 2 tasks | 1 files |
| Phase 81 P03 | 12min | 1 tasks | 2 files |
| Phase 82 P01 | 12min | 2 tasks | 1 files |
| Phase 83 P01 | 10min | 2 tasks | 5 files |
| Phase 83 P02 | 6min | 2 tasks | 4 files |
| Phase 84 P01 | 8min | 2 tasks | 2 files |
| Phase 84 P02 | 15min | 2 tasks | 1 files |
| Phase 84 P03 | 10min | 3 tasks | 1 files |
| Phase 84 P04 | 25min | 2 tasks | 2 files |
| Phase 84 P05 | 12min | 2 tasks | 2 files |

## Accumulated Context

### Roadmap Evolution

- v2.9 roadmap created 2026-07-07: 5 phases (80–84) derived from 17 requirements (PROC-01 to PROC-17), continuing phase numbering from v2.8 (last phase: 79). Strict dependency chain per research recommendation: (80) data-layer foundations — `Processo.juizo`/`origem` columns + `OrigemProcesso` enum, plus `Decisao`/`Facto`/`Testemunha` entities + `TipoDecisao` enum + repositories, no UI-visible change; (81) backend CRUD for the 3 new entities (12 endpoints, `ProcessoFase`-style double tenant/processoId check per PROC-17) + juizo/origem wired into create/update/intake/listProcessos + origem validation in both layers; (82) Honorário auto-creation split into its own phase — independently parallelizable (only depends on Phase 80, not Phase 81) and isolates the highest-severity financial/idempotency risk flagged by research; (83) frontend types/schemas/hooks, explicitly the phase where `normalizeProcesso`/`toProcessoApiPayload` must be updated in the same reviewed change as type definitions (4th recurrence prevention of a 3x-seen mapping bug); (84) frontend UI — intake Origem field, Dados card Juízo/Origem, 4 new tabs (Decisões/Factos/Testemunhas/Documentos), Termo de Honorários print route. 100% requirement coverage, no orphans. Phase 83 owns no requirement directly (pure integration layer) but is a hard dependency for Phase 84.
- v2.10 roadmap created 2026-07-08: 5 phases (85–89) derived from 16 requirements (NOTF-08 to NOTF-23), continuing phase numbering from v2.9 (last phase: 84). Dependency-ordered per architecture research: (85) `RiscoPrazoService` extraction — consolidates the 4 inconsistent "prazo crítico" computations into one shared source, zero new table, pure refactor, parallelizable with 86 (NOTF-22); (86) `Notificacao` entity + full REST API (list/unread-count/mark-read/mark-all-read) + RBAC plumbing — the hard prerequisite every other phase builds on, owns NOTF-14 (targeting/ADMIN-fan-out rule, verifiable before any real trigger exists); (87) wires the 4 event-triggered alert types (fase entrada, documento novo, atribuição de processo + its new reassignment endpoint/UI, parecer atribuído) into existing controllers (NOTF-15/16/17/18/19) — NOTF-17/18 deliberately kept in the same phase since the alert is meaningless without its own trigger flow; (88) daily `@Scheduled` job (prazos, eventos críticos, honorários — NOTF-20/21/23), hard-gated on both 85 and 86 per research, since its entire purpose is reusing the consolidated risk logic rather than adding a 5th copy; (89) bell + `/notificacoes` page (NOTF-08 to 13), the user-facing consumption layer, gated only on 86. 100% requirement coverage, no orphans.

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.9 research) `Decisao`/`Facto`/`Testemunha` mirror `Parte.java`'s lean shape — `Integer` IDENTITY id, `processo_id` FK, no own `tenant_id` column; isolation enforced transitively via parent Processo load+check.
- (v2.9 roadmap) Honorário auto-creation split into its own phase (82) separate from the Decisões/Factos/Testemunhas CRUD phase (81) — no shared dependency beyond Phase 80, and isolates the money-safety/idempotency risk for focused review.
- [Phase 84]: [Phase 84-05] canEditDocumentos derived from a second permissions.can.edit("documentos") call inside ProcessoDetailContent (TanStack-Query-cached) — a scope distinct from canEditProcessos, matching the ClienteDocumentosEntreguesTab precedent
- (v2.10 roadmap) NOTF-08/09/10/11/12/13 (contador do sino, lista do sino, marcar lida/todas, página dedicada, filtros) mapped to Phase 89 (frontend), not the earlier infra phase — these are phrased as "Utilizador vê/marca/filtra," which only becomes literally true once the UI exists, even though their backend endpoints are built in Phase 86.
- (v2.10 roadmap) NOTF-17's reassignment UI (a new form/control on the ficha do processo) bundled into Phase 87 alongside its backend endpoint and its own alert (NOTF-18), rather than deferred to Phase 89 — it's a Processo-detail-page concern, not a notification-inbox concern, so it doesn't belong with the bell/history UI work.
- (v2.10 roadmap) NOTF-14 (targeting/no-mass-broadcast rule) mapped to Phase 86 (infra), not to any single consuming phase — it's a systemic guarantee designed into `NotificacaoService`'s recipient-resolution + ADMIN fan-out at creation time, verifiable via two independent test users before any real trigger exists (per PITFALLS.md Pitfall 2/3).
- (v2.10 roadmap) Phase 88 (daily job) hard-depends on both Phase 85 and Phase 86, per architecture research — its entire purpose is reusing the consolidated risk logic, so it cannot start meaningfully before either exists.

### Pending Todos

- Carried from v2.8 close: 3 phases (75, 76, 79) have static verification 100% complete but live browser/backend UAT pending — see respective `*-HUMAN-UAT.md`. Not part of v2.9/v2.10 scope but still open.
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` — manual-execution script, must be run against the database before/alongside next deploy (no migration runner in this repo).
- REG_COMERCIAL and other `DocumentoTipo` values still render as raw enum strings instead of translated Portuguese labels on client detail page and printed ficha — non-blocking cosmetic gap, candidate for a small follow-up (carried since v2.7).
- No automated backend tests cover the 4 NIF validation scenarios introduced in Phase 73.1 — non-blocking test debt.
- v2.10 planning flag (from PITFALLS.md/ARCHITECTURE.md research): the daily job (Phase 88) must not call `getTenantId()`/`SecurityContextHolder` — first background-thread code path in this codebase; verify explicitly during Phase 88 planning/review, not just at code-review time.
- v2.10 planning flag: `Notificacao` reads/writes must filter by `destinatario_id` in addition to `tenant_id` on every query (Phase 86) — this project's first per-recipient-private entity, easy to under-scope by pattern-matching the tenant-only checks used everywhere else.

### Blockers/Concerns

None.

## Deferred Items

Items acknowledged and deferred at milestone v2.8 close on 2026-07-06 (see `.planning/v2.8-MILESTONE-AUDIT.md` for full detail):

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 75 (75-VERIFICATION.md) — static verification passed 9/9, live browser/backend test not performed | human_needed |
| verification_gap | Phase 76 (76-VERIFICATION.md) — static verification passed 7/7, live browser/backend test not performed | human_needed |
| verification_gap | Phase 79 (79-VERIFICATION.md) — static verification passed 9/9, live browser/backend test not performed | human_needed |
| uat_gap | 75-HUMAN-UAT.md — 5 pending scenarios (visual parity, save round-trip, cancel-discard, sub-component toggle, mobile scroll) | partial |
| uat_gap | 76-HUMAN-UAT.md — 4 pending scenarios (7-tab click-through, cross-tab validation surfacing, intake dialog reset, mobile scroll) | partial |
| uat_gap | 79-HUMAN-UAT.md — 3 pending scenarios (live upload flow, list/download/delete round trip, read/edit mode gating) | partial |
| tooling | `backend/migrations/74-cleanup-nif-documento-tipo.sql` is a standalone manual-execution script — no migration runner exists in this repo | must be run manually against the database before/alongside deploy |
| tech_debt | `Cliente.documentosEntregues` (backend field/column) and `DocumentoEntregue` (frontend type) are orphaned by design (CLI-29 "corte limpo") | intentional, matches `dados_tipo` precedent from v2.7 |

Items acknowledged and deferred at milestone v2.9 close on 2026-07-08 (see `.planning/v2.9-MILESTONE-AUDIT.md` for full detail — 2 cross-phase integration gaps found by the milestone audit were closed same session, commits 2ce48f7/380d435, before this close):

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 81 (81-VERIFICATION.md) — code-level verification passed 12/12, live HTTP round-trip not performed (credential-lockout constraint) | human_needed |
| verification_gap | Phase 82 (82-VERIFICATION.md) — code-level verification passed 4/4, live UI/HTTP round-trip not performed | human_needed |
| verification_gap | Phase 84 (84-VERIFICATION.md) — code-level verification passed 10/10, live browser walkthrough not performed | human_needed |
| uat_gap | 81-HUMAN-UAT.md — 5 pending scenarios (intake origem validation, juizo/origem round-trip, full CRUD + cross-processo 404 lifecycle, Facto ordem concurrency, prod migration dry-run) | partial |
| uat_gap | 82-HUMAN-UAT.md — 4 pending scenarios (live formalize → /financeiro render, duplicate-Honorário check, concurrency race, prod migration dry-run) | partial |
| uat_gap | 84-HUMAN-UAT.md — 7 pending scenarios (intake wizard, Juízo edit round-trip, Termo de Honorários print flow, Partes/Fases dialog-reset + Fases-Guardar fixes, Decisão-file↔Documentos cross-link, Factos live-reorder, RBAC-gated rendering) | partial |
| tooling | `backend/migrations/81-add-facto-ordem-unique-constraint.sql` and `backend/migrations/82-add-honorario-processo-unique-constraint.sql` are standalone manual-execution scripts — no migration runner exists in this repo | must be run manually against the database before/alongside deploy |
| tooling | `web/scripts/verify-juizo-origem-roundtrip.mjs` is a genuine, executable, non-duplicated round-trip proof, wired into `package.json` as `verify:juizo-origem`, but not yet called from any CI step | candidate for CI wiring in a future milestone |

## Session Continuity

Last session: 2026-07-08T23:45:22.791Z
Stopped at: Phase 87 UI-SPEC approved
Resume file: .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-UI-SPEC.md

## Operator Next Steps

- Run `/gsd:plan-phase 85` to start planning the first phase of v2.10 (Consolidação da Lógica de "Prazo Crítico").
