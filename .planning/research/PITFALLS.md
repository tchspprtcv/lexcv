# Pitfalls Research

**Domain:** Adding a standalone public Next.js landing app (`webpage/`) + one narrow public backend endpoint to an existing secured, multi-tenant Spring Boot / Next.js / Caddy system (LexCV v2.12)
**Researched:** 2026-07-15
**Confidence:** HIGH (grounded directly in project source files, git history, and the official Next.js 16 docs bundled in `web/node_modules/next/dist/docs/`; one item flagged MEDIUM where verification requires a live deploy smoke test)

## Critical Pitfalls

### Pitfall 1: Copying `web/src/app/page.tsx`'s full redirect logic auto-bounces every anonymous visitor to `/login`

**What goes wrong:**
`webpage/`'s root page is built by copy-pasting `web/src/app/page.tsx` wholesale (setup-status check *and* the `useMe()`-based auth branch). Every anonymous visitor — the entire target audience of a public marketing page — hits `me.isError` (no session cookie) and gets `router.replace("/login?returnUrl=/dashboard")` before ever seeing the Hero/features/social-proof content. The landing page effectively never renders for anyone who isn't already logged in.

**Why it happens:**
`web/src/app/page.tsx` (lines 8-49) was written for the *old* meaning of `/` — a pure app-entry gateway with no content of its own, whose whole job was "figure out where to send you" (setup vs. login vs. dashboard). Reusing it is the fastest way to stub `webpage/`'s page, but the requirement completely changed the semantics of `/`: it's now a public marketing page that both anonymous and authenticated users should be able to see.

**How to avoid:**
`webpage/`'s setup-status check (proxy.ts and/or page) must perform **only** the "not initialized → `/setup`" branch. It must never import `useMe`/call `/api/v1/auth/me` or branch on authentication state. The "Entrar" CTA can optionally be auth-aware (swap label/target if an `access_token` cookie is present) as a cosmetic nicety, but that must never become a hard redirect that hides the page.

**Warning signs:**
Any import of `use-me`/`useMe` inside `webpage/`; a spinner div copied verbatim from `page.tsx` (`animate-spin rounded-full border-b-2`); opening the site in an incognito window immediately lands on `/login` instead of the landing content.

**Phase to address:** webpage-app phase

---

### Pitfall 2: Primary CTA ("Entrar" → `/login`) breaks because it uses `next/link` across a Caddy-only zone boundary

**What goes wrong:**
The CTA is implemented with Next.js's `<Link href="/login">`. Next.js's client router treats any relative `href` as one of *its own* routes and tries to prefetch/soft-navigate to it — but `/login` doesn't exist in `webpage/`'s route table at all; it only exists in the sibling `web/` app, reachable exclusively through Caddy's path-based routing. This works fine in isolated `next dev` (only one app running) and breaks only once both apps are actually running behind Caddy — exactly the environment this milestone introduces.

**Why it happens:**
`web/` and `webpage/` are two independent Next.js apps stitched together at the **Caddy** layer (per the milestone plan), not via Next.js's own `rewrites()`-based Multi-Zones wiring. The official Next.js Multi-Zones guide is explicit about this exact scenario: *"Links to paths in a different zone should use an `a` tag instead of the Next.js `<Link>` component. This is because Next.js will try to prefetch and soft navigate to any relative path in `<Link>` component, which will not work across zones."*

**How to avoid:**
Use a plain `<a href="/login">Entrar</a>` (or a click handler doing `window.location.assign("/login")`) for the CTA and any other webpage→web link. Never use `next/link`/`<Link>` for cross-app navigation.

**Warning signs:**
Clicking "Entrar" in a *built* container (not `pnpm dev`) does nothing, flashes a 404, or throws a client console error; QA only catches it when testing through Caddy/docker-compose rather than each app standalone.

**Phase to address:** webpage-app phase
**Source (HIGH confidence, official docs bundled in this repo):** `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md`, section "Linking between zones"

---

### Pitfall 3: Full `Tenant` entity (or an entity-wrapping DTO) leaks `nif`/`email`/`telefone` through the new public endpoint

