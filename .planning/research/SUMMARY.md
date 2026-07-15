# Project Research Summary

**Project:** LexCV — v2.12 Landing Page (`webpage/`)
**Domain:** Standalone public marketing/landing Next.js app added to an existing secured multi-tenant monorepo (Next.js dashboard `web/` + Spring Boot `backend/` + Caddy reverse proxy), single fixed domain, path-based routing
**Researched:** 2026-07-15
**Confidence:** HIGH

## Executive Summary

This milestone adds a third application (`webpage/`) to an already-running two-app-plus-backend system: a public, unauthenticated marketing/landing page personalized with the deployment's own tenant name and logo. It is **not** a typical multi-tenant SaaS marketing site with customer logos, testimonials, pricing, or self-serve signup — LexCV's deployment model is one institution per install, so all "social proof" must come from honest, verifiable architecture facts (multi-tenant data isolation, RBAC, audit trail, Cape Verde/NOSi ecosystem fit) rather than fabricated cross-customer claims. The recommended approach is a fully independent Next.js 16.2.6 app (exact version match with `web/`, no shared workspace tooling), built using Next.js's official **Multi-Zones** pattern (`assetPrefix` + Caddy path-based `handle` routing) to coexist with `web/` under one domain, backed by exactly one new narrow backend endpoint (`GET /api/v1/public/branding`) that returns only `nome` + `logoDataUrl`.

The stack, feature scope, and architecture are all grounded directly in this repo's own pinned versions and existing patterns (copied shadcn primitives, `SetupController`-style narrow public controllers, `web/Dockerfile`'s 3-stage build), which is why confidence is high across the board except for Cape-Verde-market-specific feature framing (no local competitor data exists, so that guidance is directional). The single biggest risk category is **integration/cross-cutting infrastructure, not the landing page's own code**: this repo has three independently-drifting Caddy configuration sources (`Caddyfile`, `Caddyfile.prod`, and an inline heredoc embedded in `docker-compose.hostinger.yml` — the last of which is confirmed, via git history, to be the actual live production path), and the four commits immediately preceding this research were all bug fixes to that exact inline config. A second major risk is naive reuse of `web/`'s existing `page.tsx`/`proxy.ts` logic, which was written for a pure "where do I route you" gateway and would silently redirect every anonymous visitor straight to `/login` — defeating the entire point of a public landing page — if copied wholesale. A third is leaking `Tenant` PII (`nif`, `email`, `telefone`) through the new public endpoint by taking an entity-pass-through shortcut instead of the narrow explicit-copy DTO pattern this codebase already uses elsewhere (`AuthController.getMe()`).

Mitigation is well-understood and low-cost for all three risk categories: (1) update all three Caddy config sources together in the same change and verify through a full `docker compose up`, never `pnpm dev` alone; (2) build `webpage/`'s setup-status gate as a standalone server-side redirect with zero authentication branching, and use plain `<a>` tags (never `next/link`) for any cross-app navigation such as the "Entrar" CTA; (3) hand-build a two-field `TenantPublicInfoResponse` DTO with explicit getter-to-setter copying and an exact-literal (never wildcarded) `SecurityConfig` allowlist entry. None of this requires new tooling, a CMS, or a monorepo build system — everything reuses proven, already-shipped patterns from `web/` and `backend/`.

## Key Findings

### Recommended Stack

Next.js 16.2.6 (exact pin matching `web/package.json`) + React 19.2.4 + TypeScript `^5` + Tailwind CSS v4 (CSS-first, no `tailwind.config.ts`) form the core, chosen specifically to avoid tracking two different sets of Next.js 16 quirks in one repo. The linchpin technology is **Next.js Multi-Zones** (built into Next core since v15, `assetPrefix` config) — the officially documented, Vercel-maintained pattern for exactly this milestone's topology (multiple independently-deployed Next.js apps under one domain). Supporting UI libraries (`@radix-ui/react-slot`, `class-variance-authority`, `clsx`+`tailwind-merge`, `lucide-react`, `next-themes`) are hand-copied file-by-file from `web/src/components/ui/` and `web/src/lib/`, matching this repo's existing convention of no `components.json`/no shadcn CLI usage.

