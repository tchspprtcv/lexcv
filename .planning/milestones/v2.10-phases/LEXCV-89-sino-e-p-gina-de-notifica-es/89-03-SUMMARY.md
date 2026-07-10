---
phase: 89-sino-e-pagina-de-notificacoes
plan: 3
subsystem: ui
tags: [tanstack-query, typescript, notificacoes, pagination, rbac, next-app-router]

# Dependency graph
requires:
  - phase: 89-sino-e-pagina-de-notificacoes (Plan 1)
    provides: "Notificacao/NotificacaoCategoria/NotificacoesListFilters/NotificacoesPageResponse types, categoriaToLabel/categoriaToBadgeVariant/NOTIFICACAO_CATEGORIA_OPTIONS, and the useNotificacoes/useNotificacoesUnreadCount/useMarcarNotificacaoLida/useMarcarTodasNotificacoesLidas hook contract this plan's page consumes verbatim"
provides:
  - "web/src/app/(dashboard)/notificacoes/page.tsx — dedicated, RBAC-gated /notificacoes history page: categoria select + 3-way lida/não-lida chip filter (resets page to 0), stacked unread-aware row list, standalone mark-one Check button (NOTF-10), header mark-all button (NOTF-11), and functional Anterior/Seguinte pagination (NOTF-12/13)"