**What goes wrong:**
The fastest implementation is `return ResponseEntity.ok(tenant);` or a DTO built with a convenience shortcut (`@JsonUnwrapped Tenant tenant`, a builder that copies the whole entity). `Tenant.java` (`backend/src/main/java/com/lexcv/models/Tenant.java`) has no field-level `@JsonIgnore` — it's a flat `@Data`-style entity with `nome`, `nif`, `tipoEntidade`, `email`, `telefone`, `logoDataUrl`, `id`, `createdAt`. Any of these shortcuts exposes all seven fields, unauthenticated, to the entire internet.

**Why it happens:**
Under time pressure, passing the entity straight through is one line vs. writing a new DTO. There's no compiler-enforced boundary stopping it.

**How to avoid:**
This codebase already has the *exact* right precedent, twice:
- `AuthController.getMe()` (~lines 169-172) does `tenantRepository.findById(...).ifPresent(t -> { response.setTenant_nome(t.getNome()); response.setTenant_logo_data_url(t.getLogoDataUrl()); })` — explicit getter-to-setter copying, never entity pass-through.
- `UserSummaryResponse.java`'s own doc comment states the pattern in words: *"Minimal, non-admin-safe... deliberately excludes roles/permissions/email/telefone/avatar_url."*

Create a new `TenantPublicInfoResponse` (or similar name) with **exactly two `String` fields**: `nome`, `logoDataUrl`. Populate it the same explicit-copy way as `AuthController`. Do **not** add `@JsonIgnore` to `Tenant.java` itself — that's a global, fragile fix that would also strip those fields from legitimate internal callers (e.g. `AuthController`'s own `/me` response, any future admin tenant-settings screen).

**Warning signs:**
`ResponseEntity<Tenant>` anywhere in the new controller; a DTO field typed as `Tenant`; a manual `curl` of the new endpoint returning a JSON body with `"nif"`, `"email"`, or `"tipoEntidade"` keys.

**Phase to address:** backend-endpoint phase

---

### Pitfall 4: Two Next.js apps collide on `/_next/*`, `/favicon.ico` and other root-level file routes under one Caddy origin

**What goes wrong:**
Both `web/` and `webpage/` are Next.js apps that, by default, serve their JS/CSS bundles at the *same* path prefix (`/_next/static/...`) and reserve the *same* special routes (`/favicon.ico`, and if added, `/robots.txt`, `/sitemap.xml`). Caddy can only route a given path to one upstream. If the Caddyfile change is "route `/` to `webpage`, leave the existing catch-all `handle { reverse_proxy frontend:3000 }` for everything else," then the landing page's own `/_next/static/chunks/*.js` requests fall into that same catch-all and get served by `web/`'s container instead of `webpage/`'s — breaking hydration, or worse, silently serving a same-path-different-content chunk.

**Why it happens:**
This is the classic "two Next.js apps behind one reverse proxy" problem, which this project has never needed to solve before (single frontend app until now). `web/src/app/favicon.ico` already exists (confirmed) — `webpage/` will want its own too, compounding the collision.