**Core technologies:**
- Next.js 16.2.6 (exact) — App Router server + static rendering — matches `web/`'s exact pin, avoids two divergent sets of Next.js 16 training-data-breaking gotchas in one repo
- React/React DOM 19.2.4 (exact) — required peer, matches `web/`'s pin
- Tailwind CSS v4 (`^4` + `@tailwindcss/postcss ^4`) — CSS-first config via `@theme` in `globals.css`, no separate config file, matches `web/`
- Next.js Multi-Zones (`assetPrefix`, built-in) — prevents `webpage/`'s `_next/static/*` requests colliding with `web/`'s under the same domain; **not optional**, confirmed against the bundled Next 16.2.6 docs in this repo
- `next-themes` — dark/light mode toggle, explicit milestone requirement, direct port of `web/src/app/providers.tsx`

**Explicitly avoid:** `output: 'export'` (breaks the setup-status redirect and dynamic fetch this app needs), `@tanstack/react-query`/`react-hook-form`/`zod` (no client-managed state or forms in scope), `npx shadcn init` (no `components.json` exists anywhere in this repo), and skipping `assetPrefix` (silently serves `web/`'s JS/CSS to the landing page in production while looking fine in local dev).

### Expected Features

LexCV's landing page is architecturally closer to an Auth0/Okta-style branded single-tenant entry portal than a typical multi-customer SaaS marketing site — the codebase has no field, table, or endpoint for "other institutions using LexCV," so every social-proof recommendation is scoped around that structural constraint.

**Must have (table stakes, P1):**
- Setup-status gate (port of existing public `/api/v1/setup/status` check) — prevents showing "personalized" content for an uninitialized tenant
- Public Tenant Branding Endpoint (`nome` + `logoDataUrl` only) — the one genuinely new backend surface
- Personalized Hero + benefit-driven headline
- Módulos/Funcionalidades overview (Clientes, Processos, Agenda/Prazos, Documentos, Financeiro, Notificações) — static copy, zero backend dependency
- Prova social/confiança institucional using architecture-based trust copy (data isolation, RBAC, audit, NOSi/Cabo Verde ecosystem framing) — not fabricated customer proof
- Contacto/Pedir demonstração with a static contact channel (mailto or external form embed) — explicitly NOT sourced from `Tenant.email`/`telefone`
- Primary CTA "Entrar" → `/login`, top and bottom
- Responsive layout + dark/light mode (ported from `web/`)
- Basic SEO meta (title/description/favicon)

**Should have / add after validation (P2):**
- Dynamic OG/share image personalized with tenant branding (reuses the same endpoint)
- Curated real-UI screenshots from seeded/demo data replacing placeholder illustrations

**Defer / anti-features (rejected or v2+):**
- Customer logo wall, testimonials carousel — structurally impossible to source honestly (no cross-deployment registry exists)
- Persisted "solicitar demonstração" lead-capture backend — meaningful new scope (entity, controller, RBAC, spam protection), not a landing-page detail
- Public pricing page, self-serve signup/trial — explicitly out of scope; provisioning is manual/sales-led
- Blog/CMS, live chat widget, multi-language toggle, interactive product-tour sandbox, competitor comparison page, analytics/cookie-consent — all rejected as disproportionate to or inconsistent with this milestone's actual scope

### Architecture Approach

`webpage/` is added as a fully independent Next.js app (own `package.json`/`pnpm-lock.yaml`, no monorepo tooling) that shares only the backend and domain with `web/`. It reaches the outside world exclusively through Caddy's path-based routing, using Next.js's own documented Multi-Zones pattern: `webpage/` gets a unique `assetPrefix` (`/landing-static`) so its static chunks never collide with `web/`'s unprefixed `/_next/*`; `web/` itself needs zero changes since the default/catch-all zone requires no `assetPrefix`. On the backend, one new narrow `PublicController` (mirroring the existing `SetupController` precedent of a small, easy-to-audit, single-purpose public controller) exposes `nome`+`logoDataUrl` for the singleton tenant row, added to `SecurityConfig`'s existing exact-literal `permitAll()` allowlist.

**Major components:**
1. `webpage/` (new Next.js 16 app) — renders the public landing page at `/` only, SSR for SEO, server-side setup-status redirect gate via its own `proxy.ts`
2. `PublicController` + `TenantPublicInfoResponse` DTO (new, backend) — exposes exactly two fields, unauthenticated, via explicit getter-to-setter copy (never entity pass-through)
3. Caddy (`Caddyfile` / `Caddyfile.prod` / `docker-compose.hostinger.yml` inline heredoc) — routes `/api/*` → backend, exact `/` + `/landing-static/*` → `webpage`, everything else (unchanged catch-all) → `frontend`
4. `docker-compose.*` (3 files) + `.github/workflows/deploy.yml` — adds `webpage` as a 4th service/3rd built-and-pushed image, mirroring `frontend`'s existing shape

The recommended build order (from Architecture research) is: (1) backend endpoint fully isolated and curl-testable, (2) `webpage/` scaffold in parallel using a hardcoded branding stub — neither depends on the other, (3) wire the real endpoint into the stub, (4) `webpage/Dockerfile`, (5) Caddy + compose wiring across all three config sources together, (6) CI/CD build-push step last.

### Critical Pitfalls

1. **Copying `web/`'s `page.tsx`/auth-check logic wholesale auto-redirects every anonymous visitor to `/login`** before the landing content ever renders — `webpage/`'s setup-status check must contain only the "not initialized → `/setup`" branch, never a `useMe()`/authentication branch.
2. **`next/link`/`<Link>` used for the "Entrar" CTA breaks in production** because `/login` doesn't exist in `webpage/`'s own route table — it only works via Caddy routing to a sibling app. Use a plain `<a href="/login">` for every webpage→web cross-zone link; this only surfaces once both apps run behind Caddy together, not in isolated `pnpm dev`.
3. **The new public endpoint leaks `nif`/`email`/`telefone`** if built via entity pass-through or a convenience DTO shortcut — `Tenant.java` has no field-level `@JsonIgnore`. Use a hand-built two-field DTO with explicit getter-to-setter copying, exactly like `AuthController.getMe()` already does.
4. **`webpage/` and `web/` collide on `/_next/*` and `/favicon.ico`** under one Caddy origin unless `webpage/` gets a unique `assetPrefix` and Caddy's `handle` blocks route the prefixed/exact-root paths to `webpage` *before* the existing catch-all — this only breaks in production behind Caddy, never in standalone dev.
5. **Caddy routing change applied to only 1 of 3 independently-drifted config sources** — `Caddyfile`, `Caddyfile.prod`, and an inline heredoc in `docker-compose.hostinger.yml` (confirmed by git history to be the actual live production path) must all be updated together, or the live deployment silently keeps the old routing with no error at all.

Additional flagged risk: adding the new `permitAll()` entry as a wildcard (`/api/v1/public/**`) instead of the codebase's established exact-literal-string convention would silently pre-authorize any future endpoint added under that prefix without a fresh security review.

## Implications for Roadmap

Based on combined research, three phases emerge cleanly — and are already named consistently across the Pitfalls research's "Phase to address" column (`backend-endpoint phase`, `webpage-app phase`, `infra-wiring phase`), which is a strong signal this is the natural grouping.

### Phase 1: Backend Public Branding Endpoint
**Rationale:** Fully isolated with zero dependency on the `webpage/` app (per Architecture's recommended build order); can be built first or in parallel, and is independently curl-testable against the existing dev `docker-compose.yml` backend with no new infra.
**Delivers:** `TenantPublicInfoResponse` DTO (exactly `nome` + `logoDataUrl`), `TenantRepository.findFirstByOrderByCreatedAtAsc()`, new `PublicController` (`GET /api/v1/public/branding`), one exact-literal `SecurityConfig` `permitAll()` addition.
**Addresses:** Public Tenant Branding Endpoint (P1, FEATURES.md).
**Avoids:** Tenant entity/PII leak (Pitfall 3), wildcard `permitAll()` matcher (Pitfall 6), nondeterministic singleton-tenant lookup (Technical Debt table).

### Phase 2: `webpage/` Landing App
**Rationale:** Can proceed in parallel with Phase 1 using a hardcoded branding stub — the biggest phase, covering nearly the entire v1 feature set; UI work is never blocked on the backend piece.
**Delivers:** `webpage/` Next.js 16.2.6 app scaffold (Dockerfile mirroring `web/Dockerfile`'s 3-stage build, `next.config.ts` with `assetPrefix: '/landing-static'`, `proxy.ts` with setup-status-only gate), ported layout/globals.css/dark-light mode, Hero/Módulos/Prova social/Contacto sections, "Entrar" CTA as a plain `<a>`, matching security headers (CSP/HSTS/X-Frame-Options), and final integration swapping the branding stub for the real Phase 1 endpoint.
**Addresses:** Setup-status gate, Personalized Hero, Módulos overview, Prova social (architecture-based trust copy), Contacto (static contact channel), CTA, responsive layout, dark/light mode, basic SEO meta — the full P1 MVP list from FEATURES.md.
**Avoids:** Auto-redirect of anonymous visitors to `/login` (Pitfall 1), broken CTA via `next/link` across zones (Pitfall 2), client-gated spinner harming SEO/LCP (UX Pitfalls), CSP/security-header omission on the most internet-exposed page in the domain.

### Phase 3: Infra Wiring & Deployment
**Rationale:** Only makes sense once Phase 2 produces a working, buildable Docker image (per Architecture's build order) — this is where the highest-risk, highest-coordination-cost work (three drifting Caddy config sources, new container wiring, CI/CD) is deliberately deferred to the end, validated against two already-complete, independently-tested pieces rather than debugged blind.
**Delivers:** `assetPrefix`-aware Caddy `handle` blocks added to **all three** config sources (`Caddyfile`, `Caddyfile.prod`, the inline heredoc in `docker-compose.hostinger.yml`) in correct pre-catch-all order; new `webpage` service added to all three `docker-compose*.yml` files (mirroring `frontend`'s resource limits); a third `docker/build-push-action@v6` step in `.github/workflows/deploy.yml`.
**Uses:** Caddy 2 (existing), GitHub Actions (existing), Next.js Multi-Zones `assetPrefix` mechanism (Stack/Architecture).
**Implements:** Caddy routing component + `docker-compose.*`/CI-CD component (Architecture's Component Responsibilities).

### Phase Ordering Rationale

- Phases 1 and 2 have zero mutual dependency (thanks to a mockable branding payload and the pre-existing public `/setup/status` endpoint), so they can be built in parallel or in either order — this maximizes early velocity.
- Phase 3 must come last because it depends on real, working artifacts from both prior phases (an actual endpoint response shape to verify against, and a buildable `webpage` Docker image) and because it touches the exact area (`docker-compose.hostinger.yml`'s inline Caddy config) that has already caused four consecutive bug-fix commits in this repo's recent history — deferring it avoids debugging infra and application logic simultaneously.
- This ordering directly avoids the "looks done but isn't" trap identified in Pitfalls research: testing `webpage/` only via `pnpm dev` (Phases 1-2) proves nothing about the cross-app collision/CTA/Caddy-drift risks that only surface once everything runs together behind Caddy (Phase 3) — so Phase 3's own verification step must explicitly include a full `docker compose up` test, not a repeat of Phase 2's isolated dev testing.

### Research Flags

Needs deeper attention during planning:
- **Phase 3 (infra-wiring):** The three-source Caddy config drift has already caused real production bugs in this exact repo (commits `67e2120`, `534fa92`, `ba67f4e`, `1482f47`) — planning for this phase should explicitly re-read those commits and plan a side-by-side diff/verification step across all three files, plus a full `docker compose up` smoke test (not just `pnpm dev`), before considering it done.

Standard patterns, well-documented (skip additional research-phase):
- **Phase 1 (backend-endpoint):** Strong existing precedent in this exact codebase (`AuthController.getMe()`'s explicit-copy pattern, `SetupController`'s narrow-public-controller shape, `UserSummaryResponse`'s narrow-DTO doc comment) — implementation guidance is already concrete and code-ready in ARCHITECTURE.md/PITFALLS.md.
- **Phase 2 (webpage-app):** Grounded directly in official Next.js 16 Multi-Zones docs (bundled in this repo's own `node_modules`, matching the installed version) plus a working `web/` app to copy patterns from — low ambiguity.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Grounded in this repo's actual pinned versions (`web/package.json`, configs) plus Next.js 16.2.6's own bundled docs — the exact version installed in this repo, not an assumed/newer version |
| Features | MEDIUM | Generic B2B SaaS/legal-tech landing-page patterns are well-documented and cross-verified across multiple sources; Cape Verde/NOSi-specific market data is sparse (no local competitor found). Codebase constraints (what data structurally can/cannot be shown) are HIGH confidence, sourced directly from `Tenant.java`, `SetupInitializeRequest.java`, `SecurityConfig.java` |
| Architecture | HIGH | Grounded in actual repo files, official Next.js Multi-Zones docs matching the exact installed version, official Caddy `handle` docs, and this repo's own recent commit history for Compose-specific gotchas |
| Pitfalls | HIGH | Grounded directly in project source files, git history, and official Next.js 16 docs; one item (server-side hairpin fetch risk) flagged MEDIUM pending a live-deploy smoke test |

**Overall confidence:** HIGH (Features section carries a MEDIUM sub-rating due to sparse Cape-Verde-specific market data, but this does not affect the structural/codebase-constraint findings, which are HIGH and are what actually drive scope decisions)

### Gaps to Address

- **Cape Verde/NOSi market specifics are directional, not validated:** No local competitor was found in research, so positioning/copy framing (NOSi ecosystem alignment, institutional trust messaging) is a reasonable inference rather than confirmed market research. Validate messaging with actual institutional stakeholders during content review, not just this research.
- **Server-side "hairpin" fetch risk in `proxy.ts` is unverified:** A server-side relative-URL `fetch()` inside a container may resolve against the public domain (routing back out through Caddy) rather than the internal Docker network, unlike a browser-side fetch. `web/` already does this successfully, but PITFALLS.md flags this as needing an explicit staging/VPS deploy test before assuming it's risk-free for `webpage/` too — address this as a verification step in Phase 3, not an assumption in Phase 2.
- **Whether `webpage/` needs its own distinct favicon/OG image/robots.txt is unresolved:** Architecture research flags this as a "Known Limitation, not a blocker" — `webpage/public/*` files are not reachable through Caddy's default catch-all today. PROJECT.md doesn't call out explicit SEO requirements, so this needs a scope decision during phase planning (add extra Caddy `handle` branches, or accept `web/`'s favicon/OG image for now).
- **Exact contact channel for "Contacto/Pedir demonstração" needs a business decision:** Research confirms it must be a static, hardcoded value in `webpage/`'s own config (not sourced from `Tenant.email`/`telefone`), but which channel (mailto vs. external form embed) is a content/business decision, not a technical one — flag for the requirements/planning step.

## Sources

### Primary (HIGH confidence)
- `web/node_modules/next/dist/docs/01-app/02-guides/multi-zones.md` — Next.js 16.2.6's own bundled Multi-Zones guide (assetPrefix, routing, cross-zone linking, "no rewrite needed since Next 15")
- `web/node_modules/next/dist/docs/01-app/03-api-reference/05-config/01-next-config-js/output.md`, `assetPrefix.md`, `basePath.md` — version-matched Next.js config docs bundled with this repo's install
- `web/node_modules/next/dist/docs/01-app/02-guides/content-security-policy.md` — official Next.js 16 CSP guide
- [Caddy — `handle` directive docs](https://caddyserver.com/docs/caddyfile/directives/handle) — official docs, mutual exclusivity and `handle_path` behavior
- [Next.js — Guides: Multi-Zones](https://nextjs.org/docs/pages/guides/multi-zones) — official docs, version 16.2.10 (matches installed `next@16.2.6`)
- Direct repo inspection: `web/package.json`, `web/next.config.ts`, `web/Dockerfile`, `web/tsconfig.json`, `web/postcss.config.mjs`, `web/src/app/globals.css`/`providers.tsx`/`layout.tsx`/`page.tsx`, `web/proxy.ts`, `web/src/lib/setup.ts`/`api.ts`/`utils.ts`, `web/src/components/ui/button.tsx`/`card.tsx`, `web/src/components/theme-toggle.tsx`
- Backend repo inspection: `backend/src/main/java/com/lexcv/models/Tenant.java`, `config/SecurityConfig.java`, `controllers/SetupController.java`, `controllers/AuthController.java`, `services/SetupService.java`, `repositories/TenantRepository.java`, `dtos/UserResponse.java`/`UserSummaryResponse.java`/`SetupInitializeRequest.java`
- Infra inspection: `Caddyfile`, `Caddyfile.prod`, `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml`, `.github/workflows/deploy.yml`
- Git history: commits `67e2120`, `534fa92`, `ba67f4e`, `1482f47` (inspected via `git show`) — confirms `docker-compose.hostinger.yml` as the live, recently-fragile Caddy config path
- [Customize Universal Login Page Templates (Auth0 Docs)](https://auth0.com/docs/customize/login-pages/universal-login/customize-templates); [Brands | Okta Developer](https://developer.okta.com/docs/concepts/brands/) — official docs, tenant-branding-portal precedent
- [NOSi | Núcleo Operacional Para a Sociedade de Informação EPE](https://www.nosi.cv/en/); [Governo de Cabo Verde — NOSi](https://www.governo.cv/nucleo-operacional-da-sociedade-de-informacao-tem-novo-conselho-de-administracao/) — official Cape Verde government sources
- `.planning/PROJECT.md` (v2.12 Landing Page milestone section) — project's own scope/constraints source of truth

### Secondary (MEDIUM confidence)
- [Best Practices for Designing B2B SaaS Landing Pages (Genesys Growth)](https://genesysgrowth.com/blog/designing-b2b-saas-landing-pages)
- [18 B2B SaaS Landing Page Best Practices That Convert (SaaS Hero)](https://www.saashero.net/design/saas-landing-page-best-practices/) and companion CTA/trust-signal articles from the same source
- [26 SaaS landing pages: examples, trends and best practices (Unbounce)](https://unbounce.com/conversion-rate-optimization/the-state-of-saas-landing-pages/)
- [How to Create a Lawyer Landing Page That Actually Converts (Clio)](https://www.clio.com/blog/lawyer-landing-page/) — mobile-traffic stat, consumer-facing context
- [Best Legal Practice Management Software 2026 (PracticePanther)](https://www.practicepanther.com/blog/best-legal-practice-management-software/)
- [The role of security badges on SaaS landing page effectiveness (Markettailor)](https://www.markettailor.io/blog/role-of-security-badges-on-saas-landing-page) — substitute-for-testimonials framing
- WebSearch: "Caddy multiple Next.js apps same domain path routing assetPrefix" — cross-verification only, Next.js official docs remain primary source
- WebSearch: "Next.js latest release version July 2026" — informational footnote (patch-version currency), does not change exact-match stack recommendation

### Tertiary (LOW confidence)
- [21 Best Law Firm Landing Page Examples & Inspirations (Landingi)](https://landingi.com/landing-page/law-firm-examples/) — directional inspiration only, needs validation against actual institutional stakeholder feedback

---
*Research completed: 2026-07-15*
*Ready for roadmap: yes*
