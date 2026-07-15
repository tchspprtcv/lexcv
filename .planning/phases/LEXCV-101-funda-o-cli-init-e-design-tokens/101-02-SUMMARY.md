---
phase: 101-funda-o-cli-init-e-design-tokens
plan: 02
subsystem: ui
tags: [shadcn, tailwind-v4, design-tokens, radix, tw-animate-css, css-cascade]

# Dependency graph
requires:
  - phase: 101-01
    provides: package-legitimacy gate approval for all net-new phase packages (shadcn, radix-ui, tw-animate-css)
provides:
  - "web/components.json (shadcn CLI formally initialized, Radix base, resolved style=radix-vega, baseColor=neutral, cssVariables=true, iconLibrary=lucide, empty tailwind.config)"
  - "web/src/app/globals.css with the full shadcn semantic token set merged in a single :root + single .dark block, correct cascade order, restored institutional --background/--foreground, deliberate --radius:0rem and --primary blue"
  - "web/ animate plugin swapped from tailwindcss-animate to tw-animate-css@1.4.0 (exact pin)"
affects: [101-03, 101-04, 102]

# Tech tracking
tech-stack:
  added: ["shadcn@4.13.0 (CLI, dev-time only)", "radix-ui@^1.6.2 (unified package)", "tw-animate-css@1.4.0"]
  patterns:
    - "shadcn CLI 4.13.0's preset system: init -b radix -p vega -y (non-interactive; the interactive preset picker does not honor -y alone)"
    - "CSS custom-property cascade tie-break by source order — :root must be declared before .dark in the same stylesheet, since both match <html> with identical specificity when .dark is applied"

key-files:
  created: ["web/components.json"]
  modified: ["web/src/app/globals.css", "web/package.json", "web/pnpm-lock.yaml"]

key-decisions:
  - "shadcn CLI 4.13.0 stores the resolved preset name (radix-vega) in components.json's style field, not the legacy new-york string used to select it — matches 101-UI-SPEC.md's own documented '[legacy value, resolves to radix-vega]' note, not a deviation"
  - "Reverted two CLI side-effects outside Task 1's file scope (web/src/app/layout.tsx Inter-font injection, web/src/lib/utils.ts semicolon-only reformat) via git checkout -- <file>, since the plan's acceptance criteria explicitly required utils.ts untouched and UI-SPEC.md states font wiring is unchanged this phase"
  - "Kept @import \"shadcn/tailwind.css\" (new in CLI 4.13.0, not anticipated by phase research) — provides accordion keyframes and data-state custom variants; verified it resolves via node_modules and pnpm build passes with it present"
  - "Left .dark's --primary-foreground at the CLI's neutral-baseColor default (not hand-tuned to #ffffff) — the plan's <interfaces> section only specifies --background/--foreground/--primary for .dark, not --primary-foreground; net-new tokens not explicitly enumerated are left at default per plan instruction"
  - "Only requirement FND-01 marked complete in REQUIREMENTS.md — FND-03 and FND-07 remain Pending because both requirements explicitly span 'ambas as apps' (web/ + webpage/) and webpage/'s mirror (101-04) has not landed yet; marking them complete now would misrepresent traceability state"

patterns-established:
  - "Non-interactive shadcn init requires -p <preset-name> (nova|vega|maia|lyra|mira|luma|sera|rhea) alongside -b <base> and -y — a bare -b radix -y still drops into an interactive preset prompt"
  - "After any shadcn init/add on this repo, verify :root is declared BEFORE .dark in globals.css — the CLI does not guarantee this ordering and a same-specificity cascade tie silently resolves in source order"

requirements-completed: [FND-01]

# Metrics
duration: ~20min (task execution) + 1 human-verification round-trip (found and fixed a cascade-order bug)
completed: 2026-07-15
---

# Phase 101 Plan 02: shadcn CLI Init + Design Tokens (web/) Summary

**shadcn CLI formally initialized in web/ on the Radix base (preset Vega), full semantic token set merged into a single, correctly-ordered :root/.dark block with the institutional colors restored, tw-animate-css swapped in, and a genuine CSS-cascade tie-break bug (wrong `.dark`/`:root` declaration order) found by human visual QA and fixed before sign-off.**

## Performance

