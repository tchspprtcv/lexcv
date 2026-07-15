---
phase: 99-webpage-nova-app-next-js-de-landing
reviewed: 2026-07-15T11:20:47Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - webpage/.env.example
  - webpage/.gitignore
  - webpage/eslint.config.mjs
  - webpage/next.config.ts
  - webpage/package.json
  - webpage/postcss.config.mjs
  - webpage/tsconfig.json
  - webpage/proxy.ts
  - webpage/src/app/globals.css
  - webpage/src/app/layout.tsx
  - webpage/src/app/page.tsx
  - webpage/src/app/providers.tsx
  - webpage/src/components/theme-toggle.tsx
  - webpage/src/components/ui/button.tsx
  - webpage/src/components/ui/card.tsx
  - webpage/src/lib/utils.ts
  - webpage/src/lib/setup.ts
  - webpage/src/lib/branding.ts
  - webpage/src/types/setup.ts
  - webpage/src/types/branding.ts
  - webpage/src/components/brand-mark.tsx
  - webpage/src/components/site-header.tsx
  - webpage/src/components/hero-section.tsx
  - webpage/src/components/site-footer.tsx
  - webpage/src/components/features-section.tsx
  - webpage/src/components/trust-section.tsx
  - webpage/src/components/contact-section.tsx
findings:
  critical: 1
  warning: 5
  info: 3
  total: 9
status: issues_found
---

# Phase 99: Code Review Report

**Reviewed:** 2026-07-15T11:20:47Z
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Reviewed the new standalone `webpage/` Next.js 16 app (landing page, no auth, no forms) end to end, with a focus on the four areas requested: XSS via tenant-supplied `nome`/`logoDataUrl`, SSRF/URL-construction correctness in the server-to-server fetches, safety of `proxy.ts`'s fail-open behavior, and correctness of `next.config.ts`'s `assetPrefix`/security-headers/rewrite config.

Cross-checked findings against this phase's own planning artifacts (`99-CONTEXT.md`, `99-02-PLAN.md`, `99-02-SUMMARY.md`, `98-CONTEXT.md`) to avoid re-litigating deliberately-accepted, already-documented risks (e.g. the `/setup`/`/login` cross-zone routing deferred to Phase 100, and the STRIDE-reasoned XSS/SSRF/fail-open threat model in `99-CONTEXT.md`). Where the team's own threat-model reasoning is sound, this review says so explicitly rather than re-flagging it.

**XSS (`nome`/`logoDataUrl`):** No exploitable XSS found. `nome` is rendered as a React text child (`{nome}`), which auto-escapes — confirmed no `dangerouslySetInnerHTML` anywhere in the app. `logoDataUrl` is only ever used as an `<img src>` after a `startsWith("data:image/")` guard, and browsers do not execute scripts embedded in SVGs loaded via `<img>` (image context, not document context). One residual hardening gap is noted (WR-05): the guard does not exclude the `image/svg+xml` subtype specifically.

**SSRF:** No SSRF found in either `lib/setup.ts` or `lib/branding.ts` — both fetch destinations are built entirely from fixed environment variables and literal path suffixes, never from visitor-supplied input (query params, headers, or path segments), so a visitor cannot redirect either call to an attacker-chosen destination.

**URL-construction correctness:** This is where the real, high-severity bug lives (CR-01 below). `fetchSetupStatus()` builds a **relative** URL (`${NEXT_PUBLIC_API_BASE_PATH}/setup/status`) and this is the one server-side fetch call in the app that is invoked from `proxy.ts` (a Node.js-runtime Edge Proxy) with no request-derived base — and relative URLs are not valid `fetch()` input outside a browser document context. This was verified both empirically (a plain `node -e "fetch('/api/v1/setup/status')"` throws `TypeError: Failed to parse URL from /api/v1/setup/status`) and against Next.js's own locally-installed docs, which record "`v12.0.9` — Enforce absolute URLs in Edge Runtime" as an intentional, long-standing platform constraint. The practical effect: the entire "redirect to `/setup` when the system is uninitialized" gate — this phase's core requirement (LP-05) — never fires, in any environment, regardless of whether the backend is actually initialized.

