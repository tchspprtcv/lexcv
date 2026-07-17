---
phase: 109-notifica-es-settings-setup-wizard
verified: 2026-07-17T22:40:00Z
status: human_needed
score: 13/14 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Open the desktop sidebar footer, the mobile Sheet (hamburger) footer, and the topbar user menu, in BOTH light and dark theme. Click through: Perfil → /profile, Configurações → /settings, Terminar sessão → full logout to /login. Confirm the footer DropdownMenu opens upward without clipping."
    expected: "All 3 UserMenu instances open a DropdownMenu (not a direct navigation); all 3 menu actions work end-to-end; no visual clipping/collision issue on the two footer instances; light theme renders with correct contrast (only dark theme was live-clicked this session)."
    why_human: "109-03-SUMMARY.md and deferred-items.md both explicitly state that only the topbar instance was live-clicked (dark theme only) — 'Perfil'→/profile, 'Terminar sessão'→logout, the 2 footer instances, and light theme were all confirmed via source-code reading only, not live interaction, after a documented Browser-pane/hydration-mismatch tooling failure. The blocking human-verify checkpoint (109-03-PLAN.md Task 2, gate=\"blocking\") was closed by the executor agent itself (via computer-use browser clicks + source review), not by an actual human typing \"approved\" — no such sign-off is recorded in any artifact."
  - test: "Trigger a real unread notification (or use an account with unread items) and confirm the Bell's red counter Badge renders in the correct top-right position with no layout shift, in both light and dark theme; optionally induce a fetch error and confirm the badge turns slate-gray with '!'."
    expected: "Badge renders exactly as before the migration — same position, same red/gray coloring in both themes, same '9+' cap — with no regression."
    why_human: "Source and git-diff analysis (this verification) confirm the markup and dark: classes are correct, but no live render with actual unread data was performed this session (deferred-items.md: 'confirmed instead via direct source read... not live')."
  - test: "Visit /setup on a fresh/uninitialized install and submit the form, watching the Progress bar move 33% → 66% → 100%."
    expected: "Progress bar and percentage text advance as described, in both light and dark theme, with no layout shift."
    why_human: "Same as above — confirmed via source only this session, not via a live submit-and-watch run (deferred-items.md)."
---

# Phase 109: Notificações / Settings / Setup Wizard Verification Report

**Phase Goal:** O novo menu de utilizador da topbar, o contador do sino e o wizard de setup usam primitivos oficiais, sem tocar no `Popover` do sino que já está correto. (NTF-28, NTF-29, NTF-30)
**Verified:** 2026-07-17T22:40:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

