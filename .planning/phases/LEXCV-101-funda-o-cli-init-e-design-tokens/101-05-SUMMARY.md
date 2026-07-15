---
phase: 101-funda-o-cli-init-e-design-tokens
plan: 05
subsystem: ui
tags: [shadcn, sonner, radix-ui, toast, migrate-radix]

# Dependency graph
requires:
  - phase: 101-03
    provides: "the 16 new shadcn/Radix primitives in web/src/components/ui/ (already importing from the unified radix-ui package), which migrate radix needed to reconcile against the 8 hand-rolled files still on scoped @radix-ui/react-* imports"
provides:
  - "web/src/components/ui/sonner.tsx — sonner's <Toaster /> themed via next-themes, richColors enabled for green/red success/error distinction, mounted once at the web/ root (app/layout.tsx)"
  - "web/src/hooks/use-toast.ts rewritten as a thin contract-preserving wrapper delegating toast/toast.success/toast.error to sonner — Sucesso/Erro titles and all ~26 existing call sites unchanged"
  - "web/src/components/ui/toast.tsx and toaster.tsx deleted; @radix-ui/react-toast removed from package.json"
  - "all 8 hand-rolled Radix-backed components (alert-dialog, button, dialog, label, popover, radio-group, sheet, switch) migrated from scoped @radix-ui/react-* imports to the unified radix-ui package via shadcn migrate radix; 7 now-dead scoped packages pruned from package.json/pnpm-lock.yaml — radix-ui is the only Radix dependency remaining in web/"
affects: [102, 104, 105, 106, 107, 108, 109]

# Tech tracking
tech-stack:
  added: ["sonner@2.0.7"]
  patterns:
    - "shadcn migrate radix -y (non-interactive flag confirmed via --help) rewrites scoped @radix-ui/react-* imports to the unified radix-ui package in-place, import-path-only — safe to run on hand-rolled files with custom styling since it never touches JSX/props/classNames"
    - "sonner's richColors prop is the built-in equivalent of a custom emerald/red toast contract — no custom CSS needed, confirmed by reading node_modules/sonner/dist/styles.css's [data-rich-colors='true'][data-type='success'/'error'] rules"

key-files:
  created: ["web/src/components/ui/sonner.tsx"]
  modified: ["web/src/hooks/use-toast.ts", "web/src/app/layout.tsx", "web/package.json", "web/pnpm-lock.yaml", "web/src/components/ui/alert-dialog.tsx", "web/src/components/ui/button.tsx", "web/src/components/ui/dialog.tsx", "web/src/components/ui/label.tsx", "web/src/components/ui/popover.tsx", "web/src/components/ui/radio-group.tsx", "web/src/components/ui/sheet.tsx", "web/src/components/ui/switch.tsx"]

key-decisions:
  - "Added richColors to the CLI-scaffolded sonner.tsx wrapper — the plan's action text required success=green/error=red parity with the old emerald/red toaster.tsx contract; verified via sonner's own stylesheet that richColors is the native mechanism for this, not a custom class"
  - "useToast() kept as a thin shim returning { toast, dismiss: sonnerToast.dismiss } rather than dropped entirely — grep-confirmed zero call sites destructure it (its only prior consumer, toaster.tsx, is deleted), but keeping the export avoids a dangling import if any future file expects the old hook shape"
  - "Bare toast(message, options) delegates directly to sonner's own toast(message, options) rather than preserving the old object-shaped { title, description, variant } signature — grep-confirmed zero call sites in the app ever call bare toast(...) (only .success/.error are used), so no behavior-preserving translation was needed for that path"
  - "FND-05 and FND-08 marked Complete in REQUIREMENTS.md — 101-04-SUMMARY.md already established that webpage/'s Radix-migration and Sonner clauses are out of scope/not-applicable for Phase 101 (webpage/ has zero toast usage and was explicitly excluded from FND-04/FND-05 scope), so this plan's web/-only work is what fully closes both requirements"

patterns-established:
  - "Any future shadcn add/migrate step on this repo should be run with the CLI's own -y/--yes flag (confirmed present on both add and migrate) rather than attempting to script answers to the interactive prompt — avoids the non-TTY hang risk documented in 101-03-SUMMARY.md"

requirements-completed: [FND-05, FND-08]

# Metrics
duration: ~25min (task execution across 2 commits, plus investigation/verification)
completed: 2026-07-15
---

# Phase 101 Plan 05: Sonner Adoption + Radix Package Unification Summary

**Sonner replaces the deprecated Radix Toast stack behind an unchanged toast.success("Sucesso")/toast.error("Erro") contract (richColors green/red), and `shadcn migrate radix` unifies all 8 remaining hand-rolled components plus the 16 new primitives onto the single `radix-ui` package — closing the milestone's last dual-Radix-tree bridge state in `web/`.**

## Performance

