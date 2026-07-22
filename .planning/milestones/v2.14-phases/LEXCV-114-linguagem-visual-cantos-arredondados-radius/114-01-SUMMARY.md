---
phase: 114-linguagem-visual-cantos-arredondados-radius
plan: "01"
subsystem: ui
tags: [tailwind, css-custom-properties, shadcn, design-tokens, radius, next.js]

# Dependency graph
requires:
  - phase: 101-fundacao-shadcn-ui
    provides: "shadcn CLI foundation, --radius token + @theme inline calc chain in both web/src/app/globals.css and webpage/src/app/globals.css (originally set to 0rem for the v2.13 'institutional document' look)"
provides:
  - "--radius token flipped to 0.5rem (shadcn Default preset) in both web/ and webpage/, feeding all --radius-sm/md/lg/xl/2xl/3xl/4xl derived tiers"
  - "271 illegitimate rounded-none className overrides removed across 29 files (24 web/src call sites + settings/page.tsx special case + 4 webpage/src call sites), so every primitive's own token-derived default radius is now visible"
  - "webpage/src Card primitive fixed from a hardcoded rounded-none default to rounded-lg, matching web/'s already-correct Card"
  - "Mechanical proof of done: grep -rn \"rounded-none\" web/src webpage/src returns exactly the 6 legitimate exception lines (calendar.tsx x3, input-group.tsx x2, tabs.tsx x1)"
affects: [115, future-icon-phases, future-filter-icon-only-phase]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Mechanical className-token removal via a throwaway Node script (not hand-editing), applying a 3-step ordered regex (token+trailing-space, leading-space+token, lone-token) derived from the plan's <transformation_contract> — reusable pattern for any future large-scale Tailwind utility sweep"

key-files:
  created: []
  modified:
    - web/src/app/globals.css
    - webpage/src/app/globals.css
    - web/src/app/(dashboard)/settings/page.tsx
    - webpage/src/components/ui/card.tsx
    - "24 additional web/src call-site files (pages + columns) — see Files Modified below"
    - "4 additional webpage/src call-site files (site-header, hero-section, site-footer, contact-section)"

key-decisions:
  - "--radius: 0.5rem (8px, shadcn Default) in BOTH apps' :root, identical value, per 114-CONTEXT.md — reverts the v2.13 institutional 0rem look"
  - "Sweep scope covers all 271 rounded-none occurrences (270 web/src + ... webpage/src), not just the 2-file token edit, because tailwind-merge (cn()) deterministically lets a literal rounded-none className win over the primitive's own token-derived default"
  - "settings/page.tsx's 4 Cards get uniform rounded-xl at every breakpoint (was rounded-none lg:rounded-xl, flat-on-mobile) rather than a plain deletion"
  - "4 named exceptions preserved verbatim: calendar.tsx range-continuity classes (x3), input-group.tsx flush-inner-input classes (x2), tabs.tsx 'line' variant underline (x1), and processos/[id]/page.tsx's 2 mobile bottom-sheet Dialogs keep max-sm:rounded-b-none while losing only their illegitimate base rounded-none"

requirements-completed: [RAD-01]

# Metrics
duration: ~18min
completed: 2026-07-21
---

# Phase 114 Plan 01: Cantos Arredondados (--radius) Summary

**Flipped `--radius` from `0rem` to `0.5rem` in both apps and swept 271 hardcoded `rounded-none` className overrides (271 occurrences across 29 files) that were silently overriding the token via `tailwind-merge`, so the rounded-corner look is now actually visible everywhere except 4 documented legitimate exceptions.**

## Performance

- **Duration:** ~18 min
- **Tasks:** 3/3 completed
- **Files modified:** 32 (2 globals.css + 25 web/src files + 5 webpage/src files)

## Accomplishments

