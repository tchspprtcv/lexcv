---
phase: 99-webpage-nova-app-next-js-de-landing
plan: 03
subsystem: ui
tags: [nextjs, react-server-components, tailwind, asChild-slot, radix-slot, landing-page]

# Dependency graph
requires:
  - phase: 99-01
    provides: "Standalone webpage/ app skeleton, Button (CVA+Slot) primitive, buildable pnpm build"
  - phase: 99-02
    provides: "BrandMark Server Component (XSS-hardened data:image/ guard, Building2 fallback), BrandingResponse type"
provides:
  - "webpage/src/components/site-header.tsx — sticky header Server Component (BrandMark, 3-anchor nav hidden<md, ThemeToggle, 'Entrar' secondary CTA as real <a href=/login>)"
  - "webpage/src/components/hero-section.tsx — Hero Server Component (BrandMark subordinate to H1, accent hairline, eyebrow, locked H1 Display 48px + locked subtitle, 'Entrar' secondary + 'Ver Funcionalidades' ghost scroll CTA)"
  - "webpage/src/components/site-footer.tsx — footer Server Component (BrandMark, 'Entrar' large/primary CTA + ArrowRight, dynamic © {getFullYear()} LexCV fine print)"
affects: [99-04, 100]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CTA anchors rendered via Button's asChild/Slot pattern to keep a real <a href='/login'> in the DOM (never next/link/<Link>) — required for cross-zone navigation to web/'s /login route once Phase 100 wires Caddy"
    - "Tailwind utility-class token order is non-semantic (order-independent) — safe to reorder for literal grep-gate matching without any rendered-output change"

key-files:
  created:
    - webpage/src/components/site-header.tsx
    - webpage/src/components/hero-section.tsx
    - webpage/src/components/site-footer.tsx
  modified: []

key-decisions:
  - "Reordered site-header.tsx's nav className from the plan's literal interfaces-block text ('hidden items-center gap-6 md:flex') to ('hidden md:flex items-center gap-6') — the plan's own Task 1 automated verify gate (`grep -Fq 'hidden md:flex'`) cannot match a substring split by intervening tokens ('items-center gap-6'), so the interfaces block's exact code would fail its own acceptance gate as literally written. Tailwind CSS applies utility classes independently of their order in the class attribute, so this reorder is a zero-risk, behavior-preserving fix (Rule 1) — same 4 classes present, same rendered output, gate now passes literally."

patterns-established: []

requirements-completed: [LP-07, LP-11]

# Metrics
duration: ~20min
completed: 2026-07-15
---

# Phase 99 Plan 03: Header, Hero & Footer Chrome Summary

**Sticky `SiteHeader` + focal-point `HeroSection` (locked H1/subtitle, tenant BrandMark) + `SiteFooter`, all three wiring the "Entrar" CTA as a real `<a href="/login">` via `Button`'s `asChild`/`Slot` pattern — never `next/link` — satisfying LP-11's topo+fundo requirement and LP-07's Hero content contract.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 3
- **Files modified:** 3 (all new)