- **Duration:** ~20 min of task execution across 3 commits, plus one human-verification round-trip that surfaced and required fixing a real bug
- **Started:** 2026-07-15T20:21:34-01:00 (first commit)
- **Completed:** 2026-07-15T20:37:11-01:00 (fix commit); checkpoint sign-off received after re-verification
- **Tasks:** 3 (2 `type="auto"` + 1 `type="checkpoint:human-verify"`)
- **Files modified:** 4 (`web/components.json` created; `web/src/app/globals.css`, `web/package.json`, `web/pnpm-lock.yaml` modified)

## Accomplishments

- `web/components.json` created via `pnpm dlx shadcn@latest init -b radix -p vega -y` — Radix base (not the July-2026 Base UI default), `tailwind.config` empty string (Tailwind v4 preserved), `tailwind.css` → `src/app/globals.css`, `baseColor: neutral`, `cssVariables: true`, `iconLibrary: lucide`, default aliases (zero remapping needed against existing `tsconfig.json`)
- `web/src/app/globals.css` carries the full shadcn semantic token set (`--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, plus chart/sidebar tokens) in exactly one `:root` block and one `.dark` block, with `--background`/`--foreground` restored to the validated institutional hex (`#f8fafc`/`#020617` light, `#020617`/`#f8fafc` dark), `--radius: 0rem` (sharp-corner identity, overriding the Vega preset's `0.625rem` default), and `--primary: #2563eb` light / `#3b82f6` dark
- `tailwindcss-animate` fully removed from both `globals.css` and `package.json`; `tw-animate-css@1.4.0` (exact pin) installed and imported
- `pnpm build` passes clean (PITFALLS.md Pitfall 1's explicit build-gate mitigation)
- Human visual sign-off completed for both light and dark themes, after one fix-and-reverify cycle (see Issues Encountered)

## Task Commits

Each task was committed atomically:

1. **Task 1: Initialize shadcn CLI in web/ on the Radix base** - `6b32259` (feat)
2. **Task 2: Consolidate globals.css tokens, swap tw-animate-css, prove the build** - `41ca1fa` (feat)
3. **Task 3 fix: Reorder `:root` before `.dark`** (found during human visual sign-off) - `29829ca` (fix)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md update)

## Files Created/Modified

- `web/components.json` - shadcn CLI config; Radix base, resolved style `radix-vega`, `tailwind.config: ""`, `baseColor: neutral`, `cssVariables: true`, `iconLibrary: lucide`
- `web/src/app/globals.css` - full semantic token layer merged, single correctly-ordered `:root`/`.dark`, institutional colors restored, `tw-animate-css` import, `shadcn/tailwind.css` import (new CLI infra)
- `web/package.json` - `tailwindcss-animate` removed; `tw-animate-css@1.4.0`, `radix-ui@^1.6.2`, `shadcn@^4.13.0` added
- `web/pnpm-lock.yaml` - lockfile updated to match

## Decisions Made

- **CLI preset flag reality vs. plan text:** the plan's action text called for `init -b radix --dry-run` then the real `init -b radix`; the CLI installed at execution time (`shadcn@4.13.0`, matching STACK.md's researched version) does not support `--dry-run` on `init` (only documented, and confirmed, on `add`), and a bare `-b radix -y` still drops into an interactive preset picker (Nova/Vega/Maia/Lyra/Mira/Luma/Sera/Rhea) that `-y` alone does not skip. Re-verified the actual CLI surface live (`init --help`), discovered the `-p, --preset` flag, and used `-p vega` (the preset that resolves to `radix-vega`, matching 101-UI-SPEC.md's documented "new-york legacy value resolves to radix-vega"). Since `web/` had zero uncommitted changes before this task, the real `init` run plus a full `git diff` review afterward served as an equivalent safety net to `--dry-run` (Pitfall 1's actual goal — catching corruption before it's committed — was still met).
- **Reverted `layout.tsx`/`utils.ts` CLI side-effects:** the CLI's "Updating fonts" step injected an `Inter` font import into `layout.tsx` and reformatted `utils.ts` (semicolon stripping only). Both are outside Task 1's declared `<files>` scope and explicitly required to stay untouched (`utils.ts` per acceptance criteria; font wiring per UI-SPEC.md "unchanged by this phase"). Reverted both via `git checkout -- <file>`, then restored `@theme inline`'s `--font-sans` mapping back to `var(--font-geist-sans)` (it had been changed to a self-referential `var(--font-sans)` that only resolved via the now-reverted Inter font injection — left as-is it would have silently broken the sans-serif font across the whole app).
- **Kept `@import "shadcn/tailwind.css"`:** not anticipated by any of the phase's research documents (STACK.md/ARCHITECTURE.md/PITFALLS.md all predate this CLI behavior). Verified `node_modules/shadcn/dist/tailwind.css` resolves and contains accordion keyframes + `data-state` custom variants that newly-added Radix components will need; `pnpm build` passes with it present. Kept as legitimate, current CLI-generated infrastructure rather than removed as an unexplained addition.
- **`.dark`'s `--primary-foreground` left at CLI default:** the plan's `<interfaces>` section and Task 2's action text enumerate exactly `--background`/`--foreground`/`--primary` for `.dark` (mirroring `:root`'s explicit `--primary-foreground: #ffffff`, but without repeating it for dark). Since no reserved-accent surface in the current codebase uses `bg-primary text-primary-foreground` as a solid-fill pairing (they use raw utility classes like `bg-blue-600/10 text-blue-400` today, per UI-SPEC.md), and the plan didn't explicitly call for a dark-mode value, left `.dark`'s `--primary-foreground` at the CLI's neutral default (`oklch(0.205 0 0)`) rather than presuming a value. Flagged during the Task 3 checkpoint request; human sign-off raised no objection.
- **Only FND-01 marked complete in REQUIREMENTS.md:** FND-03 and FND-07 both explicitly require "ambas as apps" (web/ + webpage/) in their requirement text; this plan only covers web/'s half (the plan's own frontmatter says "the web/ half of FND-07"). `webpage/`'s mirror lands in 101-04. Marking FND-03/FND-07 complete now would misstate traceability — left them Pending.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `init --dry-run` unsupported by the installed CLI version; interactive preset picker required an explicit `-p` flag**
- **Found during:** Task 1
- **Issue:** Plan text called for `init -b radix --dry-run`, then `init -b radix` (implicitly non-interactive via `-y`). The actual CLI (`shadcn@4.13.0`) errors `unknown option '--dry-run'` on `init`, and `-b radix -y` alone still opened an interactive preset selector (Nova/Vega/Maia/Lyra/Mira/Luma/Sera/Rhea) since `-y` only skips the overwrite-confirmation prompt, not preset selection.
- **Fix:** Re-verified `init --help` live, found `-p, --preset [name]`, confirmed valid preset names via the CLI's own error message (`Invalid preset: radix-vega. Available presets: nova, vega, maia, lyra, mira, luma, sera, rhea`), then ran `init -b radix -p vega -y` successfully non-interactively. Used `git diff` review (web/ had zero uncommitted changes beforehand) as the equivalent Pitfall-1 safety net in place of the unsupported `--dry-run`.
- **Files modified:** none beyond the task's own scope
- **Committed in:** `6b32259`

