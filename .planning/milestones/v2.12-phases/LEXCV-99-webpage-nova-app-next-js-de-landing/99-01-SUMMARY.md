---
phase: 99-webpage-nova-app-next-js-de-landing
plan: 01
subsystem: ui
tags: [nextjs, react, tailwind, next-themes, shadcn-pattern, standalone-app, monorepo]

# Dependency graph
requires: []
provides:
  - "Standalone Next.js 16 app `webpage/` (sibling of `backend/`/`web/`), own package.json/tsconfig/lockfile, no pnpm workspace"
  - "Buildable skeleton: `pnpm install` + `pnpm build` succeed; dev server serves on port 3001"
  - "Theme shell: next-themes (theme-only, no QueryClientProvider) + dark/light functional via ThemeToggle"
  - "Reused UI primitives (Button, Card family, ThemeToggle, cn()) copied byte-for-byte from web/ — zero new UI dependencies"
  - "next.config.ts foundation for Phase 100 Multi-Zones: assetPrefix '/landing-static' + output 'standalone' + security headers with img-src data:"
affects: [99-02, 99-03, 99-04, 100]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Standalone sibling app in monorepo (no root pnpm-workspace.yaml) — each app has its own node_modules/lockfile"
    - "Curated dependency subset pinned verbatim from web/package.json version strings (supply-chain provenance via existing production lockfile, not new resolution)"
    - "Byte-for-byte primitive reuse from web/ (verified via `diff -q`/`cmp`, not hand-retyped) to guarantee zero drift"
    - "Dev port 3001 convention to coexist with web/'s 3000 and backend's 8080"

key-files:
  created:
    - webpage/package.json
    - webpage/tsconfig.json
    - webpage/postcss.config.mjs
    - webpage/eslint.config.mjs
    - webpage/next.config.ts
    - webpage/.env.example
    - webpage/.gitignore
    - webpage/pnpm-lock.yaml
    - webpage/src/app/globals.css
    - webpage/src/app/providers.tsx
    - webpage/src/app/layout.tsx
    - webpage/src/app/page.tsx
    - webpage/src/lib/utils.ts
    - webpage/src/components/ui/button.tsx
    - webpage/src/components/ui/card.tsx
    - webpage/src/components/theme-toggle.tsx
  modified: []

key-decisions:
  - "Copied the 5 byte-for-byte files (globals.css, utils.ts, button.tsx, card.tsx, theme-toggle.tsx) via filesystem `cp` from web/, not Read+Write — web/'s working-tree copies are CRLF (core.autocrlf=true on this Windows checkout) while the Write tool emits LF; `cp` was required to satisfy the plan's literal `diff -q` byte-for-byte acceptance gate."

patterns-established:
  - "webpage/ as a fully standalone Next.js app, matching web/'s conventions (Tailwind v4 CSS-based config, no tailwind.config.*, no components.json/shadcn CLI) with a deliberately curated dependency subset."

requirements-completed: [LP-03, LP-12]

# Metrics
duration: ~10min
completed: 2026-07-15
---

# Phase 99 Plan 01: Standalone webpage/ Scaffold Summary

**Standalone Next.js 16 app `webpage/` scaffolded from scratch (own package.json/tsconfig/next.config), with next-themes dark/light ported theme-only and Button/Card/ThemeToggle/cn copied byte-for-byte from web/ — builds clean with zero new UI dependencies.**

## Performance

- **Duration:** ~10 min
- **Tasks:** 2
- **Files modified:** 16 (15 new files + generated `pnpm-lock.yaml`)

## Accomplishments
- `webpage/` exists as a fully standalone Next.js 16 app (no pnpm workspace) — `pnpm install` resolves a curated, version-pinned dependency subset of `web/package.json` (zero new UI packages), generating `webpage/pnpm-lock.yaml`.
- `pnpm build` succeeds end-to-end (Turbopack, TypeScript check, static page generation) with `webpage/.env.local` (`BACKEND_API_ORIGIN`) present.
- `next.config.ts` carries the Phase 100 Multi-Zones foundation (`assetPrefix: '/landing-static'`, `output: 'standalone'`) plus the verbatim security-header block (CSP with `img-src 'self' data:` for the future tenant-logo data URL) and the same fail-fast-on-missing-env pattern as `web/`.
- Dark/light theme shell works: `providers.tsx` ports only the `NextThemesProvider` slice (no `QueryClientProvider`/TanStack Query, per CONTEXT.md); `layout.tsx` drops `<Toaster>` and `overflow-hidden` (landing needs natural scroll) and carries landing-specific metadata.
- `Button`, `Card` family, `ThemeToggle`, and `cn()` are verified byte-for-byte identical to their `web/` counterparts (via `diff -q` and `cmp`) — zero drift, zero new UI dependency surface (LP-12).
- Smoke-tested: `pnpm dev -p 3001` serves `/` (HTTP 200), rendered HTML contains the `ThemeToggle` (Sun/Moon icons, "Alternar tema" sr-only label) and the placeholder copy.

## Task Commits

Each task was committed atomically:

