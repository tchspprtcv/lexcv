# Architecture Research

**Domain:** Standalone marketing/landing Next.js app added to an existing multi-container monorepo (Caddy + Spring Boot + Next.js), single fixed domain, path-based routing
**Researched:** 2026-07-15
**Confidence:** HIGH (grounded in actual repo files + official Next.js "Multi-Zones" docs matching the exact installed Next version + official Caddy docs + this repo's own recent commit history for Compose-specific gotchas)

## Standard Architecture

### System Overview

Today (verified from `Caddyfile.prod`, `docker-compose.hostinger.yml`, `docker-compose.yml`):

```
                         Caddy (:80/:443, single fixed domain)
                    ┌─────────────┴─────────────┐
              handle /api/*                  handle {} (catch-all)
                    │                              │
                    ▼                              ▼
            backend:8080                    frontend:3000  (web/, Next 16)
        (Spring Boot, JWT-gated              /login, /dashboard, /setup,
         except permitAll list)              /, /_next/*, everything else
```

Target (this milestone) — insert ONE new mutually-exclusive `handle` branch, matched ONLY on the exact root path `/` plus a dedicated asset-prefix path, everything else falls through unchanged to the existing catch-all:

```
                         Caddy (:80/:443, single fixed domain)
                    ┌─────────────┬───────────────────────────┬─────────────┐
              handle /api/*   handle @webpage              handle {}   (unchanged)
                    │          (path / OR /landing-static/*)     │
                    ▼                    ▼                       ▼
            backend:8080          webpage:3000              frontend:3000
        (+ new permitAll      (NEW app, Next 16,          (web/, unchanged:
         endpoint for           serves ONLY "/",           /login, /dashboard,
         tenant branding)       its own _next assets        /setup, its own
                                under /landing-static/*)    /_next/*, etc.)
```

This is Next.js's own documented **Multi-Zones** pattern (confirmed against `nextjs.org/docs/pages/guides/multi-zones`, doc version 16.2.10 — matches this repo's installed `next@16.2.6` in `web/package.json`): "A zone is a normal Next.js application where you also configure an `assetPrefix` to avoid conflicts with pages and static files in other zones... The default application handling all paths not routed to another more specific zone does not need an `assetPrefix`." That means **`web/` needs zero changes** for this to work — only the new, more-specific `webpage/` zone needs an `assetPrefix`.

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|-----------------|-------------------------|
| `webpage/` (NEW) | Renders the public landing page at `/` only; SSR for SEO; checks setup status server-side and hard-redirects to `/setup` when uninitialized | Next.js 16 App Router, `output: 'standalone'`, `assetPrefix: '/landing-static'`, its own `proxy.ts` |
| `PublicController` (NEW, backend) | Exposes only `nome` + `logoDataUrl` for the singleton tenant, unauthenticated | Small dedicated `@RestController` under `/api/v1/public`, mirrors `SetupController`'s "narrow, dedicated, public" shape |
| Caddy (`Caddyfile`/`Caddyfile.prod`/hostinger entrypoint) | Routes `/api/*` → backend, exact `/` + `/landing-static/*` → webpage, everything else → frontend | 3rd mutually-exclusive `handle` block using a named matcher with two `path` patterns |
| `docker-compose.*` | Adds `webpage` as a 4th application service (peer of `backend`/`frontend`), on the same `lexcv_net` network | New service block per file, mirrors `frontend`'s shape |
| `.github/workflows/deploy.yml` | Builds/pushes a 3rd image (`webpage`) alongside `backend`/`frontend` | New `docker/build-push-action@v6` step, `context: ./webpage` |

## Recommended Project Structure

