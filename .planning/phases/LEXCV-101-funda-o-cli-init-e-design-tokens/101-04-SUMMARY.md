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
  - "FND-08 webpage/ clause resolved as not-applicable (zero toast/Toaster/sonner usage confirmed by grep)"
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

requirements-completed: []

# Metrics
duration: "~15min (Tasks 1-2 automated); Task 3 (blocking human-verify checkpoint) not yet resolved"
completed: "2026-07-15 (PARTIAL — see Status below)"
---

# Phase 101 Plan 04: webpage/ Design Tokens Mirror Summary — CHECKPOINT PENDING

**webpage/components.json and globals.css now byte/field-for-field mirror web/'s resolved shadcn foundation (single correctly-ordered :root/.dark, restored institutional colors, tw-animate-css swap), `pnpm build` passes clean — but the plan's mandatory human visual sign-off (Task 3) has NOT yet been performed, so this plan is NOT complete.**

## Status: 2/3 tasks complete — BLOCKED on Task 3 (`checkpoint:human-verify`, `gate="blocking"`)

This plan's frontmatter (`autonomous: false`) and Task 3's `gate="blocking"` mean the plan cannot be considered done until a human visually confirms webpage/'s light+dark themes match `101-UI-SPEC.md`'s Color values / Figma reference AND read identically to `web/` side-by-side (no Pitfall-5 cross-app drift). Per the executor's standard checkpoint protocol, execution STOPS here — this SUMMARY documents the current state for the orchestrator/continuation agent, it does not claim the plan finished.

**A `pnpm dev` server for webpage/ is running in the background on `http://localhost:3001`** (started as the "automation before verification" step) — confirmed responding `HTTP 200`. Whoever performs the Task 3 sign-off can visit this URL directly; `web/` (the dashboard, for side-by-side comparison) is not started by this executor — it belongs to the parallel 101-03 plan's worktree.

## Performance

- **Duration:** ~15 min for Tasks 1-2 (2 commits)
- **Started:** 2026-07-15 (session start)
- **Tasks:** 2 of 3 complete (`type="auto"` × 2 done; `type="checkpoint:human-verify"` × 1 pending)
- **Files modified:** 4 (`webpage/components.json` created; `webpage/src/app/globals.css`, `webpage/package.json`, `webpage/pnpm-lock.yaml` modified)

## Accomplishments

- `webpage/components.json` created via verbatim copy of `web/components.json` — automated field-for-field diff (`style`, `rsc`, `tsx`, `iconLibrary`, `aliases`, `tailwind.{config,css,baseColor,cssVariables}`) confirms zero divergence
- `webpage/src/app/globals.css` extended with the full shadcn semantic token set (`--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, plus chart/sidebar tokens) in exactly one `:root` block (declared first) and one `.dark` block, with `--background`/`--foreground` restored to `#f8fafc`/`#020617` (light) and `#020617`/`#f8fafc` (dark), `--radius: 0rem`, and `--primary: #2563eb` light / `#3b82f6` dark — matching `web/` exactly
- `tailwindcss-animate` removed from both `globals.css` and `package.json`; `tw-animate-css@1.4.0` (exact pin) installed and imported via `@import "tw-animate-css"`
- Confirmed via grep that `webpage/src` has zero `toast`/`Toaster`/`sonner` occurrences — FND-08's webpage clause correctly resolved as **not applicable** (no `<Toaster />` mounted, none needed)
- `pnpm build` passes clean in `webpage/` (PITFALLS.md Pitfall 1's explicit build-gate mitigation, applied per-app since no root workspace/build_command exists to catch this automatically)
- Dev server started (`http://localhost:3001`, confirmed HTTP 200) in preparation for the pending Task 3 human visual sign-off

## Task Commits

Each completed task was committed atomically:

1. **Task 1: Create webpage/components.json by mirroring web/'s resolved config** - `2ece3a1` (feat)
2. **Task 2: Mirror the token block into webpage/globals.css + animate swap; confirm no Toaster needed; prove the build** - `86cf4d5` (feat)
3. **Task 3: Human visual sign-off** - NOT STARTED (blocking checkpoint; dev server prepared, awaiting human verdict)

**Plan metadata:** this SUMMARY commit (below) — STATE.md/ROADMAP.md/REQUIREMENTS.md updates deliberately NOT made by this executor (orchestrator-owned, per parallel-execution instructions)

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

## Deviations from Plan

None - Tasks 1 and 2 executed exactly as written; no auto-fixes needed, no blocking issues encountered, no architectural questions arose (this plan being a hand-copy of already-settled 101-02 decisions left little room for divergence).

## Issues Encountered

None for Tasks 1-2. Task 3 has not been attempted — no browser-automation/computer-use tool was invoked in this session to perform the visual comparison, consistent with the checkpoint's explicit requirement for a HUMAN verdict (this is a blocking gate, not something this executor should auto-approve or attempt to substitute its own judgment for).

## User Setup Required

None - no external service configuration required. A human (or a continuation agent with browser/computer-use tooling) needs to:
1. Visit `http://localhost:3001` (already running) and confirm LIGHT theme: background `#f8fafc`, foreground `#020617`, sharp corners.
2. Toggle DARK mode and confirm: background `#020617`, foreground `#f8fafc`, institutional `--primary` blue on accent surfaces only.
3. Compare side-by-side with `web/` (the dashboard, run separately — not started by this executor) in the same mode.
4. Compare both against the Figma reference.
5. Record "approved" or describe the discrepancy so Task 2 can be corrected.

## Next Phase Readiness

- **This plan (101-04) is NOT ready to be marked complete.** Task 3's blocking human-verify checkpoint must be resolved (approved or discrepancy-fixed-and-reverified) before FND-02/FND-03(webpage half)/FND-07(webpage half)/FND-08(webpage clause) can be marked complete in REQUIREMENTS.md.
- Per this plan's parallel-execution instructions, STATE.md/ROADMAP.md/REQUIREMENTS.md are NOT updated by this executor — the orchestrator owns those writes after all wave-3 worktree agents complete (and, for this specific plan, after the checkpoint is separately resolved).
- Phase 102 (module-by-module primitive reconciliation) depends on both `web/`'s and `webpage/`'s foundations being fully signed off — this plan's incomplete status should be tracked as a blocker for that dependency until Task 3 resolves.
- `webpage/`'s `pnpm dev` server is left running in the background on port 3001 for the checkpoint verifier's convenience; it should be stopped once verification completes.

---
*Phase: 101-funda-o-cli-init-e-design-tokens*
*Completed: PARTIAL — 2026-07-15 (Tasks 1-2 only; Task 3 checkpoint pending)*

## Self-Check: PASSED

- FOUND: webpage/components.json
- FOUND: webpage/src/app/globals.css
- FOUND: webpage/package.json
- FOUND: .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-04-SUMMARY.md
- FOUND: commit 2ece3a1
- FOUND: commit 86cf4d5
