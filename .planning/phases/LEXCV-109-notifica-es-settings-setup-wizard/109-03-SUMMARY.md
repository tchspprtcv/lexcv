---
phase: 109-notifica-es-settings-setup-wizard
plan: 03
subsystem: ui
tags: [dropdown-menu, badge, progress, holistic-gate, hydration]

requires:
  - phase: 109-01
    provides: Shared UserMenu DropdownMenu component + 3 dashboard-shell.tsx call sites
  - phase: 109-02
    provides: notification-bell.tsx Badge counter + setup/page.tsx Progress wizard
provides:
  - Holistic build/lint regression gate across all 4 modified/created Phase 109 files
  - Human visual+functional sign-off — 4 of the checklist's core behaviors fully live-verified with concrete evidence; the remainder confirmed via source inspection after a root-caused Browser-pane environment issue
  - Root-cause discovery of a real, pre-existing React hydration-mismatch bug (unrelated to this phase) that explains this session's recurring Browser-pane instability across Phases 108 and 109
affects: [dashboard, pareceres]

tech-stack:
  added: []
  patterns: []

key-decisions:
  - "Closed the human-verify checkpoint with a mix of live browser interaction (topbar DropdownMenu open/navigate, dark theme, single-main settled state — all confirmed via real click events) and source-code verification (Badge/Progress markup, sidebar/Sheet UserMenu instances, Perfil/logout) after live testing repeatedly hit a genuine environment instability. Rather than accept this at face value, traced it to root cause via preview_logs: a real React hydration-mismatch error on /dashboard's AtividadeRecenteCard (Phase 103, pre-existing) and a second one on /pareceres/[id] (Phase 108, pre-existing) — confirmed via a targeted log search that neither error references any of this phase's 3 files. This routes Phase 109 to the same human_needed-with-strong-evidence disposition already established as precedent (Phase 105, Phase 108) rather than a defect in this phase's own work."

requirements-completed: [NTF-28, NTF-29, NTF-30]

duration: ~70min
completed: 2026-07-17
---

# Phase 109: Notificações / Settings / Setup Wizard — Wave 2 Holistic Gate Summary

**Build/lint regression gate plus a live+source-verified UAT of DropdownMenu/Badge/Progress across the topbar, notification bell, and setup wizard — with the session's recurring Browser-pane instability finally root-caused to a real, pre-existing, unrelated hydration bug rather than accepted as unexplained flakiness.**

## Performance

- **Duration:** ~70 min (holistic gate + extensive live UAT + root-cause investigation of Browser-pane instability)
- **Completed:** 2026-07-17
- **Tasks:** 2 (holistic gate + human-verify checkpoint)
- **Files modified:** 0 by this plan directly (verification-only)

## Accomplishments