```
webpage/
├── Dockerfile              # 3-stage pnpm build, copy of web/Dockerfile pattern
├── package.json             # Next 16 + React 19 + Tailwind v4 + shadcn primitives (copy pnpm versions from web/package.json — no CLI needed, no components.json exists in web/ either; primitives were hand-ported there too)
├── next.config.ts           # output: standalone; assetPrefix: '/landing-static'; SAME rewrites()+headers() shape as web/next.config.ts
├── proxy.ts                 # Next 16 middleware-equivalent — ONLY the "not initialized -> redirect /setup" branch (no /setup-path branch needed, webpage never serves /setup)
├── src/
│   ├── app/
│   │   ├── layout.tsx        # copy web/src/app/layout.tsx shape (fonts, Providers if needed, globals.css)
│   │   ├── globals.css       # copy web/src/app/globals.css (same Tailwind v4 @theme tokens, dark mode via .dark class)
│   │   └── page.tsx          # Server Component: fetch branding server-side, render Hero/Módulos/Prova social/Contacto sections, "Entrar" CTA as plain <a href="/login">
│   ├── components/
│   │   ├── ui/                # ONLY the shadcn primitives actually needed (button, card) — hand-copy from web/src/components/ui/, don't re-run a CLI
│   │   └── landing/            # new: hero-section.tsx, features-section.tsx, trust-section.tsx, contact-section.tsx
│   └── lib/
│       ├── setup.ts           # duplicate of web/src/lib/setup.ts (fetchSetupStatus) — same contract, same public endpoint
│       └── branding.ts         # new: fetchTenantBranding() calling the new public endpoint
└── public/
    └── favicon.ico, og-image.png, etc. (see Known Limitation below re: Caddy catch-all)
```

### Structure Rationale