**How to avoid:**
Next.js has a first-class feature for exactly this — Multi-Zones (verified against this repo's actual Next 16 install, `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md`): give `webpage/next.config.ts` a unique `assetPrefix` (e.g. `assetPrefix: '/landing-static'`) since it's the narrower-scoped app (serves only `/`); leave `web/next.config.ts` unchanged — per the doc, *"the default application handling all paths not routed to another more specific zone does not need an assetPrefix."* Then add explicit Caddy `handle` blocks, evaluated **before** the generic catch-all:
```
handle /landing-static/* { reverse_proxy webpage:3000 }
handle /favicon.ico      { reverse_proxy webpage:3000 }
handle /                 { reverse_proxy webpage:3000 }   # exact match, not a prefix
handle /api/*            { reverse_proxy backend:8080 }
handle                   { reverse_proxy frontend:3000 }  # unchanged catch-all, still last
```

**Warning signs:**
Landing page renders unstyled/broken only in production (works in `pnpm dev` since there's no Caddy in front there to cause the collision); browser Network tab shows `/_next/static/...` 404s or HTML-instead-of-JS content-type for the landing page specifically.

**Phase to address:** infra-wiring phase (Caddy `handle` blocks) done together with webpage-app phase (`assetPrefix` in `next.config.ts`) — splitting these two changes across separate phases/PRs is itself a coordination risk.

---

### Pitfall 5: Caddy routing change only applied to one of THREE independent, already-drifted config sources

**What goes wrong:**
This repo has **three** separate Caddy configuration sources, not one:
1. `Caddyfile` (dev, port 80 only)
2. `Caddyfile.prod` (mounted by `docker-compose.prod.yml` as an override)
3. An **inline** Caddyfile heredoc embedded directly in `docker-compose.hostinger.yml`'s `caddy.entrypoint` (a hand-duplicated third copy)

Git history shows the four most recent commits before this milestone (`67e2120`, `534fa92`, `ba67f4e`, `1482f47`) were *all* bug fixes to the `docker-compose.hostinger.yml` inline Caddy config specifically (brace-expansion bug, MinIO basicauth crash from bcrypt `$` signs, env-var expansion syntax, domain hardcoding) — strong evidence `docker-compose.hostinger.yml` is the currently **live** production deployment path, while `Caddyfile.prod` hasn't been touched since much earlier phases (38/52). If this milestone's new `/` → `webpage` rule is only added to `Caddyfile.prod`, the live Hostinger deployment keeps the old routing — webpage never actually goes live — with **no error at all**, just silent partial deployment.

**Why it happens:**
The two prod-shaped configs were built at different times for different purposes and were never consolidated; nothing enforces they stay in sync.

**How to avoid:**
Update the routing rule in **all three** files in the same change, and diff them side by side to confirm identical handle-block ordering. If budget allows, flag (but treat as out of scope for this milestone unless the team explicitly agrees) consolidating to a single Caddy config source to remove this drift class permanently.

**Warning signs:**
The PR only touches `Caddyfile.prod`; manual UAT against the actual Hostinger VPS still shows the old behavior at `/`.

**Phase to address:** infra-wiring phase

---

### Pitfall 6: `permitAll()` entry added as a wildcard instead of an exact path

**What goes wrong:**
Adding `"/api/v1/public/**"` (or `/api/v1/public/*`) to `SecurityConfig`'s `requestMatchers(...)` instead of one exact literal string silently pre-authorizes any future endpoint later added under that prefix, without a fresh security review at the time it's added.

**Why it happens:**
`SecurityConfig.java` (lines 51-60) today lists exactly 5 **exact string** matchers — `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, `/api/v1/setup/status`, `/api/v1/setup/initialize` — no wildcards at all. This milestone's brief explicitly frames this list as "an exact, minimal allowlist." A wildcard is a tempting shortcut if more public endpoints are anticipated later (e.g. a future contact-form endpoint), but it breaks the grep-able, one-entry-per-reviewed-capability property of the list.

**How to avoid:**
Add exactly one new literal string, e.g. `"/api/v1/public/branding"`, restricted to `@GetMapping` in the controller. If a second public endpoint is needed in a future milestone, add a second exact literal then — never a shared wildcard.

**Warning signs:**
Any `*` character inside the new `.requestMatchers(...)` call.

**Phase to address:** backend-endpoint phase

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Duplicating the `headers()` block from `web/next.config.ts` into `webpage/next.config.ts` instead of centralizing at Caddy | No Caddy changes needed | CSP/HSTS/X-Frame-Options can silently drift between the two apps as one is updated and the other isn't | Acceptable for this milestone if both files carry a comment pointing at each other; revisit centralization if a 3rd app is ever added |
| `tenantRepository.findAll().get(0)` instead of an explicitly ordered repository method for the public endpoint | One line, no new query | Nondeterministic result the moment a second `Tenant` row ever exists (no DB constraint prevents it) | Never — the ordered version costs nothing extra to write |
| Reusing `web/Dockerfile` with only path/name tweaks for `webpage/Dockerfile` instead of validating it independently | Fast copy-paste | Silent divergence if `webpage/` ever needs different build args (e.g. its own `assetPrefix`, a different `NEXT_PUBLIC_*` var) and the Dockerfile isn't updated in lockstep | Acceptable initially; revisit once `webpage/`'s config meaningfully diverges from `web/`'s |
| Building the "Contacto/Pedir demonstração" section as a real persisted form instead of a static `mailto:`/contact-info block | Feels more "real" | Reopens exactly the multi-tenant/lead-capture scope this milestone explicitly excludes, with none of the spam/rate-limit hardening a real public form needs | Never in this milestone — treat a real form as a separate, explicitly-scoped future capability |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|-----------------|-------------------|
| Caddy `handle` block ordering | Placing the new `/`/asset-prefix rules *after* the existing catch-all `handle { reverse_proxy frontend:3000 }` | Caddy `handle` blocks match first-hit; the new webpage-specific blocks must come *before* the generic catch-all in all three config files (Pitfall 4 & 5) |
| Next.js Multi-Zones | Using `next/link`/`<Link>` for any webpage→web navigation | Plain `<a>` tags for all cross-app links (Pitfall 2) |
| Docker build-time env vars | Assuming `NEXT_PUBLIC_*` vars can be swapped at container *runtime* like `BACKEND_API_ORIGIN` | `NEXT_PUBLIC_*` values are baked into the JS bundle at **build** time (see `web/Dockerfile` lines 14-17, `ARG`/`ENV` in the builder stage) — `webpage/Dockerfile` needs its own matching `ARG`s, and `deploy.yml`'s new build-push step must pass matching `--build-arg`/`build-args`, or the shipped bundle silently reverts to whatever default was baked in at image-build time |
| Caddy vs. Next.js headers | Assuming Caddy adds any security headers | Neither `Caddyfile.prod` nor the `docker-compose.hostinger.yml` inline config sets any `header` directive today — every security header on `web/`'s responses comes exclusively from `web/next.config.ts`'s own `headers()` function; a new app that doesn't replicate it ships with none (Moderate pitfall below) |
| Server-side relative-URL fetch in `proxy.ts` | Assuming a bare relative path (`/api/v1/setup/status`, per `web/src/lib/setup.ts`) resolves identically whether called from the browser or from server-side Next.js code | Browser fetches of a relative path are same-origin-through-Caddy and safe; a **server-side** fetch of the same relative path from inside a container resolves against whatever origin Next.js's proxy runtime derives from the incoming request (potentially the public domain, hair-pinning back out through Caddy) rather than the internal Docker network — verify this explicitly rather than assuming `web/`'s existing pattern is risk-free just because it "already works" (see Moderate Pitfall below) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Uncached ~5MB base64 `logoDataUrl` re-fetched from DB on every anonymous page view (endpoint has no auth, so no login-friction rate limiting) | Rising DB/bandwidth load correlated with bot/crawler traffic, not real users | Add `Cache-Control: public, max-age=300` (or similar) to the new endpoint's response | Only noticeable once the site is indexed/scraped regularly — low risk at expected traffic for a niche B2B Cape Verde legal SaaS, but cheap to add now |
| Two Next.js containers (`web`, `webpage`) both cold-starting on redeploy, neither with a `healthcheck:`, with `caddy`'s `depends_on` being start-order-only (not health-gated) | Brief 502s from Caddy immediately after `docker compose up`/redeploy | Optional: add a lightweight healthcheck to `webpage`'s compose service | Only visible during redeploys, not steady-state; pre-existing gap for `web`/`backend` too, so this is "don't make it worse," not a new requirement |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Entity pass-through (or `@JsonUnwrapped`) in the new public endpoint | Unauthenticated PII leak: `nif`, `email`, `telefone` exposed to any internet caller | Narrow, explicit-field-copy DTO — the exact pattern already used in `AuthController.getMe()` and documented in `UserSummaryResponse.java` (Critical Pitfall 3) |
| Wildcard `permitAll()` matcher | Silently widens the public attack surface for any future endpoint added under the same prefix, without a fresh review | Exact-string matcher only, one literal per public endpoint (Critical Pitfall 6) |
| Accepting a tenant identifier as input to the public endpoint (e.g. `?tenantId=`) | Reopens a tenant-enumeration / cross-tenant-disclosure vector in a system whose core invariant is `tenant_id`-scoped isolation — directly contradicts the project's central security convention | The endpoint must take **no** id/tenant parameter at all; server resolves "the" singleton tenant itself (deterministically — see Technical Debt table) |
| Building the CSP/HSTS/X-Frame-Options duplication (Moderate Pitfall below) as an afterthought instead of a deliberate decision | Public landing page — the single most internet-exposed page on the whole domain — ships with weaker security headers than every authenticated page | Explicitly decide and document: duplicate-and-keep-in-sync vs. centralize at Caddy; don't let it happen by accident of "forgot to copy the block" |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|--------------|-------------------|
| Client-gated spinner (copied from `web/src/app/page.tsx`) blocking all visible content until an async setup-status fetch resolves | Poor SEO (crawlers see a spinner, not content), slow LCP, layout shift, bad first impression for the one page whose entire job is making a good first impression | Gate the setup-status redirect at the `proxy.ts` layer only (before any HTML ships); render the landing content immediately/statically once past that gate — no client-side spinner state around the marketing content itself |
| Broken "Entrar" CTA via `next/link` across the zone boundary | The single required conversion path (marketing page → login) is broken | Plain `<a href="/login">` hard navigation (Critical Pitfall 2) |
| Landing page and rest-of-app show different favicons/branding because `/favicon.ico` silently falls through to the wrong app's catch-all | Minor but visible inconsistency reinforcing a "stitched-together" feel | Explicit Caddy `handle /favicon.ico` routed to `webpage` (Critical Pitfall 4) |

## "Looks Done But Isn't" Checklist

- [ ] **Public endpoint field-narrowness:** Tested with a bare `curl` (no cookies at all), confirming the response body has *only* `{nome, logoDataUrl}` keys — testing "in the browser while logged in" doesn't prove the anonymous/no-leak path since the browser may already carry auth cookies that aren't even used by this endpoint but can mask leftover debug code.
- [ ] **Landing page tested behind Caddy, not just `pnpm dev`:** `_next` asset collisions (Pitfall 4) and the broken CTA (Pitfall 2) only surface once both `web` and `webpage` are actually proxied together through Caddy in a full `docker compose up` — a green `pnpm dev` on `webpage/` alone proves nothing about either.
- [ ] **All three Caddy config sources updated:** `Caddyfile`, `Caddyfile.prod`, and the inline entrypoint string in `docker-compose.hostinger.yml` — a PR diffing only one of them looks complete but silently doesn't reach production (Pitfall 5).
- [ ] **Full deploy-surface wiring:** `.github/workflows/deploy.yml` has a third `docker/build-push-action@v6` step for `webpage`, and all three `docker-compose*.yml` files define a `webpage` service with matching env vars — "the code works locally" often stops short of CI/CD wiring.
- [ ] **Anonymous/incognito browser test of `/`:** Confirms the page renders landing content and does *not* silently redirect to `/login` before anything paints (Critical Pitfall 1).
- [ ] **Security headers present on the landing page specifically:** `curl -I` the landing page and compare against `curl -I` on `/login` — both should carry the same CSP/HSTS/X-Frame-Options set.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|----------------|------------------|
| Full `Tenant` (or extra fields) leaked publicly | LOW | Swap in the narrow DTO, redeploy backend image; check access/edge logs for whether the leak was ever live in prod before treating it as a real disclosure incident (vs. caught pre-deploy) |
| Broken CTA (`next/link` across zones) | LOW | One-line change to a plain `<a>` tag, rebuild + redeploy the `webpage` image only |
| Caddy config drift (only 1 of 3 sources updated) | LOW-MEDIUM | Diff all three files, backport the routing change, restart the `caddy` container (Caddy reloads config on restart; no data loss) |
| `_next` asset collision discovered in prod | MEDIUM | Add `assetPrefix` to `webpage/next.config.ts`, add the matching Caddy `handle` block, rebuild both `web` and `webpage` images |
| Missing CI/CD wiring (webpage image never built) | LOW | Add the third `build-push-action` step + compose service blocks; next push to `master` builds and deploys it — no rollback needed since nothing broken was ever live |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| 1. Auto-redirect to `/login` for anonymous visitors | webpage-app phase | Incognito browser test of `/` shows landing content, not `/login` |
| 2. Broken CTA via `next/link` across zones | webpage-app phase | Manual click-through test through Caddy (not `pnpm dev` alone) |
| 3. `Tenant` entity leak through public endpoint | backend-endpoint phase | `curl -s .../api/v1/public/branding \| jq keys` returns exactly `["logoDataUrl","nome"]` |
| 4. `_next`/favicon collision between the two apps | infra-wiring + webpage-app phases (joint) | Load `/` through Caddy in full `docker compose up`; Network tab shows no 404s / wrong content-type for `_next` assets |
| 5. Caddy config drift across 3 sources | infra-wiring phase | Diff `Caddyfile` / `Caddyfile.prod` / `docker-compose.hostinger.yml` routing blocks — must match |
| 6. Wildcard `permitAll()` matcher | backend-endpoint phase | `grep -n "requestMatchers" SecurityConfig.java` shows only exact literal strings, no `*` |
| CSP/security header omission on `webpage/` | webpage-app phase | `curl -I` landing page vs. `/login`, compare header sets |
| Server-side hairpin fetch for setup-status | webpage-app phase, verified in infra-wiring phase | Deploy to actual VPS/staging; confirm the setup-status check completes quickly and doesn't depend on the public domain resolving from inside the container |
| Missing CI/CD + compose wiring for `webpage` | infra-wiring phase | `docker compose config` resolves a `webpage` service; GH Actions log shows 3 build-push steps |
| Nondeterministic singleton-tenant lookup | backend-endpoint phase | Code review confirms explicit ordering (e.g. `findFirstByOrderByCreatedAtAsc`), not bare `findAll().get(0)` |
| Scope creep into multi-tenant onboarding/subdomains | webpage-app phase (planning/CONTEXT), not code | Phase CONTEXT explicitly reconfirms the "out of scope" list from PROJECT.md before implementation starts |

## Sources

- `backend/src/main/java/com/lexcv/config/SecurityConfig.java` — current exact-match `permitAll()` allowlist (lines 51-60), CSP/header config
- `backend/src/main/java/com/lexcv/models/Tenant.java` — full field list (`nome`, `nif`, `tipoEntidade`, `email`, `telefone`, `logoDataUrl`, `id`, `createdAt`)
- `backend/src/main/java/com/lexcv/dtos/UserResponse.java`, `UserSummaryResponse.java` — precedent narrow-DTO patterns
- `backend/src/main/java/com/lexcv/controllers/AuthController.java` (~lines 153-172) — precedent explicit-field-copy from `Tenant` into a response DTO
- `backend/src/main/java/com/lexcv/controllers/SetupController.java`, `backend/src/main/java/com/lexcv/services/SetupService.java` — singleton-tenant creation flow, existing public-endpoint style
- `web/next.config.ts`, `web/proxy.ts`, `web/src/app/page.tsx`, `web/src/app/setup/page.tsx`, `web/src/lib/setup.ts` — existing setup-status-check patterns and security headers
- `web/Dockerfile` — build-time `ARG`/`ENV` baking for `NEXT_PUBLIC_*` vars
- `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml`, `Caddyfile`, `Caddyfile.prod` — the three-way Caddy config drift and current routing shape
- `.github/workflows/deploy.yml` — current 2-image build-and-push job
- Git history (`git log --oneline` on `docker-compose.hostinger.yml`/`Caddyfile*`) — commits `67e2120`, `534fa92`, `ba67f4e`, `1482f47` confirming `docker-compose.hostinger.yml` as the actively-deployed, recently-fragile Caddy config path
- `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md` — official Next.js 16 Multi-Zones guide, bundled with this repo's actual installed version (HIGH confidence, per `CLAUDE.md`'s instruction to check this directory rather than training data)
- `web/node_modules/next/dist/docs/01-app/02-guides/content-security-policy.md` — official Next.js 16 CSP guide, confirms headers must be set per-app via `next.config.js`/proxy when no shared layer exists
- `.planning/PROJECT.md` — v2.12 milestone scope, explicit out-of-scope decisions (self-service onboarding, subdomain/slug routing)

---
*Pitfalls research for: LexCV v2.12 (standalone public landing app + narrow public backend endpoint)*
*Researched: 2026-07-15*