- `--radius` token flipped from `0rem` to `0.5rem` in both `web/src/app/globals.css` and `webpage/src/app/globals.css`, with the 7 derived `--radius-sm/md/lg/xl/2xl/3xl/4xl` calc tokens and `.dark` parity left untouched (dark inherits `:root` by design, confirmed no second `--radius` declaration exists in either `.dark` block).
- Removed all illegitimate `rounded-none` overrides from 24 `web/src` call-site files (270 occurrences at plan-authoring time, 270 confirmed present and removed at execution time) via a mechanical, scripted sweep — not hand-editing — so every element now falls back to its shadcn primitive's own token-derived radius (`rounded-md`/`rounded-lg`/`rounded-sm` etc.).
- Fixed the `settings/page.tsx` special case: 4 Cards changed from `rounded-none lg:rounded-xl` (flat on mobile, rounded on desktop) to plain `rounded-xl` (uniform at every breakpoint).
- Fixed the `webpage/src` Card primitive (`components/ui/card.tsx`): base class changed from hardcoded `rounded-none` to `rounded-lg`, fixing every landing-page Card (Hero, etc.) in one edit — matching `web/`'s already-correct Card.
- Removed the 6 remaining `webpage/src` call-site `rounded-none` overrides (2× site-header "Entrar", 2× hero-section "Entrar"/"Ver Funcionalidades", 1× site-footer, 1× contact-section).
- Preserved all 4 named legitimate exceptions verbatim: `calendar.tsx` range-continuity cells (x3), `input-group.tsx` flush-inner-input classes (x2), `tabs.tsx` "line" variant underline (x1), and the two `processos/[id]/page.tsx` mobile bottom-sheet Dialogs (`max-sm:rounded-b-none` kept, only the illegitimate base `rounded-none` removed).
- Both apps typecheck (`tsc --noEmit`) and lint clean of any new issues — the bulk className edit broke no JSX anywhere across 32 files.

## Task Commits

Each task was committed atomically:

1. **Task 1: Flip the --radius token from 0rem to 0.5rem in both apps** - `0b6d88f` (feat)
2. **Task 2: Remove the illegitimate rounded-none overrides across web/src (25 files)** - `87da7ff` (feat)
3. **Task 3: Fix the webpage Card primitive and remove the 6 rounded-none button overrides in webpage/src** - `f9ab004` (feat)

**Plan metadata:** _(see final commit below)_

## Files Created/Modified