- **Duration:** ~25 min (2 task commits ~9 min apart, plus pre-execution reading/verification)
- **Started:** 2026-07-15T21:34:13-01:00 (first commit)
- **Completed:** 2026-07-15T21:43:28-01:00 (second commit)
- **Tasks:** 2 (both `type="auto"`)
- **Files modified:** 16 (1 created, 15 modified; 2 deleted)

## Accomplishments

- `sonner@2.0.7` installed; `web/src/components/ui/sonner.tsx` scaffolded via `pnpm dlx shadcn@latest add sonner`, with `richColors` added so `toast.success`/`toast.error` render with the same green/red visual distinction the old `toaster.tsx` (emerald `CheckCircle2`/red `AlertCircle`) provided
- `web/src/hooks/use-toast.ts` rewritten as a thin wrapper delegating to sonner's own `toast()`/`toast.success()`/`toast.error()` — the `"Sucesso"`/`"Erro"` titles are preserved verbatim, and all ~26 existing call-site files (grep-verified) compile and run unchanged
- `web/src/app/layout.tsx` now imports `Toaster` from `@/components/ui/sonner` (single-line import swap; the JSX mount itself was already `<Toaster />` inside `<Providers>`, so no further change was needed there)
- `web/src/components/ui/toast.tsx` and `toaster.tsx` deleted; `@radix-ui/react-toast` removed from `package.json`
- `shadcn migrate radix -y` run from inside `web/`: rewrote the 8 hand-rolled files still on scoped imports (`alert-dialog.tsx`, `button.tsx`, `dialog.tsx`, `label.tsx`, `popover.tsx`, `radio-group.tsx`, `sheet.tsx`, `switch.tsx`) to `import { X as XPrimitive } from "radix-ui"` — confirmed via full diff review that every change is import-path-only (zero prop/markup/behavior changes)
- 7 now-dead scoped `@radix-ui/react-*` packages (`-alert-dialog`, `-dialog`, `-label`, `-popover`, `-radio-group`, `-slot`, `-switch`) removed from `package.json`/`pnpm-lock.yaml` after grep-confirming zero remaining imports anywhere in `web/src`; `radix-ui` is now the only Radix dependency in `web/`
- `pnpm exec tsc --noEmit` and `pnpm build` both run after each task — `pnpm build` exits 0 both times (production bundle, all 30 routes); the only `tsc` errors present are the 3 pre-existing, out-of-scope `vitest` module-resolution errors already logged in `deferred-items.md` by 101-03 (confirmed unrelated to this plan's changes)

## Task Commits

Each task was committed atomically:

1. **Task 1: Adopt Sonner behind the preserved toast contract; delete the Radix Toast stack** - `61c020f` (feat)
2. **Task 2: Run `shadcn migrate radix` and prune unused scoped Radix packages** - `c1b12ec` (feat)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md update)

## Files Created/Modified

- `web/src/components/ui/sonner.tsx` - CLI-scaffolded sonner `<Toaster />` wrapper, themed via `next-themes`, `richColors` added for the green/red success/error contract
- `web/src/hooks/use-toast.ts` - rewritten to delegate `toast`/`toast.success`/`toast.error` to sonner; reactive `useToast()` kept as a thin `{ toast, dismiss }` compatibility shim
- `web/src/app/layout.tsx` - `Toaster` import switched from `@/components/ui/toaster` to `@/components/ui/sonner`
- `web/src/components/ui/toast.tsx` - deleted (Radix Toast primitives, superseded by sonner)
- `web/src/components/ui/toaster.tsx` - deleted (superseded by `sonner.tsx`)
- `web/package.json` / `web/pnpm-lock.yaml` - `@radix-ui/react-toast` removed (Task 1); `sonner` added (Task 1); 7 scoped `@radix-ui/react-*` packages removed after `migrate radix` (Task 2); `radix-ui` is the sole remaining Radix dependency
- `web/src/components/ui/alert-dialog.tsx`, `button.tsx`, `dialog.tsx`, `label.tsx`, `popover.tsx`, `radio-group.tsx`, `sheet.tsx`, `switch.tsx` - import-path-only rewrite from scoped `@radix-ui/react-*` to the unified `radix-ui` package via `shadcn migrate radix -y`

## Decisions Made

