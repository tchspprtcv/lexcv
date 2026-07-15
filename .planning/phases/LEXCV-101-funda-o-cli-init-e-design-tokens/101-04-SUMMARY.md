---
phase: 101-funda-o-cli-init-e-design-tokens
plan: 04
subsystem: ui
tags: [shadcn, tailwind-v4, design-tokens, tw-animate-css, webpage, css-cascade]

# Dependency graph
requires:
  - phase: 101-02
    provides: "web/components.json (resolved shadcn CLI config) and web/src/app/globals.css (final merged token block, single correctly-ordered :root/.dark) to hand-copy from"
provides:
  - "webpage/components.json — exact field-for-field mirror of web/components.json (never re-wizarded)"
  - "webpage/src/app/globals.css — same merged semantic token set as web/, single :root (declared first) + single .dark block, restored --background/--foreground, deliberate --radius:0rem and --primary blue"
  - "webpage/ animate plugin swapped from tailwindcss-animate to tw-animate-css@1.4.0 (exact pin, matches 101-01 legitimacy gate)"
  - "FND-08 webpage/ clause resolved as not-applicable (zero toast/Toaster/sonner usage confirmed by grep) — FND-08 overall remains Pending until 101-05 completes web/'s Sonner adoption"
affects: [102]

# Tech tracking
tech-stack:
  added: ["tw-animate-css@1.4.0 (webpage/, exact pin matching web/)"]
  patterns:
    - ":root declared before .dark in webpage/globals.css from the start (applying the cascade-order lesson 101-02 discovered), never introducing the bug a second time"
    - "webpage/ deliberately does NOT import shadcn/tailwind.css (an emergent web/-only CLI-infra addition from 101-02) since webpage/ adds zero new primitives this phase and has no shadcn npm dependency"

key-files:
  created: ["webpage/components.json"]
  modified: ["webpage/src/app/globals.css", "webpage/package.json", "webpage/pnpm-lock.yaml"]

key-decisions:
  - "webpage/components.json copied verbatim from web/components.json (byte-for-byte) rather than re-running an independent shadcn init wizard — per CONTEXT.md decision and PITFALLS.md Pitfall 5, this prevents the two apps' design systems from silently forking"
  - "webpage/globals.css mirrors web/'s ENTIRE token block (including net-new sidebar-*/chart-* tokens and the radius-sm..4xl scale) rather than only the subset explicitly named in UI-SPEC.md's Color section — matches the plan's own instruction to keep the two files 'in manual lockstep'"
  - "Deliberately excluded @import \"shadcn/tailwind.css\" from webpage/globals.css even though web/'s equivalent file has it — that import was an emergent, web/-only CLI infrastructure decision from 101-02 (provides accordion keyframes/data-state variants for the ~15 NEW primitives 101-03 adds to web/ only); webpage/ is explicitly out of scope for FND-04 (new primitives) and FND-05 (radix migration) this phase, and adding the import without the shadcn npm package installed would break webpage/'s build for zero benefit"
  - "Applied the :root-before-.dark ordering correctly on the FIRST attempt (per 101-02-SUMMARY's explicit handoff note) — verified via grep immediately after writing the file, not deferred to the human visual sign-off round-trip"

patterns-established: []

requirements-completed: [FND-02, FND-03, FND-07]

# Metrics
duration: "~20min (Tasks 1-2 automated + Task 3 human visual sign-off)"
completed: "2026-07-15"
---

# Phase 101 Plan 04: webpage/ Design Tokens Mirror Summary

**webpage/components.json and globals.css now byte/field-for-field mirror web/'s resolved shadcn foundation (single correctly-ordered :root/.dark, restored institutional colors, tw-animate-css swap), `pnpm build` passes clean, and a human has visually confirmed light+dark parity with web/ live at http://localhost:3001 — plan complete.**

## Status: 3/3 tasks complete — APPROVED

