---
phase: 89-sino-e-pagina-de-notificacoes
plan: 1
subsystem: ui
tags: [tanstack-query, react-query, typescript, notificacoes, polling]

# Dependency graph
requires:
  - phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting
    provides: "GET /notificacoes, GET /notificacoes/unread-count, PATCH /notificacoes/{id}/lida, POST /notificacoes/ler-todas — already-shipped REST API this plan's hooks consume"
provides:
  - "Notificacao / NotificacaoCategoria / NotificacoesListFilters / NotificacoesPageResponse types — plain camelCase, no normalize*/toApiPayload mapping layer"
  - "categoriaToLabel / categoriaToBadgeVariant / NOTIFICACAO_CATEGORIA_OPTIONS single-source-of-truth categoria map"
  - "useNotificacoes / useNotificacoesUnreadCount / useMarcarNotificacaoLida / useMarcarTodasNotificacoesLidas hooks — the complete data contract for the sino and /notificacoes page"
affects: [89-02, 89-03, 89-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-query refetchInterval + refetchOnWindowFocus override of the global refetchOnWindowFocus:false default (first use of refetchInterval in the project)"
    - "Top-level query-key prefix invalidation (['notificacoes']) instead of a ['notificacoes','list']-suffixed key, to catch unread-count and both list queries (dropdown + page) in one invalidateQueries call"
    - "opts.poll opt-in flag on a single list hook, letting the same useNotificacoes serve both a polling consumer (dropdown) and a non-polling consumer (page)"

key-files:
  created:
    - web/src/types/notificacoes.ts
    - web/src/lib/notificacao-categoria.ts
    - web/src/hooks/use-notificacoes.ts
  modified: []

key-decisions:
  - "No normalize*/toApiPayload mapping layer for Notificacao — 89-UI-SPEC verified the backend JSON keys are already camelCase; first entity type file in the project without this translation layer"
  - "useNotificacoes(filters, { poll }) takes a second opts argument so Plan 89-02 (dropdown, poll:true) and Plan 89-03 (page, no poll) share one hook instead of two near-duplicates"
  - "Deferred the actual .planning/REQUIREMENTS.md checkbox flip for NOTF-08/10/11/13 to Plan 89-04 (wave-3 end-to-end verification) since this plan's own truths are data-layer only — see Deviations for full rationale"

patterns-established:
  - "Polling override pattern: refetchInterval: 30_000 + refetchOnWindowFocus: true, opt-in per query"
  - "Broad top-level invalidateQueries({ queryKey: ['notificacoes'] }) prefix match after mutations touching multiple sibling queries at once"

requirements-completed: [NOTF-08, NOTF-10, NOTF-11, NOTF-13]

# Metrics
duration: 20min
completed: 2026-07-10
---

# Phase 89 Plan 1: Camada de Dados de Notificações Summary

**Tipos camelCase sem mapeamento, mapa categoria->label/variant single-source, e 4 hooks TanStack Query (list com polling opt-in, contador com polling+refocus obrigatórios, marcar-uma/marcar-todas com invalidação de prefixo top-level) — o contrato de dados completo que as Plans 89-02/89-03 consomem em paralelo na Wave 2.**

## Performance

- **Duration:** ~20 min (includes `pnpm install` for a fresh worktree checkout + full `pnpm build`)
- **Completed:** 2026-07-10T09:11:29Z
- **Tasks:** 3/3 completed
- **Files modified:** 3 (all new)

## Accomplishments
- `web/src/types/notificacoes.ts` defines `Notificacao`/`NotificacaoCategoria` (9-value union)/`NotificacoesListFilters`/`NotificacoesPageResponse` in plain camelCase — the first entity type file in the codebase that needs no `normalize*`/`toApiPayload` translation layer, verified directly against the backend entity by 89-UI-SPEC.
- `web/src/lib/notificacao-categoria.ts` establishes the single source of truth for categoria display: `categoriaToLabel`/`categoriaToBadgeVariant` as exhaustive `Record<NotificacaoCategoria, T>` maps (TypeScript enforces exhaustiveness against the 9-value union) plus `NOTIFICACAO_CATEGORIA_OPTIONS` derived for the future filter `<select>`.
- `web/src/hooks/use-notificacoes.ts` ships the complete 4-hook data contract: `useNotificacoes` (paginated list, polling opt-in via `opts.poll`), `useNotificacoesUnreadCount` (mandatory 30s polling + window-refocus override — first `refetchInterval` use in the project), and `useMarcarNotificacaoLida`/`useMarcarTodasNotificacoesLidas` (no-body PATCH/POST mutations invalidating the top-level `["notificacoes"]` prefix, no manual optimistic writes).
- Zero visual surface, as scoped — this plan is purely the interface Wave 2 (89-02 sino, 89-03 página) builds on.

## Task Commits

Each task was committed atomically:

1. **Task 1: Tipos em web/src/types/notificacoes.ts** - `be23341` (feat)
2. **Task 2: Mapa de categoria em web/src/lib/notificacao-categoria.ts** - `f9f6224` (feat)
3. **Task 3: Hooks TanStack Query em web/src/hooks/use-notificacoes.ts** - `a3b50d5` (feat)

## Files Created/Modified
- `web/src/types/notificacoes.ts` - `Notificacao` entity, `NotificacaoCategoria` 9-value union, `NotificacoesListFilters`, `NotificacoesPageResponse` paginated wrapper; no mapping functions.
- `web/src/lib/notificacao-categoria.ts` - `categoriaToLabel`/`categoriaToBadgeVariant` (`Record` + safe `??` fallback, mirrors `prazos.ts`) and `NOTIFICACAO_CATEGORIA_OPTIONS` (`{value,label}[]` derived via `.map`).
- `web/src/hooks/use-notificacoes.ts` - `buildNotificacoesSearch` (private) + `useNotificacoes`, `useNotificacoesUnreadCount`, `useMarcarNotificacaoLida`, `useMarcarTodasNotificacoesLidas`.

## Decisions Made
- No `normalize*`/`toApiPayload` for `Notificacao` — confirmed against 89-UI-SPEC's direct read of `Notificacao.java`; the backend JSON keys are already the exact camelCase field names.
- `useNotificacoes`'s query key uses the plan's specified defaults (`filters.categoria ?? ""`, `filters.lida ?? null`, `filters.page ?? 0`, `filters.size ?? 20`) distinct from `buildNotificacoesSearch`'s own `!== undefined` guards — the query key needs stable defaults for cache identity, while the URL builder only sends params the caller explicitly set.
- `opts.poll === true` is the sole gate for `refetchInterval`/`refetchOnWindowFocus` on `useNotificacoes`; when omitted or `false`, neither option is set at all (not set-to-`false`), so the query inherits the global default cleanly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Installed frontend dependencies into the fresh worktree checkout**
- **Found during:** Task 1 verification (`pnpm --dir web lint`)
- **Issue:** `web/node_modules` does not exist in this git worktree (worktrees don't carry over untracked/ignored directories) — `eslint`/`next` were both unresolvable, blocking every verification step in this plan.
- **Fix:** Ran `pnpm --dir web install --frozen-lockfile`. Confirmed via `git status --short -- web/pnpm-lock.yaml web/package.json` before and after that neither file changed — no new/updated packages, purely materializing the existing lockfile into `node_modules`. Not a "new package install" under the Rule 3 exclusion (no new dependency name introduced, nothing to verify for legitimacy).
- **Files modified:** none tracked (node_modules is gitignored)
- **Verification:** `pnpm --dir web lint` and `pnpm --dir web build` both ran to completion afterward.
- **Committed in:** n/a (gitignored, nothing to commit)

**2. [Rule 3 - Blocking] Created local `web/.env.local` to unblock the build**
- **Found during:** Task 3 verification (`pnpm --dir web build`)
- **Issue:** `next.config.ts` throws `BACKEND_API_ORIGIN is required` at config-load time; this fresh worktree checkout has no `.env.local` (gitignored, per-developer local file, expected per CLAUDE.md — "Requires web/.env.local").
- **Fix:** Created `web/.env.local` with the exact placeholder values from `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`) — sufficient for `next build`'s static compile/typecheck pass, which does not need a live backend connection.
- **Files modified:** `web/.env.local` (confirmed gitignored via `git check-ignore -v`; not committed, not staged, will not appear in the merged branch)
- **Verification:** `pnpm --dir web build` completed successfully — Turbopack compile, full TypeScript check, and static generation of all 23 routes all passed.
- **Committed in:** n/a (gitignored, intentionally local-only)

### Scope Boundary — logged, not fixed

**3. Pre-existing lint errors/warnings unrelated to this plan's files**
- `pnpm --dir web lint` reports 6 errors / 17 warnings, all in files this plan does not touch (`dashboard-shell.tsx`, `processos/[id]/page.tsx`, `pareceres/nova/page.tsx`, `settings/page.tsx`, `processos/novo/page.tsx`, `termo-honorarios/page.tsx`, `user-profile-form.tsx`). Confirmed zero matches for "notificac" in the lint output — the 3 new files are lint-clean.
- Logged to `.planning/phases/LEXCV-89-sino-e-p-gina-de-notifica-es/deferred-items.md` per the scope-boundary rule (only auto-fix issues directly caused by this plan's changes); not fixed.

### Judgment Call — requirement-tracking scope

**4. `.planning/REQUIREMENTS.md` intentionally not modified for NOTF-08/10/11/13**
- This plan's frontmatter declares `requirements: [NOTF-08, NOTF-10, NOTF-11, NOTF-13]`, but all 4 IDs are also declared in `89-02-PLAN.md` and/or `89-03-PLAN.md` (Wave 2, both `depends_on: [89-01]`), and all 6 phase requirements — including these 4 — are re-declared in `89-04-PLAN.md` (Wave 3, `depends_on: [89-02, 89-03]`, `autonomous: false`), the dedicated end-to-end verification plan against real notification data.
- This plan's own `must_haves.truths` are data-layer-only (hook/query behavior) — no UI consumes these hooks yet ("Nenhuma superfície visual" per the plan objective). Flipping NOTF-08 ("Utilizador vê um contador...")/NOTF-10/NOTF-11/NOTF-13 to "Done" in the shared `.planning/REQUIREMENTS.md` now, before the sino/página exist, would be a materially premature claim.
- `requirements-completed` in this SUMMARY's frontmatter is populated mechanically per the SUMMARY template's instruction (copy the plan's own frontmatter field) for dependency-graph/context-assembly purposes. The live `.planning/REQUIREMENTS.md` traceability table itself was left untouched, consistent with this worktree agent's mandate to leave shared/cross-wave artifacts to the orchestrator — deferring the actual checkbox flip to Plan 89-04, which is structurally positioned to confirm these truths end-to-end.

---

**Total deviations:** 2 auto-fixed (both Rule 3, environment/tooling setup, no code changes), 1 scope-boundary item logged (not fixed), 1 requirement-tracking judgment call (documented, no shared-file mutation).
**Impact on plan:** Zero impact on the 3 delivered files or their behavior. All auto-fixes were local environment setup (uncommitted/gitignored) required to run this plan's own verification commands.

## Issues Encountered
None beyond the environment/tooling setup documented above — no bugs or rework needed on the 3 delivered files themselves; each task's automated verification (`pnpm --dir web lint` for Tasks 1-2, `pnpm --dir web build` for Task 3) passed on first attempt once the environment was set up.

## User Setup Required

None - no external service configuration required. The local `web/.env.local` created to unblock the build verification is gitignored and matches the normal per-developer setup step already documented in `CLAUDE.md`/`web/.env.example` — not a new requirement introduced by this plan.

## Next Phase Readiness

- The full data contract (types + categoria map + hooks) is committed and ready for Wave 2: `89-02` (sino rewrite) and `89-03` (página `/notificacoes`), both `depends_on: [89-01]`.
- `useNotificacoes(filters, { poll })`'s `opts.poll` flag is the exact mechanism 89-02 (dropdown, `{ size: 10 }` + `poll: true`) and 89-03 (page, paginated filters, no poll) are expected to consume per 89-UI-SPEC's Query behavior contract.
- `.planning/phases/LEXCV-89-sino-e-p-gina-de-notifica-es/deferred-items.md` carries forward 6 pre-existing lint errors / 17 warnings (unrelated to this plan) for a future cleanup phase.
- `.planning/REQUIREMENTS.md` checkbox completion for NOTF-08/10/11/13 deferred to Plan 89-04 (see Deviations item 4) — no action needed from 89-02/89-03, this is a Wave-3/orchestrator concern.
- No blockers for Wave 2.

## Self-Check: PASSED

- FOUND: web/src/types/notificacoes.ts
- FOUND: web/src/lib/notificacao-categoria.ts
- FOUND: web/src/hooks/use-notificacoes.ts
- FOUND commit: be23341
- FOUND commit: f9f6224
- FOUND commit: a3b50d5

---
*Phase: 89-sino-e-p-gina-de-notifica-es*
*Plan: 01*
*Completed: 2026-07-10*
