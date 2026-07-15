---
phase: 99-webpage-nova-app-next-js-de-landing
verified: 2026-07-15T13:05:00Z
status: passed
score: 5/5 ROADMAP success criteria verified; 10/10 LP requirements satisfied
overrides_applied: 0
---

# Phase 99: webpage/ — Nova App Next.js de Landing — Verification Report

**Phase Goal:** Existe uma nova aplicação Next.js 16 standalone `webpage/`, estruturalmente pronta para coexistir com `web/` sob o mesmo domínio via Multi-Zones (`assetPrefix` próprio), que serve a landing page completa e personalizada — verificada isoladamente nesta fase via `pnpm dev`/build próprios (a integração real com Caddy/routing fica para a Phase 100).
**Verified:** 2026-07-15T13:05:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Independent CR-01 Fix Verification (priority focus of this pass)

The task specifically requested independent re-verification of CR-01 (the relative-URL/module-scope-throw bug that could silently disable the `/setup` gate or crash the site), rather than trusting the 3-iteration review/fix chain's self-reported success. This was done from first principles, not by re-reading the fix report's own claims:

1. **Read the actual current source directly** (not the review's excerpts): `webpage/src/lib/backend-origin.ts`, `webpage/src/lib/setup.ts`, `webpage/src/lib/branding.ts`, `webpage/proxy.ts`.
   - Confirmed `getBackendOrigin()` builds an **absolute URL** by validating `BACKEND_API_ORIGIN` has an `http(s)://` scheme and stripping trailing slashes (`backend-origin.ts:23-38`).
   - Confirmed the call site moved **inside** the async function bodies: `setup.ts:8` (`fetchSetupStatus`'s first line) and `branding.ts:11` (first line inside `fetchBranding`'s own `try` block) — matching the fix report's claim, verified by direct inspection, not trust.
2. **Built an independent Node.js reproduction** (own files, own reasoning, not copy-pasted from the fix report) mirroring the exact import chain (`backend-origin.mjs` → `setup.mjs`/`branding.mjs` → `proxy.mjs`), and ran it in a **fresh process per case** for all 4 `BACKEND_API_ORIGIN` inputs:
   - `http://localhost:8080` → `IMPORT_OK`, proxy takes the success path, branding returns real data.
   - `http://localhost:8080/` → `IMPORT_OK`, trailing slash stripped correctly, no double-slash.
   - `localhost:8080` (missing scheme — the exact regression case) → `IMPORT_OK` (no module-load crash), proxy **caught via its fail-open catch** (`"NEXT() via fail-open catch"`), branding returns `{nome:"LexCV", logoDataUrl:null}`.
   - unset → same graceful degradation as above.
3. **Went beyond the review chain's own testing**: rather than stopping at an isolated script reproduction, ran the **real, compiled webpage/ dev server** with `BACKEND_API_ORIGIN` pointed at a non-routable address (`http://10.255.255.1:8080`), forcing a genuine network-level hang. The request to `/` took **3.47s** (matching `setup.ts`'s `AbortSignal.timeout(3000)` almost exactly) and still returned **HTTP 200** — empirical, end-to-end proof that (a) `proxy.ts` genuinely executes on every real request (not dead code — this also resolves my own initial suspicion raised by Turbopack's empty `middleware-manifest.json`, confirmed via this timing test to be exactly the "manifest-format quirk" the 99-02-SUMMARY described, not broken wiring), and (b) the fail-open catch genuinely fires and lets the page render rather than crashing or hanging indefinitely.

**Conclusion: CR-01's fix is sound.** A malformed or missing `BACKEND_API_ORIGIN` degrades gracefully in both consumers (`proxy.ts` fails open to `NextResponse.next()`; `page.tsx`'s `fetchBranding()` falls back to `{nome:"LexCV", logoDataUrl:null}`) instead of crashing module import. This matches the review chain's conclusion, but was independently re-derived here with a more rigorous, live-server empirical test than either the review or the fix report performed.

