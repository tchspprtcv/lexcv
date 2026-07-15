# Feature Research

**Domain:** Institutional B2B landing page for a single-tenant legal-tech deployment (Cape Verde legal practice management platform)
**Researched:** 2026-07-15
**Confidence:** MEDIUM (generic B2B SaaS/legal-tech landing page patterns are well-documented and cross-verified; Cape Verde/NOSi-specific market data is sparse — codebase constraints are HIGH confidence, sourced directly from `Tenant.java`, `SetupInitializeRequest.java`, and `SecurityConfig.java`)

> Supersedes the v2.11-dated `FEATURES.md` previously at this path (that research covered NOTF-24/25/26 notification extensions; this one is scoped entirely to the new v2.12 Landing Page milestone).

## Framing: this is NOT a typical multi-tenant SaaS marketing site

Almost all "B2B SaaS landing page" research (Clio, MyCase, PracticePanther, generic SaaS growth blogs) assumes a marketing site that serves **prospects across many potential customers**, backed by a shared database of named clients, testimonials, logos, and self-serve signup/pricing. LexCV's v2.12 landing page is architecturally different and closer to a **branded splash/entry portal for one already-provisioned institution** — the same pattern used by:

- **Auth0/Okta Universal Login branding** — a tenant's login page shows only that org's logo/name, pulled from a small branding record (logo + display name), never cross-tenant data. (MEDIUM confidence, verified via Auth0/Okta developer docs)
- **Zendesk Guide / Freshdesk support portals** — publicly reachable, personalized per-organization, but not a comparison/marketing hub across all customers of the underlying platform.

This distinction is the single most important input for scoping: **the codebase has no field, table, or endpoint for "other institutions using LexCV," named customer logos, or testimonials** — and the deployment model (one tenant per deployment, `/setup` singleton) means such data structurally cannot exist without new cross-deployment infrastructure that is explicitly out of scope. Every "social proof" recommendation below is scoped around this constraint.

## Feature Landscape

### Table Stakes (Users Expect These)

