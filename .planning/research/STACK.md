# Stack Research

**Domain:** Second standalone Next.js app (public marketing/landing page) sharing a domain, backend, and Docker/Caddy topology with an existing Next.js app
**Researched:** 2026-07-15
**Confidence:** HIGH (grounded in this repo's actual pinned versions + Next.js 16.2.6's own bundled docs, which is the exact version installed in `web/node_modules`)

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Next.js | `16.2.6` (exact, no `^`) | App Router server + static rendering for `webpage/` | Match `web/package.json`'s exact pin. `web/AGENTS.md` already flags this major version as having training-data-breaking changes (`proxy.ts` replacing `middleware.ts`, etc.) — running two *different* Next.js versions in the same repo means tracking two sets of those gotchas independently. One pinned version, one set of quirks, already verified working in this repo. |
| React / React DOM | `19.2.4` (exact, no `^`) | UI runtime | Required peer for Next 16.2.6; match `web/`'s exact pin for the same reason as above. |
| TypeScript | `^5` | Type safety | Match `web/`'s devDependency; `strict: true` in `tsconfig.json` is the established convention. |
| Tailwind CSS | `^4` + `@tailwindcss/postcss ^4` | Styling | Match `web/`. Tailwind v4 is CSS-first (`@import "tailwindcss"` + `@theme` block in `globals.css`) — **no `tailwind.config.ts` file needed**, confirmed by `web/src/app/globals.css` having no companion config file. |
| **Next.js Multi-Zones** (`assetPrefix` config, built into Next.js core since v15) | n/a (built-in) | Prevents `webpage/`'s `_next/static/*` asset requests from colliding with `web/`'s, when both are reverse-proxied under the same domain | This is the officially documented, Vercel-maintained pattern for "multiple independently-deployed Next.js apps under one domain" — exactly this milestone's topology. Verified directly against the bundled docs at `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md` (Next 16.2.6, the version actually installed in this repo). **This is not optional** — without it, the landing page's own JS/CSS chunks will be silently routed to the wrong container by Caddy's catch-all `handle {}` block (see Stack Patterns / Version Compatibility below for the concrete config). |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@radix-ui/react-slot` | `^1.2.3` | Powers `Button`'s `asChild` prop | Only if the "Entrar" CTA renders as `<Button asChild><Link href="/login">…</Link></Button>` (recommended — same pattern already used in `web/`). |
| `class-variance-authority` | `^0.7.1` | Variant styling for `Button`/`Badge` | Copy alongside the `button.tsx`/`badge.tsx` files from `web/src/components/ui/`. |
| `clsx` + `tailwind-merge` | `^2.1.1` / `^3.3.1` | `cn()` utility (`web/src/lib/utils.ts`) | Copy `utils.ts` verbatim — every shadcn-style primitive depends on it. |
| `lucide-react` | `^0.543.0` | Icons for feature/module cards, trust badges | Same icon set as `web/`, avoids a second icon library shipping duplicate SVGs. |
| `next-themes` | `^0.4.6` | Dark/light mode toggle | Required per milestone scope ("reutiliza… dark/light mode já usados em `web/`"). Copy the `Providers` pattern from `web/src/app/providers.tsx`, but **drop the `QueryClientProvider` wrapper** (see "What NOT to Use"). |
| `tailwindcss-animate` | `^1.0.7` | Radix animation utility classes (`data-[state=open]:animate-in`, etc.) | **Only** if a Dialog/Sheet/Accordion is added later (e.g., a demo-video modal or FAQ accordion). Not needed for the four sections currently scoped (Hero, Módulos, Prova social, Contacto) — skip at v1, add if/when such a component is introduced. |
| `sharp` | latest matching Next 16 peer range | Production image optimization for `next/image` in self-hosted (non-Vercel) deployments | Add explicitly if the hero/module sections use `next/image` for local static assets. Next.js's official guidance is to install `sharp` yourself for self-hosted image optimization — Vercel's own CDN handles it automatically, but Caddy/Docker self-hosting does not. **Do not** use `next/image` for the tenant `logoDataUrl` (see "What NOT to Use"). |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| pnpm (via `corepack enable && corepack prepare pnpm@latest --activate`) | Package manager | Identical to `web/Dockerfile` stage 1 — `webpage/` gets its **own** `pnpm-lock.yaml`, not a shared one (see Alternatives Considered). |
| Docker multi-stage build (`node:22-alpine`: `deps` → `builder` → `runner`) | Container image | Copy `web/Dockerfile` verbatim into `webpage/Dockerfile`, only changing build ARG defaults if needed. Confirmed working pattern (`output: 'standalone'` + non-root `appuser` + `node server.js` entrypoint). |
| GitHub Actions (`docker/build-push-action@v6`) | CI image build/push to GHCR | Add a third `Build and push webpage image` step in `.github/workflows/deploy.yml`, alongside the existing backend/frontend steps; add a `pnpm-cache` step keyed on `hashFiles('webpage/pnpm-lock.yaml')`. |
| Caddy 2 (`caddy:2-alpine`, already in the compose topology) | Reverse proxy / automatic HTTPS | No new tool — but the **routing rules** need concrete changes in 3 separate files (see Stack Patterns by Variant). |
| ESLint (`^9` + `eslint-config-next: 16.2.6`) | Lint | Match `web/`'s exact `eslint-config-next` pin (must equal the `next` version). |