One caveat carried forward honestly (not new, already flagged by the review chain as IN-03, still unresolved): the `catch {}` blocks in both `proxy.ts` and (implicitly) `branding.ts` remain silent — no `console.error`/logging. A production misconfiguration (e.g., a scheme-less `BACKEND_API_ORIGIN`) will now fail open *correctly* but *invisibly* — the site keeps working, but nobody is alerted that setup-gating/branding personalization is broken. This is a legitimate, already-surfaced, non-blocking observability gap (see Anti-Patterns below), not a functional defect in the fix itself.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — the roadmap contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Visiting `/` on the standalone dev server always shows the full landing, authenticated or not — `proxy.ts` contains only the "not-initialized→/setup" branch, zero `useMe()`/auth branch | ✓ VERIFIED | Read `webpage/proxy.ts` directly: only `fetchSetupStatus()` + redirect-if-not-initialized + fail-open catch. Grep-confirmed zero `access_token`/`login`/`DASHBOARD` anywhere in the file. Empirically confirmed the proxy genuinely executes per-request (see CR-01 section above) and fails open correctly under a hung/broken backend. |
| 2 | When initialized, landing shows Hero (title+subtitle+value prop), Módulos (6: Clientes/Processos/Agenda-Prazos/Documentos/Financeiro/Notificações), Confiança (exactly 4 verifiable claims, no fabricated testimonials), Contacto (fixed `mailto:`, never `Tenant.email`/`telefone`), with tenant nome+logo shown in Hero | ✓ VERIFIED | Read `hero-section.tsx`, `features-section.tsx`, `trust-section.tsx`, `contact-section.tsx` directly — all content matches `99-CONTEXT.md`/`99-UI-SPEC.md` verbatim. Live-rendered HTML (own `pnpm dev` + `curl`, backend down) confirms all 3 section ids, all 6 module titles, all 4 trust titles, and the mailto CTA are present in the actual DOM output, plus the `LexCV` fallback branding rendering correctly in the Hero/header/footer. |
| 3 | CTA "Entrar" is a simple `<a href="/login">`, never `next/link`/`<Link>`, visible top and bottom | ✓ VERIFIED | `grep` for `href="/login"` across `webpage/src` finds exactly 3 instances (`site-header.tsx`, `hero-section.tsx`, `site-footer.tsx` — header+footer satisfy "topo e fundo"; hero is a bonus third instance). `grep -r "next/link"` across the entire `webpage/src` tree and project root returns zero matches. Live-rendered HTML confirms 3× `href="/login"` present in actual DOM output. |
| 4 | Page is responsive and supports dark/light via a ported `next-themes` provider, reusing shadcn/ui components and Tailwind conventions copied manually from `web/`, zero new UI dependencies | ✓ VERIFIED (one documented, non-blocking, net-positive deviation) | `providers.tsx` confirmed theme-only (`NextThemesProvider`, no `QueryClientProvider`). `diff` confirms `utils.ts`, `button.tsx`, `card.tsx`, `globals.css` are byte-for-byte identical to `web/`'s. `package.json` confirmed to exclude `@tanstack/react-query`/`react-hook-form`/`zod`/extra `@radix-ui/*`, with exact pinned version strings matching `web/package.json`. One deviation: `theme-toggle.tsx` is **no longer** byte-for-byte identical to `web/`'s — a later code-review fix (`4d11b32`, WR-04) switched `useTheme()`'s destructured `theme` to `resolvedTheme` to fix a real first-click no-op bug with `defaultTheme="system"`. This is a legitimate, targeted bug fix scoped only to `webpage/` (matches this phase's own boundary: "não modifica `web/`"), not a functional regression — dark/light mode works, arguably better than before. |
| 5 | `next.config.ts` defines its own `assetPrefix` (e.g. `/landing-static`) and `output: 'standalone'`, so `_next/static/*` chunks are structurally distinct from `web/`'s | ✓ VERIFIED | Confirmed in `webpage/next.config.ts`: `assetPrefix: "/landing-static"`, `output: "standalone"`. **Empirically confirmed** via a live `curl -D -` against the running dev server: the `link` preload header returns asset URLs prefixed `/landing-static/_next/static/...` — proving the prefix is genuinely applied to emitted asset URLs, not just declared in config. |

**Score:** 5/5 ROADMAP success criteria verified.

### Requirements Coverage (LP-03 through LP-12)

