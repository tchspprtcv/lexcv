---
phase: 92-agenda-riscoprazoservice-consolida-o
plan: 02
subsystem: ui
tags: [nextjs, react, tanstack-query, agenda, risco-prazo, refactor]

# Dependency graph
requires:
  - phase: 92-01
    provides: GET /eventos now returns a top-level risco (ok/proximo/vencido) per event, computed via RiscoPrazoService; removal of the orphaned GET /eventos/upcoming endpoint
provides:
  - "web/src/types/eventos.ts Evento.risco?: PrazoRisco field, mirroring the existing Prazo.risco convention"
  - "agenda/page.tsx allUnifiedEvents carries risco for both Prazos (previously discarded) and Eventos (new field from 92-01), via explicit copy and existing spread respectively"
  - "weekStats.urgentes now derived from backend risco (proximo/vencido) instead of the prioridade === ALTA client-side proxy"
  - "Dead useUpcomingEventos hook, UpcomingEvento type, and 4 orphaned [\"eventos\",\"upcoming\"] query invalidations removed"
affects: [agenda-page-frontend, dashboard-risco-consistency-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Frontend fully defers to backend RiscoPrazoService verdict via a risco?: PrazoRisco field on Evento (already established for Prazo); no client-side risk recomputation anywhere in web/src"

key-files:
  created: []
  modified:
    - web/src/types/eventos.ts
    - web/src/hooks/use-eventos.ts
    - web/src/app/(dashboard)/agenda/page.tsx

key-decisions:
  - "weekStats.urgentes counts risco === \"proximo\" || risco === \"vencido\" (not vencido-only), per the plan's locked product decision — a MEDIA-priority prazo due tomorrow now counts as urgent, while a distant ALTA-priority prazo no longer does; this is an intentional correction of the prior prioridade===ALTA proxy, not a regression"
  - "getCategoria (PRAZO/AUDIENCIA/DILIGENCIA/REUNIAO classification) left untouched — a separate concept from risco, out of this plan's scope"
  - "No new UI components/badges added — risco was already being computed server-side and consumed nowhere new; only the source of truth changed"

requirements-completed: [AGD-34, AGD-35]

# Metrics
duration: ~20min
completed: 2026-07-13
---

# Phase 92 Plan 02: Agenda consumes backend risco, drops dead upcoming hook Summary

**`agenda/page.tsx` now derives "Urgentes" and per-item risk entirely from the backend's `RiscoPrazoService` output (via `risco` on both `Prazo` and `Evento`), replacing the last client-side `prioridade === "ALTA"` proxy, and the dead `useUpcomingEventos`/`UpcomingEvento` code (orphaned since the `/eventos/upcoming` endpoint removal in 92-01) is deleted.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-13T22:39:48Z
- **Tasks:** 2/2 auto tasks completed; 1 checkpoint task auto-verified (see below)
- **Files modified:** 3

## Accomplishments
- `Evento` interface gained `risco?: PrazoRisco` (imports `PrazoRisco` from `@/types/processos`), and the dead `UpcomingEvento` interface was deleted
- `use-eventos.ts`: removed the `UpcomingEvento` import, the entire `useUpcomingEventos` export, and all 4 orphaned `invalidateQueries({ queryKey: ["eventos", "upcoming"] })` calls (in `useToggleEventoConcluido`, `useSetEventoConcluido`, `useDeleteEvento`, `useDeleteEventoInstance`)
- `agenda/page.tsx`: `allUnifiedEvents`'s `pzs` mapping now copies `risco: p.risco` (previously silently discarded); the `evs` mapping already carries `e.risco` transparently via its pre-existing `{...e}` spread now that `Evento.risco` exists
- `weekStats.urgentes` now reads `e.risco === "proximo" || e.risco === "vencido"` instead of `e.prioridade === "ALTA"` — the Agenda's "Urgentes" count now agrees with the Dashboard and the daily job on what counts as risky, closing the "5th divergent prazo-crítico implementation"
- Zero residual client-side risk computation, zero `prioridade === "ALTA"`, zero `useUpcomingEventos`/`UpcomingEvento`/`eventos/upcoming` references anywhere in `web/src` (confirmed via grep)

## Task Commits

Each task was committed atomically:

1. **Task 1: Tipo Evento.risco + remoção do hook/tipo mortos de upcoming** - `d80054d` (feat)
2. **Task 2: Agenda usa risco do backend para Prazos e Eventos; remove o proxy prioridade === ALTA** - `d513438` (feat)
3. **Task 3: Verificação visual da consolidação de risco na Agenda** - checkpoint, auto-verified per autonomous-mode config (`parallelization.skip_checkpoints: true`); see "Checkpoint Verification" below

**Plan metadata:** this SUMMARY commit (SUMMARY.md only — STATE.md/ROADMAP.md/REQUIREMENTS.md intentionally left untouched per orchestrator contract for this execution)

## Files Created/Modified
- `web/src/types/eventos.ts` - added `import type { PrazoRisco } from "@/types/processos"` and `risco?: PrazoRisco` on `Evento`; removed dead `UpcomingEvento` interface
- `web/src/hooks/use-eventos.ts` - removed `UpcomingEvento` import, `useUpcomingEventos` export, and 4 orphaned `["eventos","upcoming"]` invalidations
- `web/src/app/(dashboard)/agenda/page.tsx` - `pzs` mapping in `allUnifiedEvents` now sets `risco: p.risco`; `weekStats.urgentes` now uses `e.risco === "proximo" || e.risco === "vencido"` instead of `e.prioridade === "ALTA"`

## Decisions Made
- Followed the plan's locked product decision verbatim: "Urgentes" = `risco` in `{proximo, vencido}`, not `vencido`-only — items entering the 7-day/3-day proximity window are precisely what a weekly "urgent" count should capture.
- Left `getCategoria` (PRAZO/AUDIENCIA/DILIGENCIA/REUNIAO classification) and the local `upcoming` const (unrelated to the removed endpoint) untouched, exactly as the plan specified.
- No new UI components, badges, or visual states added — deep_work_rules scope boundary respected.

## Deviations from Plan

None (Rules 1-4) required for the plan's own task content — both auto tasks were implemented exactly as specified and passed their own automated verification on the first attempt.

Two environment-setup actions were needed to *run* the plan's prescribed verification commands (not deviations from the plan's code changes, but necessary to execute `pnpm build`/live-checks in this fresh git worktree):
- `web/node_modules` did not exist in this worktree (each worktree checkout is independent) — ran `pnpm install` against the existing, unmodified `pnpm-lock.yaml` to install already-pinned dependencies. No package.json/lockfile changes; this is environment setup, not a new dependency.
- `web/.env.local` and `backend/.env` did not exist in this worktree (both gitignored, not shared across worktrees) — recreated them with the same non-secret local-dev values already present in the main repo checkout (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`; backend DB/JWT dev placeholders matching `backend/.env.example` conventions), so that `pnpm build` and the backend smoke-start attempt could run. Neither file is committed (gitignored).

## Checkpoint Verification (Task 3, `checkpoint:human-verify`)

Per project config (`.planning/config.json` → `parallelization.skip_checkpoints: true`, autonomous execution mode) and the executor's checkpoint-handling instructions, this checkpoint was treated as auto-passable: all automated verification feasible without live human eyes was performed, documented below, and execution continued rather than blocking.

**Automated checks performed (all passed):**
- `cd web && npx tsc --noEmit` — no errors
- `cd web && pnpm build` — succeeded; all 24 routes (including `/agenda`) compiled and statically/dynamically generated with no runtime errors
- `cd web && pnpm lint` — 6 errors / 17 warnings total in the repo, but **zero** in any of the three files touched by this plan (`agenda/page.tsx`, `use-eventos.ts`, `types/eventos.ts`); pre-existing baseline in unrelated files unaffected, matching the plan's "no NEW issues in touched files" requirement
- `grep -rn "prioridade === \"ALTA\"\|useUpcomingEventos\|UpcomingEvento\|eventos/upcoming" web/src` — zero matches, confirming full removal
- `cd backend && mvn -q -DskipTests compile` — backend (already modified by 92-01, now merged to master and fast-forwarded into this worktree) compiles cleanly

**Attempted but blocked (documented, not fixed — pre-existing, out of this plan's scope):**
- Attempted to start the backend (`mvn spring-boot:run`) headlessly against local Postgres (reachable on `localhost:5432`) to perform a live browser/API smoke check of the Agenda page and the "Urgentes" count. Startup failed with `Illegal character in path at index 1: ${MINIO_ENDPOINT}` — `MinioConfig.s3Client` requires a `MINIO_ENDPOINT` env var not present in the locally reconstructed `backend/.env` (and not part of `backend/.env.example` in this checkout). This is the same pre-existing "MinIO env issue" already documented as blocking live E2E in Phase 87 and Phase 89 (see `.planning/STATE.md` deferred items for v2.10) — unrelated to this plan's frontend-only changes, and out of scope to fix here.
- Because live backend startup was blocked, the browser-based steps in the plan's `<how-to-verify>` (opening `/agenda`, visually confirming the "Urgentes" count reflects risco-by-proximity, confirming no `/eventos/upcoming` network calls) were **not** performed live. These remain genuinely pending human verification.

**What still needs a human's eyes:**
1. Start backend with a working `MINIO_ENDPOINT` (or local MinIO/S3-compatible stub) + frontend (`pnpm dev`), log in as `admin@lexcv.cv` / `Pa$$w0rd`, open `/agenda`.
2. Confirm the calendar/list load without console errors and Prazos/Eventos render normally.
3. Confirm the "Urgentes" number in the "Visão Geral da Semana" card reflects proximity-based risk (a distant ALTA-priority prazo no longer counts; a near-term MEDIA-priority prazo now does) rather than the old ALTA-only count.
4. Confirm the Network tab shows zero requests to `/eventos/upcoming`.

## Issues Encountered

- Backend smoke-start blocked by pre-existing `MINIO_ENDPOINT` env gap (see above) — not introduced by this plan, not fixed here (out of scope; same gap already tracked against Phases 87/89 in STATE.md).

## User Setup Required

None required for the code changes in this plan. For the deferred live-verification step above, whoever performs it will need a working `MINIO_ENDPOINT` value (or equivalent local MinIO setup) in `backend/.env` — this is a pre-existing repo-wide gap, not specific to this plan.

## Next Phase Readiness

- `agenda/page.tsx`, `use-eventos.ts`, and `types/eventos.ts` are fully consistent with the `RiscoPrazoService`-backed contract established across Phase 85 (v2.10) and Phase 92-01 — no client-side risk computation remains anywhere in `web/src`.
- The one still-open item is the live human/browser verification of the "Urgentes" count and network tab, blocked purely by a pre-existing local MinIO env gap unrelated to this plan; this should be folded into the same pending human-verification backlog already tracked for Phases 87/89 in `.planning/STATE.md`, not treated as a new blocker specific to Phase 92.
- No blockers for closing Phase 92 pending that same category of already-acknowledged live-verification gap.

---
*Phase: 92-agenda-riscoprazoservice-consolida-o*
*Completed: 2026-07-13*

## Self-Check: PASSED

- FOUND: web/src/types/eventos.ts
- FOUND: web/src/hooks/use-eventos.ts
- FOUND: web/src/app/(dashboard)/agenda/page.tsx
- FOUND: .planning/phases/LEXCV-92-agenda-riscoprazoservice-consolida-o/92-02-SUMMARY.md
- FOUND: commit d80054d (Task 1)
- FOUND: commit d513438 (Task 2)
