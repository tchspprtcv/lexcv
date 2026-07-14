---
phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
plan: 04
subsystem: ui
tags: [nextjs, react, tanstack-query, notifications, rbac]

# Dependency graph
requires:
  - phase: 93-03
    provides: "GET/PUT/DELETE /api/v1/notificacoes/preferencias(/{categoria}) self-service REST endpoints"
provides:
  - "NotificacaoPreferenciasResponse type (types/notificacoes.ts)"
  - "useNotificacaoPreferencias / useSilenciarCategoria / useReativarCategoria hooks (hooks/use-notificacao-preferencias.ts)"
  - "Notification preferences tab in /settings (NotificationPreferencesTab), 8 toggles derived from NOTIFICACAO_CATEGORIA_OPTIONS with PRAZO_VENCIDO omitted"
affects: [97-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Preferences hook mirrors use-notificacoes.ts style but uses a distinct query key (\"notificacao-preferencias\") so silencing doesn't invalidate the unread-count/list cache"
    - "Per-row pending/disabled state scoped via mutation.isPending && mutation.variables === rowKey, mirroring notificacoes/page.tsx's marcarLida pattern (plan-checker followup #2)"
    - "No manual toast.error on mutation failure — apiFetch already auto-toasts non-2xx errors (plan-checker followup #1)"

key-files:
  created:
    - web/src/hooks/use-notificacao-preferencias.ts
  modified:
    - web/src/types/notificacoes.ts
    - web/src/app/(dashboard)/settings/page.tsx

key-decisions:
  - "Toggle semantics: checked = entregar (not muted); unchecking calls silenciar (PUT), checking calls reativar (DELETE) — matches the plan's derived-membership design (silenciadas.includes(categoria))"
  - "Reused NOTIFICACAO_CATEGORIA_OPTIONS as-is (no new categoria enum/list) and filtered PRAZO_VENCIDO client-side; backend still rejects a direct PUT of PRAZO_VENCIDO with 400 as defense in depth"

patterns-established:
  - "Settings page sub-component tabs continue the RbacTab/UserManagementTab convention: a TabId union member, a gated button, a gated panel, and a same-file sub-component function"

requirements-completed: [NOTF-24]

# Metrics
duration: ~35min
completed: 2026-07-14
---

# Phase 93 Plan 04: NOTF-24 Notification Preferences UI Summary

**New `/settings` "Notificações" tab with 8 self-service mute toggles wired to the Plan 93-03 REST endpoints via a dedicated TanStack Query hook, reusing `NOTIFICACAO_CATEGORIA_OPTIONS` with `PRAZO_VENCIDO` filtered out**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-07-14T11:05:00Z (approx, after worktree fast-forward)
- **Completed:** 2026-07-14T11:40:00Z (approx)
- **Tasks:** 3 (2 auto + 1 checkpoint, auto-passed per `skip_checkpoints: true`)
- **Files modified:** 3 (1 created, 2 modified) + 1 docs (deferred-items.md)

## Accomplishments
- `NotificacaoPreferenciasResponse` type added to `types/notificacoes.ts`, reusing the existing `NotificacaoCategoria` union
- `use-notificacao-preferencias.ts` hook file: `useNotificacaoPreferencias` (GET, query key `["notificacao-preferencias"]`, distinct from `["notificacoes"]`), `useSilenciarCategoria` (PUT), `useReativarCategoria` (DELETE), both mutations invalidating the preferences query key on success
- `/settings` gained a `"notificacoes"` tab, gated by `can.view("notificacoes")` (self-service scope already seeded to all 4 roles), rendering `NotificationPreferencesTab`
- `NotificationPreferencesTab` renders exactly 8 toggles — `NOTIFICACAO_CATEGORIA_OPTIONS.filter(o => o.value !== "PRAZO_VENCIDO")` — with `checked = !silenciadas.includes(o.value)`; unchecking mutes (PUT), checking unmutes (DELETE)
- Per-row loading state scoped to the specific category being toggled (`silenciar.isPending && silenciar.variables === o.value`, mirroring the existing `notificacoes/page.tsx` per-row pattern) rather than disabling the whole list
- No manual `toast.error` added on mutation failure — `apiFetch` already auto-toasts non-2xx responses project-wide; only `toast.success` added explicitly on each successful toggle

## Task Commits

Each task was committed atomically:

1. **Task 1: Tipo + hook use-notificacao-preferencias** - `e1433d3` (feat)
2. **Task 2: Tab de preferências de notificação na página /settings** - `1842324` (feat)
3. **Task 3: Verificação humana end-to-end das preferências** - checkpoint, auto-passed (see below); no code commit (no-code task)

Additional docs commit: `3862606` (docs) — logs pre-existing tsc/lint findings discovered while verifying, unrelated to this plan's files.

_Note: no plan-metadata commit for STATE.md/ROADMAP.md is included here — the orchestrator commits those separately after this SUMMARY lands._

## Files Created/Modified
- `web/src/types/notificacoes.ts` - added `NotificacaoPreferenciasResponse { silenciadas: NotificacaoCategoria[] }`
- `web/src/hooks/use-notificacao-preferencias.ts` (new) - 3 TanStack Query hooks reading/writing `/notificacoes/preferencias`
- `web/src/app/(dashboard)/settings/page.tsx` - extended `TabId`, added gated "Notificações" tab button + panel, added `NotificationPreferencesTab` sub-component
- `.planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/deferred-items.md` (new) - logs pre-existing, out-of-scope tsc/lint findings

## Decisions Made
- Followed the plan's Claude's-Discretion guidance as-is: GET/PUT/DELETE with category in the path (already decided in Plan 93-03), toggle checked-state derived from list membership (no full 9-entry on/off map needed)
- Applied both plan-checker followups verbatim: no duplicate manual error toast, per-row (not per-list) pending/disabled scoping

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fast-forwarded worktree branch to local master before reading plan files**
- **Found during:** Setup, before Task 1
- **Issue:** This worktree's branch had no `.planning/phases/` directory at all — several commits behind local `master`, which already contained Plan 93-01/02/03 artifacts (backend endpoints this plan consumes) and the 93-04-PLAN.md file itself
- **Fix:** Verified `git merge-base --is-ancestor HEAD master` (HEAD was a strict ancestor, clean working tree), then ran `git merge --ff-only master`; re-verified the worktree-agent branch safety assertion afterward
- **Files modified:** none (fast-forward merge only, no new commits created by the merge itself)
- **Verification:** Confirmed `93-04-PLAN.md`, `93-CONTEXT.md`, and `93-03-SUMMARY.md` present on disk after the merge
- **Committed in:** N/A (fast-forward, no new commit)

**2. [Rule 3 - Blocking] Ran `pnpm install` — `node_modules` was entirely missing in this worktree**
- **Found during:** Task 1 verification (`pnpm exec tsc --noEmit`)
- **Issue:** The worktree had no `node_modules` directory at all. The `rtk` CLI wrapper around `pnpm exec tsc`/`pnpm lint` was silently reporting false-positive success ("TypeScript: No errors found") instead of surfacing the "command not found" failure — confirmed by re-running the same commands via `rtk proxy` (unfiltered), which correctly showed `'tsc' is not recognized`/`Command "eslint" not found`
- **Fix:** Ran `pnpm install` (`pnpm-lock.yaml` untouched, no version changes — clean reinstall from existing lockfile); re-ran all verification via `rtk proxy` afterward to get real, unfiltered results
- **Files modified:** none tracked (node_modules is gitignored)
- **Verification:** Real `tsc --noEmit` and `pnpm lint` now execute; both confirmed to report zero new issues in this plan's files (see Issues Encountered)
- **Committed in:** N/A (no file changes — node_modules is gitignored)

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking environment issues, neither touched application code)
**Impact on plan:** Both fixes were prerequisites for running the plan's own specified verification commands correctly. No scope creep — no application code changed beyond what the plan specified.

## Issues Encountered

**Pre-existing tsc/lint findings, unrelated to this plan (documented, not fixed):** After installing dependencies, real `tsc --noEmit` surfaces 3 `Cannot find module 'vitest'` errors in `use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts` (`vitest` isn't declared in `package.json` at all); real `pnpm lint` surfaces the same 6 errors / 17 warnings already documented in Phase 89's `deferred-items.md` (`react-hooks/set-state-in-effect`, `react-hooks/incompatible-library`, `react-hooks/refs`, `@next/next/no-img-element`, unused vars), across files this plan does not touch. Confirmed via filtered grep: zero matches against this plan's `files_modified`. Logged in `.planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/deferred-items.md` per the scope-boundary rule (out-of-scope discoveries are logged, not fixed).

**Checkpoint (Task 3) auto-passed with partial automated verification, full live E2E not possible:** Per this execution's explicit instruction (`parallelization.skip_checkpoints: true`, autonomous mode), the `checkpoint:human-verify` task was treated as auto-passable. Automated verification performed:
- `pnpm exec tsc --noEmit` (via `rtk proxy`, real invocation): zero errors in any file this plan touches
- `pnpm lint` (via `rtk proxy`, real invocation): zero new errors/warnings in any file this plan touches (all findings pre-existing, see above)
- `pnpm build` (via `rtk proxy`, real invocation, with a temporary local-only `.env.local` created from `.env.example` and removed immediately after — `.env*` is gitignored, never committed): production build succeeds, `/settings` compiles and is listed as a static route in the build output
- Static source assertions from the plan's own grep-based verification scripts: pass for both tasks

**Not verified (requires human + a running backend):**
- Backend could not be started for live E2E: `backend/.env` does not exist in this worktree (only `.env.example`), and this project has a recurring documented `MINIO_ENDPOINT` environmental blocker preventing full Spring context startup for live UAT across v2.8–v2.10 sessions (see `.planning/STATE.md` Blockers/Concerns, owned by v2.11 Phase 97/AUD-04) — consistent with the checkpoint task's own note that a full-context test would hit this same blocker
- The plan's `<how-to-verify>` steps 1–7 (login, visually confirm 8 toggles + absence of "Prazo vencido", toggle off/on with toast + F5 persistence, optional cross-user isolation check, optional `PUT .../PRAZO_VENCIDO` → 400 check) remain to be performed by a human against a running backend+DB — this is genuine runtime behavior (persistence, cross-user isolation, backend 400 rejection) that static analysis cannot substitute for
- This mirrors the same "code-level verification passed, live E2E blocked by MinIO env issue" pattern already recorded for Phases 87/89 in `.planning/STATE.md`'s Deferred Items table

## User Setup Required

None - no new external service configuration required. (Live human verification of the checkpoint's `<how-to-verify>` steps still needs a running backend with `backend/.env` configured and DB reachable — same pre-existing environmental gap tracked by v2.11 Phase 97/AUD-04, not a new requirement from this plan.)

## Next Phase Readiness
- NOTF-24 UI is code-complete and statically verified: type, hooks, and `/settings` tab exist, reuse `NOTIFICACAO_CATEGORIA_OPTIONS` without duplicating the enumeration, and never render `PRAZO_VENCIDO` as a mutable option
- Phase 93 (NOTF-24) is now fully implemented across backend (93-01/02/03) and frontend (93-04); ready for the milestone's own live-UAT closure step (v2.11 Phase 97/AUD-04) to perform the still-pending human `<how-to-verify>` walkthrough once the `MINIO_ENDPOINT`/backend-env gap is resolved
- No blockers for Phase 94 (NOTF-27 dedup fix) — it depends on the mute guard existing in `NotificacaoService.criar()` (Plan 93-02), which this plan's frontend work does not touch or affect

---
*Phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador*
*Completed: 2026-07-14*

## Self-Check: PASSED

All modified/created files confirmed on disk (`web/src/types/notificacoes.ts`, `web/src/hooks/use-notificacao-preferencias.ts`, `web/src/app/(dashboard)/settings/page.tsx`, `deferred-items.md`, this `93-04-SUMMARY.md`); all 3 task commits (`e1433d3`, `1842324`, `3862606`) confirmed in `git log --oneline --all`.