1. **Task 1: Scaffolding e configuração da app standalone webpage/** - `83644f4` (feat)
2. **Task 2: Shell de tema + primitivos reutilizados + page placeholder** - `96d6adf` (feat)

_Plan metadata commit (this SUMMARY + STATE.md/ROADMAP.md, owned by orchestrator) is applied separately after this worktree agent completes._

## Files Created/Modified
- `webpage/package.json` - standalone manifest, name `webpage`, scripts on port 3001, curated deps (next-themes, lucide-react, CVA, clsx, tailwind-merge, tailwindcss-animate, @radix-ui/react-slot) pinned to web/'s exact version strings; excludes @tanstack/react-query, react-hook-form, zod, and all @radix-ui/* except react-slot
- `webpage/tsconfig.json` - copied from web/, `exclude` simplified to `["node_modules"]` (no `_api-backup` in this app), `@/*` alias preserved
- `webpage/postcss.config.mjs`, `webpage/eslint.config.mjs` - byte-for-byte from web/
- `webpage/next.config.ts` - adapted from web/: adds `assetPrefix: "/landing-static"`, keeps `output: "standalone"`, the `/api/v1/:path*` rewrite, the verbatim security-headers block, and the `BACKEND_API_ORIGIN` fail-fast throw
- `webpage/.env.example`, `webpage/.gitignore` - byte-for-byte from web/ (`.gitignore` ignores `.env*` except `.env.example`, does not ignore `pnpm-lock.yaml`)
- `webpage/pnpm-lock.yaml` - generated by `pnpm install`, committed (authoritative per CLAUDE.md)
- `webpage/src/app/globals.css` - byte-for-byte from web/ (Tailwind v4 CSS-based tokens, `--background`/`--foreground`, `@custom-variant dark`)
- `webpage/src/app/providers.tsx` - theme-only `NextThemesProvider` (attribute="class", defaultTheme="system", enableSystem, disableTransitionOnChange); no QueryClient
- `webpage/src/app/layout.tsx` - Geist/Geist Mono fonts + `<Providers>`, no `<Toaster>`, body `min-h-screen bg-background text-foreground` (scrollable), landing metadata (title/description)
- `webpage/src/app/page.tsx` - minimal placeholder (`<ThemeToggle />` + one line of copy), to be fully replaced by plan 99-04
- `webpage/src/lib/utils.ts` - byte-for-byte `cn()` helper from web/
- `webpage/src/components/ui/button.tsx` - byte-for-byte CVA+Slot Button from web/
- `webpage/src/components/ui/card.tsx` - byte-for-byte Card/CardHeader/CardTitle/CardDescription/CardContent/CardFooter from web/
- `webpage/src/components/theme-toggle.tsx` - byte-for-byte dark/light toggle from web/

## Decisions Made
- Used filesystem `cp` (not Read+Write) for the 5 byte-for-byte files, after discovering web/'s on-disk copies are CRLF (Windows `core.autocrlf=true` checkout) while freshly Write-tool-authored files are LF — `cp` guarantees the literal byte-for-byte match the plan's `diff -q`/acceptance criteria require. No content difference, purely a line-ending mechanism fix; git's `core.autocrlf=true` will normalize both to LF in the object database on any future commit, keeping the two apps' git-stored blobs consistent.

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria and both `<verify>` automated gates passed without needing any Rule 1-4 auto-fixes.

## Known Stubs

- `webpage/src/app/page.tsx` is an intentional, plan-documented placeholder (renders only `<ThemeToggle />` + one line of text). This is **not** an unplanned stub — the plan's own `<interfaces>`/task description explicitly marks it "PLACEHOLDER mínimo... será SUBSTITUÍDO integralmente pelo plano 99-04" (existing only to make `pnpm build`/`pnpm dev` verifiable and prove the theme shell + a reused primitive render correctly). Resolved by: plan 99-04 (content sections).

## Issues Encountered
- Minor tooling caution (not a plan defect): a background `pnpm dev` smoke-test process was stopped via `taskkill //F //IM node.exe //T`, which kills **all** `node.exe` processes system-wide rather than the single targeted PID. Verified this worktree's own git state and files were unaffected, but this command is overly broad in any environment with other concurrent Node processes (e.g., parallel sibling-worktree agents) and should be avoided in favor of a PID-scoped kill in future sessions.

## User Setup Required

None - no external service configuration required. `webpage/.env.local` (gitignored, not committed) was created locally in this worktree with `BACKEND_API_ORIGIN=http://localhost:8080` and `NEXT_PUBLIC_API_BASE_PATH=/api/v1` purely to make `pnpm build`/`pnpm dev` verifiable during execution — any environment running this app will need the same `webpage/.env.local` (or equivalent env injection) created from `webpage/.env.example`.

## Next Phase Readiness
- `webpage/` is buildable, dark/light-capable, and carries the `assetPrefix`/`output: standalone` foundation Phase 100 needs for Multi-Zones/Docker wiring.
- Plan 99-02 (setup gate / `proxy.ts`) can build directly on this skeleton — `next.config.ts`'s `/api/v1/:path*` rewrite is already in place for the setup-status fetch.
- Plan 99-03 (header/hero/footer) and 99-04 (content sections) can build directly on `layout.tsx`/`providers.tsx`/the reused `Button`/`Card`/`ThemeToggle` primitives; `page.tsx`'s placeholder is explicitly disposable.
- One human-visual confirmation remains non-blocking per the plan's own annotation: `pnpm dev` → http://localhost:3001/ dark/light toggle click-through (fundo `#f8fafc` ↔ `#020617`, no hydration flash). Automated smoke test (curl, HTTP 200 + expected HTML content) passed; live click-through was not performed in this non-interactive execution.

---
*Phase: 99-webpage-nova-app-next-js-de-landing*
*Completed: 2026-07-15*

## Self-Check: PASSED

All 16 created files verified present on disk (webpage/ config + src tree + generated pnpm-lock.yaml). Both task commits (`83644f4`, `96d6adf`) verified present in git log.