- **`richColors` added to the CLI-generated `sonner.tsx`:** the plan's action text required the new Sonner Toaster to preserve the old success(green)/destructive(red) visual distinction. The CLI's default `add sonner` output does not enable this by default; verified via `node_modules/sonner/dist/styles.css` that `richColors` is the built-in mechanism (`[data-rich-colors='true'][data-type='success']`/`[data-type='error']` rules define `--success-bg`/`--error-bg`), so this was a one-line prop addition rather than hand-rolled CSS.
- **`useToast()` kept as a thin shim, not dropped:** grep confirmed its only prior consumer (`toaster.tsx`) is deleted by this same plan and no other file destructures it, matching the plan's explicit permission to drop it — kept anyway as a zero-cost compatibility export (`{ toast, dismiss: sonnerToast.dismiss }`) so any future import of `useToast` still resolves rather than breaking with a missing-export error.
- **Bare `toast(message, options)` forwards directly to sonner's own `toast(message, options)`:** the previous implementation's bare `toast({...props})` took an object with `title`/`description`/`variant`; grep across the whole `web/src` tree confirmed zero call sites anywhere ever call bare `toast(...)` — every one of the ~146 call sites uses `.success(...)` or `.error(...)` only. Since there was nothing to preserve on that path, the new bare `toast` simply passes its arguments straight through to sonner's positional `(message, data)` signature.
- **`shadcn migrate radix` run non-interactively via its own `-y`/`--yes` flag** (confirmed present via `migrate radix --help`), avoiding the interactive-prompt-hang risk that 101-03 had previously worked around with a move-aside/restore technique for `shadcn add` — `migrate` exposes a cleaner non-interactive path than `add` does.
- **FND-05 and FND-08 marked Complete (not left Pending for a `webpage/` half):** 101-04-SUMMARY.md's own decision notes state `webpage/` is "explicitly out of scope for FND-04 (new primitives) and FND-05 (radix migration) this phase" and that FND-08's `webpage/` clause ("e webpage/ se aplicável") is "resolved as not-applicable" (zero toast usage in `webpage/`, grep-confirmed). This plan's `web/`-only work is therefore what fully closes both requirements for the milestone — unlike FND-02/FND-03/FND-07, which explicitly require "ambas as apps" in their own requirement text.

## Deviations from Plan

None - plan executed exactly as written. Both tasks completed using the documented CLI mechanics (`pnpm add sonner`, `pnpm dlx shadcn@latest add sonner`, `pnpm dlx shadcn@latest migrate radix -y`), with no blocking issues, no bugs requiring a fix, and no architectural questions. The only investigative work beyond the plan's literal action text was confirming (a) sonner's `richColors` mechanism via its own source/stylesheet before adding the prop, and (b) via grep that zero call sites use bare `toast(...)` or `useToast()` before deciding how to shape those two code paths — both were verification steps in service of the plan's own acceptance criteria, not deviations from it.

## Issues Encountered

- A raw `grep` pattern containing literal double-quote characters (e.g. `grep '"radix-ui"' package.json`), when invoked through the Bash tool's shell layer in this environment, intermittently failed to match content that was confirmed present via `od -c`/the dedicated Grep tool. Root-caused to this environment's command-rewriting layer, not a real file/content issue. Resolved by switching all pattern-matching verification to the dedicated Grep tool instead of shelling out to `grep` inside Bash (which is also the documented tool-usage guidance) — all package.json/src content checks in this plan's verification were ultimately confirmed via the Grep tool, and the two required build/typecheck gates (`pnpm exec tsc --noEmit`, `pnpm build`) were run via Bash as actual commands, not pattern searches.

## User Setup Required

None - no external service configuration required. `sonner` is an official npm package added via a legitimacy-gated flow (101-01's blocking-human checkpoint already covers all net-new phase packages including `sonner`/`radix-ui`).

## Next Phase Readiness

- This is the last plan in Phase 101. All 8 FND requirements (FND-01 through FND-08) are now satisfied: `web/` and `webpage/` both have formally-initialized shadcn CLI foundations with the full semantic token set (101-02/101-04), the 16 missing primitives exist in `web/` with `react-day-picker` pinned to `9.14.0` (101-03), `tailwindcss-animate` → `tw-animate-css` is swapped in both apps (101-02/101-04), and this plan closes the last two: Sonner replaces Radix Toast (FND-08) and every Radix-backed component in `web/` — old and new — now imports from the single unified `radix-ui` package with zero dead scoped dependencies (FND-05).
- Phase 102 (Design System Reconciliation) can now proceed: it needs exactly this plan's outcome — a single Radix import convention across all 22 `web/src/components/ui/*` Radix-backed files (8 migrated hand-rolled + 14 already-unified from 101-03, minus toast/toaster which no longer exist) — before starting its component-by-component `--diff` reconciliation pass.
- No visible page changes shipped, matching Phase 101's own scope declaration ("sem que nenhuma página visível mude ainda") — toast copy/colors are functionally identical to before, and the Radix migration is a pure import-path rewrite.
- The 3 pre-existing, out-of-scope `vitest` module-resolution errors (Phase 97-02, logged in `deferred-items.md` by 101-03) remain open for a future test-infrastructure decision — unaffected by this plan.

---
*Phase: 101-funda-o-cli-init-e-design-tokens*
*Completed: 2026-07-15*

## Self-Check: PASSED

- FOUND: web/src/components/ui/sonner.tsx
- CONFIRMED DELETED: web/src/components/ui/toast.tsx
- CONFIRMED DELETED: web/src/components/ui/toaster.tsx
- FOUND: commit 61c020f
- FOUND: commit c1b12ec
- FOUND: .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-05-SUMMARY.md
