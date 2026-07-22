---
phase: LEXCV-114-linguagem-visual-cantos-arredondados-radius
fixed_at: 2026-07-21T23:32:27Z
review_path: .planning/phases/LEXCV-114-linguagem-visual-cantos-arredondados-radius/114-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-114: Code Review Fix Report

**Fixed at:** 2026-07-21T23:32:27Z
**Source review:** .planning/phases/LEXCV-114-linguagem-visual-cantos-arredondados-radius/114-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2
- Fixed: 2
- Skipped: 0

## Fixed Issues

### WR-01: Removing `rounded-none` turns every Badge from rectangular into a full pill

**Files modified:** `web/src/components/ui/badge.tsx`
**Commit:** b7059f4
**Applied fix:** Per an explicit user decision made during this fix session (badges should NOT become pill-shaped; they should get the same moderate rounded-corner treatment as the rest of the app, consistent with the phase's literal goal of "cantos arredondados de forma consistente em todos os componentes"), replaced `rounded-full` with `rounded-md` directly in `badgeVariants`'s base `cva` class list (line 7), rather than appending `rounded-md` alongside the now-dead `rounded-full` and relying on tailwind-merge to silently drop it at render time. This fixes the shape once at the primitive level for all 7 color variants (`default/secondary/outline/blue/green/amber/red/purple/gray`) and every call site — including the 36 call sites whose `rounded-none` override the original phase-114 sweep stripped — instead of re-adding a class to all 36 call sites individually. `Badge` still merges call-site `className` after the base via `cn()` (tailwind-merge), so any call site that wants a different radius can still override this default; only the *fallback* behavior changed.

Noted but intentionally left out of scope: `webpage/src/components/ui/badge.tsx` (the marketing site) has an identical `rounded-full` base class, but it is not cited by WR-01's **File:** line and is not part of `114-REVIEW.md`'s `files_reviewed_list` (it was not touched by the original phase-114 sweep and was never reviewed), so it was left unchanged to keep the fix scoped to the finding as written.

### WR-02: Icon-wrapper squares, avatar-initials squares, and raw toggle-pill `<button>`s have no primitive to fall back to

**Files modified:** `web/src/app/(dashboard)/dashboard/page.tsx`, `web/src/app/(dashboard)/clientes/columns.tsx`, `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/processos/page.tsx`, `web/src/app/(dashboard)/processos/dashboard/page.tsx`, `web/src/app/(dashboard)/notificacoes/page.tsx`, `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** 10f262d
**Applied fix:** Added an explicit `rounded-md` class (matching the rest of the app's card/button radius, per explicit user instruction) to all 19 raw `<div>`/`<button>` elements cited by WR-02. None of these route through a styled primitive, so none had a `cn(base, className)` merge to "fall back to" once their `rounded-none` override was stripped by the original sweep — deleting the override was a no-op (native default border-radius is already `0`), leaving them permanently sharp-cornered. Breakdown of the 19 edits:
- 7 icon-wrapper squares in `dashboard/page.tsx` (3 `iconWrapperClassName` string constants consumed by `AtividadeRecenteCard`, + 4 KPI-card icon-wrapper `<div>`s: Clientes/Processos/Agenda/Financeiro)
- 1 avatar-initials square in `clientes/columns.tsx` (table row initials)
- 1 avatar-initials square in `clientes/page.tsx` (mobile stacked-card view initials)
- 2 KPI icon-wrapper squares in `processos/page.tsx` (Total Ativos, Suspensos)
- 2 icon-wrapper squares in `processos/dashboard/page.tsx` (Prazos Críticos, Processos Inativos)
- 1 read/unread filter-chip `<button>` (both active/inactive ternary branches, 1 source location rendering N chips) in `notificacoes/page.tsx`
- 5 timeline-type filter-chip `<button>`s — movimentação, transição, evento, documento, decisão (both ternary branches each) in `processos/[id]/page.tsx`

During investigation, also verified that `processos/[id]/page.tsx:1428`'s "Limpar filtros" element is a `<Button variant="ghost">` (the shadcn `Button` primitive, not a raw `<button>`) and already correctly falls back to the Button primitive's own token-derived radius default — it was correctly excluded from WR-02's cited line list (1290-1383) and was left unchanged.

## Skipped Issues

None — all findings were fixed.

## Verification Notes

- **Tier 1 (mandatory):** Every modified file was re-read after editing and cross-checked against `git diff` to confirm only the intended `rounded-md` insertions were present, with no corruption to surrounding code (diff line counts matched the expected edit count exactly in every file).
- **Tier 2 (syntax check):** Full `tsc --noEmit` could not run against the ephemeral fix worktree because it has no `node_modules` (gitignored, not present in a fresh `git worktree add` checkout), and attempting to borrow the main repo's installed TypeScript compiler via an absolute path broke module resolution project-wide (all imports unresolvable, ~5150 unrelated errors across every file in the project) — this is a path-resolution artifact of the cross-directory setup, not a real defect, and was discarded. As a substitute, all 8 modified files (`badge.tsx` + the 7 WR-02 files) were parsed directly with the TypeScript compiler's parser (`ts.createSourceFile`, TSX-aware, borrowed read-only from the main repo's installed `typescript` package) with zero parse diagnostics reported in any file — a genuine syntax-level check equivalent in spirit to `python ast.parse` for this file type, without requiring a full dependency install.
- All edits were pure string-literal content changes (adding/replacing a Tailwind class token inside existing `className` strings) — no JSX structure, imports, or logic were touched, so no findings in this pass require the "logic error — human verification" downgrade.

---

_Fixed: 2026-07-21T23:32:27Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
