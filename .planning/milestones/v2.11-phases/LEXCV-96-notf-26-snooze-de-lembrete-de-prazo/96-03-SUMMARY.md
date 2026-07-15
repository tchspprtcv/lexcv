---
phase: 96-notf-26-snooze-de-lembrete-de-prazo
plan: 03
subsystem: ui
tags: [nextjs, react, tanstack-query, radix-ui, popover, radio-group, notifications, snooze]

# Dependency graph
requires:
  - phase: 96-notf-26-snooze-de-lembrete-de-prazo (plan 01)
    provides: "Notificacao.snoozedUntil column + PATCH /notificacoes/{id}/snooze endpoint"
  - phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
    provides: "NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS constant (reused verbatim to hide the snooze control for PRAZO_VENCIDO)"
provides:
  - "snoozedUntil field on the Notificacao frontend type"
  - "useSnoozeNotificacao TanStack Query mutation hook (PATCH .../snooze, invalidates [\"notificacoes\"])"
  - "NotificacaoSnoozeControl reusable Popover+RadioGroup component (1/3/7-day presets)"
  - "Bell dropdown: snooze control wired outside the row Link, client-side presentation filter hiding snoozed items"
  - "/notificacoes history page: snooze control + 'Adiado até DD/MM' badge on every row, unfiltered list"