| Requirement | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| LP-03 | 99-01 | Standalone Next.js app `webpage/`, independent of `web/` | ✓ SATISFIED | Own `package.json`/`tsconfig.json`/`pnpm-lock.yaml`; no root `pnpm-workspace.yaml`; `pnpm build` succeeds independently (ran it myself — succeeded with the backend **not running**, confirming full independence). |
| LP-04 | 99-02 | Authenticated or not, visitor always sees the landing — never auto-redirected to `/dashboard`/`/login` | ✓ SATISFIED | `proxy.ts` contains zero auth branch (grep-confirmed absence of `access_token`/`login`/`dashboard`, case-insensitive). |
| LP-05 | 99-02 | Not-initialized system → redirect to `/setup` | ✓ SATISFIED (code-verified; live redirect trigger not exercised — see note) | `proxy.ts:13-15` implements exactly this branch. A live trigger of the actual redirect requires a backend running with an uninitialized DB — out of this phase's own declared scope (`/setup` doesn't even exist in `webpage/`'s route table; only resolves cross-zone via Caddy, explicitly deferred to Phase 100 per both `99-CONTEXT.md` and the ROADMAP goal statement). The fail-open path (the harder case) was empirically exercised and confirmed correct. |
| LP-06 | 99-02, 99-04 | Landing shows tenant nome+logo when initialized (via LP-01 endpoint) | ✓ SATISFIED | `fetchBranding()` correctly calls `BACKEND_API_ORIGIN/api/v1/public/branding` server-to-server, parses `{nome, logoDataUrl}` matching Phase 98's documented DTO shape, passed via props to `SiteHeader`/`HeroSection`/`SiteFooter`. Live-tested (backend down): fallback `{nome:"LexCV", logoDataUrl:null}` renders correctly end-to-end. The "real tenant name+logo" success path is code-verified (straightforward, deterministic mapping) but not live-tested against a running backend in this pass — reasonable given Phase 100 owns the full `docker compose up` integration test. |
| LP-07 | 99-03 | Hero section with title, subtitle, value proposition | ✓ SATISFIED | `hero-section.tsx` contains the exact locked H1 ("Gestão jurídica completa para a sua instituição") and subtitle, plus eyebrow + accent hairline, confirmed both in source and in live-rendered HTML. |
| LP-08 | 99-04 | Funcionalidades/Módulos section: Clientes, Processos, Agenda/Prazos, Documentos, Financeiro, Notificações | ✓ SATISFIED | `features-section.tsx`, `id="funcionalidades"`, all 6 modules present with correct icons/copy, `text-blue-600 dark:text-blue-400` accent, grid `1→md:2→lg:3`. |
| LP-09 | 99-04 | Prova Social/Confiança Institucional: verifiable claims only (isolamento, RBAC, auditoria, NOSi/CV), never fabricated testimonials | ✓ SATISFIED | `trust-section.tsx`, `id="confianca"`, exactly 4 cards (counted directly), icons `text-slate-900 dark:text-slate-100` (confirmed absent of any `text-blue-600` — non-accent, per UI-SPEC), no counters/testimonials anywhere in the file. |
| LP-10 | 99-04 | Contacto/Pedir Demonstração: fixed `mailto:` link, never sourced from `Tenant.email`/`telefone` | ✓ SATISFIED | `contact-section.tsx` hardcodes `mailto:contacto@lexcv.cv?subject=...`; the component does not even receive a `branding` prop, making it structurally impossible to leak `Tenant.email`. |
| LP-11 | 99-03 | "Entrar" CTA is a plain `<a href="/login">`, never `next/link`, visible top and bottom | ✓ SATISFIED | 3× `href="/login"` confirmed (header, hero, footer); zero `next/link` anywhere in `webpage/src`. |
| LP-12 | 99-01 | Responsive, dark/light mode, reusing shadcn/ui + Tailwind conventions from `web/` | ✓ SATISFIED (see truth #4 note) | `next-themes` ported theme-only; `Button`/`Card`/`cn()`/`globals.css` byte-for-byte identical to `web/`; `theme-toggle.tsx` deviates by one line for a documented bug fix (net improvement, not a regression); zero new UI dependencies added. |

**Requirements coverage: 10/10 satisfied. Zero orphaned requirements** — cross-referenced against `.planning/REQUIREMENTS.md`'s traceability table (LP-03 through LP-12 all map to Phase 99) and against the `requirements:` frontmatter of all 4 plans (99-01: LP-03,LP-12; 99-02: LP-04,LP-05,LP-06; 99-03: LP-07,LP-11; 99-04: LP-06,LP-08,LP-09,LP-10) — the union is exactly the 10 IDs ROADMAP.md declares for this phase, no more, no less.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `webpage/package.json` | Standalone manifest, curated deps, port 3001 scripts | ✓ VERIFIED | Confirmed: no react-query/react-hook-form/zod; exact pinned versions matching `web/package.json`. |
| `webpage/next.config.ts` | `assetPrefix`, `output: standalone`, security headers | ✓ VERIFIED (rewrite + module-scope fail-fast removed by reviewed fix WR-02 — see Anti-Patterns) | `assetPrefix`/`standalone`/headers all present and empirically confirmed live. |
| `webpage/src/app/providers.tsx` | Theme-only provider | ✓ VERIFIED | No `QueryClientProvider`. |
| `webpage/src/app/layout.tsx` | Geist fonts + Providers, no Toaster, scrollable body | ✓ VERIFIED | Confirmed no `Toaster`/`overflow-hidden`. |
| `webpage/src/components/ui/button.tsx`, `card.tsx` | Byte-for-byte from `web/` | ✓ VERIFIED | `diff` confirms identical. |
| `webpage/src/components/theme-toggle.tsx` | Byte-for-byte from `web/` | ⚠️ 1-line deviation (documented, accepted — WR-04 fix) | `resolvedTheme` vs `theme`; functional improvement. |
| `webpage/proxy.ts` | Setup-gate only, fail-open | ✓ VERIFIED, wiring empirically confirmed | See CR-01 section — proven to genuinely execute per-request via timeout test. |
| `webpage/src/lib/setup.ts`, `branding.ts`, `backend-origin.ts` | Fetch logic, fail-open, absolute URL validation | ✓ VERIFIED | See CR-01 section. |
| `webpage/src/components/brand-mark.tsx` | XSS-guarded logo render, Building2 fallback | ✓ VERIFIED (hardened further by WR-05) | Regex narrowed to `data:image/(png|jpe?g|gif|webp);base64,` explicitly excluding `svg` — a security improvement over the plan's original looser guard. |
| `webpage/src/components/site-header.tsx`, `hero-section.tsx`, `site-footer.tsx` | Chrome components | ✓ VERIFIED | Content matches UI-SPEC verbatim; live-rendered. |
| `webpage/src/components/features-section.tsx`, `trust-section.tsx`, `contact-section.tsx` | Content sections | ✓ VERIFIED | All copy/icons/ids match UI-SPEC verbatim; live-rendered. |
| `webpage/src/app/page.tsx` | Real assembly, `force-dynamic`, single fetch | ✓ VERIFIED | Confirmed structure; build succeeds with backend down (proves `force-dynamic` decouples build from backend). |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `layout.tsx` | `providers.tsx` | `<Providers>` wraps children | ✓ WIRED | Confirmed in source. |
| `providers.tsx` | `next-themes` | `NextThemesProvider attribute=class defaultTheme=system` | ✓ WIRED | Confirmed. |
| `next.config.ts` | asset URLs | `assetPrefix` | ✓ WIRED, empirically confirmed | Live `link` preload headers show `/landing-static/_next/static/...`. |
| `proxy.ts` | `fetchSetupStatus` → `/setup` | redirect when `!initialized` | ✓ WIRED, empirically confirmed | Proven via non-routable-IP timeout test (proxy genuinely runs and its fail-open path genuinely fires). |
| `branding.ts` | `BACKEND_API_ORIGIN/api/v1/public/branding` | server-to-server fetch | ✓ WIRED | Confirmed server-only import chain (`page.tsx` → `branding.ts`), zero browser-side call. |
| `brand-mark.tsx` | `logoDataUrl`/`Building2` | guarded conditional render | ✓ WIRED | Confirmed guard logic; fallback rendering live-confirmed. |
| `site-header.tsx`/`site-footer.tsx` | `<a href=/login>` | `Button asChild`/`Slot` | ✓ WIRED | Confirmed DOM stays a real `<a>`, live-rendered. |
| `page.tsx` | all 6 sections | composition | ✓ WIRED | Confirmed all 6 imported and rendered; live HTML shows all 3 section ids + Hero + header + footer. |
| `site-header.tsx` nav | section `id`s | anchor hrefs match | ✓ WIRED | `#funcionalidades`/`#confianca`/`#contacto` present both in nav and as section `id`s. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `SiteHeader`/`HeroSection`/`SiteFooter` | `branding` prop | `page.tsx`'s single `await fetchBranding()` | Yes — real fetch to Phase 98's endpoint, with correct fail-open fallback (not a hardcoded empty stub) | ✓ FLOWING |
| `FeaturesSection`/`TrustSection`/`ContactSection` | none (static copy arrays) | hardcoded `MODULOS`/`CONFIANCA` arrays, by design (CONTEXT.md: static content, zero tenant data) | N/A — intentionally static | ✓ FLOWING (by design) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| `pnpm build` succeeds, backend unreachable | `pnpm build` (backend confirmed down via `curl` timeout first) | Compiled successfully in 5.1s; TypeScript passed; `/` shown as `ƒ (Dynamic)` | ✓ PASS |
| `pnpm lint` clean | `pnpm lint` | 0 errors, 1 pre-existing accepted warning (`@next/next/no-img-element` in `brand-mark.tsx`) | ✓ PASS |
| `tsc --noEmit` clean | `npx tsc --noEmit -p tsconfig.json` | "No errors found", exit 0 | ✓ PASS |
| Live root request renders full landing | `pnpm dev` + `curl http://localhost:3099/` | HTTP 200; all 3 section ids, all 6 module titles, all 4 trust titles, mailto CTA, 3× `href="/login"`, `LexCV` fallback branding, no error/crash banner (only routine Next.js `global-error` boilerplate strings) present in rendered HTML | ✓ PASS |
| Asset URLs use `/landing-static` prefix | `curl -D -` response `link` header | `</landing-static/_next/static/media/...>`, `</landing-static/_next/static/chunks/...>` | ✓ PASS |
| Security headers present | `curl -D -` response headers | `X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS, CSP with `img-src 'self' data:` all present | ✓ PASS |
| Fail-open under a genuinely hung backend | `pnpm dev` with `BACKEND_API_ORIGIN=http://10.255.255.1:8080` (non-routable) + timed `curl` | HTTP 200 after 3.47s (matches the 3s `AbortSignal.timeout`) — proxy genuinely executes and fails open rather than crashing or hanging indefinitely | ✓ PASS |
| `web/`/`backend/` untouched | `git status --short web/ backend/` | Empty output | ✓ PASS |
| `webpage/` git-clean after this verification's own testing | `git status --short webpage/` | Empty output | ✓ PASS |

### Anti-Patterns Found

All four Info-level items from `99-REVIEW.md` were independently re-confirmed as still present in the current source (not just trusted from the review doc) — none are new findings, and none block any LP-03..LP-12 requirement, but are surfaced here per the adversarial-verification mandate.

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `webpage/proxy.ts` | 16-18 | `catch {}` with zero logging | ⚠️ WARNING | Now that CR-01's fix routes real `BACKEND_API_ORIGIN` misconfigurations through this exact catch, a broken config in production would fail open **silently** — the site keeps working but nobody is alerted that the `/setup` gate (and by the same mechanism, branding personalization) is non-functional. Already flagged by the review chain (IN-03) as "a necessary companion, not just a nice-to-have," explicitly deferred out of this phase's fix-loop scope. Does not block any LP-03..LP-12 requirement (none mandate logging/observability); worth a deliberate follow-up decision before Phase 100 production deployment. |
| `webpage/src/lib/branding.ts` | 21-22 | `(await response.json()) as BrandingResponse` unvalidated cast; `data.nome \|\| "LexCV"` doesn't check `typeof` | ℹ️ INFO | If Phase 98's endpoint ever returned a non-string `nome` (contract drift), it could reach `<span>{nome}</span>` unexpectedly. Narrow, low-likelihood (Phase 98's own DTO is typed), non-blocking. |
| `webpage/.env.example` | 2 | `NEXT_PUBLIC_API_BASE_PATH` is dead/orphaned config | ℹ️ INFO | Confirmed via grep: referenced nowhere in `webpage/src`, only in its own declaration. Could mislead a future deployer. Non-blocking. |
| `webpage/src/components/brand-mark.tsx` | 25 | Tenant logo `<img alt="">` | ℹ️ INFO | Accessibility nice-to-have; logo conveys institution identity, empty `alt` hides it from screen readers. Non-blocking. |
| `webpage/next.config.ts` | 22 | `// TODO: 'unsafe-inline' still allows inline-script XSS payloads; migrate to a nonce-/hash-based script-src (see 99-REVIEW.md WR-01) as follow-up.` | ℹ️ INFO | A `TODO`, not `TBD`/`FIXME`/`XXX` (the debt-marker-gate's stricter tier) — and it references a specific, traceable follow-up (`99-REVIEW.md WR-01`), so it is not an unauditable/unreferenced marker. Documented, deliberate, non-blocking hardening deferral. |

**Noted plan-deviations (not anti-patterns, but literal-text mismatches vs. the original PLAN.md `<interfaces>` blocks — both are net-positive, reviewed fixes, not defects):**
- `webpage/next.config.ts` no longer has the `/api/v1/:path*` rewrite nor the module-scope `BACKEND_API_ORIGIN` fail-fast throw the 99-01-PLAN.md's `<interfaces>` block specified — both were deliberately removed by the reviewed WR-02 fix (`282202d`) because the rewrite was dead code that would have unnecessarily exposed the full backend surface through this public, unauthenticated marketing site's origin (no code path used it — every server-side fetch calls `BACKEND_API_ORIGIN` directly with an absolute URL). Fail-fast-on-missing-origin behavior moved to `getBackendOrigin()` (still throws a clear, descriptive error) but is now caught gracefully by callers rather than crashing `next.config.ts`'s module load. This does not affect ROADMAP Success Criterion 5 (which only requires `assetPrefix` + `output: standalone`, both still present).
- `webpage/src/components/theme-toggle.tsx` is no longer byte-for-byte identical to `web/`'s (see Observable Truth #4).

## Human Verification Required

None required as a new blocking item. Per the task's explicit framing, the orchestrator's own live browser session (dark mode toggle, both `/login` links, all 6 module cards, all 4 trust cards, backend down confirming the `LexCV` fallback) is treated as already-satisfied human/live verification for LP-04 and LP-07 through LP-12. I found no reason to distrust that test — my own independent, separate live-server testing (curl-based HTML inspection, response-header inspection, and a forced-timeout empirical proxy test) corroborates the same behaviors end-to-end, including going beyond the orchestrator's own test by proving proxy.ts genuinely executes per-request (not just present in a bundle).

One item is worth a light, non-blocking follow-up note for whoever plans Phase 100: the "real tenant name+logo" success path of `fetchBranding()` (as opposed to the fallback path, which is thoroughly verified) has only been code-verified in this phase, not exercised against a live backend with an initialized tenant and a real logo. This is a reasonable scope boundary for an isolated `pnpm dev`/build phase (Phase 100 owns the full `docker compose up` integration test), not a gap in Phase 99's own goal.

## Gaps Summary

No gaps found. All 5 ROADMAP Success Criteria are verified against the actual codebase (not SUMMARY.md claims), all 10 LP-03 through LP-12 requirements are satisfied with direct evidence, all key links are wired (with `proxy.ts`'s wiring specifically proven via an empirical forced-timeout test rather than just a bundle/sourcemap grep), and the CR-01 fix — the single most consequential finding from this phase's 3-iteration review/fix loop — was independently re-derived and confirmed sound through both a from-scratch Node reproduction and a live-server empirical test.

The only items surfaced are already-known, already-documented, non-blocking Info/Warning-level follow-ups from the review chain (IN-01 through IN-04, re-confirmed still present) plus two literal-text deviations from the original PLAN.md interfaces (`next.config.ts`'s removed rewrite/fail-fast, `theme-toggle.tsx`'s one-line fix) — both net-positive, reviewed, and non-blocking to the phase's actual goal and requirements.

---

_Verified: 2026-07-15T13:05:00Z_
_Verifier: Claude (gsd-verifier)_
