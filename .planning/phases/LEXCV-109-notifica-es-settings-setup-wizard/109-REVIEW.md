---
phase: LEXCV-109-notifica-es-settings-setup-wizard
reviewed: 2026-07-17T14:23:05Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - web/src/components/shared/user-menu.tsx
  - web/src/components/shared/dashboard-shell.tsx
  - web/src/components/shared/notification-bell.tsx
  - web/src/app/setup/page.tsx
findings:
  critical: 0
  warning: 5
  info: 7
  total: 12
status: issues_found
---

# Phase LEXCV-109: Code Review Report

**Reviewed:** 2026-07-17T14:23:05Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Reviewed the four files touched by this phase: the newly-extracted `UserMenu` shared
component, the `DashboardShell` that now consumes it, the `NotificationBell` (span → `Badge`
swap for the unread-count pill), and the `/setup` first-run wizard (added `Progress`
indicator). Cross-checked call sites (`use-me`, `use-notificacoes`, `permissions.ts`,
`notificacao-categoria.ts`, `lib/api.ts`, `lib/auth.ts`, `schemas/setup.ts`, the Radix
`dropdown-menu`/`badge`/`progress` UI primitives, and the `(dashboard)/layout.tsx` Suspense
boundary) to confirm behavior rather than assuming it.

No crashes, injection vectors, or hardcoded secrets found in these four files. The main
finding worth blocking on is a real visual regression introduced by this diff: the
notification unread-count badge silently loses its intended red/gray color in dark mode
because of a Tailwind CSS specificity interaction between the new `Badge` wrapper's default
variant and the ad-hoc override classes. The rest are logic gaps and quality issues
(duplicate code the phase's own refactor didn't finish addressing, a double-submit window
in the setup wizard, superficial file-type validation) that should be fixed but don't block
core functionality.

## Warnings

### WR-01: Unread notification badge loses its color in dark mode

**File:** `web/src/components/shared/notification-bell.tsx:88-97`
**Issue:** This diff replaced a plain `<span className={...}>` with `<Badge className={cn(...)}>`,
but no `variant` prop is passed, so `Badge` falls back to its `defaultVariants: { variant: "secondary" }`
(see `web/src/components/ui/badge.tsx:25-27`), which injects
`border-transparent bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-50`
ahead of the passed-in className.

`cn()` uses `tailwind-merge`, which only dedupes classes that share the *same* variant/modifier
key. `bg-red-500` / `bg-slate-400` (no modifier) do not conflict with `dark:bg-neutral-800`
(has the `dark:` modifier) from tailwind-merge's point of view, so **both** classes survive
in the final class string. In the browser, Tailwind v4's `dark:` variant here compiles to
`:is(.dark *)` (see `app/globals.css:5`, `@custom-variant dark (&:is(.dark *));`), which has
higher CSS specificity (0,2,0) than a bare `.bg-red-500` (0,1,0). Result: whenever the app is
in dark mode (confirmed wired via `next-themes` `attribute="class"` in `app/providers.tsx`),
`dark:bg-neutral-800` wins and the badge renders as a dull gray pill instead of red
(unread) / slate-400 (error), defeating the purpose of the indicator. This bug did not exist
in the prior plain-`<span>` implementation, which had no default variant classes to fight.

**Fix:**
```tsx
<Badge
  className={cn(
    "absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full p-0 flex items-center justify-center text-[10px] font-bold leading-none text-white border-transparent",
    unread.isError ? "bg-slate-400 dark:bg-slate-400" : "bg-red-500 dark:bg-red-500",
  )}
>
```
Add the `dark:` counterpart for every color utility that must win in both themes (any single
custom `variant` value in `badgeVariants` would have the same problem, since every variant
in that `cva` config defines both a light and a dark background).

### WR-02: Setup wizard submit button re-enables during the post-success redirect window

**File:** `web/src/app/setup/page.tsx:84-108, 300-307`
**Issue:** `onSubmit` sets `successMessage` and schedules `window.setTimeout(() => router.replace("/login"), 1200)`
but does not `await` the timeout — the async handler resolves immediately after that. Once
it resolves, `form.formState.isSubmitting` flips back to `false`, and the submit
`<Button disabled={form.formState.isSubmitting}>` re-enables while the success banner is
still showing and the app is still on `/setup` for ~1.2s. A user who clicks "Concluir
configuração" again in that window fires a second `POST /setup/initialize`, which the
backend will reject (already initialized), flashing an error over the success state right
before the redirect fires. This is the one-time bootstrap wizard, so it's worth closing the
window rather than relying on timing.
**Fix:**
```tsx
<Button
  type="submit"
  disabled={form.formState.isSubmitting || wizardPhase !== "idle"}
  ...
>
```
(`wizardPhase !== "idle"` covers both `"submitting"` and `"success"`.)