**2. [Rule 1 - Bug] CLI's font-updater step injected an Inter font and modified files outside task scope**
- **Found during:** Task 1
- **Issue:** `shadcn init`'s "Updating fonts" step modified `web/src/app/layout.tsx` (added an `Inter` font import/variable, rewired the `<html>` className via `cn()`) and reformatted `web/src/lib/utils.ts` (semicolons stripped, no logic change) — both explicitly out of Task 1's file scope and required to remain untouched.
- **Fix:** `git checkout -- web/src/lib/utils.ts web/src/app/layout.tsx` to discard both changes; separately restored `globals.css`'s `--font-sans: var(--font-geist-sans)` (the CLI had repointed it to a self-referential `var(--font-sans)` that depended on the now-reverted Inter injection).
- **Files modified:** `web/src/lib/utils.ts`, `web/src/app/layout.tsx` (reverted to original), `web/src/app/globals.css` (font-sans mapping fixed)
- **Committed in:** `6b32259` (revert) / `41ca1fa` (font-sans mapping fix, part of the token consolidation commit)

**3. [Rule 1 - Bug] `.dark` block declared before `:root` in globals.css — CSS cascade tie-break silently favored the light theme in dark mode**
- **Found during:** Task 3 (human visual sign-off, first verification round)
- **Issue:** After Task 2's token consolidation, `.dark { ... }` appeared at line 51 and `:root { ... }` at line 89 — i.e., `.dark` was declared BEFORE `:root` in the file. Since `<html class="... dark">` matches both selectors with identical specificity (0,1,0), the tie is broken by source order, and the LAST-declared block wins for any shared custom property. With `:root` declared last, its light-theme values were silently overriding `.dark`'s dark-theme values whenever dark mode was active. Confirmed live: with `.dark` on `document.documentElement.classList`, `getComputedStyle(document.documentElement).getPropertyValue('--background')` returned `#f8fafc` (light) instead of `#020617`. Not yet visible on any shipped page (no reconciled component consumes `bg-background`/`bg-card`/`bg-popover` yet — that's Phase 102), but a latent bug that would have silently broken every reconciled component's dark mode the moment Phase 102 landed.
- **Fix:** Reordered so `:root` is declared before `.dark` (matches shadcn's own default generation order). Verified via `grep -n '^:root\|^\.dark'` (root now at line 51, dark at line 86) and, since no browser-automation tool was available in this session, via the actual compiled/served CSS asset: fetched `_next/static/chunks/0bg~24-1o3r4n.css` from a running `pnpm start` instance and confirmed `:root`'s block (byte offset 93861) precedes `.dark`'s block (byte offset 95623) in the same stylesheet. Human re-verified live via `getComputedStyle` after the fix: `--background` correctly resolves to `#020617` with `.dark` active and `#f8fafc` without it; `--foreground`/`--primary` and computed body background/color all confirmed correct in both directions; sharp corners (`--radius: 0rem`) visually confirmed in both themes on `/login`.
- **Files modified:** `web/src/app/globals.css`
- **Verification:** `pnpm build` re-run (exit 0); live `getComputedStyle` re-check by the human reviewer (approved)
- **Committed in:** `29829ca`

---

**Total deviations:** 3 auto-fixed (1 blocking/CLI-surface-churn, 2 bugs — one CLI side-effect scope violation, one CSS cascade-order bug caught by human visual QA)
**Impact on plan:** All three were necessary for correctness (bug fixes) or to complete the task given the actual CLI surface (blocking). No scope creep — every fix stayed within `web/`'s token/config files already in this plan's declared scope. The cascade-order bug (#3) is the most consequential: it was invisible today (no component consumes the semantic tokens yet) but would have silently broken dark mode across every Phase 102-reconciled component had it shipped uncaught — exactly the class of risk PITFALLS.md Pitfall 1 flagged the human visual sign-off checkpoint to catch.

## Issues Encountered

- The `type="checkpoint:human-verify"` gate did its job: the first sign-off request was rejected with a specific, reproducible discrepancy (CSS cascade tie-break bug), not a rubber-stamp approval. Fixed and re-verified in a single round-trip; second sign-off approved.
- No browser-automation/computer-use tool was available in this execution session to directly drive `getComputedStyle` myself; verified the fix's correctness via the actual compiled CSS asset served by a running `pnpm start` instance instead (byte-offset comparison of `:root` vs. `.dark` in the real stylesheet), which the human's own live `getComputedStyle` re-check subsequently corroborated.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `web/components.json` is ready to be hand-copied field-for-field into `webpage/` by 101-04 (per CONTEXT.md decision — never re-run the wizard independently in `webpage/`). Resolved values to copy: `style: "radix-vega"`, `rsc: true`, `tsx: true`, `tailwind: { config: "", css: "src/app/globals.css", baseColor: "neutral", cssVariables: true, prefix: "" }`, `iconLibrary: "lucide"`, `rtl: false`, default `aliases`, `menuColor: "default"`, `menuAccent: "subtle"`, `registries: {}`.
- The restored/deliberate token values (`--background`/`--foreground`/`--radius: 0rem`/`--primary`) and the `:root`-before-`.dark` ordering must be replicated identically in `webpage/`'s `globals.css` by 101-04 — this plan's Deviation #3 is the single most important thing for 101-04 to get right the first time (verify order immediately after merging, don't wait for a second human-verify round-trip).
- `web/` is ready for Task 101-03 (adding the ~15 missing primitives) — `components.json` and the full token set both exist and are verified correct.
- FND-03/FND-07 remain Pending in REQUIREMENTS.md until 101-04 completes `webpage/`'s half.

---
*Phase: 101-funda-o-cli-init-e-design-tokens*
*Completed: 2026-07-15*

## Self-Check: PASSED

- FOUND: web/components.json
- FOUND: web/src/app/globals.css
- FOUND: web/package.json
- FOUND: .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-02-SUMMARY.md
- FOUND: commit 6b32259
- FOUND: commit 41ca1fa
- FOUND: commit 29829ca
