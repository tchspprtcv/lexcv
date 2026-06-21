# Retrospective: LexCV

Living retrospective — one section per shipped milestone.

---

## Milestone: v2.3 — Responsividade App

**Shipped:** 2026-06-21
**Phases:** 4 | **Plans:** 8 | **Commits:** 28

### What Was Built

- Hamburger drawer + Sheet component (manual, no CLI); BottomNav `fixed bottom-0 md:hidden` with permission filter
- Dual-view CSS pattern: `hidden md:block` table + `md:hidden` cards in Clientes, Agenda, Documentos, Financeiro
- Horizontal scroll in Partes + Fases tables (`overflow-x-auto` + `min-w-[400/480px]`)
- 24x `grid-cols-1 md:grid-cols-2` across 12 form files; `max-sm:fixed bottom-0` on 3 DialogContent
- KPI grid `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4` in dashboard; "Hoje" mobile block in Agenda

### What Worked

- **CSS-only dual-view pattern** (`hidden md:block` / `md:hidden`) — zero rerenders, no JS branching, easily reviewable in diff. Clear winner over JS-based responsive switching.
- **Single-day sprint** — all 4 phases completed in a single day with autonomous workflow. Tight scope (CSS className changes) made this extremely fast.
- **Audit gate caught a real bug** — integration checker found mobile card buttons were 36px (h-9) in clientes, below the 48px FORM-03 requirement. Fixed before tag. The audit step earned its cost.
- **Wave-based execution** — Phase 54's 3 plans in 3 waves enabled parallelism where appropriate while respecting data flow dependencies.

### What Was Inefficient

- **shadcn CLI interactive setup** — `npx shadcn add sheet` required interactive terminal wizard, blocked automation. Manual sheet.tsx creation was a workaround; the pattern for future CLI-blocked components should be documented.
- **Verifier stale state in Phase 55** — verifier agent ran against cached grep results showing 3 stale `sm:grid-cols-2` occurrences that no longer existed. Re-run confirmed clean. Pattern suggests verifiers should use fresh `git status` rather than cached results.
- **ROADMAP.md progress table staleness** — table showed Phase 54 as "2/3 In Progress" and Phase 55 as "0/TBD" even after execution completed. STATE.md + ROADMAP.md drift requires a reconciliation step after each wave.

### Patterns Established

- `md:` (768px) is the mobile/desktop breakpoint; `max-sm:` (< 640px) for bottom-sheet positioning; `sm:` reserved for tablet-only.
- React fragments (`<>...</>`) are mandatory when two sibling JSX elements appear inside a ternary branch.
- `sheet.tsx` can be created manually by copying `dialog.tsx` pattern with `@radix-ui/react-dialog` when CLI is unavailable.
- `pb-24 md:pb-8` on the scroll container in DashboardShell is the standard clearance for `fixed bottom-0` BottomNav.

### Key Lessons

1. **Audit step is load-bearing for FORM-03** — touch target requirements are easy to miss in code review but catchable by integration checker looking at className patterns. Keep the audit gate.
2. **Manual component creation as documented fallback** — when shadcn/UI CLI is unavailable (interactive setup required), copying the closest primitive (dialog.tsx → sheet.tsx) with the same @radix-ui dependency is reliable. Document this in CLAUDE.md if it happens again.
3. **CSS-only responsive is fast and safe** — milestone v2.3 shipped 51 files with 0 logic changes, 0 API changes, 0 backend changes. Pure CSS responsive keeps blast radius minimal.
4. **Duplicate event display warning (CAL-01)** — "Hoje" block + "Próximos Eventos" block in Agenda can show the same event twice on mobile when today's events haven't started yet. Deferred to v2.4+ but worth monitoring in user feedback.

### Cost Observations

- Model: claude-sonnet-4-6 throughout (autonomous workflow)
- Phases: All CSS-only — no research needed, no new dependencies (except sheet.tsx manual)
- Notable: Single-day sprint was possible because scope was well-defined (CSS breakpoints) and non-blocking across phases (53→54→55 and 53→56 in parallel)

---

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Files | Requirements |
|-----------|--------|-------|------|-------|--------------|
| v2.3 Responsividade | 4 | 8 | 1 | 51 | 11/11 |

*Table grows with each milestone*
