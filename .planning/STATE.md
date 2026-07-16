---
gsd_state_version: 1.0
milestone: v2.13
milestone_name: Refactor UI/UX (shadcn/ui)
status: planning
stopped_at: Completed 105-03-PLAN.md (Partes/Fases/Testemunhas Table primitives + Testemunhas Avatar + Documentos DataTable); 105-06 (closing plan) remains
last_updated: "2026-07-16T17:33:36.694Z"
last_activity: 2026-07-16
progress:
  total_phases: 10
  completed_phases: 4
  total_plans: 22
  completed_plans: 21
  percent: 40
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-15)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 105 — módulos clientes + processos (combinados)

## Current Position

Phase: 105 of 110 (módulos clientes + processos (combinados))
Plan: 6 of 6 (105-01, 105-02, 105-03, 105-04, 105-05 complete — 105-06 closing plan remaining)
Status: Ready to execute
Last activity: 2026-07-16

## Performance Metrics

**Velocity:**

- Total plans completed: 88
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
| 101 P02 | 1 | 20min | 20min |
| 101 P05 | 1 | 25min | 25min |
| 101 | 5 | - | - |
| 102 | 4 | - | - |
| 103 | 1 | - | - |
| 104 | 6 | - | - |

*(Full per-phase history for v2.0–v2.8 lives in `.planning/milestones/*-ROADMAP.md` archives; table trimmed here per STATE.md size constraint.)*
| Phase 103 P01 | ~10min | 2 tasks | 1 files |
| Phase 104 P02 | ~25min | 3 tasks | 7 files |
| Phase 105 P03 | ~25min | 2 tasks | 2 files |

## Accumulated Context

### Roadmap Evolution