## Accomplishments
- `SiteHeader` (`webpage/src/components/site-header.tsx`): sticky `h-16` bar (`bg-white/80 dark:bg-[#020617]/80 backdrop-blur-md`, `sticky top-0 z-10`, mirrors `dashboard-shell.tsx`'s header treatment), `BrandMark` on the left, a 3-anchor nav (`#funcionalidades`/`#confianca`/`#contacto`, `hidden md:flex`, blue hover accent) grouped with `ThemeToggle` and the "Entrar" CTA (`variant="secondary"`, `rounded-none`, never `size="sm"`) on the right.
- `HeroSection` (`webpage/src/components/hero-section.tsx`): the page's single focal point — tenant `BrandMark` (small, subordinate) → the Hero's one accent hairline (`bg-blue-600 dark:bg-blue-400`) → bordered uppercase eyebrow "PLATAFORMA DE GESTÃO JURÍDICA" → flat `text-5xl` H1 with the CONTEXT.md-locked title "Gestão jurídica completa para a sua instituição" (no responsive step-down, confirmed via grep on the `<h1` line) → the locked subtitle → "Entrar" secondary CTA + a ghost "Ver Funcionalidades" in-page scroll link to `#funcionalidades`.
- `SiteFooter` (`webpage/src/components/site-footer.tsx`): centered `BrandMark` + the large/primary "Entrar" CTA (`size="lg"` + `ArrowRight`, the second of the two LP-11-required instances) + a fine-print line computing the year server-side via `new Date().getFullYear()` (never a hardcoded year).
- All three components are presentation-only Server Components (`branding: BrandingResponse` prop in, JSX out) — zero data fetching, zero client-side state, zero forms — ready to be composed by `page.tsx` in plan 99-04.
- `pnpm build` (Turbopack) passes cleanly with all three files present together; `pnpm lint` reports 0 errors across the three new files (the project's one pre-existing warning, `@next/next/no-img-element` in `brand-mark.tsx`, was already accepted in 99-02 and is untouched by this plan).
- Zero `next/link`/`<Link>` anywhere across the three files (grep-confirmed, both per-file and cross-file) — the "Entrar" CTA's DOM stays a real `<a>` in both its header and footer instances, per LP-11 and the plan's own T-99-06 threat-register mitigation.

## Task Commits

Each task was committed atomically:

1. **Task 1: site-header.tsx (header sticky + nav âncoras + Entrar secondary)** - `9670b23` (feat)
2. **Task 2: hero-section.tsx (eyebrow + H1 Display + subtítulo + CTAs + branding)** - `355e00a` (feat)
3. **Task 3: site-footer.tsx (marca + Entrar grande + © {ano} LexCV)** - `a2ca6b8` (feat)

_Plan metadata commit (this SUMMARY, owned by orchestrator) is applied separately after this worktree agent completes._

## Files Created/Modified
- `webpage/src/components/site-header.tsx` - sticky header Server Component: `BrandMark` + 3-anchor nav (hidden below `md:`) + `ThemeToggle` + "Entrar" secondary CTA (`asChild` → real `<a href="/login">`)
- `webpage/src/components/hero-section.tsx` - Hero Server Component: `BrandMark` + accent hairline + eyebrow + locked H1 (Display, flat `text-5xl`) + locked subtitle + "Entrar" secondary CTA + "Ver Funcionalidades" ghost scroll CTA
- `webpage/src/components/site-footer.tsx` - footer Server Component: `BrandMark` + "Entrar" large/primary CTA (`size="lg"` + `ArrowRight`) + dynamic `© {getFullYear()} LexCV` fine print

## Decisions Made
- Reordered `site-header.tsx`'s nav `className` token order (see `key-decisions` in frontmatter) so the plan's own literal `grep -Fq 'hidden md:flex'` acceptance gate passes — a zero-behavior-change fix, since Tailwind utility classes are applied independently of their order in the `class` attribute. All four original classes (`hidden`, `items-center`, `gap-6`, `md:flex`) are still present; only their left-to-right order in the string changed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Reordered site-header.tsx nav className so the plan's own Task 1 verify gate could pass**
- **Found during:** Task 1 (site-header.tsx), while running the task's automated `<verify>` block
- **Issue:** The plan's `<interfaces>` block specifies `<nav className="hidden items-center gap-6 md:flex">`, but the same plan's Task 1 `<verify><automated>` gate runs `grep -Fq 'hidden md:flex' src/components/site-header.tsx`. Because `items-center gap-6` sits between `hidden` and `md:flex` in the literal interfaces-block string, that fixed-string grep can never match the interfaces block's own exact code — the plan's acceptance gate was inconsistent with its own reference implementation.
- **Fix:** Reordered the class string to `"hidden md:flex items-center gap-6"` — same four Tailwind utility classes, same rendered behavior (Tailwind applies each utility class independently of its position in the attribute string), now containing `"hidden md:flex"` as a literal contiguous substring.
- **Files modified:** `webpage/src/components/site-header.tsx`
- **Verification:** Re-ran the task's full automated verify sequence (all `grep` gates + `pnpm build`) after the edit — all passed, including the previously-failing `hidden md:flex` gate. Visually/functionally identical to the un-reordered version (Tailwind CSS class order carries no semantic meaning).
- **Committed in:** `9670b23` (Task 1 commit — the fix was applied before the file's first and only commit, so no separate fix-up commit exists)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 — bug, zero-risk Tailwind class reorder)
**Impact on plan:** No scope creep, no behavior change, no visual change. Purely closes a self-inconsistency between the plan's reference code and its own acceptance gate.

## Issues Encountered
- **Fresh worktree, no `node_modules`/`.env.local` (same friction 99-02 documented):** this worktree had no `webpage/node_modules` and no `webpage/.env.local` at spawn time. Ran `pnpm install` (respected the existing committed `pnpm-lock.yaml` with zero modification, confirmed via `git status --short webpage/` returning empty immediately after install) and recreated `webpage/.env.local` (gitignored, `BACKEND_API_ORIGIN=http://localhost:8080` + `NEXT_PUBLIC_API_BASE_PATH=/api/v1`) purely to make `pnpm build` runnable during execution — matches the pattern already established and documented by both `99-01-SUMMARY.md` and `99-02-SUMMARY.md`.
- **Bash-tool shell-quoting artifact (tooling, not a code defect):** inline single-quoted grep patterns containing embedded double quotes (e.g. `grep -Fq 'href="/login"' file`) produced unreliable/inconsistent pass-fail results when run as inline multi-command Bash-tool calls in this Windows/Git-Bash environment — sometimes reporting a false "not found" for strings confirmed present via `grep -n`. Worked around this by writing each task's verification as a standalone `.sh` script file (via the Write tool, which has no shell-argument-quoting exposure) and executing it with `bash script.sh` — this produced fully consistent, reliable results across repeated runs. This is an execution-environment quirk of how this session's Bash tool marshals command strings, not a defect in any project file; documented here so a future agent in this same environment isn't misled by an apparent grep "failure" that is actually a quoting artifact. No project code or plan content was at fault for this specific symptom (distinct from the separate, real Task 1 plan-gate inconsistency documented above).

## User Setup Required

None - no external service configuration required. `webpage/.env.local` (gitignored, not committed) was recreated locally in this worktree purely to make `pnpm build` verifiable during execution — same requirement already documented by `99-01-SUMMARY.md`/`99-02-SUMMARY.md` (any environment running this app needs `BACKEND_API_ORIGIN` + `NEXT_PUBLIC_API_BASE_PATH` from `webpage/.env.example`).

## Next Phase Readiness
- `SiteHeader`, `HeroSection`, and `SiteFooter` are all ready for plan 99-04 to import and compose into `page.tsx` (alongside the Funcionalidades/Confiança/Contacto sections 99-04 builds), each taking the same `branding: BrandingResponse` prop shape already produced by `fetchBranding()` (99-02).
- LP-11 is now fully satisfied at the component level: both required "Entrar" instances (header/topo, footer/fundo) exist as real `<a href="/login">` elements via `asChild`/`Slot`, zero `next/link` anywhere in the three files — ready for Phase 100's Caddy cross-zone wiring to resolve `/login` against `web/`.
- LP-07 is fully satisfied: the Hero's locked H1/subtitle content, eyebrow, and CTA row are in place and confirmed as the largest/boldest element on the page (flat `text-5xl`, no competing accent below the fold within this plan's scope).
- No blockers for 99-04. One non-blocking item carried forward from 99-03's own scope boundary (matching the pattern of 99-01/99-02's own `<human-check>` annotations): live visual confirmation of the Hero/header/footer (dark/light parity, sticky-scroll behavior, hover accent) was not performed in this non-interactive execution — the plan's own `<verify><human-check>` on Task 2 explicitly defers this to plan 99-04, once `page.tsx` actually composes and renders these components together.

---
*Phase: 99-webpage-nova-app-next-js-de-landing*
*Completed: 2026-07-15*

## Self-Check: PASSED

All 3 created files verified present on disk (`webpage/src/components/site-header.tsx`, `webpage/src/components/hero-section.tsx`, `webpage/src/components/site-footer.tsx`). All 3 task commits (`9670b23`, `355e00a`, `a2ca6b8`) verified present in git log.