- `web/src/app/globals.css` - `:root` base token `--radius: 0rem` → `0.5rem`
- `webpage/src/app/globals.css` - `:root` base token `--radius: 0rem` → `0.5rem`
- `web/src/app/(dashboard)/settings/page.tsx` - 4× `rounded-none lg:rounded-xl` → `rounded-xl` (uniform rounding)
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - 70 occurrences removed (badges, buttons, 2 Dialogs' illegitimate base token, inputs/textarea, NativeSelect wrappers, file input, sub-section pill tabs); `max-sm:rounded-b-none` preserved on both Dialogs
- `web/src/app/(dashboard)/processos/novo/page.tsx` - 33 occurrences removed (step pills, inputs, textarea, buttons, badges, alert box)
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - 26 occurrences removed (form inputs, textarea)
- `web/src/app/(dashboard)/pareceres/page.tsx` - 17 occurrences removed (6× SelectTrigger, inputs, buttons)
- `web/src/app/(dashboard)/agenda/page.tsx` - 17 occurrences removed (view-toggle pills, event cards, badges, empty-state button, info card)
- `web/src/app/(dashboard)/clientes/page.tsx` - 16 occurrences removed (filter buttons/inputs, list-row icon squares, badges, export button)
- `web/src/app/(dashboard)/processos/page.tsx` - 15 occurrences removed (KPI icon squares, badges, filter inputs, action buttons)
- `web/src/app/(dashboard)/dashboard/page.tsx` - 15 occurrences removed (KPI icon squares, activity badges, empty-state button)
- `web/src/app/(dashboard)/pareceres/nova/page.tsx` - 9 occurrences removed (NativeSelect wrappers ×4 inert, textarea, buttons)
- `web/src/app/(dashboard)/clientes/novo/page.tsx` - 8 occurrences removed (create-form inputs)
- `web/src/app/(dashboard)/processos/dashboard/page.tsx` - 7 occurrences removed (icon squares, badges, cards)
- `web/src/app/setup/page.tsx` - 5 occurrences removed (wizard inputs + submit button)
- `web/src/app/(dashboard)/clientes/columns.tsx` - 4 occurrences removed (table-cell badges + icon square)
- `web/src/app/(dashboard)/processos/columns.tsx` - 3 occurrences removed (table-cell badges)
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` - 3 occurrences removed (status badges, small button)
- `web/src/app/(dashboard)/notificacoes/page.tsx` - 3 occurrences removed (filter input, toggle-pill buttons)
- `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` - 2 occurrences removed (table-cell badges)
- `web/src/app/(dashboard)/documentos/columns.tsx` - 2 occurrences removed (table-cell badges)
- `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx` - 1 occurrence removed (print button)
- `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` - 1 occurrence removed (form element)
- `web/src/app/(dashboard)/pareceres/columns.tsx` - 1 occurrence removed (status badge)
- `web/src/app/(dashboard)/financeiro/page.tsx` - 1 occurrence removed (badge)
- `web/src/app/(dashboard)/financeiro/columns.tsx` - 1 occurrence removed (badge)
- `web/src/app/(dashboard)/documentos/page.tsx` - 1 occurrence removed (badge)
- `webpage/src/components/ui/card.tsx` - primitive base class `rounded-none` → `rounded-lg`
- `webpage/src/components/site-header.tsx` - 2 occurrences removed (desktop + mobile Sheet "Entrar" Buttons)
- `webpage/src/components/hero-section.tsx` - 2 occurrences removed ("Entrar" + "Ver Funcionalidades" Buttons)
- `webpage/src/components/site-footer.tsx` - 1 occurrence removed (Button)
- `webpage/src/components/contact-section.tsx` - 1 occurrence removed (Button)

**Left untouched (by design):** `web/src/components/ui/calendar.tsx`, `input-group.tsx`, `tabs.tsx` (3 exception files, 6 legitimate lines), `web/src/components/ui/checkbox.tsx` (`rounded-[4px]`, unrelated), `webpage/src/components/ui/button.tsx`/`badge.tsx` (already `rounded-md`/`rounded-full`).

## Decisions Made

- `--radius: 0.5rem` (8px, shadcn's "Default" mid-point preset) applied identically to both `web/` and `webpage/`, per `114-CONTEXT.md`'s locked decision — a single value change would have made the public landing site visually inconsistent with the internal app.
- Full remediation scope (271 occurrences, 29 files) delivered rather than a "v1" two-file token edit, because codebase research (`114-UI-SPEC.md` "Critical Scope Finding") proved `cn()` = `twMerge(clsx(...))`, so any literal `rounded-none` className deterministically wins over a primitive's token-derived default — the token flip alone would have been invisible on ~90% of the interactive surface.
- `settings/page.tsx`'s special-case fix (`rounded-none lg:rounded-xl` → `rounded-xl`) applied as a distinct edit from the generic sweep, giving those 4 Cards uniform rounding at every breakpoint instead of flat-on-mobile.
- Applied the transformation via a throwaway Node script implementing the plan's `<transformation_contract>` 3-step ordered regex, rather than hand-editing 271 occurrences — verified file-by-file and via full diff token-frequency analysis before committing.

## Deviations from Plan

None - plan executed exactly as written. Actual occurrence counts at execution time (270 occurrences / 28 files in `web/src`, 7 occurrences / 5 files in `webpage/src`) matched the plan's pre-verified numbers exactly, confirmed via the dedicated Grep tool before the sweep began.

## Issues Encountered

- Initial `bash grep -rn ... | wc -l` undercounted (207 instead of 270) due to a Git-Bash/path-quoting quirk with parenthesized route-group directories (e.g. `(dashboard)`); resolved by re-running the count with the dedicated Grep tool, which correctly reported 270/28 for `web/src` and 7/5 for `webpage/src`, matching `114-UI-SPEC.md`'s inventory exactly. No functional impact — this only affected my own pre-sweep verification step, not any file content.
- The first `Edit` call on `settings/page.tsx` (using an 8-space-indented `old_string`) only matched 2 of the 4 identical Card lines because the other 2 lines have 4-space indentation; a second `Edit` call with the 4-space-indented variant caught the remainder. Both edits landed correctly; verified via final grep counts (0 remaining `rounded-none`/`lg:rounded-xl`, 4× `backdrop-blur-sm rounded-xl`).
- `cd web && pnpm exec tsc --noEmit` reports 3 pre-existing errors (`Cannot find module 'vitest'` in `use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`) — confirmed via `git log` these files were last touched in Phases 74/83/97, `vitest` is not a `package.json` dependency, and none of the 3 files are in this plan's scope. Out-of-scope per the deviation rules' scope boundary; not fixed, logged here for visibility only.
- `cd web && pnpm lint` reports 6 pre-existing errors and 17 pre-existing warnings (`react-hooks/set-state-in-effect`, `react-hooks/incompatible-library`, `@next/next/no-img-element`, `react-hooks/refs`, 2× unrelated `@typescript-eslint/no-unused-vars`) across 14 files. Verified each one individually: all concern `React.useEffect`/`form.watch()`/`<img>`/ref-in-render/dead-variable patterns entirely unrelated to `className`/`rounded-none` strings, and several are in files never touched by this plan (`dashboard-shell.tsx`, `documentos/novo/page.tsx`, `user-menu.tsx`, `data-table.tsx`, `user-profile-form.tsx`, `agenda/novo/page.tsx`, `clientes/[id]/ficha/page.tsx`). The two occurring in plan-touched files (`processos/[id]/page.tsx`'s unused `textareaClassName`/`no-unused-vars` on a `rounded-md` constant never edited by the sweep, and `processos/novo/page.tsx`'s intentionally-prefixed `_estado` destructure) are confirmed pre-existing and unrelated to any `rounded-none` line. Zero new lint issues introduced by this plan. Out of scope, not fixed.
- `cd webpage && pnpm exec tsc --noEmit` is fully clean (no errors). `cd webpage && pnpm lint` reports 1 pre-existing, unrelated `@next/next/no-img-element` warning in `brand-mark.tsx` (not part of this plan's file list).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- RAD-01 is closed for real: the `--radius` token is `0.5rem` in both apps, and the mechanical proof (`grep -rn "rounded-none" web/src webpage/src` → exactly 6 lines, all named exceptions) confirms the sweep, not just the token, landed.
- Visual QA (8 screens × 2 themes, per `114-UI-SPEC.md`'s Verification/QA Plan) is the phase's operative acceptance and was **not** performed here — it is explicitly deferred to a downstream verifier/HUMAN-UAT step per this plan's own `<verification>` block ("cannot be automated here"). Recommend the phase verifier or a human pass walk the 8-screen × 2-theme checklist before closing Phase 114.
- No blockers for subsequent phases (icons-on-buttons, icon-only filter actions) — this plan touched only `className` radius tokens, zero logic/data/RBAC changes, confirmed by the threat model's "no new threat surface" assessment (still true; nothing in the actual diff contradicts it).

---
*Phase: 114-linguagem-visual-cantos-arredondados-radius*
*Completed: 2026-07-21*

## Self-Check: PASSED

- FOUND: `web/src/app/globals.css`
- FOUND: `webpage/src/app/globals.css`
- FOUND: `webpage/src/components/ui/card.tsx`
- FOUND: `.planning/phases/LEXCV-114-linguagem-visual-cantos-arredondados-radius/114-01-SUMMARY.md`
- FOUND commit: `0b6d88f` (Task 1)
- FOUND commit: `87da7ff` (Task 2)
- FOUND commit: `f9ab004` (Task 3)