Task 3's blocking human-verify checkpoint was resolved with an "approved" verdict (see Task Commits / Decisions Made below for the verbatim verification). FND-02 is fully satisfied by this plan alone. FND-03 and FND-07 each required both apps' halves — 101-02 already closed `web/`'s half, and this plan closes `webpage/`'s half, so **both are now fully complete**. FND-08's `webpage/` clause ("e webpage/ se aplicável") is resolved as not-applicable, but FND-08 as a whole requirement remains **Pending** — the `web/` half (mounting sonner's `<Toaster />`, removing `toast.tsx`/`toaster.tsx`) is 101-05's scope (wave 4, not yet executed).

## Performance

- **Duration:** ~20 min total (2 automated tasks + 1 human visual sign-off round-trip)
- **Started:** 2026-07-15 (session start)
- **Tasks:** 3 of 3 complete
- **Files modified:** 4 (`webpage/components.json` created; `webpage/src/app/globals.css`, `webpage/package.json`, `webpage/pnpm-lock.yaml` modified)

## Accomplishments

- `webpage/components.json` created via verbatim copy of `web/components.json` — automated field-for-field diff (`style`, `rsc`, `tsx`, `iconLibrary`, `aliases`, `tailwind.{config,css,baseColor,cssVariables}`) confirms zero divergence
- `webpage/src/app/globals.css` extended with the full shadcn semantic token set (`--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, plus chart/sidebar tokens) in exactly one `:root` block (declared first) and one `.dark` block, with `--background`/`--foreground` restored to `#f8fafc`/`#020617` (light) and `#020617`/`#f8fafc` (dark), `--radius: 0rem`, and `--primary: #2563eb` light / `#3b82f6` dark — matching `web/` exactly
- `tailwindcss-animate` removed from both `globals.css` and `package.json`; `tw-animate-css@1.4.0` (exact pin) installed and imported via `@import "tw-animate-css"`
- Confirmed via grep that `webpage/src` has zero `toast`/`Toaster`/`sonner` occurrences — FND-08's webpage clause correctly resolved as **not applicable** (no `<Toaster />` mounted, none needed)
- `pnpm build` passes clean in `webpage/` (PITFALLS.md Pitfall 1's explicit build-gate mitigation, applied per-app since no root workspace/build_command exists to catch this automatically)
- Dev server started (`http://localhost:3001`, confirmed HTTP 200) for the Task 3 human visual sign-off; human confirmed light+dark parity live, then the server was stopped

## Task Commits

Each task was committed atomically:

1. **Task 1: Create webpage/components.json by mirroring web/'s resolved config** - `2ece3a1` (feat)
2. **Task 2: Mirror the token block into webpage/globals.css + animate swap; confirm no Toaster needed; prove the build** - `86cf4d5` (feat)
3. **Task 3: Human visual sign-off** - APPROVED (checkpoint; no code changes, verdict recorded in this SUMMARY — see Decisions Made)

**Plan metadata:** this SUMMARY commit (below) — STATE.md/ROADMAP.md/REQUIREMENTS.md updates deliberately NOT made by this executor (orchestrator-owned, per parallel-execution instructions — see Next Phase Readiness)

## Files Created/Modified

- `webpage/components.json` - shadcn CLI config, exact mirror of `web/components.json` (style `radix-vega`, `tailwind.config: ""`, `baseColor: neutral`, `cssVariables: true`, `iconLibrary: lucide`)
- `webpage/src/app/globals.css` - full semantic token layer merged, single correctly-ordered `:root`/`.dark`, institutional colors restored, `tw-animate-css` import (no `shadcn/tailwind.css` import — deliberately excluded, see Decisions)
- `webpage/package.json` - `tailwindcss-animate` removed; `tw-animate-css@1.4.0` added
- `webpage/pnpm-lock.yaml` - lockfile updated to match

## Decisions Made

- **Verbatim copy, not re-wizard:** `webpage/components.json` was created by `cp web/components.json webpage/components.json`, never a second `shadcn init` run — per CONTEXT.md's explicit decision and PITFALLS.md Pitfall 5 (two independent wizard runs risk drifting on style/baseColor/cssVariables/iconLibrary answers).
- **Full token block mirrored, not just the UI-SPEC-named subset:** copied web/'s entire `:root`/`.dark` blocks including net-new `--sidebar-*`/`--chart-*` tokens and the `@theme inline` radius scale (`--radius-sm` through `--radius-4xl`) — these are harmless CLI defaults (all resolve to `0` given `--radius: 0rem`) and keeping them identical to `web/` is more consistent with "manual lockstep" than hand-picking a subset.
- **`@import "shadcn/tailwind.css"` deliberately NOT mirrored:** 101-02 kept this import in `web/` as a legitimate, then-unanticipated CLI-generated addition providing accordion keyframes/`data-state` custom variants for the ~15 new primitives 101-03 adds to `web/` only. `webpage/` is explicitly out of scope for FND-04 (new primitives) and FND-05 (radix migration) this phase — it gets no new primitives, and the `shadcn` npm package that provides this CSS file is not (and should not be) a `webpage/` dependency. Adding the import without the package would break `webpage/`'s build for zero benefit.
- **`:root` before `.dark` verified immediately, not deferred:** 101-02-SUMMARY.md's "Next Phase Readiness" section explicitly flagged this ordering as "the single most important thing for 101-04 to get right the first time." Verified via `grep -n '^:root\|^\.dark' webpage/src/app/globals.css` immediately after writing the file (`:root` at line 51, `.dark` at line 86 — same line numbers as `web/`'s post-fix file) — the bug was not reintroduced.
- **Dev server started proactively:** since Task 3 is a blocking human-verify checkpoint and this executor cannot itself render/visually inspect a browser page, started `pnpm dev` (port 3001, confirmed `HTTP 200`) as the "automation before verification" step so whoever performs the sign-off has zero setup friction.
- **Task 3 verdict — APPROVED (verbatim):** "Verifiquei ao vivo em http://localhost:3001 (light e dark via o theme toggle do topbar): light → --background #f8fafc, --foreground #020617, --primary #2563eb; dark → --background #020617, --foreground #f8fafc, --primary #3b82f6. Valores idênticos aos confirmados em web/. Cantos sem arredondamento visíveis." Both LIGHT and DARK themes match `101-UI-SPEC.md`'s Color section values exactly, and read identically to `web/` — no Pitfall-5 cross-app drift. After the verdict, the dev server (PID bound to port 3001) was stopped.

## Deviations from Plan

None - all 3 tasks executed exactly as written; no auto-fixes needed, no blocking issues encountered, no architectural questions arose (this plan being a hand-copy of already-settled 101-02 decisions left little room for divergence), and the human visual sign-off passed on the first attempt (no discrepancy to fix, unlike 101-02's cascade-order round-trip).

## Issues Encountered

None. Task 3's human visual sign-off passed on the first attempt.

## User Setup Required

None - no external service configuration required. The Task 3 human visual sign-off is complete (see Decisions Made).

## Next Phase Readiness

- **This plan (101-04) is complete.** FND-02 and (now that both apps' halves exist) FND-03 and FND-07 are fully satisfied — `webpage/` now mirrors `web/`'s design foundation with zero visible page change, no config/token drift, and a human-confirmed light+dark match. FND-08's `webpage/` clause is resolved (not-applicable), but FND-08 overall stays Pending until 101-05 completes `web/`'s Sonner adoption.
- Per this plan's parallel-execution instructions, STATE.md/ROADMAP.md/REQUIREMENTS.md are deliberately NOT updated by this executor — the orchestrator owns those writes after all wave-3 worktree agents (101-03, 101-04) complete and are merged. This SUMMARY's `requirements-completed` frontmatter field lists `[FND-02, FND-03, FND-07]` for the orchestrator to action centrally in REQUIREMENTS.md (FND-08 intentionally excluded — still Pending on 101-05).
- Phase 102 (module-by-module primitive reconciliation) depends on both `web/`'s and `webpage/`'s foundations being fully signed off — `webpage/`'s half is now done; readiness also depends on 101-03's parallel completion (and, for FND-08 specifically, on 101-05).
- `webpage/`'s `pnpm dev` server (port 3001) has been stopped now that verification is complete.

---
*Phase: 101-funda-o-cli-init-e-design-tokens*
*Completed: 2026-07-15*

## Self-Check: PASSED

- FOUND: webpage/components.json
- FOUND: webpage/src/app/globals.css
- FOUND: webpage/package.json
- FOUND: .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-04-SUMMARY.md
- FOUND: commit 2ece3a1
- FOUND: commit 86cf4d5