### WR-03: This phase's own dedup refactor left an equally-sized duplicate block unaddressed

**File:** `web/src/components/shared/dashboard-shell.tsx:86-131` vs `146-191`
**Issue:** This phase extracted `UserMenu` specifically to remove the 3x-duplicated
avatar/name/logout JSX (per the new component's own doc comment: "Consumed at 3 call
sites... the menu content is identical across all 3"). But the desktop `<aside>` nav block
(NAV filter/map, lines 86-106, plus the "Sistema" Configurações/Suporte links, lines
108-131) is byte-for-byte duplicated in the mobile `<Sheet>` (lines 146-166 and 168-191)
and was left untouched. This is exactly the kind of drift risk (edit one copy, forget the
other) the `UserMenu` extraction in this same phase was meant to eliminate.
**Fix:** Extract a `SidebarNav` (or similar) component analogous to `UserMenu` and render it
in both the `<aside>` and the `<Sheet>`.

### WR-04: Nav active-state highlighting only works for exact-match routes plus one hardcoded exception

**File:** `web/src/components/shared/dashboard-shell.tsx:88, 148`
**Issue:**
```tsx
const active = pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"));
```
Every nested/detail route breaks nav highlighting except the one special-cased
`/processos/dashboard*`. The app has nested routes under every other nav section
(confirmed present: `clientes/[id]`, `clientes/novo`, `clientes/merge`, `agenda/[id]`,
`agenda/novo`, `documentos/[id]`, `documentos/novo`, `financeiro/[id]`, `financeiro/novo`).
Visiting e.g. `/clientes/abc-123` leaves no sidebar item highlighted as active, which is
inconsistent with how `/processos/dashboard/*` is handled.
**Fix:** Generalize instead of special-casing one route:
```tsx
const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
```
(this also subsumes the `/processos/dashboard` case, so the special-case branch can be
deleted).

### WR-05: Logo upload validation is client-side only and permits SVG despite the UI copy promising raster formats

**File:** `web/src/app/setup/page.tsx:62-66`
**Issue:** `handleLogoChange` only checks `file.type.startsWith("image/")`. `file.type` is a
client-reported MIME string with no server-side confirmation from this code path, and the
check accepts `image/svg+xml` even though the UI label says "PNG, JPG, WEBP até 2MB"
(line 192). SVG can embed `<script>`/event-handler payloads; while rendering it via
`<img src="data:image/svg+xml,...">` (as `dashboard-shell.tsx` and `user-menu.tsx` do for
`tenant_logo_data_url`/`avatar_url`) suppresses script execution per spec, this is the
first-run, effectively pre-auth bootstrap endpoint, so defense-in-depth matters more here
than elsewhere.
**Fix:**
```ts
const ALLOWED_LOGO_TYPES = ["image/png", "image/jpeg", "image/webp"];
if (!ALLOWED_LOGO_TYPES.includes(file.type)) {
  setLogoError("O logo deve ser PNG, JPG ou WEBP.");
  event.target.value = "";
  return;
}
```
and confirm the backend independently re-validates content type/magic bytes for
`SetupInitializeRequest.logo` rather than trusting the client-declared type.

## Info

### IN-01: Dead code in `onLogout` — unused dynamic import, comment describes unimplemented behavior

**File:** `web/src/components/shared/dashboard-shell.tsx:69-77`
**Issue:**
```tsx
const onLogout = async () => {
  await clearTokens();

  // Invalidamos a cache do React Query para forçar a verificação de estado e limpar dados
  await import("@tanstack/react-query");
  // Mas não podemos chamar hook dentro de função callback.
  // Em vez disso fazemos um hard reload ou só o replace que já vai forçar no auth check
  window.location.href = "/login";
};
```
`await import("@tanstack/react-query")` imports the module but never uses it (no
`queryClient.clear()` call) — it's a no-op left over from an abandoned approach; the
following two comment lines already explain why it wasn't implemented that way. Pre-existing
(not part of this diff), but present in a file under review.
**Fix:** Delete the unused import and the first comment line; the `window.location.href`
full navigation already resets all client state on its own.

### IN-02: Setup wizard re-reads/re-encodes the logo file on submit instead of reusing the cached preview

**File:** `web/src/app/setup/page.tsx:75, 93`
**Issue:** `handleLogoChange` already computes `logoPreview` via `readFileAsDataUrl(file)`
and stores it in state. `onSubmit` then calls `readFileAsDataUrl(logoFile)` again for the
same `File` object to build the payload, redundantly re-running `FileReader` on an
already-encoded value.
**Fix:** Reuse `logoPreview` directly in the payload (`logo: logoPreview`) instead of
re-invoking `readFileAsDataUrl`.

### IN-03: Wizard "progress" percentage is purely decorative and starts at 33% before any user action

**File:** `web/src/app/setup/page.tsx:48-50, 267-271`
**Issue:** `wizardProgress` is `33` at `"idle"` (i.e., on initial page load, before the user
has typed anything), `66` while submitting, `100` on success. It doesn't track the 3 listed
steps ("Criação do tenant" / "Criação do admin" / "Fecho do wizard") which all happen inside
a single network call — it's cosmetic only, and showing 33% before any input is entered can
read as "a third of the work is already done."
**Fix:** Either relabel as a phase indicator instead of a percentage, or start at `0` for
`"idle"`.

### IN-04: Non-functional placeholder "Suporte" link

**File:** `web/src/components/shared/dashboard-shell.tsx:123-129, 183-189`
**Issue:** `<Link href="#">` with a `LifeBuoy` icon and "Suporte" label does nothing when
clicked, duplicated in both the desktop aside and mobile Sheet.
**Fix:** Wire it to a real destination, or remove/disable it until implemented, to avoid a
dead-end UI affordance in a shipped feature.

### IN-05: Header search input is presentation-only

**File:** `web/src/components/shared/dashboard-shell.tsx:212-218`
**Issue:** The `<Input placeholder="Pesquisar processos, entidades...">` has no `value`,
`onChange`, or submit handling — typing into it does nothing.
**Fix:** Either wire it up to real search behavior or, if intentionally deferred to a later
phase, note that explicitly (e.g., `disabled` + tooltip) so it doesn't read as a broken
feature.

### IN-06: No maximum length on password fields

**File:** `web/src/schemas/setup.ts:10-16` (consumed by `web/src/app/setup/page.tsx:236-247, 250-261`)
**Issue:** `adminPassword`/`confirmPassword` only enforce a *minimum* complexity via regex;
there's no upper bound, so a client could submit an arbitrarily large string as a "password"
to the pre-auth `/setup/initialize` endpoint.
**Fix:** Add `.max(128)` (or similar) to both fields, consistent with common password-length
guidance (e.g. ASVS V2.1.2), and to bound payload/hash-cost size for what is effectively an
unauthenticated bootstrap endpoint.

### IN-07: `returnUrl` open-redirect risk in the flow `DashboardShell` feeds into (adjacent file, flagged for awareness)

**File:** `web/src/app/(auth)/login/page.tsx:46` (outside this review's 4-file scope; found by
tracing `web/src/components/shared/dashboard-shell.tsx:58-63`, which constructs the redirect)
**Issue:** `DashboardShell`'s auth-redirect effect safely builds
`` `/login?returnUrl=${encodeURIComponent(currentPath)}` `` from the app's own current route
— that part is fine. But the consumer, `login/page.tsx`, accepts any `returnUrl` query
param and only checks `returnUrl.startsWith("/")` before calling `router.replace(returnUrl)`.
That check does not exclude protocol-relative values like `//evil.com`, which also start
with `/` and are treated by browsers as absolute URLs, enabling an open redirect after a
successful login via a crafted link
(`/login?returnUrl=%2F%2Fevil.com`). The codebase already has a hardened pattern for exactly
this class of bug (`isInternalLinkUrl` in `web/src/lib/notificacao-categoria.ts:106-113`,
whose doc comment explicitly documents having been bypassed twice by protocol-relative and
backslash variants before landing on a URL-parser-based sentinel check). Recommend applying
the same pattern to the login redirect. Not counted in this review's Warning/Critical totals
since the vulnerable line lives outside the 4 files in scope for this phase — flagging for
separate follow-up.

---

_Reviewed: 2026-07-17T14:23:05Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