## Installation

```bash
# From webpage/ (new sibling directory to backend/ and web/)
corepack enable
corepack prepare pnpm@latest --activate

# Core
pnpm add next@16.2.6 react@19.2.4 react-dom@19.2.4 next-themes@^0.4.6 \
  @radix-ui/react-slot@^1.2.3 class-variance-authority@^0.7.1 \
  clsx@^2.1.1 tailwind-merge@^3.3.1 lucide-react@^0.543.0

# Dev dependencies
pnpm add -D typescript@^5 @types/node@^20 @types/react@^19 @types/react-dom@^19 \
  eslint@^9 eslint-config-next@16.2.6 tailwindcss@^4 @tailwindcss/postcss@^4

# Only if a future interactive component (Dialog/Accordion) is added:
# pnpm add tailwindcss-animate@^1.0.7 @radix-ui/react-accordion@<matching-radix-minor>

# Only if next/image is used for hero/static assets (self-hosted optimization):
# pnpm add sharp
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| Fully independent `webpage/package.json` + own `pnpm-lock.yaml`/`node_modules` | pnpm workspaces / Turborepo / Nx monorepo tooling | Only if a **third** frontend app appears, or if `web/` and `webpage/` need to share a non-trivial amount of *runtime* code (not just a handful of UI primitive files). This repo has **zero** JS workspace tooling today (no root `package.json`, no `pnpm-workspace.yaml` — confirmed by directory scan) and both `web/`'s CI cache key and Docker build `context` are already keyed on `web/` being a standalone project root. Introducing a workspace tool now would force a lockfile/CI/Docker-context migration for `web/` too, for a benefit (deduping ~5 small `.tsx` files) that doesn't justify the blast radius. Precedent in this repo already favors copy-paste over sharing: `sheet.tsx` was hand-written rather than pulled via the shadcn CLI (Key Decisions log, v2.3). |
| Next.js Multi-Zones via **Caddy path routing** (`handle /` + `handle /landing-static/*` → `webpage`, catch-all → `frontend`) | Next.js Multi-Zones via **`rewrites()` in one of the Next apps** (the doc's other supported option) | Only if Caddy were removed from the stack. Since Caddy already terminates TLS and does path-based routing for `/api/*` and `/minio-console*`, adding two more `handle` blocks is strictly less new surface area than adding a rewrite proxy hop inside `web/`'s own `next.config.ts` (which would also require `web/` to know about `webpage/`'s existence — an unwanted coupling). |
| Server Component `fetch()` for the tenant branding endpoint (no client library) | `@tanstack/react-query` (already used in `web/`) | Only if the landing page needs **client-side** re-fetching/polling of branding data after initial load (it doesn't — logo/name changes are rare admin actions, not something a visitor's browser needs to observe live). A landing page's core value (fast TTFB, good SEO) is better served by zero extra client JS. |
| Plain `<img>` (or `next/image` with `unoptimized`) for `logoDataUrl` | `next/image` default optimizer | `logoDataUrl` is a base64 `data:` URI (per the new public endpoint's contract) — Next's built-in image optimizer is designed for remote/local file URLs, not embedded data URIs; forcing it through the optimizer adds no benefit and has known rough edges with `data:` sources. |
| Hardcoded JSX sections (Hero/Módulos/Prova social/Contacto) | Headless CMS (Contentful, Sanity, Storyblok) | Only if non-technical staff need to edit landing copy without a deploy. Out of scope per milestone ("no new CMS/framework") — the page personalizes only two dynamic fields (tenant name + logo) via the existing narrow backend endpoint; everything else is static marketing copy that changes at the same cadence as code. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `output: 'export'` (fully static export) | Static export disables `proxy.ts` (Next 16's middleware-equivalent) and dynamic per-request `fetch()` — both of which this app needs (setup-status redirect gate + tenant branding fetch). | `output: 'standalone'`, identical to `web/next.config.ts`. Keeps the same Docker 3-stage pattern working unmodified. |
| `@tanstack/react-query`, `react-hook-form`, `zod`, `@hookform/resolvers` in `webpage/` | The current scope has no client-managed form state or client-refetched data — the CTA is a plain link to `/login`, and branding is a one-shot Server Component fetch. Adding these ships client JS bytes for zero behavioral benefit and duplicates a dependency tree already present in `web/`. | Native `fetch()` inside an `async` Server Component for the branding call; plain `<a>`/`next/link` for CTAs. Re-introduce RHF+Zod later, matching `web/`'s exact versions, **only if** a real "Pedir demonstração" lead-capture form is built (currently out of scope — the section is informational/CTA-only per `PROJECT.md`). |
| `npx shadcn init` / a `components.json` config | `web/` has **no** `components.json` — every shadcn-style primitive there was hand-written/copied (explicit Key Decision: `sheet.tsx` was manually authored "sem CLI interativo"). Running the CLI in `webpage/` would introduce a config file and tooling convention that doesn't exist anywhere else in this repo. | Copy the specific `.tsx` files needed (`button.tsx`, `card.tsx`, `badge.tsx`, `utils.ts`) from `web/src/components/ui/` and `web/src/lib/` directly into `webpage/`. |
| A brand-new/second monorepo tool, CMS, or component library | Explicitly out of scope for this milestone; also unjustified at 2-frontend-app scale (see Alternatives Considered). | Independent app + manual file copy, as above. |
| Skipping `assetPrefix` on `webpage/next.config.ts` | Without it, `webpage/`'s own `_next/static/*` chunk requests fall through to Caddy's default catch-all block (currently `reverse_proxy frontend:3000`), which serves `web/`'s JS/CSS instead of `webpage/`'s — landing page renders with **no working styles/scripts** in production while looking fine in local dev (single-container testing hides the collision). | Set `assetPrefix: '/landing-static'` (or similar) in `webpage/next.config.ts`, and add a matching `handle /landing-static/* { reverse_proxy webpage:3000 }` block to **all three** Caddy config locations (see Version Compatibility). |
| Assuming one Caddyfile edit is sufficient | This repo currently has **three** places Caddy routing is defined: `Caddyfile` (local dev), `Caddyfile.prod` (referenced by `docker-compose.prod.yml` as a mounted volume), and an **inline heredoc Caddyfile** embedded directly in `docker-compose.hostinger.yml`'s `caddy` service `entrypoint` (added in commit `67e2120` specifically to dodge a Compose brace-expansion bug — this is the one actually driving the live `alcv.tech` deployment). Editing only `Caddyfile.prod` would silently not affect production. | Update all three in the same change: `Caddyfile`, `Caddyfile.prod`, and the inline `command`/`entrypoint` string in `docker-compose.hostinger.yml`. |

## Stack Patterns by Variant

**If reusing `web/`'s shadcn/ui primitives (Button, Card, Badge):**
- Copy the `.tsx` files directly (not via CLI); copy `cn()` from `web/src/lib/utils.ts`.
- Because this repo's own convention is manual authorship over CLI-managed component libraries (no `components.json` exists anywhere).

**If serving `webpage/` at bare `/` on the same domain as `web/`'s existing routes:**
- Set `assetPrefix: '/landing-static'` in `webpage/next.config.ts` (Next.js 15+ requires no additional manual rewrite for this — Next serves the prefixed asset path automatically).
- Add two Caddy `handle` blocks *before* the existing catch-all, in this order: `handle /api/*` (unchanged) → `handle /landing-static/* { reverse_proxy webpage:3000 }` → `handle / { reverse_proxy webpage:3000 }` (exact-match, not a prefix — Caddy path matchers without a trailing `*` match the literal path only) → `handle { reverse_proxy frontend:3000 }` (unchanged catch-all, now only reached for everything else: `/login`, `/dashboard`, `/setup`, etc.).
- Consider also routing `/favicon.ico`, `/robots.txt`, `/sitemap.xml` (Next.js file-convention routes, zero extra deps — `robots.ts`/`sitemap.ts` in `webpage/src/app/`) to `webpage:3000` so crawlers hitting the bare domain get the marketing site's SEO files rather than the dashboard app's.
- Because Caddy's `handle` directive performs mutually-exclusive, first-match routing — omitting this ordering/asset rule means the two independently-built Next.js apps' `_next/static/*` paths collide under one catch-all.

**If the branding endpoint or setup-status check needs calling from `webpage/`'s `proxy.ts` (Edge middleware) *and* from a Server Component:**
- Reuse the exact `BACKEND_API_ORIGIN` (Docker build ARG/ENV) + `NEXT_PUBLIC_API_BASE_PATH=/api/v1` (rewrite target) pair already used in `web/next.config.ts` and `web/Dockerfile`, so `webpage/next.config.ts` needs the identical `rewrites()` block proxying `/api/v1/:path*` to the same `backend:8080` container.
- Because `web/src/lib/setup.ts`'s `fetchSetupStatus()` already proves this exact pattern (relative `fetch("/api/v1/setup/status")` resolved through the Next rewrite, called from both `proxy.ts` and elsewhere) works correctly in this Next.js version/deployment — no need to invent a different mechanism for the second app.

**If a real "Pedir demonstração" lead-capture form is added later (currently out of scope):**
- Add `react-hook-form@^7.62.0`, `zod@^4.1.5`, `@hookform/resolvers@^5.2.2` — matching `web/`'s exact versions — plus a new narrow `POST /api/v1/public/...` backend endpoint (rate-limited, since it would be unauthenticated).
- Because introducing a second, differently-versioned copy of the same form-handling stack across two apps in one repo is exactly the kind of divergence this research recommends avoiding elsewhere.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `next@16.2.6` | `react@19.2.4`, `react-dom@19.2.4` | Exact triplet already validated in `web/package.json`; do not mix with a different Next 16.x patch in the same repo (see Core Technologies rationale). |
| `next@16.2.6` (Multi-Zones `assetPrefix`) | Caddy `2-alpine` `handle` path matching | Confirmed via `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md`: "In versions older than Next.js 15, you may also need an additional rewrite to handle the static assets. This is no longer necessary in Next.js 15." — i.e., no extra `rewrites()` needed inside `webpage/next.config.ts` itself beyond the existing `/api/v1/*` proxy rule; only the Caddy-side `handle` block is required. |
| `eslint-config-next` | must equal installed `next` version | Match `16.2.6` exactly, same as `web/package.json`. |
| `tailwindcss@^4` | `@tailwindcss/postcss@^4` | Both required together (v4's PostCSS-plugin split); no `tailwind.config.ts` file needed — configuration lives in `globals.css` via `@theme`. |
| Current npm-published Next.js (informational only) | — | As of this research (mid-July 2026), Next.js `16.2.10` is the latest stable/LTS patch (released 2026-07-01), one minor security release ahead of this repo's pinned `16.2.6`, with another security release reportedly expected 2026-07-20. **Recommendation:** do not bump `webpage/` ahead of `web/` — if/when a patch bump happens, bump both apps together in a dedicated maintenance change, not as part of this landing-page milestone, to avoid two different Next.js patch behaviors in one repo. |

## Sources

- `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md` — Next.js 16.2.6's own bundled Multi-Zones guide (assetPrefix mechanism, routing via rewrites vs. proxy, "no rewrite needed since Next 15" caveat). HIGH confidence — read directly from the version installed in this repo.
- `web/node_modules/next/dist/docs/01-app/03-api-reference/05-config/01-next-config-js/output.md` — `output: 'standalone'` behavior and monorepo tracing-root caveats (`outputFileTracingRoot`), used to justify the independent-app recommendation. HIGH confidence.
- `web/package.json`, `web/next.config.ts`, `web/Dockerfile`, `web/tsconfig.json`, `web/postcss.config.mjs`, `web/src/app/globals.css`, `web/src/app/providers.tsx`, `web/src/app/layout.tsx`, `web/proxy.ts`, `web/src/lib/setup.ts`, `web/src/lib/api.ts`, `web/src/lib/utils.ts`, `web/src/components/ui/button.tsx`, `web/src/components/ui/card.tsx` — read directly from this repo to ground every version/pattern recommendation in what is actually pinned/used today, not assumed. HIGH confidence.
- `Caddyfile`, `Caddyfile.prod`, `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml`, `.github/workflows/deploy.yml` — read directly to map the exact 3-location Caddy config duplication and the CI/CD build-and-push pattern to extend. HIGH confidence.
- `.planning/PROJECT.md` (Current Milestone: v2.12 Landing Page section) — scope, constraints, and explicit out-of-scope items for this milestone. HIGH confidence (project's own source of truth).
- WebSearch: "Next.js latest release version July 2026" — used only to flag that `16.2.10` (2026-07-01) is ahead of this repo's pinned `16.2.6`, and that a security release was reportedly expected 2026-07-20. MEDIUM confidence (WebSearch aggregator results, not Next.js's own release notes page directly fetched) — informational footnote only, does not change the exact-match recommendation.

---
*Stack research for: standalone Next.js marketing/landing page app sharing a backend, domain, and Docker/Caddy topology with an existing Next.js app*
*Researched: 2026-07-15*