affects: [97-cross-cutting-milestone-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared content/action two-column row layout so a Popover trigger button never nests inside a navigation <Link> (invalid HTML + click-bubbling bug avoided)"
    - "Client-side presentation filter (derived array, not a query param) to hide time-gated items from a preview surface while the canonical query/history stays unfiltered"

key-files:
  created:
    - web/src/components/shared/notificacao-snooze-control.tsx
  modified:
    - web/src/types/notificacoes.ts
    - web/src/hooks/use-notificacoes.ts
    - web/src/components/shared/notification-bell.tsx
    - web/src/app/(dashboard)/notificacoes/page.tsx

key-decisions:
  - "Restructured both notification-bell.tsx row branches (linked and non-linked) into ONE shared two-column shape (content column + sibling action column) instead of keeping two divergent branches with only the linked one patched — this collapsed duplicate JSX and made the 'snooze control never nested in Link' invariant structurally impossible to violate (there's now only one action-column location for both branches)."
  - "Computed a `visibleNotificacoes` filtered array once and reused it for both the bell's empty-state check and the slice(0,10) map, rather than filtering only inside the map as the plan's action text literally specified — filtering only at map-time would leave the 'Sem notificações por agora' empty-state check looking at the unfiltered list.length, so a bell with only snoozed items would incorrectly render the populated <ul> (now empty after filtering) instead of the empty-state message."
  - "Used Badge variant \"gray\" (already defined in badge.tsx) for the history page's 'Adiado até' badge, since the categoria badge already occupies amber for PRAZO_PROXIMO — kept the two badges visually distinct."

patterns-established:
  - "When a Popover/Dialog trigger must sit inside a row that also contains a navigation <Link>, split the row into a content column (Link scope) and a sibling action column (trigger scope) rather than trying to keep one branch's DOM shape and patch around it."

requirements-completed: [NOTF-26]

# Metrics
duration: ~35min
completed: 2026-07-14
---

# Phase 96 Plan 03: NOTF-26 Snooze Frontend (Bell + History Page) Summary

**Popover + RadioGroup snooze control (1/3/7-day presets) wired into both the notification bell dropdown and the `/notificacoes` history page via a new `useSnoozeNotificacao` TanStack Query mutation, with the bell's row DOM restructured so the snooze trigger never nests inside the row's navigation `<Link>`.**

## Performance

- **Duration:** ~35 min (includes worktree fast-forward of 157 commits + `pnpm install` in a fresh worktree checkout)
- **Started:** 2026-07-14T16:05:00-01:00 (approx, worktree fast-forward + context read)
- **Completed:** 2026-07-14T16:40:28-01:00
- **Tasks:** 2/2 completed
- **Files modified:** 5 (4 modified, 1 created)

## Accomplishments
- `Notificacao.snoozedUntil: string | null` added to the frontend type, mirroring the backend's nullable `LocalDateTime` column from 96-01.
- `useSnoozeNotificacao()` hook mirrors `useMarcarNotificacaoLida`'s mutation shape: `PATCH /notificacoes/{id}/snooze` with `{ dias }`, invalidating `["notificacoes"]` on success so the badge and both lists refresh immediately.
- `NotificacaoSnoozeControl` — a self-contained `Popover` + `RadioGroup` (1/3/7-day presets) that self-hides only for categories in `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS` (currently `PRAZO_VENCIDO`), with no reference to `lida` anywhere in its render gate — drops identically into both surfaces.
- `notification-bell.tsx`: both row branches (linked and non-linked) restructured into one shared `flex items-start gap-2` shape — a `flex-1 min-w-0` content column (the `<Link>`, when present, wraps ONLY `NotificacaoConteudo`) and a sibling `flex-shrink-0 flex items-center gap-1` action column holding the marcar-lida Check button (non-linked branch only, still gated by `!n.lida`) and the always-rendered `NotificacaoSnoozeControl`. A `visibleNotificacoes` client-side filter hides items whose `snoozedUntil` is a future timestamp from both the empty-state check and the rendered/sliced list, without touching the `useNotificacoes` query.
- `/notificacoes` history page: `NotificacaoRow`'s existing sibling action `<div>` (categoria Badge + `!lida`-gated Check button) now also renders `NotificacaoSnoozeControl` (independent of `lida`) and, when `snoozedUntil` is in the future, a `gray`-variant "Adiado até DD/MM" badge. The page's query/filters are untouched — snoozed items stay fully visible in history.
- `pnpm lint` and `pnpm build` both pass with zero new errors/warnings in any file touched by this plan (pre-existing lint issues in unrelated files — `dashboard-shell.tsx`, `clientes/[id]/page.tsx`, etc. — are out of scope per the executor's scope boundary and untouched).

## Task Commits

Each task was committed atomically:

1. **Task 1: snoozedUntil type + useSnoozeNotificacao hook + reusable snooze control** - `77f0019` (feat)
2. **Task 2: Wire the snooze control into the bell + history page (with snoozed filter + badge)** - `7e0f222` (feat)

## Files Created/Modified
- `web/src/types/notificacoes.ts` - adds `snoozedUntil: string | null` to the `Notificacao` interface.
- `web/src/hooks/use-notificacoes.ts` - adds `useSnoozeNotificacao()` mutation hook.
- `web/src/components/shared/notificacao-snooze-control.tsx` (new) - `NotificacaoSnoozeControl({ notificacao })`: Popover trigger (ghost icon Button, `Clock` icon, `aria-label="Adiar notificação"`) + RadioGroup (1/3/7 dias) + confirm Button; self-hides for `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS`; success toast on confirm; disabled while mutation pending.
- `web/src/components/shared/notification-bell.tsx` - restructures both dropdown row branches into a shared content/action two-column layout; adds `visibleNotificacoes` presentation filter; wires `NotificacaoSnoozeControl` into the sibling action column, outside the row `<Link>`, in both branches.
- `web/src/app/(dashboard)/notificacoes/page.tsx` - adds `NotificacaoSnoozeControl` to `NotificacaoRow`'s action column (independent of `lida`) plus an "Adiado até DD/MM" badge (`gray` variant) when `snoozedUntil` is in the future.

## Decisions Made
- Reused `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS` (from `notificacao-categoria.ts`, Phase 93) as the snooze control's sole visibility gate, per 96-CONTEXT.md's locked decision (adiável == silenciável today).
- Restructured BOTH bell row branches (not just the linked one) into one shared two-column shape, collapsing what were two divergent JSX blocks into a single pattern — this was necessary to give the linked branch an action column at all (it previously had none) while keeping the non-linked branch's existing Check-button gating intact.
- Filtered the bell's `visibleNotificacoes` once, upfront, and reused it for the empty-state check and the `slice(0, 10)` map (rather than filtering only at map-time as the plan's action text literally described) — see Deviations below.
- Used Badge `variant="gray"` for the "Adiado até" badge on the history page to keep it visually distinct from the `amber` categoria badge already used for `PRAZO_PROXIMO`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Applied the snoozed-item filter to the bell's empty-state check, not just the map**
- **Found during:** Task 2 (wiring the bell dropdown)
- **Issue:** The plan's action text said to filter "before mapping `list.data.content`" but the existing empty-state branch (`!list.data?.content.length`) checked the *unfiltered* array length. If a user's only unread bell items were all snoozed, `content.length` would still be > 0, so the code would render the populated `<ul>` branch — which, after filtering inside the map, would then render an empty `<ul>` instead of the intended "Sem notificações por agora." message.
- **Fix:** Computed `visibleNotificacoes` once (a filtered array) and used it for both the empty-state length check and the `slice(0, 10).map(...)` render, so the two branches stay consistent.
- **Files modified:** `web/src/components/shared/notification-bell.tsx` (already in the plan's `files_modified` list — no new file added)
- **Verification:** `pnpm lint` and `pnpm build` pass; manual trace of the empty-state/populated-state branching confirms both paths now agree on what counts as "visible."
- **Committed in:** `7e0f222` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug fix, Rule 1)
**Impact on plan:** Necessary for correctness of the bell's empty-state UX; no scope creep — same file, same task, no new surface added.

## Issues Encountered
- The worktree branch (`worktree-agent-a3254e93cd8ebfbcd`) was 157 commits behind local `master` (missing Phases 90-96 entirely, including 96-01's backend snooze endpoint this plan's hook calls, and the `web/` files this plan modifies). Verified `HEAD` was a strict ancestor of `master` (`git merge-base --is-ancestor HEAD master`) before fast-forwarding (`git merge --ff-only master`) — no conflicts, no lost work. `web/node_modules` was also missing in this fresh worktree checkout; ran `pnpm install` before any lint/build verification.
- `pnpm build` initially failed with `Error: BACKEND_API_ORIGIN is required` because this fresh worktree had no `web/.env.local`. Created it from the committed `web/.env.example` (gitignored, not committed) — required for local build verification only, matching the values documented in `CLAUDE.md`.

## Next Phase Readiness
- Both frontend surfaces (bell dropdown, `/notificacoes` history page) now expose the full NOTF-26 snooze UX end-to-end against the live 96-01 backend endpoint.
- Wave 3 (96-04) can proceed to the human checkpoint for manual/visual verification of the snooze flow (bell badge drop, item disappearing from bell preview, "Adiado até" badge on history page) — this plan's `<verification>` section explicitly defers visual confirmation to that checkpoint.
- No new dependencies were added (Popover and RadioGroup were already installed); no backend changes were made in this plan.

---
*Phase: 96-notf-26-snooze-de-lembrete-de-prazo*
*Completed: 2026-07-14*

## Self-Check: PASSED

All 5 created/modified source files confirmed present on disk; both task commit hashes
(`77f0019`, `7e0f222`) confirmed present in `git log --oneline --all`.
