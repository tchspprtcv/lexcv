---
phase: LEXCV-109-notifica-es-settings-setup-wizard
fixed_at: 2026-07-17T15:03:09Z
review_path: .planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/109-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase LEXCV-109: Code Review Fix Report

**Fixed at:** 2026-07-17T15:03:09Z
**Source review:** .planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/109-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (all Warning findings, WR-01 through WR-05; the 7 Info findings
  IN-01 through IN-07 were explicitly out of scope for this run and were left undone)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-01: Unread notification badge loses its color in dark mode

**Files modified:** `web/src/components/shared/notification-bell.tsx`
**Commit:** `3853682`
**Applied fix:** Added explicit `dark:` counterparts to the unread-badge color override
(`bg-red-500 dark:bg-red-500` / `bg-slate-400 dark:bg-slate-400`) so the ad-hoc className
wins over `Badge`'s default `secondary` variant (`dark:bg-neutral-800`) in both light and
dark mode. Matches the review's suggested fix exactly.

### WR-02: Setup wizard submit button re-enables during the post-success redirect window

**Files modified:** `web/src/app/setup/page.tsx`
**Commit:** `1788f20`
**Applied fix:** Changed the submit `Button`'s `disabled` prop from
`form.formState.isSubmitting` to `form.formState.isSubmitting || wizardPhase !== "idle"`,
closing the double-submit window during the 1.2s redirect delay after a successful
`/setup/initialize` call.

### WR-03: This phase's own dedup refactor left an equally-sized duplicate block unaddressed

**Files modified:** `web/src/components/shared/dashboard-shell.tsx`, `web/src/components/shared/sidebar-nav.tsx` (new file)
**Commit:** `3b975e6`
**Applied fix:** Extracted a new `SidebarNav` shared component (analogous to `UserMenu`)
containing the NAV filter/map block plus the "Sistema" Configurações/Suporte links, and
consumed it from both the desktop `<aside>` and the mobile `<Sheet>` in `DashboardShell`.
Confirmed the two original blocks were byte-for-byte identical (no styling variant needed).
This extraction preserved the pre-existing active-state logic verbatim (including the
`/processos/dashboard` special case) so the commit is a pure refactor with no behavior
change; WR-04 below then fixes the active-state logic in the single consolidated location.
Also removed now-unused imports left behind in `dashboard-shell.tsx` after the extraction
(`Link`, `Settings`, `LifeBuoy`, `hasPermission`, `cn`) to keep the file lint-clean.

### WR-04: Nav active-state highlighting only works for exact-match routes plus one hardcoded exception

**Files modified:** `web/src/components/shared/sidebar-nav.tsx`
**Commit:** `11bedd5`
**Applied fix:** Generalized the active-state check in the (now-consolidated) `SidebarNav`
component from `pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"))`
to `pathname === item.href || pathname.startsWith(\`${item.href}/\`)`, and removed the
redundant special case. Applied once, since WR-03 (fixed immediately prior, in commit order)
had already consolidated both duplicate copies into this single component.

Note: `web/src/components/shared/bottom-nav.tsx` contains its own separate, pre-existing
copy of the same exact-match-plus-special-case pattern (its own `BOTTOM_NAV` array, not
`NAV`). It was not touched — it falls outside the 4 files reviewed in 109-REVIEW.md and
outside the WR-04 finding's cited file/line range, so fixing it was out of scope for this
run.

### WR-05: Logo upload validation is client-side only and permits SVG despite the UI copy promising raster formats

**Files modified:** `web/src/app/setup/page.tsx`
**Commit:** `1ea8b9b`
**Applied fix:** Replaced the `file.type.startsWith("image/")` check in `handleLogoChange`
with an explicit allowlist, `ALLOWED_LOGO_TYPES = ["image/png", "image/jpeg", "image/webp"]`
checked via `.includes(file.type)`, matching the UI copy's "PNG, JPG, WEBP até 2MB" promise
and rejecting `image/svg+xml` and other MIME types.

**Recommended follow-up (not applied, out of scope):** The review also recommended
confirming the backend independently re-validates content type/magic bytes for
`SetupInitializeRequest.logo` rather than trusting the client-declared MIME type. This is
backend Java code outside this frontend-only phase's 4-file review scope and was
intentionally not modified here — flagging for a separate backend-scoped follow-up.

## Skipped Issues

None — all 5 in-scope findings (WR-01 through WR-05) were fixed. The 7 Info findings
(IN-01 through IN-07) were out of scope for this run per the requested fix scope and were
left undone (not attempted, not skipped-due-to-failure).

## Verification

- Tier 1 (re-read modified sections): passed for all 5 fixes.
- Tier 2 (`npx tsc --noEmit -p tsconfig.json`): passed after each fix with only 3
  pre-existing, unrelated errors present before and after every fix
  (`Cannot find module 'vitest'` in 3 `*.test.ts` files, caused by `vitest` not being
  installed as a dependency in this environment — unrelated to any of the 5 files touched).
- Tier 2 (`npx eslint`) on all touched files: passed, after also removing now-unused
  imports (`Link`, `Settings`, `LifeBuoy`, `hasPermission`, `cn`) introduced as a side
  effect of the WR-03 extraction. One pre-existing lint error
  (`react-hooks/set-state-in-effect` on the untouched `setDrawerOpen(false)` effect in
  `dashboard-shell.tsx`) and two pre-existing `<img>`/`next/image` warnings were confirmed
  present on the same, unmodified lines before and after the fixes — not introduced by this
  run.
- `pnpm build` (Next.js 16 / Turbopack): passed in the main repository working tree after
  the fix commits were merged back (isolated worktree build failed only because of a
  Turbopack limitation with the temporary `node_modules` directory-junction used for
  fast per-fix syntax checks in the worktree — not a code defect; junction was removed
  before worktree cleanup and confirmed not to have touched the real `node_modules`).
- `pnpm lint`: passed in the main repository working tree after merge, no new errors.

---

_Fixed: 2026-07-17T15:03:09Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