- v2.10 roadmap created 2026-07-08: 5 phases (85–89) derived from 16 requirements (NOTF-08 to NOTF-23), continuing phase numbering from v2.9 (last phase: 84). See PROJECT.md Key Decisions / prior STATE.md history for full v2.9/v2.10 rationale (trimmed here per size constraint).
- v2.11 roadmap created 2026-07-12: 8 phases (90–97) derived from 15 requirements (SAST-01, TEST-01/02/03, AGD-34/35, NOTF-24/25/26/27, AUD-01 to AUD-05), continuing phase numbering from v2.10 (last phase: 89). Structure follows research's file-collision analysis: (90) SpotBugs/SAST commit+verify — already ~90% done uncommitted in working tree, zero dependency, highest risk of being lost if not landed first; (91) Testcontainers integration-test infra (native query + concurrency lock risks) — independent track, no file overlap with anything else; (92) Agenda↔RiscoPrazoService consolidation — frontend-mostly, independent, closes the "5th divergent prazo crítico implementation" debt explicitly deferred at v2.10 close. Phases 90/91/92 are mutually parallelizable (zero file overlap). Phases 93–96 are a hard sequential chain — all collide on `NotificacaoService.java` and its test file regardless of logical independence: (93) NOTF-24 mute preferences goes first (smallest, most self-contained insertion at the `criar()` choke point, so team fan-out later inherits the mute gate for free); (94) NOTF-27 dedup/ADMIN-collision fix — a pre-existing bug from Phase 88 (v2.10) that NOTF-25 would turn from latent into near-certain, so it must land before the fan-out widens; (95) NOTF-25 team fan-out (depends on 93+94); (96) NOTF-26 snooze, most additive, last in the chain. (97) Cross-cutting milestone audit (AUD-01 to AUD-05) runs last, depending on all 7 other phases, matching the v2.7/v2.9/v2.10 established retrospective pattern of auditing at milestone close. 100% requirement coverage (15/15), no orphans.
- v2.12 roadmap created 2026-07-15: 3 phases (98–100) derived from 16 requirements (LP-01 to LP-16), continuing phase numbering from v2.11 (last phase: 97). Structure follows research (`.planning/research/SUMMARY.md`)'s explicit build-order recommendation almost verbatim: (98) Backend Public Branding Endpoint (LP-01, LP-02) — fully isolated, zero dependency on `webpage/`, curl-testable against the existing dev backend; (99) `webpage/` Landing App (LP-03 to LP-12) — the largest phase, covers nearly the full v1 feature list, buildable in parallel using a hardcoded branding stub since the only real blocking dependency (`/api/v1/setup/status`) already exists today; (100) Infra Wiring & Deployment (LP-13 to LP-16) — deliberately last, depends on both 98 and 99 producing real artifacts (an actual endpoint response shape, a buildable `webpage` Docker image), and touches the exact area (`docker-compose.hostinger.yml`'s inline Caddy heredoc, plus `Caddyfile`/`Caddyfile.prod`) that produced 4 consecutive production bug-fix commits (`67e2120`, `534fa92`, `ba67f4e`, `1482f47`) immediately preceding this research — its own success criteria require a full `docker compose up` verification, not isolated `pnpm dev`/`mvn test` checks. Phases 98 and 99 are explicitly marked mutually parallelizable in ROADMAP.md (zero mutual dependency); Phase 100 depends on both. 100% requirement coverage (16/16), no orphans.
- v2.13 roadmap created 2026-07-15: 10 phases (101–110) derived from 33 requirements (FND-01 to FND-08, DSR-01 to DSR-03, DASH-01/02, DTB-01 to DTB-03, CLP-01 to CLP-05, AGD-36/37, DOF-01/02, PARC-18 to PARC-20, NTF-28 to NTF-30, LDG-17/18), continuing phase numbering from v2.12 (last phase: 100). Structure follows research (`.planning/research/SUMMARY.md`)'s 10-phase recommendation closely, adapted to the exact requirement IDs: (101) Foundation — CLI init (`-b radix`), design tokens, ~15 missing primitives, dependency swaps (Sonner/tw-animate-css/react-day-picker pin) — gates everything else, first phase of the milestone; (102) Design System Reconciliation — the 14 pre-existing hand-rolled components diffed/reconciled component-by-component, must complete before any module phase to avoid a visibly half-migrated app; (103) Dashboard — lowest primitive need, validates the new token layer at low risk; (104) Shared DataTable Pattern — built once as its own phase (never per-module) and reused by Clientes/Processos/Pareceres/Financeiro/Documentos lists (DTB-02 lives here, not duplicated in Phase 105); (105) Clientes + Processos combined — both fichas share the same toggle-button-to-Tabs migration by deliberate prior consistency decision, shipped together to avoid a visible inconsistency window; (106–109) Agenda / Documentos+Financeiro / Pareceres / Notificações-Settings-Setup — each only depends on Phase 102, explicitly mutually parallelizable, ordered narratively by descending primitive novelty/risk, not by a real blocking chain; (110) `webpage/` Landing Refinement — depends only on Phase 101 (its own hand-copied `components.json`), zero dependency on any `web/` module phase, explicitly parallelizable with any of 103–109. 100% requirement coverage (33/33), no orphans.

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.10 roadmap) Phase 88 (daily job) hard-depends on both Phase 85 and Phase 86 — its entire purpose is reusing the consolidated risk logic.
- (v2.11 research/roadmap) Notification-chain internal order is NOTF-24 → NOTF-27 (dedup fix) → NOTF-25 → NOTF-26, not the research SUMMARY.md's own initially-listed phase order (which had the dedup fix before NOTF-24) — the orchestrator's explicit sequencing instruction (matching the research executive summary's own recommended NOTF-24-first rationale) takes precedence: mute lands first so team fan-out inherits it for free, dedup fix lands immediately before the fan-out widens the recipient pool.
- (v2.11 roadmap) SpotBugs/SAST (Phase 90), Testcontainers infra (Phase 91), and Agenda/RiscoPrazoService consolidation (Phase 92) have zero file overlap with each other or with the notification chain — explicitly marked mutually parallelizable, not sequential filler.
- (v2.11 roadmap) Cross-cutting milestone audit (AUD-01 to AUD-05) placed last (Phase 97), depending on all other 7 phases — matches the established v2.7/v2.9/v2.10 pattern of auditing the milestone's final integration shape rather than mid-stream.
- (v2.11 TEST-03) CI (`deploy.yml`) gains a `test` job (`mvn verify` + `mvn spotbugs:check`) gating `build-and-push` via `needs: test` — closes the requirement's "decision registered + implemented" clause (Phase 91); OWASP `dependency-check:check` deliberately deferred (out of scope, NVD download cost).
- (v2.11 Phase 97, milestone close-out) AUD-01 through AUD-05 all closed: tenant isolation COVERED (97-01), UAT consolidated in `97-UAT.md` (97-03/AUD-02), DocumentoTipo/NIF debt closed (97-02/AUD-03), MINIO_ENDPOINT blocker RESOLVED (97-04/AUD-04), fresh code audit clean with zero new findings (97-04/AUD-05) — see PROJECT.md Key Decisions for full detail. 15/15 v2.11 requirements now Complete.
- (v2.12 roadmap) Phases 98 (backend endpoint) and 99 (`webpage/` app) are explicitly mutually parallelizable — zero shared files, `webpage/` consumes a hardcoded branding stub until Phase 98's real endpoint lands. Phase 100 (Caddy/compose/CI wiring) hard-depends on both, deliberately deferred to last given this exact infra area's recent 4-commit bug-fix history.
- (v2.12 roadmap) `webpage/`'s own setup-status gate (Phase 99) must contain zero authentication branch — never port `web/`'s `useMe()`/auth-redirect logic, which would defeat the entire purpose of a public landing page by auto-redirecting every anonymous visitor to `/login`.
- (v2.12 roadmap) The "Entrar" CTA and any other webpage→web cross-zone link must be a plain `<a>` tag, never `next/link`/`<Link>` — `/login` doesn't exist in `webpage/`'s own route table, so client-side navigation would 404; this only surfaces once both apps run together behind Caddy (Phase 100), never in isolated `pnpm dev` (Phase 99).
- (v2.12 roadmap) Phase 100's verification must include a full `docker compose up` smoke test, not just isolated `pnpm dev`/`mvn test` checks — this exact area (`docker-compose.hostinger.yml`'s inline Caddy config) produced 4 consecutive production bug-fix commits immediately preceding this milestone's research.
- (v2.13 roadmap) Foundation (Phase 101) and Design System Reconciliation (Phase 102) are a hard sequential gate before any module phase — the research's strongest ordering constraint, since a half-migrated visually-inconsistent app is itself a flagged UX pitfall, not just a nice-to-have sequencing preference.
- (v2.13 roadmap) DTB-02 (DataTable adoption on Clientes/Processos/Pareceres/Financeiro/Documentos lists) is owned entirely by Phase 104, not split or duplicated into Phase 105 — Phase 105's CLP-01/02 only cover the ficha/detail pages (different files from the list pages DTB-02 touches), so the two phases don't collide despite both touching "Clientes"/"Processos" module surfaces.
- (v2.13 roadmap) Phases 106–109 (Agenda, Documentos+Financeiro, Pareceres, Notificações/Settings/Setup) and Phase 110 (`webpage/`) are marked explicitly parallelizable — each only requires Phase 102 (or Phase 101 for 110) to be done, zero mutual file overlap or dependency between them; their numeric order reflects descending primitive-novelty/risk per research, not a blocking chain.
- [Phase 101]: (v2.13, Phase 101 Plan 02) shadcn CLI 4.13.0 requires an explicit -p <preset> flag (nova|vega|maia|lyra|mira|luma|sera|rhea) alongside -b <base> for non-interactive init; -y alone does not skip the interactive preset picker. Used -p vega (resolves to style radix-vega, matching 101-UI-SPEC.md's documented new-york-legacy resolution).
- [Phase 101]: (v2.13, Phase 101 Plan 02) Human visual sign-off caught a CSS cascade tie-break bug: shadcn init's token merge left .dark declared BEFORE :root in globals.css, so same-specificity source-order tie-break silently favored the light theme's values whenever .dark was active. Fixed by reordering (:root first); not visible today since no component yet consumes bg-background/bg-card/bg-popover (Phase 102), but would have broken dark mode across every reconciled component. Future shadcn init/add runs on this repo must verify :root precedes .dark before sign-off.
- [Phase 101]: (v2.13, Phase 101 Plan 05) shadcn migrate radix -y run non-interactively to unify all 8 remaining hand-rolled Radix-backed components onto the radix-ui package; 7 dead scoped @radix-ui/react-* deps pruned, closing FND-05's dual-tree bridge state.
- [Phase 101]: (v2.13, Phase 101 Plan 05) Sonner adopted behind an unchanged toast.success("Sucesso")/toast.error("Erro") contract (richColors green/red) — toast.tsx/toaster.tsx/@radix-ui/react-toast fully removed; all ~26 existing call sites compile/render unchanged, closing FND-08. Phase 101 (all 5 plans) now complete — 8/8 FND requirements satisfied.
- [Phase 102]: (v2.13, Phase 102 Plan 01) 7 Rule-C identity primitives (button/badge/input/label/radio-group/switch/textarea) reconciled diff-first against the official registry with zero variant/color drift — upstream's `bg-primary`-based button default, new `destructive` variant, and badge's loss of all 6 custom color variants explicitly NOT adopted. `buttonVariants` now exported from `button.tsx` (closing 101-REVIEW.md IN-05); `calendar.tsx`/`breadcrumb.tsx` deduped/unified against it; `shadcn` moved to `devDependencies`.
- [Phase 102]: (v2.13, Phase 102 Plan 02) 6 surface primitives (card/dialog/alert-dialog/popover/table/sheet) tokenized onto `--card`/`--popover`/`--muted`, replacing the flat `dark:bg-[#020617]`/`dark:bg-slate-950` magic hex that gave zero dark-mode elevation contrast — this Rule-B change was flagged for, and later confirmed by, the Plan 04 human visual checkpoint as an intended improvement, not a bug.
- [Phase 102]: (v2.13, Phase 102 Plan 03) Single global `TooltipProvider` (delayDuration=700, explicit — the primitive defaults to 0/instant) mounted in `providers.tsx`; DSR-03 "ícones da sidebar colapsada" interpreted as the sidebar-footer LogOut button (the app has no literal icon-only collapsed-rail mode) — a recorded, evidence-based reading, not a silent assumption. Broadened grep confirmed exactly 2 in-scope icon-only row-action surfaces app-wide (`clientes/page.tsx`, `settings/page.tsx`), both wrapped with Tooltip + matching aria-label.
- [Phase 102]: (v2.13, Phase 102 Plan 04, closing gate) Final holistic `pnpm build` + regression-grep gate green (badge-gray call-site surface enumerated across 8 files via `grep -rl '"gray"' web/src/app/`, not a stale 3-path list); mandatory human visual checkpoint (light+dark, real browser + getComputedStyle) APPROVED with concrete evidence — card dark background confirmed ~7.8% lightness vs. page background's `rgb(2,6,23)`, Rule-C button/badge identity unchanged, DSR-03 tooltips + aria-labels confirmed on `/clientes` and `/settings`. Phase 102 complete — DSR-01/02/03 all satisfied, 0 follow-up fix plans needed.
- [Phase 103]: (v2.13, Phase 103 Plan 01) Task split strictly followed Skeleton-first (Task 1) then Empty-second (Task 2) so each task commit builds/typechecks standalone; AtividadeRecenteCard's defensive Inbox/EmptyState branch was deliberately held out of the Task 1 commit and added in Task 2, matching the plan's own task boundaries.
- [Phase 103]: (v2.13, Phase 103 Plan 01) Verified-against-source: no literal 'A carregar...' string ever existed in dashboard/page.tsx (that string only lives in the separate, out-of-scope processos/dashboard/page.tsx); DASH-01's real gap was the discarded useDashboardKpis().isLoading field, not ad hoc text. The one ad hoc string genuinely removed was 'Sem urgencias.' on Prazos Urgentes (DASH-02).
- [Phase 104]: (v2.13, Phase 104 Plan 02) shadcn add pagination --diff (dry run) revealed it would overwrite Phase 102's reconciled button.tsx (Pagination depends on Button); declined that overwrite interactively so only pagination.tsx was created, preserving the reconciled primitive untouched
- [Phase 104]: (v2.13, Phase 104 Plan 02) Generic DataTable wrapper configures useReactTable with core+sorted+pagination row models only, never getFilteredRowModel -- filtering stays owned by each screen's existing use-* hook, closing the mandated 12px/600 uppercase muted column-header typography inconsistency across all 5 list screens
- [Phase 104]: (v2.13, Phase 104 Plan 02) Only DTB-01 marked complete in REQUIREMENTS.md, not DTB-03 -- the Pagination primitive was added but not yet applied to /notificacoes (that swap is 104-05's job); marking DTB-03 complete now would misrepresent an unfinished requirement
- [Phase 105 Plan 03]: Dropped both the 'Processo' and 'Cliente' columns from the processo-scoped documentos-columns.tsx -- Processo is redundant (row already scoped to processoId), Cliente would always render em-dash since this tab's upload flow only ever sets processo_id, never cliente_id
- [Phase 105 Plan 03]: Decisões and Factos tabs (also raw table markup in the same file) were deliberately left untouched -- neither 105-CONTEXT.md nor 105-UI-SPEC.md/105-PATTERNS.md name them in the Table-primitive migration scope, unlike Partes/Fases/Testemunhas which are explicitly named

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
- (v2.12) Contact channel for LP-10 ("Contacto/Pedir demonstração") needs a concrete value at plan time — REQUIREMENTS.md/research flag it as a static `mailto:` link, but the exact address is a content decision not yet made; Phase 99 planning should confirm this with the user rather than inventing a placeholder that ships to production.
- (v2.12) Whether `webpage/` needs its own favicon/OG-image/robots.txt is explicitly out of scope for this milestone (see REQUIREMENTS.md Out of Scope) — Phase 99/100 must not silently add extra Caddy routing branches for this; it inherits `web/`'s static files via the existing catch-all.

### Blockers/Concerns

- ~~`MINIO_ENDPOINT` environmental blocker (recurring across v2.8/v2.9/v2.10 sessions, prevents full Spring context startup for live UAT)~~ — **RESOLVED (2026-07-14, v2.11 Phase 97 AUD-04):** `backend/.env` (gitignored, not committed) now supplies a real `MINIO_ENDPOINT=http://localhost:9000` plus working credentials against a running `lexcv_minio` Docker container the user started deliberately for this session. The Spring context now boots fully — `MinioConfig.s3Client()` no longer throws the "Illegal character ... `${MINIO_ENDPOINT}`" `IllegalArgumentException` that previously blocked every controller from becoming reachable. `backend/.env.example` already documents all required `MINIO_*` vars (`MINIO_ENDPOINT`, `MINIO_PUBLIC_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`) for any future environment. This is an environment/config resolution, not a code fix — no source files were modified to close this blocker; it was never a code defect (see `.planning/milestones/v2.10-MILESTONE-AUDIT.md`).
- ~~(v2.12, flagged by research, unverified) Server-side "hairpin" fetch risk: a relative-URL `fetch()` inside the `webpage` container calling `/api/v1/setup/status` may resolve against the public domain (routing back out through Caddy) instead of the internal Docker network~~ — **RESOLVED (2026-07-15, Phase 100-04):** live `docker compose up` test proved `BACKEND_API_ORIGIN=http://backend:8080` resolves correctly against the internal Docker network with zero hairpin. Separately, code review (99-REVIEW.md CR-01) found `webpage/src/lib/setup.ts` itself used a *relative* URL (not the internal-origin absolute URL the research anticipated) — a distinct, more severe bug (relative URLs throw in Node/Edge `fetch`, silently disabling the `/setup` redirect gate entirely) — fixed by switching to the same `BACKEND_API_ORIGIN`-based absolute-URL pattern `branding.ts` already used.

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

Last session: 2026-07-16T17:33:36.664Z
Stopped at: Completed 105-03-PLAN.md (Partes/Fases/Testemunhas Table primitives + Testemunhas Avatar + Documentos DataTable); 105-06 (closing plan) remains
Resume file: None

## Operator Next Steps

- Phase 102 (Reconciliação do Design System) is complete — 13 hand-rolled shadcn components reconciled, mandatory human visual checkpoint approved (dark-mode elevation confirmed intended, Rule-C identity unchanged, DSR-03 tooltips verified).
- Start planning with /gsd:plan-phase 103 (Módulo Dashboard) — lowest primitive need, validates the new token layer; parallelizable with Phase 104 (Padrão DataTable Partilhado) once both are planned.
