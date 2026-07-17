---
phase: LEXCV-109-notifica-es-settings-setup-wizard
reviewed: 2026-07-17T21:50:00Z
depth: quick
files_reviewed: 5
files_reviewed_list:
  - web/src/components/shared/user-menu.tsx
  - web/src/components/shared/dashboard-shell.tsx
  - web/src/components/shared/sidebar-nav.tsx
  - web/src/components/shared/notification-bell.tsx
  - web/src/app/setup/page.tsx
findings:
  critical: 0
  warning: 0
  info: 10
  total: 10
status: issues_found
---

# Phase LEXCV-109: Code Review Report (Iteration 3 — Final Confirmation Pass)

**Reviewed:** 2026-07-17T21:50:00Z
**Depth:** quick (confirmation pass over a 2-commit diff, plus sanity pass over the rest)
**Files Reviewed:** 5
**Status:** issues_found (0 Critical, 0 Warning — no blocking issues remain; 10 intentionally-deferred Info items carried over)

## Summary

This is the capped, final iteration. It supersedes `109-REVIEW.md` iteration 2. Scope was
narrowed per the task: verify commits `d86d8fc` (WR-01) and `604c673` (IN-07) are applied
correctly and introduce no regression, plus a sanity pass over the remaining 3 untouched files.

**Both fixes confirmed correct, no regressions found.**

### WR-01 — Mobile drawer now closes on same-route nav clicks (RESOLVED)

**Verified via `git show d86d8fc`.** `SidebarNavProps.onNavigate` was added as
`onNavigate?: () => void` (optional, `sidebar-nav.tsx:21`), and is now fired from every
`Link`'s `onClick` in `SidebarNav` — both the primary `nav` loop (`sidebar-nav.tsx:42`) and the
hardcoded `/settings` link (`sidebar-nav.tsx:62`). `dashboard-shell.tsx` passes
`onNavigate={() => setDrawerOpen(false)}` at both `SidebarNav` call sites (desktop `<aside>`,
line 79; mobile `<Sheet>`, line 99).

Checked specifically for the two regression risks named in the task:

1. **Optionality / undefined safety.** `onNavigate` is typed `?: () => void` and consumed as
   `onClick={onNavigate}`. Passing `undefined` to a JSX `onClick` prop is valid React and is a
   no-op (no handler attached) — this does not throw and does not break any hypothetical future
   caller that omits the prop. Grepped the whole `src/` tree for `SidebarNav` usage — the only
   two call sites are the two in `dashboard-shell.tsx`, and both pass `onNavigate` today, so
   there is currently no caller exercising the "undefined" path in practice, but the type and
   runtime behavior are both safe if one were added.
2. **Desktop `<aside>` instance passing `onNavigate={() => setDrawerOpen(false)}`.** Confirmed
   harmless: `drawerOpen` only controls the mobile `<Sheet open={drawerOpen}>` (`dashboard-shell.tsx:89`),
   and the only path that sets it `true` is the hamburger button (`dashboard-shell.tsx:112-119`),
   which is `md:hidden` — i.e., not interactable on the same viewport where the desktop `<aside>`
   (`hidden md:flex`) is visible. Clicking a link in the desktop `<aside>` calls
   `setDrawerOpen(false)` while `drawerOpen` is already `false`; React bails out of re-rendering
   on a same-value `useState` setter call (`Object.is` comparison), so this is a true no-op, not
   merely inert application behavior. As a side benefit, in the edge case where a user opens the
   drawer on a narrow viewport and then resizes the window past the `md` breakpoint without the
   Sheet having closed (CSS-only breakpoint hiding, not unmounting), the `<aside>`'s `onNavigate`
   would actually help by force-closing a stray open Sheet — not a regression, a minor
   improvement. No new issue found.