Features expected of any professional institutional/B2B product page. Missing these makes the deployment look unfinished or untrustworthy to an institutional buyer.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Personalized hero (tenant `nome` + `logoDataUrl`) | Confirms "this deployment is ours" for the institution's staff/stakeholders; core requirement of the milestone | MEDIUM | **New backend work required**: `GET /api/v1/public/...` doesn't exist yet. Must be added to `SecurityConfig`'s `permitAll()` list alongside the existing `/api/v1/setup/status` pattern. Must return **only** `nome` + `logoDataUrl` — `Tenant.java` also has `nif`, `email`, `telefone`, `tipoEntidade` columns that must be explicitly excluded (confirmed via direct read of the entity; a naive `TenantResponse` DTO reuse would leak them) |
| Setup-status gate (`/api/v1/setup/status` → redirect to `/setup` if uninitialized) | Preserves existing first-run behavior; a landing page must never show "personalized" content for a tenant that doesn't exist yet | LOW | Endpoint already public (`SecurityConfig` line 56-58) and already consumed by this exact pattern in `web/`'s `proxy.ts`. Pure port of existing logic into the new `webpage/` app |
| Benefit-driven headline + sub-headline (value proposition) | First 5 seconds decide whether an institutional visitor keeps reading; HIGH confidence from cross-verified SaaS research | LOW | Static copy, no data dependency. Target headline ≤8 words per convergent industry guidance |
| Módulos/Funcionalidades overview (Clientes, Processos, Agenda/Prazos, Documentos, Financeiro, Notificações) | Confirmed content section; institutional buyers scan for "does it cover our actual workflow" before anything else | LOW | Purely static marketing copy describing already-shipped modules — zero new backend dependency. Risk is only content accuracy (must reflect real capability, not aspirational features) |
| Primary CTA "Entrar" → `/login`, repeated top + bottom | Single, unambiguous CTA outperforms multi-CTA pages in every SaaS CRO source found (13.5% vs 10.5% conversion for single vs 5+ CTAs) | LOW | `/login` route already exists and is unaffected by this milestone |
| Contact / "Pedir demonstração" section with a real reachable channel | Confirmed content section; the only lead-gen surface for institutions who don't yet have this deployment | MEDIUM | **Cannot source contact info from `Tenant.email`/`Tenant.telefone`** — those fields exist in the DB but (a) the milestone explicitly forbids exposing them via the public endpoint, and (b) the current `/setup` wizard (`SetupInitializeRequest`) never even populates them, so they may be null for every existing deployment. This section needs a **static, hardcoded contact channel in the `webpage/` app's own config** (e.g. a product-level email/mailto, or an external form embed) — not tenant-sourced data. Building a persisted "lead capture" endpoint is new backend scope (see Anti-Features) |
| Responsive/mobile-first layout | Legal-tech landing pages skew heavily mobile — one industry source found 88% of legal landing-page traffic is mobile (MEDIUM confidence, single-source, consumer-facing law-firm context but directionally consistent with LexCV's own mobile-first history in v2.3) | LOW-MEDIUM | `web/` already has a full mobile design system (drawer nav, bottom-sheets, touch targets) to reference for consistent patterns, though `webpage/` is a simpler single-page layout |
| Dark/light mode | Explicit milestone requirement ("Reutiliza... dark/light mode já usados em `web/`") | LOW | `web/src/components/theme-toggle.tsx` and `web/src/app/providers.tsx` already implement this — direct port |
| Basic SEO meta (title, description, favicon) | Table stakes for any public page; institutional stakeholders/search engines need a coherent identity | LOW-MEDIUM | Favicon/OG image ideally uses tenant logo dynamically — bumps this from LOW to LOW-MEDIUM (see dynamic OG image differentiator below for the harder version) |

### Differentiators (Competitive Advantage)

Features that set LexCV's landing page apart from generic legal-tech marketing pages — all achievable with **zero new data model changes**, by reframing verified architecture facts as trust copy rather than fabricating customer proof.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Multi-tenant data isolation as trust messaging ("os seus dados nunca se misturam com os de outra instituição") | Directly addresses the #1 enterprise security objection ("can I trust this with our data") — research confirms security-first messaging is the standard substitute for testimonials on new products (MEDIUM confidence, cross-verified across SaaS trust-signal sources) | LOW | **This is a real, verifiable architectural fact** — every domain entity carries `tenant_id`, unique constraints are per-tenant (per `CLAUDE.md`). Copy can honestly assert this without fabrication, unlike a generic SaaS bolting on a compliance badge it doesn't actually have |
| Cabo Verde / NOSi ecosystem framing ("desenhado para a realidade jurídica cabo-verdiana", institutional/e-gov alignment) | Localization/institutional-fit signal matters more than generic feature lists for public-sector-adjacent buyers; NOSi is Cape Verde's e-government operational nucleus (public verified fact) and legal Portuguese domain language is already a hard project constraint | LOW | Confirmed: NOSi ("Núcleo Operacional da Sociedade de Informação") is a real Cape Verde EPE government agency driving e-government/digital transformation (HIGH confidence, official/gov.cv sources) — framing LexCV as part of that digital-governance push is credible, not aspirational marketing |
| Curated real-UI screenshots/mockups (Dashboard, Ficha Cliente, Agenda) instead of generic stock illustrations | High-performing B2B SaaS heroes use actual product screenshots over abstract illustrations (MEDIUM confidence, cross-verified) | MEDIUM | Needs redacted/demo seed data (via existing `DatabaseSeeder`) to avoid ever screenshotting real tenant data; some design/curation effort but no new engineering |
| RBAC + audit-trail messaging ("cada ação é registada, permissões por função") | Institutional legal buyers (compliance-conscious) respond to governance/audit framing more than generic "powerful features" copy | LOW | Backed by real `@PreAuthorize` scope-based RBAC and existing audit patterns already shipped (Parecer versioning audit, Phase 90 SAST hardening) — again, honest reuse of shipped capability as copy, not new engineering |
| Dynamic OG/share image personalized with tenant name+logo | Differentiates the deployment when its link is shared (WhatsApp/email/LinkedIn preview) — an institution sharing "olha a nossa plataforma" gets a branded card, not a generic one | MEDIUM-HIGH | Requires Next.js dynamic image generation (`next/og` / `ImageResponse`) consuming the same public branding endpoint — real engineering effort, reasonable to defer to v1.x |

### Anti-Features (Commonly Requested, Often Problematic)

Patterns that appear in nearly every generic "SaaS landing page best practices" search result but are actively wrong for this milestone's constraints — flagged explicitly because the quality gate requires it.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| Customer logo wall / "trusted by N institutions" | Standard SaaS trust-signal advice (appears in every source found) | **Structurally impossible to source honestly.** This deployment is single-tenant — it has no knowledge of other LexCV deployments, and no central registry/table of "which institutions use LexCV" exists anywhere in the stack. Any such claim would be fabricated | Use the architecture-based trust messaging (data isolation, RBAC, audit) documented above — verifiable claims about *this product*, not unverifiable claims about *other customers* |
| Customer testimonials carousel | Same standard SaaS advice; explicitly flagged as a trap in this research's quality gate | No testimonials table/data exists anywhere in the schema (checked `Tenant.java` and full ERD context) — would require new data capture (consent, sourcing, a testimonials model) entirely out of this milestone's scope | Institutional-confidence copy grounded in real shipped capability (multi-tenant isolation, RBAC, audit trail) instead of quotes |
| Persisted "solicitar demonstração" lead-capture backend (name/email/message stored + admin-visible list) | Feels like the "proper" way to implement a confirmed "Contacto/Pedir demonstração" section | New backend scope: no `LeadRequest`/`DemoRequest` entity, controller, or admin UI exists today. Building one (with its own RBAC, persistence, spam protection) is a meaningfully sized feature, not a landing-page detail | A static `mailto:` link or an embed of an external form service (e.g. Formspree-style) satisfies the confirmed content section with zero new backend surface. Only build persistence if the business explicitly wants a CRM-like pipeline later |
| Public pricing page / pricing calculator | Reflexive B2B SaaS pattern (most-cited "best practice" in this research) | Milestone explicitly has no self-serve signup and no public pricing model — provisioning is manual, per-institution, off-platform (sales/procurement conversation). A pricing page would misrepresent the actual buying motion | "Contacto/Pedir demonstração" IS the correct CTA for a sales-led, manually-provisioned institutional product — no pricing page needed |
| Self-serve signup/trial CTA ("Começar grátis", "Criar conta") | Default CTA pattern for consumer-ish SaaS | **Explicitly out of scope per milestone** ("onboarding self-service multi-institituição... fora de âmbito") — the `/setup` wizard is a singleton, run once by whoever provisions the deployment, not a public registration flow | "Entrar" (existing tenant staff login) is the only authenticated CTA; "Pedir demonstração" is the only prospect-facing CTA |
| Blog / CMS-backed content section | Common SaaS growth-marketing addition | No CMS in the stack (`web/` has none either); adds a real content-ops dependency (writing, publishing cadence) disproportionate to "a landing page for one institution's own deployment" | Static, versioned marketing copy in the `webpage/` app itself, updated via normal deploys |
| Live chat widget | Common SaaS conversion tactic | Third-party script dependency (privacy/cookie implications, extra vendor), no equivalent pattern exists anywhere else in this codebase | The static contact/demo section already covers the "how do I reach someone" need |
| Multi-language toggle (PT/EN) | Common for "reach a wider market" instinct | The entire product's domain language is Portuguese by explicit project convention (`CLAUDE.md`); the target audience is Cape Verdean law firms/institutions. Adding i18n here would be inconsistent with the rest of the app and out of scope | Portuguese-only, matching the rest of LexCV |
| Interactive product tour / live sandbox demo embedded in the landing page | Appears in "high-converting SaaS page" research as a differentiator | Heavyweight engineering (would need a demo tenant environment, safe sandbox data, isolation from the real backend) — disproportionate for a landing page whose real job is "confirm this is our platform + point staff to login" | Curated screenshots (see Differentiators) achieve most of the same "show, don't tell" value at a fraction of the cost |
| "Compare us vs. competitors" page | Standard competitive-positioning SaaS pattern | No named public competitor exists in the Cape Verde legal-tech market to research or reference (search returned no evidence of local competing products), and comparison framing is inconsistent with an institutional-trust, non-adversarial tone | Focus copy entirely on capability + local/ecosystem fit instead of relative positioning |
| Cookie-consent banner / analytics tracking scripts | Often bundled by default with any new marketing page | No analytics/tracking infrastructure exists anywhere else in the app today (privacy-conscious posture is implicit throughout `CLAUDE.md`'s security constraints); adding third-party analytics introduces a compliance surface (consent banner, cookie policy) not requested by the milestone | Ship without analytics for v1; if conversion tracking is wanted later, treat it as its own scoped decision (privacy-respecting, e.g. server-side/first-party only) |

## Feature Dependencies

```
Personalized Hero (nome + logo)
    └──requires──> Public Tenant Branding Endpoint (GET /api/v1/public/...)
                       └──requires──> Tenant already provisioned via /setup (existing, singleton)
                       └──requires──> SecurityConfig permitAll() entry (new, mirrors existing /setup/status pattern)

Setup-Status Redirect Gate
    └──requires──> GET /api/v1/setup/status (existing, already public — zero new backend work)

Dynamic OG/share image (tenant-branded)
    └──requires──> Public Tenant Branding Endpoint (same one as Hero — no separate endpoint needed)

Contact / Pedir Demonstração section
    └──requires──> Static contact config in webpage/ app (NEW, app-level constant/env — NOT sourced from Tenant.email/telefone)
    └──conflicts with──> Sourcing contact info from Tenant table (forbidden: milestone explicitly excludes email/telefone from the public response; also unreliable since /setup never populates them)

Dark/Light mode toggle
    └──requires──> Port of web/src/components/theme-toggle.tsx + providers.tsx pattern (existing, low-risk reuse)

"Prova social / confiança institucional" section
    └──requires──> Architecture facts already shipped (tenant_id isolation, RBAC, audit) — NO new data
    └──conflicts with──> Customer logos / testimonials (structurally unavailable — see Anti-Features)

Persisted "solicitar demonstração" lead capture (deferred/future)
    └──requires──> NEW backend entity + controller + admin visibility (not part of this milestone's confirmed scope)
```

### Dependency Notes

- **Personalized Hero requires the Public Tenant Branding Endpoint:** this is the one genuinely new piece of backend work in the whole feature set. It must be scoped tightly (nome + logoDataUrl only) — the entity already carries fields (`nif`, `email`, `telefone`, `tipoEntidade`) that a careless DTO reuse would leak.
- **Contact section conflicts with sourcing from Tenant data:** this is the most important negative dependency in this research. The natural-looking shortcut ("just expose tenant email/telefone too") is explicitly forbidden by the milestone and also unreliable, since the current `/setup` flow (`SetupInitializeRequest`) never captures those fields in the first place.
- **Prova social conflicts with customer logos/testimonials:** flagged per the quality gate — this is exactly the "don't recommend a testimonials carousel sourced from a table that doesn't exist" trap, made explicit and structural (not just "not built yet" but "cannot exist under the current single-tenant-per-deployment model without new cross-deployment infrastructure").
- **Dynamic OG image enhances Personalized Hero:** reuses the same endpoint, so it's a natural v1.x add-on rather than a new dependency chain.

## MVP Definition

### Launch With (v1)

Minimum viable landing page — validates "this deployment has a real public front door personalized to its institution."

- [ ] Setup-status gate/redirect (port of existing `web/` `proxy.ts` logic) — without this, an uninitialized deployment would show a broken/generic page
- [ ] Public Tenant Branding Endpoint (`nome` + `logoDataUrl` only) — the one required new backend surface
- [ ] Personalized Hero + value proposition headline — the actual milestone goal
- [ ] Funcionalidades/Módulos overview (Clientes, Processos, Agenda/Prazos, Documentos, Financeiro, Notificações) — confirmed content section, zero data dependency
- [ ] Prova social/confiança institucional section using architecture-based trust copy (isolamento de dados, RBAC, ecossistema NOSi/Cabo Verde) — confirmed content section, no fabricated proof
- [ ] Contacto/Pedir demonstração with a static contact channel (mailto or external form embed) — confirmed content section, explicitly NOT sourced from Tenant.email/telefone
- [ ] Primary CTA "Entrar" → `/login`, placed top and bottom
- [ ] Responsive layout + dark/light mode (ported from `web/`)
- [ ] Basic SEO meta (title/description/favicon)

### Add After Validation (v1.x)

- [ ] Dynamic OG/share image with tenant branding — add once the base personalization endpoint is proven stable
- [ ] Curated real-UI screenshots (from seeded/demo data) replacing placeholder illustrations in the hero/features sections
- [ ] Lightweight, privacy-respecting analytics (if the institution wants to measure "Pedir demonstração" conversion) — only if explicitly requested, given no analytics infra exists today

### Future Consideration (v2+)

- [ ] Persisted "solicitar demonstração" lead-capture backend + admin visibility — only if the product's distribution model shifts from "manual procurement" to something needing a tracked pipeline
- [ ] Multi-institution case studies/comparison content — only viable if LexCV is ever deployed to multiple named, consenting Cape Verde institutions and a means to reference them (with permission) is established; currently structurally impossible given single-tenant-per-deployment isolation
- [ ] i18n — only if the product ever targets non-Portuguese-speaking markets, which would be a major strategic shift inconsistent with the entire existing domain-language convention

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Setup-status redirect gate | HIGH | LOW | P1 |
| Public Tenant Branding Endpoint | HIGH | MEDIUM | P1 |
| Personalized Hero | HIGH | LOW (once endpoint exists) | P1 |
| Módulos/Funcionalidades overview | HIGH | LOW | P1 |
| Prova social (architecture-based trust copy) | MEDIUM-HIGH | LOW | P1 |
| Contacto/Pedir demonstração (static) | MEDIUM | LOW-MEDIUM | P1 |
| CTA "Entrar" | HIGH | LOW | P1 |
| Dark/light mode | MEDIUM | LOW | P1 |
| Responsive layout | HIGH | LOW-MEDIUM | P1 |
| Basic SEO meta | MEDIUM | LOW | P1 |
| Curated UI screenshots | MEDIUM | MEDIUM | P2 |
| Dynamic OG image | LOW-MEDIUM | MEDIUM-HIGH | P2 |
| Analytics (privacy-respecting) | LOW | MEDIUM | P3 |
| Persisted demo-request capture | LOW (at current scale — single institution) | HIGH | P3 |
| Customer logos / testimonials | N/A | N/A | Rejected (structurally unavailable, see Anti-Features) |
| Pricing page / self-serve signup | N/A | N/A | Rejected (explicitly out of scope) |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

Note: "competitors" here are the closest available reference points — global legal practice-management SaaS marketing sites — since no Cape Verde-specific competitor was found in research. LexCV's actual positioning (single-tenant institutional deployment, sales-led) differs structurally from all of them.

| Feature | Clio / MyCase / PracticePanther (multi-tenant SaaS) | LexCV `webpage/` (single-tenant institutional) | Our Approach |
|---------|--------------------------------------------------------|--------------------------------------------------|--------------|
| Hero personalization | Generic, same for every visitor (the vendor's own brand) | Personalized per-deployment with the institution's own name/logo | Lean into this as the differentiator — "this is YOUR platform," not a generic vendor pitch |
| Pricing | Public pricing tiers/calculator, self-serve trial CTA | None — no self-serve model exists | Replace with "Pedir demonstração" as the sole conversion path |
| Social proof | Named customer logos, review-site badges (G2/Capterra), testimonials | Cannot use named external customers (single-tenant, no cross-deployment registry) | Substitute architecture/security trust messaging (data isolation, RBAC, audit) — honest and verifiable |
| Feature showcase | Broad feature comparison tables against competitors | Module overview reflecting only this product's actually-shipped capability | Straightforward "o que a plataforma faz" without competitive framing (no local competitor to reference) |
| Language/localization | English-first, sometimes localized | Portuguese-only, Cape Verde legal domain vocabulary throughout | Full alignment with existing product convention — no i18n |
| CTA structure | Multiple CTAs (trial, demo, pricing, contact sales) | Single primary CTA ("Entrar") + single secondary CTA ("Pedir demonstração") | Matches the single/dual-CTA pattern research shows converts best, and matches the actual (narrow) set of real user intents for this deployment |

## Sources

- [Best Practices for Designing B2B SaaS Landing Pages – 2026 (Genesys Growth)](https://genesysgrowth.com/blog/designing-b2b-saas-landing-pages) — MEDIUM confidence
- [18 B2B SaaS Landing Page Best Practices That Convert (SaaS Hero)](https://www.saashero.net/design/saas-landing-page-best-practices/) — MEDIUM confidence
- [Data-Driven B2B SaaS Landing Page CTA Best Practices (SaaS Hero)](https://www.saashero.net/design/b2b-saas-landing-cta-practices/) — MEDIUM confidence (single-CTA conversion stat)
- [26 SaaS landing pages: examples, trends and best practices (Unbounce)](https://unbounce.com/conversion-rate-optimization/the-state-of-saas-landing-pages/) — MEDIUM confidence
- [How to Create a Lawyer Landing Page That Actually Converts (Clio)](https://www.clio.com/blog/lawyer-landing-page/) — MEDIUM confidence (consumer-facing law-firm context, directionally useful for mobile-traffic stat)
- [21 Best Law Firm Landing Page Examples & Inspirations (Landingi)](https://landingi.com/landing-page/law-firm-examples/) — LOW-MEDIUM confidence
- [Best Legal Practice Management Software 2026 (PracticePanther)](https://www.practicepanther.com/blog/best-legal-practice-management-software/) — MEDIUM confidence (module/feature framing reference)
- [Customize Universal Login Page Templates (Auth0 Docs)](https://auth0.com/docs/customize/login-pages/universal-login/customize-templates) — HIGH confidence, official docs
- [Brands | Okta Developer](https://developer.okta.com/docs/concepts/brands/) — HIGH confidence, official docs
- [Landing Page Trust Signals: 10 Proven B2B SaaS Tactics (SaaS Hero)](https://www.saashero.net/design/landing-page-design-trust-signals/) — MEDIUM confidence
- [The role of security badges on SaaS landing page effectiveness (Markettailor)](https://www.markettailor.io/blog/role-of-security-badges-on-saas-landing-page) — MEDIUM confidence (substitute-for-testimonials framing for new products)
- [NOSi | Núcleo Operacional Para a Sociedade de Informação EPE](https://www.nosi.cv/en/) — HIGH confidence, official Cape Verde government-agency source
- [Núcleo Operacional da Sociedade de Informação — Governo de Cabo Verde](https://www.governo.cv/nucleo-operacional-da-sociedade-de-informacao-tem-novo-conselho-de-administracao/) — HIGH confidence, official source
- Direct codebase inspection: `backend/src/main/java/com/lexcv/models/Tenant.java`, `backend/src/main/java/com/lexcv/dtos/SetupInitializeRequest.java`, `backend/src/main/java/com/lexcv/config/SecurityConfig.java`, `web/src/app/setup/page.tsx`, `web/src/components/theme-toggle.tsx`, `web/src/app/providers.tsx`, `.planning/PROJECT.md` (v2.12 milestone section) — HIGH confidence, ground truth

---
*Feature research for: institutional B2B legal-tech landing page, single-tenant deployment personalization*
*Researched: 2026-07-15*