- **No client-side TanStack Query needed for branding:** unlike `web/` (an authenticated, highly interactive dashboard app where TanStack Query's cache/refetch/mutation model earns its keep), `webpage/` is a mostly-static marketing page. Fetching tenant branding in a Server Component (`async function Home()`, plain `fetch()`) is simpler, SSR's the tenant name/logo for SEO/social previews, and avoids introducing a `Providers`/`QueryClientProvider` wrapper for a single GET with no mutations.
- **`proxy.ts` only, no client-side `useEffect` check:** `web/src/app/page.tsx` today does BOTH a server-side check (`web/proxy.ts`) AND a client-side check (`useEffect` + `fetchSetupStatus()` in `page.tsx`) as defense-in-depth, because `web/page.tsx` also has to route already-authenticated users to `/dashboard` vs `/login` (an auth concern `webpage/` doesn't have). `webpage/` only needs the setup-status gate, which `proxy.ts` already covers as a real HTTP redirect — no client-side duplicate check needed.
- **Hand-copy shadcn primitives, don't invoke a CLI:** confirmed `web/` has no `components.json` and one primitive (`sheet.tsx`) was hand-written to match `dialog.tsx`'s pattern because `npx shadcn` requires an interactive prompt this environment doesn't support (see PROJECT.md Key Decisions). Same constraint applies to `webpage/`.

## Architectural Patterns

### Pattern 1: Next.js Multi-Zones via `assetPrefix` + Caddy path matcher (not `handle_path` stripping)

**What:** Give the new, path-specific zone (`webpage`) an `assetPrefix` so its `/_next/static/*` chunk requests are namespaced under a prefix (`/landing-static`) that cannot collide with `web/`'s own unprefixed `/_next/*` requests on the same domain. Route the FULL path (including the prefix) to the `webpage` container unmodified — do **not** strip the prefix with `handle_path`, because Next.js itself (confirmed Next 15+, this repo is on Next 16.2.6) natively serves assets at `{assetPrefix}/_next/...` without any additional rewrite. The official Next.js docs explicitly note: *"In versions older than Next.js 15, you may also need an additional rewrite to handle the static assets. This is no longer necessary in Next.js 15."*

**When to use:** Any time two or more independently-built Next.js apps must be reverse-proxied under the same domain with path-based routing (this is literally Next's documented use case for it).

**Trade-offs:** One extra Caddy `handle` branch and one extra Next.js config line (`assetPrefix`) — negligible cost. The one thing to get right: only the **non-default** zone needs `assetPrefix` (the existing `web/`/`frontend` catch-all needs zero changes).

**Example — `webpage/next.config.ts` (new file):**
```typescript
import type { NextConfig } from "next";

const backendOrigin = process.env.BACKEND_API_ORIGIN;
if (!backendOrigin) {
  throw new Error("BACKEND_API_ORIGIN is required");
}

const nextConfig: NextConfig = {
  output: "standalone",
  assetPrefix: "/landing-static",
  async rewrites() {
    // Only exercised in local dev when running `pnpm dev` directly (no Caddy in front).
    // In prod, Caddy's /api/* block reaches backend first and this never fires.
    return [
      { source: "/api/v1/:path*", destination: `${backendOrigin}/api/v1/:path*` },
    ];
  },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self';" },
        ],
      },
    ];
  },
};

export default nextConfig;
```

**Caddy side (all 3 files — exact diffs below in Integration Points).**

### Pattern 2: Dedicated narrow public controller (mirrors `SetupController` precedent)

**What:** A new, small, single-purpose `@RestController` whose entire surface is public by construction, rather than adding a public method to the ~1000-line `ResourceController` (which is entirely `authenticated()`-gated by default) or to `AuthController`. This repo already has exactly this precedent: `SetupController` (`/api/v1/setup/*`) is a tiny dedicated controller kept separate from everything else specifically so its `permitAll()` surface is easy to audit in one file.

**When to use:** Any time you add an unauthenticated endpoint to a backend where authentication is the default posture — isolate it so the security review only has to look at one small file, not scroll a 1000-line controller looking for a stray missing `@PreAuthorize`.

**Example — new `backend/src/main/java/com/lexcv/dtos/PublicTenantBrandingResponse.java`:**
```java
package com.lexcv.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicTenantBrandingResponse {
    private final String nome;
    private final String logoDataUrl;
    // Deliberately NOTHING else: Tenant.nif / tipoEntidade / email / telefone
    // must never reach this DTO. Adding a field here == adding an authorization gap.
}
```

**Example — new `backend/src/main/java/com/lexcv/controllers/PublicController.java`:**
```java
package com.lexcv.controllers;

import com.lexcv.dtos.PublicTenantBrandingResponse;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {
    private final TenantRepository tenantRepository;

    @GetMapping("/branding")
    public ResponseEntity<PublicTenantBrandingResponse> getBranding() {
        return tenantRepository.findFirstByOrderByCreatedAtAsc()
                .map(t -> ResponseEntity.ok(
                        PublicTenantBrandingResponse.builder()
                                .nome(t.getNome())
                                .logoDataUrl(t.getLogoDataUrl())
                                .build()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

**One-line addition to `backend/src/main/java/com/lexcv/repositories/TenantRepository.java`** (currently `public interface TenantRepository extends JpaRepository<Tenant, UUID> {}` — an empty marker interface):
```java
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findFirstByOrderByCreatedAtAsc();
}
```

This is safe because the system is structurally single-tenant per install: `SetupService.initializeSystem()` is gated by the `SystemSetting.SINGLETON_ID` row (can only run once) and is the ONLY code path that creates a `Tenant` row — confirmed by grep, `AdminController` has no tenant-creation endpoint. There will only ever be zero or one `Tenant` rows in this deployment model (matches PROJECT.md's explicit v2.12 scope note: "este deployment continua a servir uma única instituição").

**Endpoint naming:** recommend `/api/v1/public/branding` over `/api/v1/public/tenant`. The name itself is a guardrail — "tenant" invites someone later to add more `Tenant` fields "since it's the tenant endpoint anyway"; "branding" scopes the endpoint's contract to exactly what it's for.

**One-line addition to `SecurityConfig.securityFilterChain()`'s existing `permitAll()` list** (exact current list quoted from the file):
```java
.requestMatchers(
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/api/v1/setup/status",
    "/api/v1/setup/initialize",
    "/api/v1/public/branding"          // NEW
).permitAll()
```
Use the exact literal path, not a `/api/v1/public/**` wildcard — this repo's existing convention lists every public path explicitly (5 exact strings today, zero wildcards), and a wildcard would silently permit any future `/public/*` endpoint without a deliberate `SecurityConfig` review.

### Pattern 3: Cross-zone navigation must be hard, not soft

**What:** Next.js's own Multi-Zones docs are explicit: *"Links to paths in a different zone should use an `a` tag instead of the Next.js `<Link>` component... Next.js will try to prefetch and soft navigate to any relative path in `<Link>`, which will not work across zones."* `/login`, `/dashboard`, `/setup` all live in `web/`'s route manifest, not `webpage/`'s. `webpage/`'s router has zero knowledge of those paths.

**When to use:** Every outbound link/redirect FROM `webpage/` TO `web/` (the "Entrar" CTA, and the setup-status redirect).

**Trade-offs:** A full page reload instead of a soft client transition — irrelevant here since these are zone boundary crossings anyway (full asset reload is unavoidable regardless of `<a>` vs `<Link>`; using `<Link>` would just 404 first).

**Example — the CTA:**
```tsx
{/* NOT <Link href="/login"> — that's a different zone, soft-nav 404s */}
<a href="/login" className="...">Entrar</a>
```

**Example — `webpage/proxy.ts` (new file, adapted from `web/proxy.ts`'s already-proven pattern, dropped down to only the branch `webpage` needs):**
```typescript
import { NextResponse, type NextRequest } from "next/server";
import { fetchSetupStatus } from "./src/lib/setup";

export async function proxy(request: NextRequest) {
  try {
    const status = await fetchSetupStatus();
    if (!status.initialized) {
      return NextResponse.redirect(new URL("/setup", request.url));
    }
  } catch {
    return NextResponse.next();
  }
  return NextResponse.next();
}

export const config = {
  matcher: "/",
};
```
This redirect is a real HTTP 302 (not a client script), so it correctly crosses from the `webpage` zone into the `web` zone — the browser re-requests `/setup` fresh, and Caddy's catch-all routes that fresh request to `frontend:3000` as it does today. No `<Link>`/soft-nav problem exists here because `NextResponse.redirect` never was a soft navigation to begin with.

### Pattern 4: Server Component data fetch, no client Query for a public GET

**What:** `webpage/src/app/page.tsx` as an `async` Server Component calling `fetchTenantBranding()` directly (plain `fetch`, same relative-URL-resolves-via-request-origin behavior already proven by `web/proxy.ts`'s `fetchSetupStatus()` in production today).

**When to use:** Read-only, unauthenticated, low-frequency data with no client-side interactivity requirement (exactly this case). Reserve TanStack Query for `web/`'s authenticated, mutation-heavy screens where it's already standard.

**Trade-offs:** No client-side refetch/cache — irrelevant for a page whose branding only changes once, at initial `/setup`, and is expected to be effectively static afterward. If the tenant name/logo can change post-setup in some future milestone, this page will just need a revalidation strategy then (`revalidate` / `dynamic = "force-dynamic"`) — not needed for this milestone's scope.

## Data Flow

### Request Flow (production, all 3 apps live)

```
Browser → https://alcv.tech/
    ↓
Caddy: matches @webpage (path == "/") → reverse_proxy webpage:3000
    ↓
webpage's proxy.ts: fetch(relative "/api/v1/setup/status")
    → resolves against request origin → https://alcv.tech/api/v1/setup/status
    → Caddy: matches /api/* → reverse_proxy backend:8080 (SetupController, already permitAll)
    ↓
  initialized == false → NextResponse.redirect("/setup")
    → Browser re-requests https://alcv.tech/setup (full reload)
    → Caddy catch-all (unchanged) → frontend:3000 → web/'s existing /setup wizard
  initialized == true → webpage/page.tsx Server Component renders:
    → fetch(relative "/api/v1/public/branding")
    → Caddy /api/* → backend:8080 → NEW PublicController (permitAll) → {nome, logoDataUrl}
    → landing page renders Hero/Módulos/Prova social/Contacto + "Entrar" <a href="/login">
```

### Static asset flow (why `assetPrefix` matters)

```
webpage's HTML references: /landing-static/_next/static/chunks/<hash>.js
    ↓
Caddy: matches @webpage (path starts with "/landing-static/") → reverse_proxy webpage:3000
    ↓
webpage's own Next.js server (assetPrefix registered) serves it natively — no stripping needed

web/'s (frontend) HTML references: /_next/static/chunks/<hash>.js  (unprefixed, unchanged)
    ↓
Caddy catch-all (unchanged) → frontend:3000 → serves as it always has
```
These two asset namespaces (`/landing-static/_next/*` vs `/_next/*`) never collide — that is the entire point of `assetPrefix` on the non-default zone.

## Scaling Considerations

| Concern | At current scale (single institution) | If ever multi-tenant/self-service | Notes |
|---------|------------------------------------------|--------------------------------------|-------|
| Traffic | Trivial — a marketing page for one institution's own staff/prospects, not internet-scale | N/A — explicitly out of scope per PROJECT.md ("onboarding self-service multi-institituição... fora de âmbito") | Don't over-build caching/CDN for this milestone |
| Branding data freshness | Fetched per-request server-side; fine at this volume | Would need per-tenant routing (subdomain/slug) — explicitly deferred | No action needed now |
| Container footprint | `webpage` should get the smallest resource limits of the 3 app containers (it's the lightest workload) — mirror `frontend`'s `cpus: '0.5'`, `memory: 256M` in `docker-compose.prod.yml`/hostinger, or even less | N/A | Matches existing `frontend` limits already in `docker-compose.prod.yml` |

## Anti-Patterns

### Anti-Pattern 1: Exposing more than `nome`+`logoDataUrl` on the public DTO

**What people do:** Add `@Data`/`@Builder` directly on the JPA `Tenant` entity to a `ResponseEntity<Tenant>`, or add "just one more field" (e.g. `email` for a "contact us" mailto link) to the public DTO later.
**Why it's wrong:** `Tenant` also carries `nif`, `tipoEntidade`, `email`, `telefone` — PII/business data this milestone explicitly forbids exposing unauthenticated. Serializing the entity directly (or growing the DTO ad hoc) is exactly how that leaks.
**Instead:** Keep `PublicTenantBrandingResponse` a hand-built, two-field DTO (Pattern 2 above) and treat any future field addition as a deliberate, reviewed decision, not a convenience shortcut.

### Anti-Pattern 2: `handle_path` stripping the asset prefix before proxying

**What people do:** Assume the reverse proxy must strip the `/landing-static` prefix (like a classic "mount path" reverse-proxy setup) before forwarding to the Next.js app, e.g. `handle_path /landing-static/* { reverse_proxy webpage:3000 }`.
**Why it's wrong:** Next.js 15+'s own multi-zone docs show the recommended top-level router forwards the FULL prefixed path unmodified to the zone (`destination: ${BLOG_DOMAIN}/blog-static/:path+`) — the zone's own server expects and serves requests AT that prefixed path, because `assetPrefix` registration makes Next's router accept it there. Stripping the prefix would make `webpage`'s Next server receive `/_next/static/...` (unprefixed) while its manifest expects `/landing-static/_next/static/...` — a 404.
**Instead:** Use a plain `handle` (or a named matcher combining `path / /landing-static/*`), not `handle_path`, for the webpage branch.

### Anti-Pattern 3: `{$VAR}`/`${VAR}` templating inside a Docker Compose `entrypoint: |` heredoc

**What people do:** Try to keep the Hostinger Caddy config parametrized via Caddy-native `{$DOMAIN_NAME}` syntax embedded inside the `entrypoint: sh -c "echo '...' > Caddyfile"` string in `docker-compose.hostinger.yml`.
**Why it's wrong:** **This already bit this exact repo twice**, in the two commits immediately preceding this research (`67e2120`, `534fa92`). Docker Compose performs its own `$VAR`/`${VAR}` interpolation across the ENTIRE compose file's string values — including inside an embedded heredoc meant for Caddy — BEFORE Caddy ever sees it. Verified via `git show 67e2120`: `echo '{$DOMAIN_NAME}, www.{$DOMAIN_NAME} {'` had to become the hardcoded literal `echo 'alcv.tech, www.alcv.tech {'`. Verified via `git show 534fa92`: `{$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH}` inside the same heredoc was removed entirely because the bcrypt hash's own literal `$` characters got mangled once Compose (and then the shell) tried to interpolate through them, crashing Caddy on startup.
**Instead:** In `docker-compose.hostinger.yml` specifically, any new Caddy config text added inside that `entrypoint` block must use plain hardcoded values (matching the `alcv.tech` domain literal already there) and must contain **zero** `$` characters. `Caddyfile.prod` (a real mounted file, NOT embedded in Compose YAML) is unaffected by this bug and can safely keep using native `{$DOMAIN_NAME}` templating — the two files must be treated differently, and this repo already has both variants live today.

### Anti-Pattern 4: `useRouter().push()`/`<Link>` for the setup-status redirect inside `webpage/`

**What people do:** Copy `web/src/app/page.tsx`'s client-side `useEffect` + `useRouter().replace("/setup")` pattern verbatim into `webpage/`.
**Why it's wrong:** That pattern works in `web/` because `/setup` is part of `web/`'s own route manifest — a soft client navigation there is a same-zone transition. Inside `webpage/`, `/setup` does not exist in the build, so a soft navigation attempt would produce a client-side 404 before ever reaching Caddy/`web/`.
**Instead:** Do the check server-side in `webpage/proxy.ts` (Pattern 3) — an actual HTTP redirect, not a client route change, so it correctly re-enters through Caddy and lands in the `web/` zone.

### Anti-Pattern 5: Wildcarding the new `permitAll()` entry

**What people do:** Add `/api/v1/public/**` to `SecurityConfig`'s matcher list "to save having to edit this again for future public endpoints."
**Why it's wrong:** Every other entry in this list (`/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, `/api/v1/setup/status`, `/api/v1/setup/initialize`) is an exact literal path — the existing convention is "list what's public, explicitly, one at a time," which makes the whole authorization surface auditable by reading one `.requestMatchers(...)` call. A wildcard breaks that invariant for anyone who adds a `/public/whatever` endpoint later without re-reading `SecurityConfig`.
**Instead:** Add the exact literal `"/api/v1/public/branding"`.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|----------------------|-------|
| GHCR (`ghcr.io/tchspprtcv/lexcv`) | New 3rd image `webpage`, pushed by `.github/workflows/deploy.yml`'s existing `build-and-push` job | Add a 3rd `docker/build-push-action@v6` step, `context: ./webpage`, `tags: ${{ env.REGISTRY }}/webpage:latest` + sha tag, `cache-from/to: type=gha,scope=webpage` — exact mirror of the existing `frontend` step (lines 97-110 of `deploy.yml`) |

### Internal Boundaries (exact current syntax → exact new syntax)

**1. `Caddyfile` (dev, plain, unparametrized — read today verbatim):**
```caddyfile
:80 {
    handle /api/* {
        reverse_proxy backend:8080
    }
    handle {
        reverse_proxy frontend:3000
    }
}
```
→ becomes:
```caddyfile
:80 {
    handle /api/* {
        reverse_proxy backend:8080
    }

    @webpage {
        path / /landing-static/*
    }
    handle @webpage {
        reverse_proxy webpage:3000
    }

    handle {
        reverse_proxy frontend:3000
    }
}
```

**2. `Caddyfile.prod` (real mounted file, uses Caddy-native `{$DOMAIN_NAME}` — safe to keep):**
```caddyfile
{$DOMAIN_NAME}, www.{$DOMAIN_NAME} {
    handle /api/* {
        reverse_proxy backend:8080
    }
    handle_path /minio-console* {
        basicauth {
            {$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH}
        }
        reverse_proxy minio:9001
    }
    handle {
        reverse_proxy frontend:3000
    }
}
```
→ insert the same `@webpage` block used above, between the `/minio-console*` block and the catch-all.

**3. `docker-compose.hostinger.yml` (embedded heredoc, hardcoded literal domain since commit `67e2120` — must add ZERO new `$` characters, per Anti-Pattern 3):**
```yaml
    entrypoint:
      - sh
      - -c
      - |
        echo 'alcv.tech, www.alcv.tech {
            handle /api/* {
                reverse_proxy backend:8080
            }
            handle {
                reverse_proxy frontend:3000
            }
        }' > /etc/caddy/Caddyfile && exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
```
→ becomes (note: also update this service's `depends_on` list to include `webpage`):
```yaml
    entrypoint:
      - sh
      - -c
      - |
        echo 'alcv.tech, www.alcv.tech {
            handle /api/* {
                reverse_proxy backend:8080
            }
            @webpage {
                path / /landing-static/*
            }
            handle @webpage {
                reverse_proxy webpage:3000
            }
            handle {
                reverse_proxy frontend:3000
            }
        }' > /etc/caddy/Caddyfile && exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
    depends_on:
      - frontend
      - backend
      - webpage
```

**4. New `webpage` service, all 3 compose files (mirrors `frontend`'s existing shape):**

`docker-compose.yml` (base/dev — add after the `frontend` block):
```yaml
  webpage:
    build:
      context: ./webpage
      dockerfile: Dockerfile
    container_name: lexcv_webpage
    depends_on:
      - backend
    environment:
      BACKEND_API_ORIGIN: http://backend:8080
      NEXT_PUBLIC_API_BASE_PATH: /api/v1
    networks:
      - lexcv_net
    ports:
      - "3004:3000"
```
Also add `webpage` to Caddy's `depends_on: [frontend, backend]` → `[frontend, backend, webpage]` in this file too.

`docker-compose.prod.yml` (override — add alongside the existing `frontend:` override):
```yaml
  webpage:
    image: ${REGISTRY:-ghcr.io/lexcv}/webpage:${IMAGE_TAG:-latest}
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M
```

`docker-compose.hostinger.yml` (self-contained — add a full block mirroring the existing `frontend:` block):
```yaml
  webpage:
    image: ghcr.io/tchspprtcv/lexcv/webpage:latest
    container_name: lexcv_webpage
    depends_on:
      - backend
    environment:
      BACKEND_API_ORIGIN: http://backend:8080
      NEXT_PUBLIC_API_BASE_PATH: /api/v1
    networks:
      - lexcv_net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M
    restart: unless-stopped
```

**5. New backend endpoint (`SecurityConfig` + `TenantRepository` + `PublicController` + `PublicTenantBrandingResponse`)** — see Pattern 2 above for exact code.

## Recommended Build Order

The two biggest pieces — the backend endpoint and the `webpage` app — have **no hard dependency on each other** and should be built in parallel; only the infra wiring is strictly sequential and comes last.

1. **Backend endpoint first (or in parallel), fully isolated:** `TenantRepository.findFirstByOrderByCreatedAtAsc()` + `PublicTenantBrandingResponse` + `PublicController` + the one-line `SecurityConfig` permitAll addition. Zero dependency on `webpage/`. Verifiable standalone with `curl http://localhost:8089/api/v1/public/branding` against the existing dev `docker-compose.yml` backend (port `8089` already mapped) — no new infra needed to test this in isolation.

2. **`webpage/` scaffold + setup-status gate, in parallel with (1):** This does NOT need the new backend endpoint at all — it reuses `/api/v1/setup/status`, which is **already public today** (`SetupController`, already in `permitAll()`). Scaffold `webpage/` (layout, globals.css, `proxy.ts`, landing sections), and for the branding fetch, use a hardcoded stub (`{ nome: "LexCV", logoDataUrl: null }`) so UI work is never blocked on the backend piece. `webpage/` can be run standalone via `pnpm dev` (needs only its own `next.config.ts` rewrite + a `BACKEND_API_ORIGIN` pointing at a running backend, or none at all if the setup-status check is temporarily mocked too) — **no Caddy, no Docker, no compose changes needed yet** to build and visually iterate on this app.

3. **Wire (1) into (2):** once the endpoint lands, swap the stub in `webpage/src/lib/branding.ts` for a real `fetch` call. Small, low-risk integration step.

4. **`webpage/Dockerfile`:** copy `web/Dockerfile`'s exact 3-stage shape (deps → build → standalone runner), adjusting only package name/paths. Can be written and `docker build`-tested standalone before touching any compose file.

5. **Compose wiring (all 3 files) + Caddy routing (all 3 files):** only makes sense once (4) produces a working image/buildable context — this is where `webpage` becomes reachable end-to-end for the first time (`docker compose up` locally, verify `http://localhost/` hits `webpage` and `http://localhost/login` still hits `frontend`).

6. **CI/CD (`deploy.yml`):** add the 3rd build-push step last — it only matters once `webpage/Dockerfile` exists and the compose files reference the `ghcr.io/.../webpage` image tag, otherwise CI would be building an image nothing yet consumes.

**Why this order:** it maximizes parallelizable work (steps 1 and 2 have zero mutual dependency thanks to the pre-existing public `/setup/status` endpoint and a mockable branding payload) and defers all infra/deployment risk (Caddy's known Compose brace-expansion footgun, new container wiring, CI changes) to the end, where it can be validated against two already-complete, independently-tested pieces rather than debugged blind.

## Known Limitation (flag, not a blocker)

`webpage/public/*` static files (`favicon.ico`, `robots.txt`, `sitemap.xml`, OG images) are NOT reachable through Caddy's catch-all default today, because that catch-all (unchanged) still routes unprefixed root-level static paths to `frontend:3000`, which will serve `web/`'s own `public/*` files instead. This only matters if the landing page needs its own distinct favicon/OG image/robots.txt from `web/`'s. If so, add explicit exact-path `handle` branches for those specific files to the `@webpage` matcher (e.g. `path / /favicon.ico /robots.txt /landing-static/*`) — deliberately not included in the default recommendation above to keep the routing change minimal and match the milestone's stated scope (no SEO/self-service requirements called out in PROJECT.md).

## Sources

- `Caddyfile`, `Caddyfile.prod`, `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml` — read directly from this repo (2026-07-15)
- `backend/src/main/java/com/lexcv/config/SecurityConfig.java`, `Tenant.java`, `SetupController.java`, `SetupService.java`, `TenantRepository.java`, `UserResponse.java`, `AuthController.java`, `AdminController.java` — read directly from this repo
- `web/src/app/page.tsx`, `web/proxy.ts`, `web/src/lib/setup.ts`, `web/src/lib/api.ts`, `web/next.config.ts`, `web/Dockerfile`, `web/src/app/layout.tsx`, `web/src/app/providers.tsx`, `web/src/app/globals.css`, `web/src/components/shared/dashboard-shell.tsx`, `web/package.json` — read directly from this repo
- `.github/workflows/deploy.yml` — read directly from this repo
- This repo's own git history: commits `67e2120` ("fix: hardcode alcv.tech in Caddy entrypoint - avoid Docker Compose brace expansion bug") and `534fa92` ("fix: remove MinIO basicauth from Caddy - fix crash due to bcrypt hash dollar signs"), inspected via `git show` — HIGH confidence, first-party evidence, not inferred
- [Next.js — Guides: Multi-Zones](https://nextjs.org/docs/pages/guides/multi-zones) — official docs, fetched version 16.2.10, matches installed `next@16.2.6` in `web/package.json`. HIGH confidence: `assetPrefix` behavior, "no rewrite needed since Next 15", `<a>` vs `<Link>` cross-zone requirement, "default zone needs no assetPrefix"
- `web/node_modules/next/dist/docs/01-app/03-api-reference/05-config/01-next-config-js/assetPrefix.md` and `basePath.md` — local, version-matched docs bundled with the installed Next package (per this repo's `web/AGENTS.md` warning to prefer these over training data)
- [Caddy — `handle` directive docs](https://caddyserver.com/docs/caddyfile/directives/handle) — official docs, fetched directly. HIGH confidence: mutual exclusivity of sequential `handle` blocks, `handle_path` strips the matched prefix (confirms why NOT to use it here), `handle_path` sorts at the same priority as a `handle` with a path matcher
- WebSearch: "Caddy multiple Next.js apps same domain path routing assetPrefix" — MEDIUM confidence, used only to corroborate/triangulate the Multi-Zones approach against real-world community write-ups (Caddy Community forum, dev.to); the Next.js official docs fetch above is the primary/authoritative source, this was cross-verification only

---
*Architecture research for: standalone Next.js landing page app integration into existing Caddy/Compose/Spring Boot monorepo*
*Researched: 2026-07-15*