Also confirmed via `npx tsc --noEmit` and `npx eslint` on all 5 files: 0 new type errors, 0 new
lint errors. The only lint findings present (`react-hooks/set-state-in-effect` on the untouched
`setDrawerOpen(false)` pathname effect in `dashboard-shell.tsx`, `@next/next/no-img-element` on
`dashboard-shell.tsx`/`user-menu.tsx`) are unchanged pre-existing items, out of scope, already
noted in iteration 2. The 3 `Cannot find module 'vitest'` `tsc` errors are pre-existing,
unrelated `*.test.ts` environment issues, also already noted in iteration 2.

### IN-07 — Configurações active-state check now consistent with the nav-item loop (RESOLVED)

**Verified via `git show 604c673`.** A new `settingsActive` constant was introduced at
`sidebar-nav.tsx:30`: `pathname === "/settings" || pathname.startsWith("/settings/")`, mirroring
the nav-item loop's generalized check (`pathname === item.href || pathname.startsWith(\`${item.href}/\`)\`,
`sidebar-nav.tsx:36`). Both usages of the old inline `pathname === "/settings"` check (the
`className` conditional at line 65 and the `Settings` icon's conditional at line 70) were
updated to reference `settingsActive` — no stale references to the old exact-match expression
remain in the file (confirmed via full read and grep). Exactly the suggested fix, applied
correctly, no regression.

### Sanity pass over untouched files

`user-menu.tsx`, `notification-bell.tsx`, and `setup/page.tsx` received no commits since
iteration 2's full review (confirmed via `git log 11bedd5..HEAD` scoped to all 5 in-scope
files — only `d86d8fc` and `604c673` appear, both touching only `dashboard-shell.tsx` and
`sidebar-nav.tsx`). A quick re-read plus a targeted grep for debug artifacts and dangerous
patterns (`console.log`, `debugger`, `eval(`, `innerHTML`, `dangerouslySetInnerHTML`, empty
catch blocks) across the two changed files turned up nothing new. No Critical or new Warning
issues found anywhere in the 5-file scope.

## Structural Findings (fallow)

None provided for this iteration.

## Narrative Findings (AI reviewer)

No Critical or Warning findings remain. The following Info-level items are carried over
verbatim from iteration 2 — confirmed still present and unchanged by this iteration's 2
commits — and remain intentionally unfixed per the requested fix scope for this iteration.

## Info

### IN-01: Dead code in `onLogout` — unused dynamic import, comment describes unimplemented behavior

**File:** `web/src/components/shared/dashboard-shell.tsx:58-66`
**Issue:** `await import("@tanstack/react-query")` is imported but never used — no
`queryClient.clear()` call. Still present verbatim.
**Fix:** Delete the unused import and its explanatory comment line; `window.location.href`
already resets all client state via full navigation.

### IN-02: Setup wizard re-reads/re-encodes the logo file on submit instead of reusing the cached preview

**File:** `web/src/app/setup/page.tsx:76, 94`
**Issue:** `handleLogoChange` already computes `logoPreview` via `readFileAsDataUrl(file)`;
`onSubmit` redundantly re-invokes `readFileAsDataUrl(logoFile)` for the same `File`.
**Fix:** Reuse `logoPreview` directly in the payload (`logo: logoPreview`).

### IN-03: Wizard "progress" percentage is purely decorative and starts at 33% before any user action

**File:** `web/src/app/setup/page.tsx:49-51, 268-272`
**Issue:** `wizardProgress` is `33` at `"idle"` (on initial page load, before any input), `66`
while submitting, `100` on success — cosmetic only, not tied to the 3 listed steps.
**Fix:** Either relabel as a phase indicator instead of a percentage, or start at `0` for
`"idle"`.

### IN-04: Non-functional placeholder "Suporte" link

**File:** `web/src/components/shared/sidebar-nav.tsx:73-79`
**Issue:** `<Link href="#">` with a `LifeBuoy` icon and "Suporte" label does nothing when
clicked.
**Fix:** Wire it to a real destination, or remove/disable it until implemented.

### IN-05: Header search input is presentation-only

