---
phase: 99-webpage-nova-app-next-js-de-landing
plan: 02
subsystem: ui
tags: [nextjs, edge-proxy, react-server-components, fetch, xss-hardening, multi-tenant-branding]

# Dependency graph
requires:
  - phase: 99-01
    provides: "Standalone webpage/ app skeleton (package.json/tsconfig/next.config), cn()/utils.ts, buildable pnpm build"
provides:
  - "webpage/proxy.ts — Edge gate containing ONLY the not-initialized→/setup branch, zero authentication logic, fail-open on backend error"
  - "webpage/src/lib/setup.ts — fetchSetupStatus(), byte-for-byte identical to web/src/lib/setup.ts"
  - "webpage/src/lib/branding.ts — fetchBranding() server-to-server against BACKEND_API_ORIGIN + /api/v1/public/branding, cache no-store, dual fail-open path"
  - "webpage/src/components/brand-mark.tsx — Server Component brand mark (logo+name), XSS-hardened data:image/ guard, Building2 fallback"
  - "webpage/src/types/setup.ts, webpage/src/types/branding.ts — response types matching Phase 98's real DTO shape"
affects: [99-03, 99-04, 100]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "proxy.ts adapted (not copied) from web/proxy.ts: surgically strips every authentication branch (access_token/LOGIN_PATH/DASHBOARD_PATH), keeping only the public setup-gate concern"
    - "Server-to-server fetch (absolute BACKEND_API_ORIGIN, never a relative URL) from a lib consumed by a Server Component — closes CORS entirely, browser never makes the call"
    - "Fail-open on both routing (proxy.ts catch → NextResponse.next()) and data (branding.ts !ok/catch → {nome:'LexCV', logoDataUrl:null}) — availability-over-strictness tradeoff, named as an accepted risk in the plan's threat model (T-99-03)"
    - "XSS-hardened conditional render: data:image/ prefix guard before ever using an untrusted string as <img src>, mirroring dashboard-shell.tsx's existing fallback pattern"

key-files:
  created:
    - webpage/proxy.ts
    - webpage/src/lib/setup.ts
    - webpage/src/types/setup.ts
    - webpage/src/lib/branding.ts
    - webpage/src/types/branding.ts
    - webpage/src/components/brand-mark.tsx
  modified: []

key-decisions:
  - "webpage/src/lib/setup.ts copied via filesystem cp (not Read+Write) to guarantee byte-for-byte match against web/'s CRLF working-tree copy, per the plan's literal `diff -q` acceptance gate — same mechanism 99-01 established for its 5 reused files."
  - "brand-mark.tsx uses a raw <img> (not next/image) per the plan's locked interface, mirroring dashboard-shell.tsx's own established pattern for base64 data-URL logos; produces one accepted, non-blocking `@next/next/no-img-element` ESLint warning consistent with that reused precedent — not treated as a bug to fix."

patterns-established:
  - "When adapting a shared web/ source file that mixes auth+setup concerns, webpage/ never ports the auth branch literally — extraction is surgical (setup-gate branch only), confirmed by grep-negative acceptance gates (no access_token/login/dashboard anywhere in the file)."

requirements-completed: [LP-04, LP-05, LP-06]

# Metrics
duration: ~15min
completed: 2026-07-15
---

# Phase 99 Plan 02: Setup Gate & Branding Fetch Summary

**Edge `proxy.ts` strips web/'s entire authentication branch (setup-gate only, fail-open); `fetchBranding()` calls the Phase 98 endpoint server-to-server with cache `no-store` and a `{nome:"LexCV"}` fail-open fallback; `BrandMark` hardens the `data:image/` render path against XSS.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 2
- **Files modified:** 6 (all new)

## Accomplishments
- `webpage/proxy.ts` lives at the app root (Next.js 16 convention, confirmed against `webpage/node_modules/next/dist/docs/.../proxy.md`), contains only the "not initialized → `/setup`" branch; grep-confirmed absence of `access_token`/`login`/`DASHBOARD`; `catch` fails open via `NextResponse.next()`; matcher excludes `landing-static` (LP-04, LP-05).
- `webpage/src/lib/setup.ts` is byte-for-byte identical (`diff -q`) to `web/src/lib/setup.ts` — reuses the exact, already-proven `fetchSetupStatus()` mechanism running in production today.
- `webpage/src/lib/branding.ts` calls `BACKEND_API_ORIGIN` + `/api/v1/public/branding` server-to-server (`cache: "no-store"`), with two independent fail-open paths (`!response.ok` and `catch`) both returning `{nome: "LexCV", logoDataUrl: null}` — zero CORS exposure, zero crash surface for an anonymous visitor (LP-06).
- `webpage/src/components/brand-mark.tsx` is a Server Component (no `"use client"`) that only renders `<img>` when `logoDataUrl` starts with `"data:image/"`; otherwise falls back to `Building2` + name, mirroring `dashboard-shell.tsx`'s established pattern; zero `dangerouslySetInnerHTML`.
- `pnpm build` (Turbopack) passes cleanly with all 6 new files present; cross-checked the server chunk's sourcemap to confirm `proxy.ts`'s `SETUP_PATH` constant is genuinely compiled into the middleware bundle (not silently dropped, despite `middleware-manifest.json` showing an empty top-level object — a Turbopack manifest-format quirk, not a build defect).