Every truth below was checked directly against the current committed source (`Read`/`Grep`), cross-checked against `git show` diffs for every cited commit, an independent re-run of `pnpm build` and `pnpm lint` (not trusting the SUMMARY's claims), and a commit-hash existence sweep.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | The topbar/sidebar/mobile-Sheet user menu is a real Radix `DropdownMenu` (not a direct-navigation link), consumed via a single shared `UserMenu` component at exactly 3 call sites | VERIFIED | `web/src/components/ui/dropdown-menu.tsx` wraps `radix-ui`'s `DropdownMenu as DropdownMenuPrimitive` (real Radix primitive, not a stub). `web/src/components/shared/user-menu.tsx` exports one `UserMenu` (only definition in `web/src`, confirmed via grep for `export function UserMenu`/`export default UserMenu`). `dashboard-shell.tsx` renders `<UserMenu` exactly 3 times (grep count = 3): line 84 (`variant="sidebar"`, desktop `<aside>` footer), line 104 (`variant="sidebar"`, mobile `<SheetContent>` footer), line 151 (`variant="topbar"`, header). Each trigger is a plain non-navigating `<button type="button">` — zero `<Link href="/settings">` remains inside `user-menu.tsx`. |
| 2 | Menu items appear in exactly the locked order — Perfil, Configurações, separator, Terminar sessão — identical across all 3 instances | VERIFIED | `user-menu.tsx` lines 72-87: one `DropdownMenuContent` per render, `<DropdownMenuItem asChild><Link href="/profile">Perfil</Link></DropdownMenuItem>` → `<DropdownMenuItem asChild><Link href="/settings">...Configurações</Link></DropdownMenuItem>` → `<DropdownMenuSeparator />` → `<DropdownMenuItem variant="default" onSelect={onLogout}>...Terminar sessão</DropdownMenuItem>`. No per-`variant` branching in the item list — only the trigger visuals differ. |
| 3 | Selecting "Terminar sessão" logs the user out via the existing, unmodified `onLogout` | VERIFIED | `dashboard-shell.tsx` lines 58-66: `onLogout` (`clearTokens()` then `window.location.href = "/login"`) passed unchanged as a prop to all 3 `<UserMenu onLogout={onLogout}>` call sites; `user-menu.tsx` wires it via `onSelect={onLogout}` (line 83). |
| 4 | The desktop sidebar and Sheet footers no longer show a standalone icon-only logout button | VERIFIED | `grep -c "Terminar sessão"` against `dashboard-shell.tsx` (not `user-menu.tsx`) returns 0 matches; the only "Terminar sessão" instance in the codebase now lives inside `user-menu.tsx`. `Button`/`Tooltip`/`TooltipContent`/`TooltipTrigger`/`LogOut` imports are all absent from `dashboard-shell.tsx`'s current import block (confirmed via full-file read), consistent with their prior sole use being the removed logout buttons. |
| 5 | The notification bell's unread-count indicator is the official `Badge` component, not a manual `<span>` | VERIFIED | `notification-bell.tsx` lines 89-96: `<Badge className={cn(...)}>{unread.isError ? "!" : count > 9 ? "9+" : count}</Badge>`. `grep -c '<span'` outside comments in the file returns 0. `git show 4b538bb` confirms the prior manual `<span className={...template-literal...}>` was replaced 1:1 by this `<Badge>`. |
| 6 | The Badge counter renders correctly (red/gray) in **both** light and dark mode — the WR-01 dark-mode fix is genuinely present | VERIFIED | Current source, `notification-bell.tsx:92`: `unread.isError ? "bg-slate-400 dark:bg-slate-400" : "bg-red-500 dark:bg-red-500"`. `git show 3853682` (commit message: `fix(109): WR-01 -- add dark: color counterparts to notification unread badge`) shows the single-line diff adding exactly these two `dark:` classes over the pre-fix version (which had only `bg-slate-400`/`bg-red-500`, defaulting to `Badge`'s shipped `dark:bg-neutral-800` in dark mode — the bug 109-REVIEW.md iteration 1 caught). Fix is committed and present in the current working tree (verified clean `git status` on this file). |
| 7 | The notification bell's own `Popover` composition (trigger/content/snooze/mark-read) was NOT touched/converted to `DropdownMenu` | VERIFIED | `notification-bell.tsx` still imports and renders `Popover`/`PopoverTrigger`/`PopoverContent` (lines 10, 80-99-178), `NotificacaoSnoozeControl`, `useMarcarNotificacaoLida`/`useMarcarTodasNotificacoesLidas` — all unchanged. `git show 4b538bb -- notification-bell.tsx` (the only phase-109 commit to this file besides the WR-01 dark-mode fix) diffs exactly 12 lines, all confined to the counter block + one new `cn` import — the `Popover` composition is untouched in both diffs. |
| 8 | The `/setup` wizard shows a linear `Progress` indicator, not a third-party stepper | VERIFIED | `setup/page.tsx` imports `Progress` from `@/components/ui/progress` (line 14) — the official shadcn/Radix primitive, confirmed Radix-backed by reading `web/src/components/ui/progress.tsx`. Renders `<Progress value={wizardProgress} />` (line 272), always mounted (no conditional wrapper). No `Stepper`/third-party package import anywhere in the file or `package.json`. |
| 9 | The Progress value correctly maps wizard phase (idle=33%, submitting=66%, success=100%), and the 3 checklist lines are preserved verbatim as a legend | VERIFIED | Lines 49-51: `wizardPhase = successMessage ? "success" : form.formState.isSubmitting ? "submitting" : "idle"`; `wizardProgress = wizardPhase === "success" ? 100 : wizardPhase === "submitting" ? 66 : 33` — computed value, no new `useState`, exactly as locked. Lines 273-277 preserve the 3 `<p>` lines verbatim ("1. Criação do primeiro tenant institucional." / "2. Criação do utilizador administrador com password hasheada." / "3. Fecho definitivo do wizard após sucesso."). |
| 10 | The 7 Warning-level code-review fixes (iteration 1: WR-01 through WR-05; iteration 3: WR-01/onNavigate + IN-07) are genuinely reflected in the current file state | VERIFIED | Spot-checked all 7 directly, cross-referenced against `git show` for each commit: **WR-01 (dark-mode Badge, `3853682`)** — confirmed above (Truth #6). **WR-02 (submit button re-enable window, `1788f20`)** — `setup/page.tsx:304`: `disabled={form.formState.isSubmitting \|\| wizardPhase !== "idle"}`, present. **WR-03 (`SidebarNav` extraction, `3b975e6`)** — `sidebar-nav.tsx` exists and is consumed at both `dashboard-shell.tsx` call sites (lines 75-80, 95-100); see Truth #11 for full dedup confirmation. **WR-04 (generalized active-state match, `11bedd5`)** — `sidebar-nav.tsx:36`: `pathname === item.href \|\| pathname.startsWith(\`${item.href}/\`)`, no hardcoded `/processos/dashboard` special case remains. **WR-05 (logo MIME allowlist, `1ea8b9b`)** — `setup/page.tsx:20`: `const ALLOWED_LOGO_TYPES = ["image/png", "image/jpeg", "image/webp"]`, used via `.includes(file.type)` at line 63. **Iteration-3 "WR-01" (onNavigate drawer-close, `d86d8fc`)** — `sidebar-nav.tsx:21` (`onNavigate?: () => void`), fired at lines 42 and 62; `dashboard-shell.tsx` passes `onNavigate={() => setDrawerOpen(false)}` at both `SidebarNav` call sites (lines 79, 99). **IN-07 (`settingsActive` consistency, `604c673`)** — `sidebar-nav.tsx:30`: `const settingsActive = pathname === "/settings" \|\| pathname.startsWith("/settings/")`, used at both lines 65 and 70; no stale `pathname === "/settings"` inline check remains. All 11 cited commit hashes across both SUMMARYs/REVIEW/REVIEW-FIX (`2665265`, `e90d241`, `4b538bb`, `be07a57`, `3853682`, `1788f20`, `3b975e6`, `11bedd5`, `1ea8b9b`, `d86d8fc`, `604c673`) resolve via `git cat-file -t`. |
| 11 | `sidebar-nav.tsx` genuinely eliminates the nav-block duplication it was extracted to remove; `dashboard-shell.tsx` has no leftover dead/duplicate nav-block code | VERIFIED | `dashboard-shell.tsx` imports `SidebarNav` from `@/components/shared/sidebar-nav` (line 4) and renders it exactly twice (lines 75-80 desktop `<aside>`, 95-100 mobile `<Sheet>`) — both calls pass identical props (`nav={NAV}`, `pathname`, `permissions={me.data?.permissions}`, `onNavigate={() => setDrawerOpen(false)}`). No inline `NAV.filter(...).map(...)` block or "Sistema"/Configurações/Suporte markup remains duplicated in `dashboard-shell.tsx` — that logic lives solely inside `sidebar-nav.tsx` (confirmed via full-file read of both files; `sidebar-nav.tsx`'s own doc-comment states it is "consumed at 2 call sites... markup and active-state logic are identical across both", matching what's observed). One pre-existing Info-level nit remains unfixed (intentionally, per 109-REVIEW.md IN-06): `sidebar-nav.tsx` still exports both a named `export function SidebarNav` and `export default SidebarNav` even though only the named import is used — cosmetic, non-blocking. |
| 12 | `pnpm build` passes with zero new type errors after both implementation plans land | VERIFIED | Independently re-ran (not trusting 109-03-SUMMARY.md's claim): `pnpm build` → `Compiled successfully in 26.1s`, `Finished TypeScript in 34.1s` with no errors, `Generating static pages using 7 workers (24/24)` — matches the claimed 24/24 routes exactly. |
| 13 | `pnpm lint` passes with zero new errors (no orphaned imports left in `dashboard-shell.tsx`) | VERIFIED | Independently re-ran and parsed the JSON output: 6 errors / 17 warnings project-wide (matches 109-03-SUMMARY's own count). Of the 6 errors, only 1 touches a phase-109 file: `react-hooks/set-state-in-effect` on `dashboard-shell.tsx:55` (`setDrawerOpen(false)` inside the pre-existing pathname-change `useEffect`, lines 54-56) — this effect is untouched by this phase (109-01-PLAN.md explicitly required leaving it alone: "do NOT add any loading/isFetched guard"), and is a pre-existing lint rule finding, not a regression. `user-menu.tsx` has 0 errors (2 pre-existing-class `@next/next/no-img-element` warnings, confirmed by the SUMMARY as a "relocation, not a regression" of avatar `<img>` tags moved out of `dashboard-shell.tsx`). `notification-bell.tsx`, `setup/page.tsx`, and `sidebar-nav.tsx` have **zero** lint findings of any kind. No `no-unused-vars` findings for `Button`/`Tooltip`/`LogOut` anywhere. |
| 14 | A human confirms the `DropdownMenu` user menu, the `Badge` counter, and the `/setup` `Progress` render and behave correctly in light and dark themes | **UNCERTAIN — human_needed** | 109-03-SUMMARY.md and `deferred-items.md` both explicitly document that the blocking human-verify checkpoint (109-03-PLAN.md Task 2, `gate="blocking"`) was closed by the **executor agent itself** — via its own computer-use browser clicks plus source-code reading — after live testing repeatedly hit a genuine, root-caused, pre-existing hydration-mismatch bug unrelated to this phase. No artifact records an actual human typing "approved" or reviewing the checklist. Only the topbar `UserMenu` (dark theme only) was live-clicked with concrete DOM/network evidence; "Perfil"→`/profile`, "Terminar sessão"→logout, both sidebar/Sheet-footer `UserMenu` instances, the Badge counter with real unread data, the `/setup` Progress bar advancing through submit, and the entire light theme were all confirmed via source-code reading only, not live interaction — a substitution the plan's own acceptance criteria did not authorize. |

**Score:** 13/14 truths verified programmatically + directly observed against source and git history; 1 truth is UNCERTAIN pending genuine human live confirmation.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/components/shared/user-menu.tsx` | Shared `UserMenu` DropdownMenu component (topbar + sidebar variants) | VERIFIED | 90 lines, `"use client"`, exports `UserMenu`; contains `DropdownMenuTrigger`, `DropdownMenuContent`, all 4 locked items; no `<Link href="/settings">` inside the trigger. |
| `web/src/components/shared/dashboard-shell.tsx` | 3 `UserMenu` call sites; removed inline avatar+logout blocks | VERIFIED | `<UserMenu` count = 3; `Button`/`Tooltip`/`LogOut` imports absent; `SidebarNav` consumed twice, no duplicate nav markup. |
| `web/src/components/shared/notification-bell.tsx` | Badge-based unread counter replacing the manual span; Popover untouched | VERIFIED | `<Badge` present (2 usages: category chip + counter); 0 `<span` outside comments; `cn` imported. |
| `web/src/app/setup/page.tsx` | Progress indicator derived from form/submit state | VERIFIED | `<Progress value={wizardProgress} />` present; `Progress` imported; `wizardPhase`/`wizardProgress` computed inline, no new `useState`. |
| `web/src/components/shared/sidebar-nav.tsx` | New shared nav component eliminating duplication (WR-03) | VERIFIED | Exists, exports `SidebarNav`, consumed at exactly 2 sites in `dashboard-shell.tsx`, contains generalized active-state logic (WR-04, IN-07) and the `onNavigate` drawer-close callback (iteration-3 WR-01). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `dashboard-shell.tsx` | `user-menu.tsx` | import + 3 render sites | WIRED | `import { UserMenu } from "@/components/shared/user-menu"` (line 5); 3 `<UserMenu` renders; `pnpm build` confirms it resolves and type-checks. |
| `user-menu.tsx` | `@/components/ui/dropdown-menu` | `DropdownMenu` primitive import | WIRED | Import present (lines 6-12); underlying primitive confirmed Radix-backed (`radix-ui`'s `DropdownMenu` export). |
| `user-menu.tsx` | `onLogout` prop | `DropdownMenuItem onSelect` | WIRED | `onSelect={onLogout}` (line 83), `onLogout` passed through unchanged from `dashboard-shell.tsx`. |
| `notification-bell.tsx` | `@/components/ui/badge` | `Badge` for unread counter | WIRED | `<Badge` at line 89; `Badge` already imported at line 8 (pre-existing, shared with the category-chip usage). |
| `setup/page.tsx` | `@/components/ui/progress` | `Progress` import + render | WIRED | `import { Progress } from "@/components/ui/progress"` (line 14); rendered at line 272. |
| `setup/page.tsx` | `form.formState.isSubmitting` / `successMessage` | `wizardPhase` derivation | WIRED | Lines 49-51 derive `wizardPhase`/`wizardProgress` directly from these 2 existing signals; no new business state. |
| `dashboard-shell.tsx` | `sidebar-nav.tsx` | import + 2 render sites | WIRED | `import { SidebarNav, type NavItem } from "@/components/shared/sidebar-nav"` (line 4); 2 `<SidebarNav` renders with identical props. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `UserMenu` trigger (name/role/avatar) | `me` (all 3 instances) | `useMe()` in `dashboard-shell.tsx`, a real TanStack Query hook against `/auth/me` — passed as `me={me.data}` | Yes — live-fetched tenant/user data, not hardcoded | FLOWING |
| Notification `Badge` counter | `count`/`unread.isError` | `useNotificacoesUnreadCount()` — real backend-scoped TanStack Query, unchanged by this phase | Yes | FLOWING |
| `/setup` `Progress` value | `wizardProgress` | Locally computed from `form.formState.isSubmitting` (react-hook-form live state) and `successMessage` (set from the real `apiFetch("/setup/initialize")` response) | Yes — not a static value; genuinely reflects submit lifecycle | FLOWING |
| `SidebarNav` active-state / filtered items | `pathname`, `permissions` | `usePathname()` (Next.js router) / `me.data?.permissions` (from the same live `useMe()` query) | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full app builds with all 5 phase-109-touched/created files included | `cd web && pnpm build` | `Compiled successfully in 26.1s`, TypeScript clean, 24/24 routes generated | PASS |
| No new lint regressions introduced by this phase's diffs | `cd web && pnpm lint` (parsed JSON, cross-checked per-file) | 6 errors/17 warnings project-wide; only 1 pre-existing (untouched-line) error touches a phase-109 file; `notification-bell.tsx`/`setup/page.tsx`/`sidebar-nav.tsx` are lint-clean | PASS |
| Zero standalone "Terminar sessão" buttons remain outside `user-menu.tsx` | `grep -c "Terminar sessão" dashboard-shell.tsx` | 0 matches | PASS |
| Exactly 3 `UserMenu` call sites, exactly 1 `UserMenu` definition | `grep -c "<UserMenu" dashboard-shell.tsx` / repo-wide `export function UserMenu` search | 3 / 1 | PASS |
| Dark-mode Badge fix present verbatim | `grep "dark:bg-red-500\|dark:bg-slate-400" notification-bell.tsx` | Both present on the same conditional line (92) | PASS |
| All 11 code-review/implementation commit hashes exist | `git cat-file -t <hash>` ×11 | All resolve to `commit` | PASS |

### Probe Execution

No formal `scripts/*/tests/probe-*.sh` convention exists in this repository for frontend UI-migration phases — SKIPPED. The equivalent evidence is the independently-re-run `pnpm build`/`pnpm lint` gate above, plus direct source/diff verification of all 3 NTF surfaces, consistent with the pattern used in Phase 108's verification.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| NTF-28 | 109-01 | Novo menu de utilizador na topbar usa `DropdownMenu` | SATISFIED | Truths #1-#4, #10 (WR-03/04, onNavigate), #11 |
| NTF-29 | 109-02 | Contador de não-lidas do sino trocado do `<span>` manual para `Badge` oficial (Popover mantém-se) | SATISFIED | Truths #5-#7, #10 (WR-01) |
| NTF-30 | 109-02 | Wizard `/setup` usa indicador de progresso linear baseado em `Progress` (sem Stepper de terceiros) | SATISFIED | Truths #8-#9, #10 (WR-02, WR-05) |

All 3 requirements are marked `[x]`/"Complete" in `.planning/REQUIREMENTS.md` (checkbox list lines 64-66, traceability table lines 133-135) — consistent with the code-level evidence found. No orphaned requirements found for this phase.

**Documentation-sync finding (not a functional gap):** `.planning/ROADMAP.md` still shows the 109-01/02/03 plan checkboxes as `[ ]` (unchecked, lines 431-433) and the milestone summary table still lists Phase 109 as "0/3, Not started" (line 516), even though `REQUIREMENTS.md` already reflects Complete and all 3 plans' own SUMMARYs/commits confirm the work landed. Same class of doc-lag flagged in Phase 108's VERIFICATION.md. Recommend syncing ROADMAP.md before closing the phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` debt markers found in any of the 5 files touched by this phase (`placeholder=` matches are legitimate HTML input-placeholder attributes, not debt markers) | — | None — clean |
| `sidebar-nav.tsx:86` | 86 | Unused `export default SidebarNav` alongside the named export (109-REVIEW.md IN-06, intentionally left unfixed as Info-level) | INFO | Cosmetic only; only the named import is used anywhere in `src/`. |
| `dashboard-shell.tsx:58-66` | 58-66 | Dead unused dynamic `import("@tanstack/react-query")` inside `onLogout`, with a comment describing unimplemented cache-clear behavior (109-REVIEW.md IN-01, intentionally left unfixed as Info-level) | INFO | Pre-existing-in-spirit dead code; `window.location.href` navigation already resets client state via full page reload, so functionally harmless. |
| `web/src/components/shared/bottom-nav.tsx:23` | 23 | Pre-existing exact-match-plus-special-case active-route pattern, now inconsistent with the newly-generalized `sidebar-nav.tsx` pattern (109-REVIEW.md IN-11) | INFO | Out of this phase's 5-file scope (separate `BOTTOM_NAV` array); flagged for awareness, not a regression introduced by this phase. |

### Human Verification Required

### 1. Live end-to-end click-through of all 3 `UserMenu` instances, both themes

**Test:** In both light and dark theme: click the desktop sidebar footer avatar (opens upward, no clipping), click "Perfil" (lands on `/profile`), reopen and click "Terminar sessão" (full logout to `/login`). Repeat for the mobile Sheet (hamburger) footer instance.
**Expected:** All 3 instances open a DropdownMenu (not a direct navigation); "Perfil"/"Configurações"/"Terminar sessão" all work end-to-end; no clipping/collision issue when the footer menus open upward; light theme renders with correct contrast.
**Why human:** 109-03-SUMMARY.md and `deferred-items.md` both explicitly admit that only the topbar instance (dark theme only) was actually clicked live this session; the 2 footer instances, "Perfil" navigation, the full logout flow, and light theme were all substituted with source-code reading after a documented Browser-pane/hydration-mismatch failure. The plan's Task 2 was a `gate="blocking"` human checkpoint — no artifact records an actual human (as opposed to the executor agent) reviewing and approving it.

### 2. Notification bell Badge with a real unread notification, both themes

**Test:** With unread notifications present, confirm the Bell's red counter Badge renders in the correct top-right position with no layout shift, in both light and dark theme; if a fetch error can be induced, confirm the badge turns slate-gray with "!".
**Expected:** Same position/size/color as before the migration, correct in both themes, no regression.
**Why human:** Confirmed only via source/diff analysis this session — no live render with real unread data was performed (`deferred-items.md`).

### 3. `/setup` wizard Progress bar through a real submit cycle

**Test:** Visit `/setup` on a fresh/uninitialized install; watch the Progress bar and percentage advance 33% → 66% → 100% as the form is submitted successfully, in both themes.
**Expected:** Bar and label update live and match the wizard's actual submit lifecycle, no layout shift.
**Why human:** Confirmed only via source reading this session, not a live submit-and-watch run (`deferred-items.md`).

### Gaps Summary

No functional gaps were found in the shipped code: the `DropdownMenu`-based `UserMenu` (3 call sites, 1 shared component), the `Badge`-based unread counter (including the WR-01 dark-mode fix), the untouched bell `Popover`, the `Progress`-based `/setup` wizard, the `SidebarNav` dedup extraction, and all 7 Warning-level code-review fixes across both iterations are genuinely present and correct in the current source — independently confirmed via direct file reads, targeted greps, `git show` diffs against each cited commit, and a live re-run of `pnpm build`/`pnpm lint` (not by trusting the SUMMARY/REVIEW/REVIEW-FIX narratives).

The phase is held at `human_needed` status for one reason: 109-03's own blocking human-verify checkpoint was not closed by an actual human. The executor agent substituted its own browser-automation clicks (1 of 3 UserMenu instances, dark theme only) plus source-code reading for the remaining checklist items, after a genuinely root-caused but unrelated pre-existing hydration bug made further live testing difficult. Since the plan's own gate required real human confirmation — not code inference — this verification routes the remaining checklist items to a human for final sign-off, consistent with how Phase 108's verification handled an analogous incomplete-live-check gap. A minor ROADMAP.md doc-sync lag (unchecked plan boxes, stale "0/3" summary row) was also found and should be corrected before closing the phase, but does not block on its own.

---

_Verified: 2026-07-17T22:40:00Z_
_Verifier: Claude (gsd-verifier)_