**File:** `web/src/components/shared/dashboard-shell.tsx:121-127`
**Issue:** The `<Input placeholder="Pesquisar processos, entidades...">` has no `value`,
`onChange`, or submit handling.
**Fix:** Wire it up to real search behavior, or mark it `disabled` with a tooltip if
intentionally deferred.

### IN-06: Unused default export in `sidebar-nav.tsx`

**File:** `web/src/components/shared/sidebar-nav.tsx:86`
**Issue:** `export default SidebarNav;` in addition to the named `export function SidebarNav`,
but the only consumer (`dashboard-shell.tsx`) imports it via the named import. No default
import of this module exists anywhere in `src/`.
**Fix:** Drop the default export; keep only the named export, consistent with `UserMenu`'s
pattern in the same directory.

### IN-08: Logo file input's `accept` attribute wasn't narrowed to match the MIME allowlist

**File:** `web/src/app/setup/page.tsx:214`
**Issue:** The underlying `<Input id="logo" type="file" accept="image/*" ...>` still
advertises `image/*` to the OS file picker even though `handleLogoChange`'s JS validation
allowlists only PNG/JPEG/WEBP. Not a security regression (the JS allowlist still runs and
still blocks other types after selection), but an incomplete UX fix.
**Fix:**
```tsx
<Input id="logo" type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={handleLogoChange} />
```

### IN-09 (adjacent file, flagged for awareness): No maximum length on password fields

**File:** `web/src/schemas/setup.ts:10-16` (consumed by `web/src/app/setup/page.tsx:238-249,
252-263`) — outside this review's 5-file scope.
**Issue:** `adminPassword`/`confirmPassword` only enforce a *minimum* complexity via regex; no
upper bound, so a client could submit an arbitrarily large string to the pre-auth
`/setup/initialize` endpoint.
**Fix:** Add `.max(128)` (or similar) to both fields.

### IN-10 (adjacent file, flagged for awareness): `returnUrl` open-redirect risk in the flow `DashboardShell` feeds into

**File:** `web/src/app/(auth)/login/page.tsx:46` — outside this review's 5-file scope.
**Issue:** `login/page.tsx` accepts any `returnUrl` query param and only checks
`returnUrl.startsWith("/")` before calling `router.replace(returnUrl)`. This does not exclude
protocol-relative values like `//evil.com`, enabling an open redirect after login via a
crafted link. `dashboard-shell.tsx`'s own construction of `returnUrl` (from the app's own
current route) is safe; the vulnerable code is the *consumer* in `login/page.tsx`.
**Fix:** Apply the same hardened pattern already used elsewhere in this codebase
(`isInternalLinkUrl` in `web/src/lib/notificacao-categoria.ts:106-113`) to the login redirect.

### IN-11 (adjacent file, flagged for awareness): `bottom-nav.tsx` still has the pre-generalization active-route pattern, now inconsistent with `sidebar-nav.tsx`

**File:** `web/src/components/shared/bottom-nav.tsx:23` — outside this review's 5-file scope,
a deliberate, explicitly-noted scope decision per `109-REVIEW-FIX.md` (its own separate
`BOTTOM_NAV` array, not part of the WR-04/IN-07 fixes' cited file/line range).
**Issue:**
```tsx
const active = pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"));
```
This is the pre-generalization pattern that both `sidebar-nav.tsx` checks (nav-item loop and,
as of this iteration, the Configurações link) have now moved past. The persistent bottom tab
bar (visible on mobile, `flex md:hidden`) still does not highlight nested routes like
`/clientes/abc-123`, while the hamburger-drawer `SidebarNav` correctly does.
**Fix:** Apply the same generalized check to `bottom-nav.tsx:23` for consistency across both
mobile nav surfaces:
```tsx
const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
```

---

_Reviewed: 2026-07-17T21:50:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: quick_
_Iteration: 3 (final, capped — supersedes 109-REVIEW.md iteration 2)_
