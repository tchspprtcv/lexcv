---
gsd_state_version: 1.0
milestone: v2.9
milestone_name: Melhoria Módulo Processos
status: executing
stopped_at: Completed 81-01-PLAN.md
last_updated: "2026-07-07T19:42:27.503Z"
last_activity: 2026-07-07
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 4
  completed_plans: 2
  percent: 20
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-06)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 81 — Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem

## Current Position

Phase: 81 (Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem) — EXECUTING
Plan: 2 of 3
Status: Ready to execute
Last activity: 2026-07-07

Progress: [█████░░░░░] 50%

## Performance Metrics

**Velocity:**

- Total plans completed: 22
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

*(Full per-phase history for v2.0–v2.8 lives in `.planning/milestones/*-ROADMAP.md` archives; table trimmed here per STATE.md size constraint.)*
| Phase 80 P01 | 15min | 3 tasks | 10 files |
| Phase 81 P01 | 12min | 2 tasks | 1 files |

## Accumulated Context

### Roadmap Evolution

- v2.9 roadmap created 2026-07-07: 5 phases (80–84) derived from 17 requirements (PROC-01 to PROC-17), continuing phase numbering from v2.8 (last phase: 79). Strict dependency chain per research recommendation: (80) data-layer foundations — `Processo.juizo`/`origem` columns + `OrigemProcesso` enum, plus `Decisao`/`Facto`/`Testemunha` entities + `TipoDecisao` enum + repositories, no UI-visible change; (81) backend CRUD for the 3 new entities (12 endpoints, `ProcessoFase`-style double tenant/processoId check per PROC-17) + juizo/origem wired into create/update/intake/listProcessos + origem validation in both layers; (82) Honorário auto-creation split into its own phase — independently parallelizable (only depends on Phase 80, not Phase 81) and isolates the highest-severity financial/idempotency risk flagged by research; (83) frontend types/schemas/hooks, explicitly the phase where `normalizeProcesso`/`toProcessoApiPayload` must be updated in the same reviewed change as type definitions (4th recurrence prevention of a 3x-seen mapping bug); (84) frontend UI — intake Origem field, Dados card Juízo/Origem, 4 new tabs (Decisões/Factos/Testemunhas/Documentos), Termo de Honorários print route. 100% requirement coverage, no orphans. Phase 83 owns no requirement directly (pure integration layer) but is a hard dependency for Phase 84.

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.9 research) No new dependency required — existing stack (Spring Boot/PostgreSQL, Next.js/TanStack Query/RHF+Zod) covers 100% of the milestone; PDF/docx library and shadcn `Tabs` primitive both explicitly evaluated and rejected again (consistent with v2.8 Key Decision log).
- (v2.9 research) `Decisao`/`Facto`/`Testemunha` mirror `Parte.java`'s lean shape — `Integer` IDENTITY id, `processo_id` FK, no own `tenant_id` column; isolation enforced transitively via parent Processo load+check.
- (v2.9 research) All 3 new entities need full CRUD (not append-only like `Parte`/`Movimentacao`) since corrections/reordering/removal are all realistic use cases — and must copy the harder `ProcessoFase` double-check pattern (parent tenant + child `processoId`) for every PUT/DELETE, not the simpler `Parte`/`Movimentacao` single-check pattern (PROC-17).
- (v2.9 research) `TipoDecisao` = Despacho | Decisão Interlocutória | Sentença | Acórdão (confirmed PT/BR judicial taxonomy); `Testemunha.tipo` = closed enum Autor | Réu (confirmed — user explicitly chose over free text).
- (v2.9 research) Decisão anexo uses "upload direto na Decisão" — the create endpoint accepts multipart upload and creates the Documento internally, not a pre-existing-Documento picker (PROC-07).
- (v2.9 research) Auto-created Honorário `valorTotal` must start `null`, never pre-filled from `Cliente.honorariosPropostos` (a per-cliente soft estimate, not a per-processo hard financial commitment) — flagged as the single highest-severity pitfall in the feature set.
- (v2.9 roadmap) Honorário auto-creation split into its own phase (82) separate from the Decisões/Factos/Testemunhas CRUD phase (81) — no shared dependency beyond Phase 80, and isolates the money-safety/idempotency risk for focused review.
- (v2.9 roadmap) Documentos tab (PROC-13) folded into the final UI phase (84) as pure frontend wiring — its backend endpoint (`GET /processos/{id}/documentos`) already exists, no dedicated phase needed.
- [Phase 81]: origem violations return HTTP 422 (not 400) at intake, matching formalizarProcesso's existing convention
- [Phase 81]: PUT /processos/{id} silently ignores any origem in the payload rather than rejecting with 400 -- matches the estado-exclusion precedent

### Pending Todos

- Carried from v2.8 close: 3 phases (75, 76, 79) have static verification 100% complete but live browser/backend UAT pending — see respective `*-HUMAN-UAT.md`. Not part of v2.9 scope but still open.
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` — manual-execution script, must be run against the database before/alongside next deploy (no migration runner in this repo).
- REG_COMERCIAL and other `DocumentoTipo` values still render as raw enum strings instead of translated Portuguese labels on client detail page and printed ficha — non-blocking cosmetic gap, candidate for a small follow-up (carried since v2.7).
- No automated backend tests cover the 4 NIF validation scenarios introduced in Phase 73.1 — non-blocking test debt.
- v2.9 research flag: confirm whether `formalizarProcesso()` currently carries an explicit `@PreAuthorize` — verify at Phase 82 implementation time (not conclusively visible in research excerpt).
- v2.9 research flag: Documento↔Decisão FK direction (nullable `decisao_id` on `Documento` vs. `documento_id` on `Decisao`) is a real design choice with a security-pattern implication — should be recorded explicitly during Phase 80/81 planning, not improvised mid-build.

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

## Session Continuity

Last session: 2026-07-07T19:42:27.451Z
Stopped at: Completed 81-01-PLAN.md
Resume file: None

## Operator Next Steps

- Review and approve the v2.9 roadmap, then run `/gsd:plan-phase 80` to start planning the first phase.