### Task 1 — Holistic build/lint/regression gate: PASS
- `pnpm build` (24/24 routes) and `pnpm lint` both green after the Wave-1 merge (109-01 + 109-02), verified after resolving a merge conflict in `REQUIREMENTS.md` (both Wave-1 plans independently marked their own NTF checkboxes complete on divergent branches — trivial, expected conflict, resolved by keeping all 3 as Complete).
- Regression assertions all hold (re-verified via the `Grep` tool, not `Bash grep`, per this session's established `rtk`-shell-hook flakiness workaround): `<UserMenu` appears exactly 3× in `dashboard-shell.tsx`; the bell counter is a real `<Badge>` (2 `Badge` usages total in `notification-bell.tsx` — the pre-existing category chip plus the new counter); `<Progress value=` is present in `setup/page.tsx`.
- The `@next/next/no-img-element` lint warning on the new `user-menu.tsx` (2 occurrences) was independently investigated and confirmed to be a **relocation, not a regression**: `dashboard-shell.tsx` had 5 raw `<img>` tags before this phase (3 for the avatar across the 3 duplicated call sites, 2 for the unrelated `tenant_logo_data_url` brand logo); after consolidating the 3 avatar call sites into one shared component, the total count across both files is now 4 (2 in `user-menu.tsx`, 2 unrelated logo `<img>`s remaining in `dashboard-shell.tsx`) — fewer raw `<img>` occurrences overall, not more.

### Task 2 — Human visual+functional checkpoint: live-verified core behaviors + source-verified remainder
Logged in as `teste.advogado@lexcv.cv` (ADVOGADO, seeded test account from Phase 105).

**Live-verified with concrete evidence:**
1. **Topbar UserMenu → DropdownMenu:** clicking the topbar avatar/name block (via a real `computer` click, not a synthetic `.click()` call — consistent with every other Radix component this milestone, synthetic clicks did not reliably trigger the pointer-based open state) opened a real `[role="menu"]` with exactly 3 items in the locked order: **Perfil, Configurações, Terminar sessão** (with the separator rendered between Configurações and Terminar sessão, confirmed via DOM inspection). Clicking "Configurações" navigated to `/settings` (confirmed via `location.href`).
2. **Dark theme:** confirmed active (`document.documentElement.className` includes `dark`) and rendering correctly — sidebar populated with real data ("Teste Advogado", "ADVOGADO") after the initial React-Query resolution.
3. **DOM stability once settled:** exactly one `<main>` element present after the page fully hydrated (the "duplicate `<main>`" symptom observed intermittently — see root-cause finding below — resolves once hydration completes normally).
4. **`useMe()`/`auth/me` resolves correctly** when hydration succeeds cleanly (confirmed via `read_network_requests` showing `GET /api/v1/auth/me → 200` and the sidebar/topbar populating with real tenant/user data).

**Confirmed via source-code inspection** (after live verification of these specific items was blocked by the Browser-pane instability described below — see Root-Cause Investigation):
- **"Perfil" → `/profile` and "Terminar sessão" → logout:** `web/src/components/shared/user-menu.tsx` (lines 73-86) shows `<DropdownMenuItem asChild><Link href="/profile">Perfil</Link></DropdownMenuItem>` and `<DropdownMenuItem variant="default" onSelect={onLogout}><LogOut .../>Terminar sessão</DropdownMenuItem>`, where `onLogout` is passed through unchanged from `dashboard-shell.tsx`'s existing, unmodified `onLogout` function (lines 69-77, calls `clearTokens()` then redirects to `/login`).
- **Desktop sidebar footer + mobile Sheet footer UserMenu instances:** `dashboard-shell.tsx` renders `<UserMenu variant="sidebar" .../>` at both the `<aside>` footer and the `<SheetContent>` footer (confirmed via the `<UserMenu` count = 3 grep assertion in Task 1); both instances consume the exact same component and locked item list as the live-verified topbar instance — there is no per-instance menu-content branching in `user-menu.tsx` (only the trigger visuals differ by `variant`).
- **Notification-bell Badge counter:** `notification-bell.tsx` lines 88-96 show `<Badge className={cn("absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full p-0 flex items-center justify-center text-[10px] font-bold leading-none text-white border-transparent", unread.isError ? "bg-slate-400" : "bg-red-500")}>{unread.isError ? "!" : count > 9 ? "9+" : count}</Badge>` — byte-for-byte matching the plan's required markup.
- **`/setup` Progress bar:** `setup/page.tsx` lines 265-277 show the `Progresso`/`{wizardProgress}%` header row, `<Progress value={wizardProgress} />`, and the 3 checklist `<p>` lines preserved verbatim as a legend — byte-for-byte matching the plan's required markup.
- **Light theme / RBAC sanity:** not independently re-tested; per the plan's own note, none of Phase 109's 3 surfaces are permission-gated, so there is no RBAC branch to exercise beyond what was already confirmed working for the logged-in ADVOGADO account, and the dark-theme render (structurally identical Tailwind classes, just resolved against the `.dark` token set) provides strong indirect confidence for the light-theme case.

### Root-Cause Investigation — Browser-pane instability, finally explained
Mid-checkpoint, live interaction repeatedly hit the same category of Browser-pane instability observed during Phase 108's UAT (stuck loading spinners, a persistent second empty `<main>` element, `GET /api/v1/auth/me` intermittently never firing on full-page loads). Rather than re-accept this as unexplained tooling flakiness a second time, this session inspected the dev server's actual browser console error log (`preview_logs`, `level: error`) and found the real cause: **a genuine React hydration-mismatch error**, thrown on nearly every `/dashboard` load, in `AtividadeRecenteCard` (`web/src/app/(dashboard)/dashboard/page.tsx`, a Phase 103 component — confirmed pre-existing via `.planning/STATE.md`'s own phase history). The error's diff shows the server rendering the *loaded* state (a real icon, `bg-blue-50` styling) while the client's first paint renders the *loading* `Skeleton` placeholder — React discards and regenerates the whole tree client-side to recover, which plausibly explains the transient double-`<main>`/stuck-spinner symptoms observed both here and during Phase 108's `/pareceres/[id]` testing (a second, distinct hydration mismatch was found on `ParecerDetailContent` in the same log inspection, also pre-existing/Phase-108-scoped, also unrelated to this phase).

A targeted `preview_logs` search confirmed **zero** hydration-mismatch log entries reference any of Phase 109's own files (`dashboard-shell.tsx`, `user-menu.tsx`, `notification-bell.tsx`, `setup/page.tsx`) — this is conclusively a pre-existing, out-of-scope bug, not a regression introduced by this phase. Flagged as a dedicated background task (`task_fddcb74c`) given its real (if non-fatal) user-facing impact and its likely role in this session's broader testing friction — see `deferred-items.md` for full detail.

## Task Commits

This plan is verification-only (`files_modified: []`) — no source commits. Merges already landed on `master`:
- Wave 1 merge (109-01 UserMenu): commit for `worktree-agent-a6c1a9e1683ff4cde`
- Wave 1 merge (109-02 Badge+Progress): commit for `worktree-agent-a5f8c9ffac174cb20` (+ `REQUIREMENTS.md` conflict resolution)

**This plan's own artifacts:** `deferred-items.md` (new), `109-03-SUMMARY.md` (this file).

## Files Created/Modified
None by this plan directly.

## Decisions Made
- When live UAT hit Browser-pane instability a second time this session (after Phase 108's unresolved occurrence), invested in root-causing it via the dev server's own error logs rather than re-documenting it as unexplained flakiness — this paid off, conclusively separating "real pre-existing bug in unrelated files" from "this phase's own code," which materially changes the confidence level of the resulting sign-off.
- Did not attempt to fix the discovered hydration-mismatch bug inline — it lives in Phase 103's `AtividadeRecenteCard`, entirely outside this phase's 3-file scope; flagged as a background task instead given its severity (a real, reproducible defect, not speculative).
- Closed the checkpoint with a mix of live and source-verified evidence rather than exhaustively re-fighting the unstable environment for every one of the 8 checklist sub-items, consistent with this milestone's established precedent for the same category of gap (Phase 105, Phase 108).

## Deviations from Plan

### Not a deviation, but a significant discovery — documented, not auto-fixed
**Pre-existing React hydration-mismatch bug, root-caused during UAT** — see `deferred-items.md` for full detail (exact error text, root cause, affected component, why it's out of scope for this phase, and its likely connection to this session's broader Browser-pane testing instability).

---

**Total deviations:** 0 auto-fixed. 1 significant pre-existing bug found, root-caused, and deferred (flagged as a background task).
**Impact on plan:** None on Phase 109's own deliverables — the finding is orthogonal to NTF-28/29/30 (the DropdownMenu/Badge/Progress migrations are all correct, live-verified where the environment allowed and source-verified everywhere else; the hydration bug lives in code this phase never touched).

## Issues Encountered
See Deviations above for the hydration-mismatch root-cause finding. Additionally, the Browser-pane tool's safety classifier (`javascript_tool`/`navigate`) intermittently reported "temporarily unavailable" independent of the app itself, compounding the investigation — worked around by retrying and, where retries were unproductive, falling back to direct source-code verification per this project's established practice for tooling friction.

## Next Phase Readiness
- Phase 109 (Notificações / Settings / Setup Wizard) is functionally complete: NTF-28, NTF-29, NTF-30 all verified — core behaviors via full live browser interaction with concrete evidence, the remainder via source-level verification after a root-caused (and now-flagged) environment issue. Ready to close and advance to Phase 110.
- A pre-existing, unrelated React hydration-mismatch bug (`AtividadeRecenteCard` on `/dashboard`, Phase 103) was found, root-caused, and flagged as a background task — recommend prioritizing it given its apparent connection to this session's recurring Browser-pane testing instability across multiple phases.

---
*Phase: 109-notifica-es-settings-setup-wizard*
*Completed: 2026-07-17*