affects: [89-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "First functional (non-decorative) pagination controls in the codebase — Anterior/Seguinte + 'Página X de Y', built directly from 89-UI-SPEC (processos/page.tsx's numbered buttons are unwired decoration, not a real analog)"
    - "First mutually-exclusive 3-way single-select chip toggle (Todas/Não lidas/Lidas via lidaFilter === value), diverging from the codebase's only other chip-toggle precedent which is additive multi-select (Set-based)"
    - "Stacked-list-at-all-breakpoints row pattern for a non-tabular entity — no <Table>, no desktop/mobile fork, single row component used everywhere"

key-files:
  created:
    - web/src/app/(dashboard)/notificacoes/page.tsx
  modified: []

key-decisions:
  - "useNotificacoesUnreadCount() (from Plan 89-01) drives the header 'Marcar todas como lidas' button's disabled state (unreadCount===0 OR isPending) — the task's own <action> text only mentioned isPending, but the plan's must_haves.truths and 89-UI-SPEC's Copywriting Contract both require the unreadCount===0 condition too, and no later task in this plan revisits the header, so it had to land in Task 1"
  - "categoria local state kept as plain string (per plan's explicit instruction) but cast to NotificacaoCategoria at the useNotificacoes call boundary — safe because the <select>'s only non-empty values come from NOTIFICACAO_CATEGORIA_OPTIONS"
  - "Single page-level useMarcarNotificacaoLida() instance (per plan's explicit 'Estado local' instruction) shared across all rows via props, with per-row pending detection via marcarLida.variables === n.id rather than one hook instance per row"
  - "Row list + pagination controls wrapped in a <> Fragment inside the ternary's content branch — required per this project's own established precedent (PROJECT.md: 'React fragments obrigatórios em siblings dentro de ternário JSX', bug found in Phase 54)"
  - ".planning/REQUIREMENTS.md NOT modified for NOTF-10/11/12/13 — matches 89-01-SUMMARY.md's identical judgment call for these same IDs; this plan's own <verification> section explicitly defers end-to-end verification to Plan 89-04's checkpoint"

patterns-established:
  - "linkUrl same-origin guard: render <Link> only when linkUrl is a string starting with '/', else plain non-navigating text — blocks open-redirect/javascript: URIs and null values (T-89-03-01)"
  - "Standalone action button as a JSX sibling of a <Link>, never nested inside it, to avoid invalid nested-interactive-element HTML"

requirements-completed: [NOTF-10, NOTF-11, NOTF-12, NOTF-13]

# Metrics
duration: ~20min
completed: 2026-07-10
---

# Phase 89 Plan 3: Página de Notificações Summary

**Dedicated `/notificacoes` history page — categoria + 3-way lida/não-lida chip filters (single-select, reset page to 0), stacked unread-aware rows with standalone mark-one Check button, header mark-all button, and functional Anterior/Seguinte pagination — all RBAC-gated on `notificacoes:view`.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-10
- **Tasks:** 2/2 completed
- **Files modified:** 1 (new)

## Accomplishments
- New file `web/src/app/(dashboard)/notificacoes/page.tsx`: full history page for the `Notificacao` entity, gated by `permissions.can.view("notificacoes")` with `AccessDeniedState` fallback (defense-in-depth, matches every other page's pattern).
- Categoria `<select>` (verbatim className from the Phase 87 precedent) and a 3-way mutually-exclusive chip toggle (Todas/Não lidas/Lidas, `aria-pressed`, single-select via `lidaFilter === value`) — both reset `page` to `0` on change and refetch immediately (no draft/Aplicar step).
- Stacked row list (no `<Table>`, single layout at all breakpoints): unread rows show a blue dot + semibold title; the title is wrapped in a same-origin-guarded `<Link>` that marks-as-read-and-navigates on click (only when unread); a standalone icon-only `Check` button — a JSX sibling of the `<Link>`, never nested inside it — lets a row be marked read without navigating (NOTF-10).
- Header "Marcar todas como lidas" button (NOTF-11): disabled when `unreadCount.data?.count === 0` or while the mutation is pending, shows "A marcar..." while in flight, fires `toast.success(...)` only on success.
- Functional pagination (NOTF-12/13): "Anterior"/"Seguinte" + "Página {page+1} de {totalPages}", disabled at both boundaries, reads `totalPages` from the live `NotificacoesPageResponse` (not hardcoded), only rendered when `totalPages > 1` — the first real (non-decorative) pager in this codebase.
- Two distinct empty-state copies: "Sem notificações" (zero ever) vs. "Nenhuma notificação encontrada para os filtros selecionados." + a "Limpar filtros" button that resets `categoria`/`lidaFilter`/`page` to defaults (only shown when `hasFilters` is true).
- Zero polling on this page (`useNotificacoes(...)` called without a second `opts` argument) — deliberate, per 89-UI-SPEC, to avoid shifting content under the user's cursor/scroll position while paginating/reading.

## Task Commits

Each task was committed atomically:

1. **Task 1: Página /notificacoes — gate RBAC, cabeçalho, filtros e lista com marcar-uma/marcar-todas** - `7557830` (feat)
2. **Task 2: Paginação funcional + "Limpar filtros" na variante vazio-com-filtros** - `f29ca43` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/notificacoes/page.tsx` - New page: `NotificacoesPage` (RBAC gate) + `NotificacoesContent` (filters, header actions, list, pagination) + `NotificacaoRow` (stacked row: unread dot/weight, categoria Badge, title Link with same-origin guard, standalone Check button).

## Decisions Made
- `useNotificacoesUnreadCount()` (built by Plan 89-01, otherwise only consumed by the sino per 89-UI-SPEC's query table) is also used on this page solely to compute the "Marcar todas como lidas" button's disabled state — required by 89-UI-SPEC's Copywriting Contract ("Disabled when unreadCount === 0 or while the mutation is pending", stated as applying to both surfaces where the CTA appears) and by this plan's own `must_haves.truths`. This does not reintroduce list-polling on the page — only the small, separate unread-count query polls; the paginated `useNotificacoes(...)` list query itself is called with no `opts` argument at all.
- `categoria` filter state stays a plain `string` (per the plan's explicit "Estado local" instruction) but is cast to `NotificacaoCategoria` only at the `useNotificacoes` call boundary, since the `<select>`'s only non-empty values are drawn from `NOTIFICACAO_CATEGORIA_OPTIONS` — see Deviations for the type error this fixed.
- Extracted a standalone `NotificacaoRow` function component (rather than inlining the `.map()`) so the single page-level `marcarLida`/`marcarTodas` mutation instances (per the plan's explicit instruction to instantiate them once, not per row) could be passed down as props, with per-row "is this row's own mutation in flight" detected via `marcarLida.variables === n.id` rather than giving each row its own hook instance.
- Wrapped the "Resultados" card in `Card`/`CardHeader` (`CardTitle`: "Resultados") mirroring the Documentos page's two-card layout (Filtros + Resultados) — the plan left this structural choice to Claude's discretion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Installed frontend dependencies into the fresh worktree checkout**
- **Found during:** Task 1 verification (`pnpm --dir web build`)
- **Issue:** `web/node_modules` does not exist in this git worktree (worktrees don't carry over untracked/ignored directories) — identical root cause to the same deviation already documented in `89-01-SUMMARY.md`.
- **Fix:** Ran `pnpm --dir web install --frozen-lockfile`. Confirmed via `git status --short -- web/pnpm-lock.yaml web/package.json` before and after that neither file changed.
- **Files modified:** none tracked (node_modules is gitignored).
- **Verification:** `pnpm --dir web build` ran to completion afterward.
- **Committed in:** n/a (gitignored, nothing to commit).

**2. [Rule 3 - Blocking] Created local `web/.env.local` to unblock the build**
- **Found during:** Task 1 verification (`pnpm --dir web build`)
- **Issue:** `next.config.ts` throws `BACKEND_API_ORIGIN is required` at config-load time without it — same as `89-01-SUMMARY.md`'s identical deviation.
- **Fix:** Created `web/.env.local` with the exact placeholder values from `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`).
- **Files modified:** `web/.env.local` (confirmed gitignored via `git check-ignore -v`; not committed).
- **Verification:** `pnpm --dir web build` completed successfully — full TypeScript check and static generation of all 24 routes (including `/notificacoes`) passed.
- **Committed in:** n/a (gitignored, intentionally local-only).

**3. [Rule 1 - Bug] Fixed TypeScript strict-mode type error on the categoria filter**
- **Found during:** Task 1 verification (`pnpm --dir web build`)
- **Issue:** `categoria: categoria || undefined` produced type `string | undefined`, not assignable to `NotificacoesListFilters.categoria: NotificacaoCategoria | undefined` — build failed at the TypeScript check step.
- **Fix:** Switched to an explicit empty-string check with a safe cast: `categoria === "" ? undefined : (categoria as NotificacaoCategoria)`. Safe because the `<select>`'s only non-empty values are drawn from `NOTIFICACAO_CATEGORIA_OPTIONS`, whose members are exactly the `NotificacaoCategoria` union.
- **Files modified:** `web/src/app/(dashboard)/notificacoes/page.tsx`.
- **Verification:** `pnpm --dir web build` passed the TypeScript check afterward; route `/notificacoes` generated successfully.
- **Committed in:** `7557830` (Task 1 commit).

### Judgment Call

**4. `.planning/REQUIREMENTS.md` intentionally not modified for NOTF-10/11/12/13**
- This plan's frontmatter declares `requirements: [NOTF-10, NOTF-11, NOTF-12, NOTF-13]`, matching exactly the same 4 IDs (plus NOTF-08) that `89-01-SUMMARY.md` already made the identical judgment call on. This plan's own `<verification>` section explicitly states: "Verificação end-to-end (filtros contra dados reais, paginação, marcar-uma/todas) é feita no checkpoint do Plan 89-04" — i.e., this plan itself defers the real end-to-end confirmation to the dedicated wave-3 verification plan.
- `requirements-completed` in this SUMMARY's frontmatter is populated mechanically per the summary template's instruction (copy the plan's own frontmatter field); the live `.planning/REQUIREMENTS.md` traceability table (still showing all 4 as "Pending" — verified before writing this summary) was left untouched.
- Additional reason specific to this wave: Plan 89-02 (sino) runs concurrently in a sibling worktree and its frontmatter likely declares an overlapping subset of these same requirement IDs (NOTF-08/10/11) — independently editing the same `REQUIREMENTS.md` lines from two parallel worktrees would risk a merge conflict on a shared file that neither plan's own worktree isolation is designed to resolve. Left to the orchestrator/Plan 89-04 to flip centrally after the wave completes.

---

**Total deviations:** 3 auto-fixed (2x Rule 3 environment/tooling setup — identical root cause to 89-01, 1x Rule 1 type-error bug fix), 1 judgment call (requirement-tracking scope, matching established precedent).
**Impact on plan:** Zero impact on the delivered page's behavior or structure. Both Rule 3 auto-fixes were local environment setup (gitignored, not committed); the Rule 1 fix is a minimal, safe type-narrowing change committed as part of Task 1.

## Issues Encountered
None beyond the above. Task 1's automated verification (`pnpm --dir web build`) passed after the type-error fix; Task 2 passed on first attempt with no further issues. `pnpm --dir web lint` was also run defensively on the finished file (not the plan's own verification command) and confirmed the new page introduces zero new lint errors/warnings — all 6 errors/17 warnings reported are pre-existing, in unrelated files, already logged in `.planning/phases/LEXCV-89-sino-e-p-gina-de-notifica-es/deferred-items.md` by Plan 89-01.

## User Setup Required

None - no external service configuration required. The local `web/.env.local` created to unblock the build verification is gitignored and matches the normal per-developer setup step already documented in `CLAUDE.md`/`web/.env.example` — not a new requirement introduced by this plan.

## Next Phase Readiness

- `/notificacoes` is fully implemented and build-verified: RBAC gate, categoria + lida/não-lida filters (resetting page to 0 on change), stacked list with unread dot/weight signal + categoria Badge, mark-one (standalone `Check` button, sibling of `Link`) and mark-all (header button, `toast.success` on success, disabled when no unread) actions, and functional Anterior/Seguinte pagination with two distinct empty-state copies plus "Limpar filtros".
- Ready for Plan 89-04 (wave 3, `depends_on: [89-02, 89-03]`) — the dedicated end-to-end verification plan against real notification data generated by Phases 87/88; this plan's own `<verification>` section explicitly defers that step rather than duplicating it here.
- `.planning/REQUIREMENTS.md` checkbox completion for NOTF-10/11/12/13 deferred to Plan 89-04 (see Deviations item 4) — no action needed from 89-02, this is a wave-3/orchestrator concern.
- No blockers for 89-04. Plan 89-02 (sino rewrite, `web/src/components/shared/notification-bell.tsx`) ran concurrently in a sibling worktree with zero file overlap with this plan's `web/src/app/(dashboard)/notificacoes/page.tsx`.

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/notificacoes/page.tsx
- FOUND commit: 7557830
- FOUND commit: f29ca43

---
*Phase: 89-sino-e-pagina-de-notificacoes*
*Plan: 03*
*Completed: 2026-07-10*