## Task Commits

Each task was committed atomically:

1. **Task 1: Setup-status gate (proxy.ts sem ramo de autenticação)** - `6129565` (feat)
2. **Task 2: Branding fetch server-to-server + componente BrandMark** - `5068c86` (feat)

_Plan metadata commit (this SUMMARY, owned by orchestrator) is applied separately after this worktree agent completes._

## Files Created/Modified
- `webpage/proxy.ts` - Edge proxy, only-setup-gate + fail-open, matcher excludes `landing-static`
- `webpage/src/lib/setup.ts` - `fetchSetupStatus()`, byte-for-byte copy from `web/` (`cp`, not Write, for CRLF parity)
- `webpage/src/types/setup.ts` - `SetupStatusResponse` type
- `webpage/src/lib/branding.ts` - `fetchBranding()`, server-to-server against `BACKEND_API_ORIGIN`, cache `no-store`, dual fail-open path
- `webpage/src/types/branding.ts` - `BrandingResponse` type (`{nome, logoDataUrl: string|null}`), matches Phase 98's real DTO shape
- `webpage/src/components/brand-mark.tsx` - Server Component brand mark, `data:image/`-guarded `<img>`, `Building2`+name fallback

## Decisions Made
- Used `cp` (filesystem copy) for `webpage/src/lib/setup.ts` instead of Read+Write, replicating the exact reasoning documented in 99-01's SUMMARY (`web/`'s working-tree files are CRLF; the Write tool emits LF; the plan's `diff -q` gate requires a literal byte match).
- Confirmed the Next.js 16 `proxy.ts` file convention (function name `proxy`, `config.matcher`, root-level placement, Node.js runtime default) directly against `webpage/node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md` rather than assuming, per the project's Next.js 16 caveat (`CLAUDE.md`) — matched the plan's `<interfaces>` block exactly.

## Deviations from Plan

None - plan executed exactly as written. All acceptance criteria, both per-task `<verify>` automated gates, and all 6 plan-level `<verification>` items passed without needing any Rule 1-4 auto-fixes.

## Issues Encountered
- This worktree had no `node_modules` for either `web/` or `webpage/` at spawn time (a fresh worktree branched from the post-wave-1 merge commit, not a continuation of the wave-1 execution worktree) — ran `pnpm install` in `webpage/` before any build verification could run; the existing committed `pnpm-lock.yaml` (from 99-01) was respected with zero modification.
- `webpage/.env.local` (gitignored) did not exist in this worktree either — recreated locally (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`) purely to make `pnpm build` verifiable, matching 99-01's documented approach. Not committed.
- `.next/server/middleware-manifest.json` showed an empty `{}` functions/middleware object after build, which looked suspicious at first glance; cross-checked against the server chunk's sourcemap (`grep SETUP_PATH` in `.next/server/chunks/[root-of-the-server]__*.js.map`) and confirmed `proxy.ts`'s logic is genuinely compiled into the bundle. No code change needed; documented here for the next agent's awareness so this doesn't get mistaken for a real defect later.

## User Setup Required

None - no external service configuration required. `webpage/.env.local` (gitignored, not committed) was recreated locally in this worktree purely to make `pnpm build` verifiable during execution — same requirement already documented by `99-01-SUMMARY.md` (any environment running this app needs `BACKEND_API_ORIGIN` + `NEXT_PUBLIC_API_BASE_PATH` from `webpage/.env.example`).

## Next Phase Readiness
- `webpage/proxy.ts` and the branding/setup libs are ready for 99-03 (header/hero/footer) and 99-04 (content sections) to consume. `BrandMark` specifically is built exactly for those two plans to import into the sticky header and footer, per this plan's own objective ("consumido por header/hero/footer no plano 99-03/99-04") — it is intentionally not wired into any page yet in this plan (mirrors how 99-01 left `page.tsx` as an explicit, plan-documented placeholder for 99-04). Not a stub: nothing in this plan's scope required page composition, and no user-visible half-built state was introduced (the running app's `page.tsx` is unchanged from 99-01).
- One item remains non-blocking, matching the plan's own `<human-check>` annotation on Task 2: live integration confirmation of `fetchBranding()` against a running backend (both an initialized-tenant-with-real-name path and a backend-down path) was not run in this non-interactive execution — it requires 99-03/99-04 to actually call `fetchBranding()` from `page.tsx`, or a throwaway Server Component wired to a running backend, neither of which exists yet at this plan's scope boundary.
- No blockers for 99-03/99-04.

---
*Phase: 99-webpage-nova-app-next-js-de-landing*
*Completed: 2026-07-15*

## Self-Check: PASSED

All 6 created files verified present on disk (webpage/proxy.ts, webpage/src/lib/setup.ts, webpage/src/types/setup.ts, webpage/src/lib/branding.ts, webpage/src/types/branding.ts, webpage/src/components/brand-mark.tsx). Both task commits (`6129565`, `5068c86`) verified present in git log.