**`proxy.ts` fail-open safety:** The *design* is sound — this app protects no sensitive resource, so failing open to show the public marketing page is the correct trade-off (matches this phase's own explicit, accepted threat-model disposition). The *implementation* is not sound: because of CR-01, the "fail open on genuine transient error" catch block is actually catching a deterministic, always-reproducible bug on every single request, silently, with no logging (IN-02) — masking the fact that the redirect branch is dead code.

**`next.config.ts` (`assetPrefix`/headers/rewrite):** `assetPrefix: "/landing-static"` is correctly coordinated with the Proxy `matcher`'s `landing-static` exclusion (both added together, consistent, no accidental exposure). The security headers and the `/api/v1/:path*` rewrite were copied verbatim from `web/next.config.ts` rather than scoped to this app's much narrower needs — this surfaces two independent findings (WR-01, WR-02) rather than an "exposure," since the backend's own auth layer is the actual security boundary in both cases.

## Critical Issues

### CR-01: `fetchSetupStatus()`'s relative URL cannot be parsed server-side — the `/setup` redirect gate is dead code

**File:** `webpage/src/lib/setup.ts:3-12`, `webpage/proxy.ts:7-19`
**Issue:**
`setup.ts` builds the fetch target from a relative base path:
```ts
const apiBasePath = process.env.NEXT_PUBLIC_API_BASE_PATH; // "/api/v1"
const setupStatusUrl = `${apiBasePath}/setup/status`;       // "/api/v1/setup/status"
```
This is only ever called from `proxy.ts`:
```ts
try {
  const status = await fetchSetupStatus();
  if (!status.initialized && pathname !== SETUP_PATH) {
    return NextResponse.redirect(new URL(SETUP_PATH, request.url));
  }
} catch {
  return NextResponse.next(); // fail open
}
```
`proxy.ts` runs server-side (Next.js 16 Proxy defaults to the Node.js runtime; historically Edge runtime enforced this too). A bare relative string is **not valid input to `fetch()`** outside a browser document context — there is no implicit base URL to resolve against, so the underlying `fetch`/`URL` constructor throws before any network request is even attempted.

Verified empirically in this exact repo:
```
$ node -e "fetch('/api/v1/setup/status').catch(e => console.log('THROWN:', e.name, '-', e.message))"
THROWN: TypeError - Failed to parse URL from /api/v1/setup/status
```
And confirmed against this project's own locally-installed Next.js docs (`webpage/node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md`), version history table: `v12.0.9 | Enforce absolute URLs in Edge Runtime`.

Consequence: `fetchSetupStatus()` **always throws**, `proxy.ts`'s `catch` **always** fires, and `NextResponse.next()` is returned unconditionally. The `!status.initialized` redirect branch — the literal purpose of this file and this phase's LP-05 requirement — can never execute in any environment, not just when the backend is genuinely down. This is a strictly worse outcome than the risk this phase's own threat model (`99-CONTEXT.md`, T-99-03) consciously accepted ("if the backend is down during a genuine first boot, the redirect won't fire") — the redirect never fires, period, backend state notwithstanding.

This also explains why it shipped: the task's own `<verify>` gate is purely textual (`grep`/`diff -q`/`pnpm build`) and never exercises the proxy against a live, genuinely-uninitialized backend; the plan's own `<human-check>` for the adjacent branding fetch was explicitly skipped in this execution (per `99-02-SUMMARY.md`), and no equivalent human-check existed for this redirect path at all.

**Fix:** Mirror the pattern already used correctly in `webpage/src/lib/branding.ts` — call the backend directly via the absolute origin, never a relative path, for any fetch performed outside a browser:
```ts
import type { SetupStatusResponse } from "@/types/setup";

const backendOrigin = process.env.BACKEND_API_ORIGIN;
if (!backendOrigin) {
  throw new Error("BACKEND_API_ORIGIN is required");
}

const setupStatusUrl = `${backendOrigin}/api/v1/setup/status`;

export async function fetchSetupStatus(init?: RequestInit): Promise<SetupStatusResponse> {
  const response = await fetch(setupStatusUrl, {
    ...init,
    cache: "no-store",
    headers: { Accept: "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    throw new Error(`Setup status failed with ${response.status}`);
  }
  return (await response.json()) as SetupStatusResponse;
}
```
(This also removes the now-unneeded dependency on `NEXT_PUBLIC_API_BASE_PATH` for this call site.) After fixing, add an integration test/human-check that actually points at a backend with no tenant seeded and confirms the redirect fires — the current automated gate cannot catch this class of bug.

Note for the user (out of this review's file scope, but worth independent verification): `webpage/src/lib/setup.ts` was intentionally copied byte-for-byte from `web/src/lib/setup.ts`, and `web/proxy.ts` calls it the same way (no base URL). If that's accurate, the identical bug likely exists in the already-shipped `web/` app's setup-gate and post-setup redirect (`/dashboard`/`/login`) logic — worth checking independently since `web/` was not part of this review's scope.

## Warnings

### WR-01: CSP allows `'unsafe-inline'` and `'unsafe-eval'` in `script-src`

**File:** `webpage/next.config.ts:25`
**Issue:** The `Content-Security-Policy` header is `script-src 'self' 'unsafe-inline' 'unsafe-eval'; ...`. `'unsafe-inline'` permits inline `<script>`/event handlers and `'unsafe-eval'` permits `eval()`/`new Function()`/string-based `setTimeout` — together these neutralize CSP's main value as an XSS mitigation, since the dominant XSS payload shape (inline script injection) is explicitly allowed. This was copied verbatim from `web/next.config.ts` rather than authored fresh for this app.
**Fix:** Prefer a nonce- or hash-based `script-src` (Next.js supports per-request nonces via Proxy) and drop `'unsafe-eval'` unless a specific, verified dependency requires it:
```ts
{ key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self' 'nonce-<per-request-nonce>'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none';" }
```
If nonce-based CSP is out of scope for this milestone, at minimum remove `'unsafe-eval'` (no code in this app calls `eval`/`Function`) and track `'unsafe-inline'` removal as follow-up.

### WR-02: `/api/v1/:path*` rewrite proxies the entire backend API through the public marketing site, and is currently unused

**File:** `webpage/next.config.ts:11-14`
**Issue:** The rewrite forwards *any* path under `/api/v1/` to `${backendOrigin}`, not just the specific public endpoints this app needs (`/api/v1/public/branding`, `/api/v1/setup/status`). The backend's own JWT/`@PreAuthorize` layer is the real authorization boundary (per `CLAUDE.md`), so this isn't a direct data-exposure bug, but it needlessly makes the *entire* authenticated backend surface reachable through this unauthenticated app's origin — a least-privilege gap, and a larger blast radius if this domain ever gets weaker network-layer protections (WAF/rate-limits) than the main app's domain. It also appears to be dead configuration today: `fetchBranding()` calls `backendOrigin` directly (bypassing this rewrite), and `fetchSetupStatus()`'s relative call never reaches it either (see CR-01) — no code path in `webpage/` currently depends on this rewrite.
**Fix:** Narrow the rewrite to only the paths actually consumed, e.g.:
```ts
{ source: "/api/v1/public/:path*", destination: `${backendOrigin}/api/v1/public/:path*` },
{ source: "/api/v1/setup/status", destination: `${backendOrigin}/api/v1/setup/status` },
```
or remove it entirely if all fetches stay server-to-server against `backendOrigin` (consistent with the fix recommended in CR-01).

### WR-03: No timeout on either server-side `fetch()` call — a hanging backend defeats the "never block the landing" intent

**File:** `webpage/src/lib/setup.ts:12`, `webpage/src/lib/branding.ts:13`
**Issue:** Both fetches only guard against *rejection* (`catch`) and non-2xx (`!response.ok`) — neither has a timeout. If the backend accepts the connection but never responds (slow DB, thread-pool exhaustion, etc.), `await fetch(...)` hangs indefinitely. In `proxy.ts` this stalls every route in the app (subject to platform/runtime execution limits, which would then likely surface as a hard error rather than the intended graceful fail-open); in `page.tsx` (`fetchBranding`, called with `dynamic = "force-dynamic"` on every request) this stalls the entire landing page's SSR render for every visitor. Both directly contradict the documented design intent ("um erro transitório do backend nunca deve bloquear a landing pública").
**Fix:**
```ts
const response = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(3000), headers: {...} });
```
and ensure the `catch` also handles the resulting `AbortError`/`TimeoutError` (it already will, since both are thrown/rejected errors caught by the existing generic `catch`).

### WR-04: Theme toggle uses `theme` instead of `resolvedTheme` — first click can be a visual no-op

**File:** `webpage/src/components/theme-toggle.tsx:10,17`
**Issue:** `providers.tsx` sets `defaultTheme="system"` with `enableSystem`. When a visitor hasn't made an explicit choice, `next-themes`'s `theme` value is the literal string `"system"`, not `"light"`/`"dark"` — the *actually rendered* appearance in that case is exposed separately as `resolvedTheme`. The toggle handler:
```ts
onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
```
compares `theme` (which is `"system"`, not `"dark"`) rather than `resolvedTheme`. For a visitor whose OS preference is dark: `theme === "dark"` is `false`, so the first click sets `theme` to `"dark"` — which is visually identical to what was already being shown via the system preference, so the click appears to do nothing. A second click is then needed to actually reach `"light"`. This is a well-documented `next-themes` gotcha, not a hypothetical.
**Fix:**
```ts
const { resolvedTheme, setTheme } = useTheme();
...
onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
```

### WR-05: Logo guard allows `data:image/svg+xml`, a known XSS-adjacent MIME subtype

**File:** `webpage/src/components/brand-mark.tsx:14`
**Issue:** `hasLogo` only checks `logoDataUrl.startsWith("data:image/")`, which admits `data:image/svg+xml`. SVG is the one `image/*` subtype that can embed `<script>`/event handlers. Current mainstream browsers do not execute such content when the SVG is loaded via a plain `<img src>` (it's decoded in image context, not document/script context), and this phase's own threat model (`99-CONTEXT.md` T-99-02) already reasons this through soundly, including the mitigating fact that this data is admin-supplied (via `/setup`), not visitor-supplied. Given that, this is a hardening suggestion rather than a proven exploit path — but it's a cheap, concrete tightening of a control the team is explicitly relying on.
**Fix:** Narrow the allowlist to the actual raster/vector formats you intend to support, e.g.:
```ts
const hasLogo = typeof logoDataUrl === "string" && /^data:image\/(png|jpe?g|gif|webp);base64,/.test(logoDataUrl);
```

## Info

### IN-01: `fetchBranding()` casts the response JSON without runtime validation

**File:** `webpage/src/lib/branding.ts:21-22`
**Issue:** `(await response.json()) as BrandingResponse` trusts the shape completely. `data.nome || "LexCV"` means that if `nome` were ever a non-array truthy object (contract drift, a future backend bug, a misbehaving intermediary), `<span>{nome}</span>` in `brand-mark.tsx` would throw ("Objects are not valid as a React child"), crashing the SSR render for every visitor — the exact outcome this code otherwise goes to lengths to avoid. Low likelihood today since Phase 98's backend DTO declares `nome`/`logoDataUrl` as plain Java `String` fields, but cheap to close.
**Fix:** Add a minimal runtime guard before use, e.g. `typeof data?.nome === "string" ? data.nome : "LexCV"`.

### IN-02: `proxy.ts`'s `catch` swallows every error silently, with no logging

**File:** `webpage/proxy.ts:16-18`
**Issue:** The `catch {}` block has no logging of any kind. This is precisely why CR-01 can ship unnoticed: there is no server-side signal distinguishing "backend genuinely down" from "this code has a bug that always throws." Fail-open is the right behavior either way, but silence is not.
**Fix:** `console.error("[proxy] setup-status check failed, failing open:", err)` (capture `err` in the catch) before `return NextResponse.next();`.

### IN-03: Tenant logo `<img>` uses `alt=""`

**File:** `webpage/src/components/brand-mark.tsx:24`
**Issue:** The logo conveys the institution's identity, not decoration, so an empty `alt` hides that information from screen-reader users even though the adjacent `<span>{nome}</span>` is visually present.
**Fix:** `alt={nome || "Logótipo"}` (or similarly derive it from `nome`).

---

_Reviewed: 2026-07-15T11:20:47Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
