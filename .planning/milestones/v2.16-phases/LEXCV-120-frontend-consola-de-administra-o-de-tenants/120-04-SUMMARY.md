---
phase: 120-frontend-consola-de-administra-o-de-tenants
plan: 04
subsystem: ui
tags: [nextjs, react, react-hook-form, zod, tanstack-table, typescript, rbac]

# Dependency graph
requires:
  - phase: 120-03
    provides: "TenantAdminSummary/TenantPlano types (@/types/platform-admin) this plan's columns.tsx renders, and the setupSchema/SetupFormValues contract (@/schemas/setup) this plan's create-tenant panel imports verbatim"
provides:
  - "web/src/app/(dashboard)/plataforma/columns.tsx: columns({ onEdit, onToggleAtivo }) factory producing 5 ColumnDef<TenantAdminSummary> (nome/plano/utilizadores/estado/acoes), plus the exported TENANT_RESERVADO = \"LexCV\" literal"
  - "web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx: CriarTenantPanel({ onCancel, onSubmit, isSubmitting }) - presentational-only inline create-tenant form, zero network/cache coupling"
  - "First proof in this codebase that the Phase 118 Tooltip + <span tabIndex={0}> + disabled Button composition also fires correctly on a TanStack column-def action cell (not just a page-level header button)"
affects: [120-05, 120-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Column-def factory takes only callback props (onEdit/onToggleAtivo), never a hook - all dialog/mutation state stays in the page composing the table (established by clientes/columns.tsx, but this is the first column file in the app where the per-row action cell deliberately excludes any hook, even for its own confirm step)"
    - "Presentational form panel receiving onSubmit/isSubmitting as props instead of owning its own mutation - lets the composing page (Plan 05) control toast copy, cache invalidation, and panel-close timing from one place"

key-files:
  created:
    - web/src/app/(dashboard)/plataforma/columns.tsx
    - web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx
  modified: []

key-decisions:
  - "Logo file field uses a native <input type=\"file\"> element (not the shadcn Input wrapper used for the other 4 fields) - matches the plan's own casing distinction (\"Input\" for text fields vs lowercase \"input type=\\\"file\\\"\" for the logo field) and keeps the 5-input-control acceptance grep deterministic regardless of how the shadcn Input component's multi-line JSX happens to wrap"
  - "Reworded this plan's own top-of-file comment in criar-tenant-panel.tsx (the word \"toasts\" collided with this same task's own grep gate for the substring \"toast\") without changing its documented meaning - same class of self-referential collision documented in 119-04/120-01/120-02/120-03-SUMMARY.md, caught before commit via the same automated verify gate"
  - "Did not run requirements mark-complete for PROV-02 despite it being listed in this plan's own frontmatter requirements field - REQUIREMENTS.md's traceability table explicitly requires PROV-02 to have a working, reachable internal screen (\"num ecrã interno\"), which does not exist until Plan 05 composes these two isolated files into an actual /plataforma page. Marking it complete now would repeat the exact premature-completion regression Plan 03 already flagged and Plan 02 had to have corrected (commit cd45fcf9) for PROV-05. PROV-03 was left untouched - it was already [x] Complete in REQUIREMENTS.md before this plan started, set by an earlier plan, and reverting/re-deciding that attribution is out of this plan's scope"

requirements-completed: []

# Metrics
duration: ~20min
completed: 2026-07-29
---

# Phase 120 Plan 04: Tenant DataTable Columns and Criar Tenant Panel Summary

**5-column `ColumnDef<TenantAdminSummary>[]` factory with a reserved-tenant suspend guard, plus a presentational inline Criar Tenant form reusing `/setup`'s Zod schema verbatim — both built as isolated, unmounted pieces ahead of the `/plataforma` screen itself (Plan 05)**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-29T13:14:26Z (immediately after 120-03 completed)
- **Completed:** 2026-07-29T13:34:00Z
- **Tasks:** 2 completed
- **Files modified:** 2 (both created)

## Accomplishments

- `web/src/app/(dashboard)/plataforma/columns.tsx` created: `columns({ onEdit, onToggleAtivo })` factory returning exactly 5 `ColumnDef<TenantAdminSummary>` entries (`nome`, `plano`, `utilizadores`, `estado`, `acoes`) — nome renders an initials avatar + plain bold text (no `<Link>`, no tenant detail page exists this phase) with a "Plataforma" outline badge under the reserved tenant's name; plano badges map `STARTER`/`STANDARD`/`ENTERPRISE` to `gray`/`purple`/`amber`; utilizadores stacks `"X/Y"` (or `"X · sem limite"`) with a destructive "limite atingido" caption when at/over limit; estado badges `Ativo`/`Suspenso` to `green`/`red`; acoes delegates to a separate `TenantAcoesCell` component (zero hooks) with Editar (always enabled) and a single Suspender/Reativar toggle slot
- The reserved "LexCV" tenant's Suspender action renders `disabled` and wrapped in the Phase 118 `<span tabIndex={0}>` composition inside `TooltipTrigger asChild`, with `TooltipContent` reading the byte-identical guard message `"Não é possível suspender o tenant da plataforma (LexCV)."` — proven to fire despite `buttonVariants`' `disabled:pointer-events-none`, since a bare `TooltipTrigger` on a disabled `Button` never would
- `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx` created: `CriarTenantPanel({ onCancel, onSubmit, isSubmitting })`, a purely presentational inline `Card` form (not a `Dialog`) using `react-hook-form` + `zodResolver(setupSchema)` imported verbatim from `@/schemas/setup` — 5 fields (Nome da Empresa/Cliente, Logo opcional with 2MB-limited data-URL preview + remove control, Email do Administrador, Password, Confirmar Password), zero `apiFetch`/`useMutation`/`toast`/`queryClient`/`<Dialog>` calls in the file
- `npx tsc --noEmit`, `pnpm lint` (0 errors), and `pnpm build` (24 routes, unchanged from Plan 03's baseline since `/plataforma` itself isn't a route until Plan 05) all pass cleanly after both tasks; zero new dependencies (`package.json`/`pnpm-lock.yaml` diffs empty)

## Task Commits

Each task was committed atomically:

1. **Task 1: Definições de coluna da tabela de tenants** - `bc5d06b6` (feat)
2. **Task 2: Painel inline de criação de tenant** - `23c89c3d` (feat)

**Plan metadata:** (recorded in the next commit, after this SUMMARY)

## Files Created/Modified

- `web/src/app/(dashboard)/plataforma/columns.tsx` (225 lines) - `columns()` factory + `TENANT_RESERVADO` export + `TenantAcoesCell` (hook-free action cell)
- `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx` (241 lines) - `CriarTenantPanel` inline create-tenant form

## Decisions Made

- Logo file field uses a native `<input type="file">` element rather than the shadcn `Input` component wrapper used for the other 4 fields — this both matches the plan's own casing distinction between "Input" (text fields) and lowercase "input type=\"file\"" (the logo field), and makes the acceptance criterion's `<Input|type="file"` line-count (must equal exactly 5) deterministic regardless of how a multi-line shadcn `Input` tag happens to wrap its attributes.
- Reworded this file's own top-of-file doc-comment (originally said "os toasts e o fecho do painel") to "as mensagens de sucesso/erro e o fecho do painel" — the word "toasts" collided with this same task's own automated verify gate (`grep -cE 'apiFetch|useCreateTenant|useMutation|toast|queryClient'` must equal 0). Same class of self-referential collision already documented in `119-04`/`120-01`/`120-02`/`120-03-SUMMARY.md`, caught here before the commit rather than after.
- Skipped `requirements mark-complete` for `PROV-02` (see Deviations below). Left `PROV-03`'s existing `[x]` Complete status in `REQUIREMENTS.md` untouched — it was already marked before this plan started, by an earlier plan, and re-deciding that attribution is out of this plan's scope.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Own top-of-file comment collided with this same task's own automated verify gate**
- **Found during:** Task 2 (`criar-tenant-panel.tsx` implementation, pre-commit verification pass)
- **Issue:** The top-of-file doc-comment explaining that the panel is purely presentational originally read "Quem detem a mutacao, os toasts e o fecho do painel e o chamador...". Task 2's own acceptance criteria require `grep -cE 'apiFetch|useCreateTenant|useMutation|toast|queryClient'` against this exact file to equal `0` — the substring "toast" inside "os toasts" would have failed this plan's own gate.
- **Fix:** Reworded to "as mensagens de sucesso/erro e o fecho do painel", conveying the identical documented property (success/error messaging and panel-closing both live in the caller, not this component) without the literal substring "toast".
- **Files modified:** `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx`
- **Verification:** Re-ran the grep after the edit — 0 matches. All other acceptance-criteria greps for this file (`@/schemas/setup` import present, `strongPasswordPattern *=` = 0, lookahead regex `(?=.*` = 0, `<Dialog` = 0, `<Input|type="file"` = 5, `Nome do Administrador` = 0, `any` = 0) re-confirmed unaffected.
- **Committed in:** `23c89c3d` (Task 2 commit) — caught during the pre-commit verification pass, so the collision never reached git history.

---

**Total deviations:** 1 auto-fixed (Rule 1, a pre-emptive comment-wording collision with this plan's own literal-text verification gate — the same recurring class of issue documented in `119-04`/`120-01`/`120-02`/`120-03-SUMMARY.md`, caught here before commit).
**Impact on plan:** Cosmetic only — no functional/behavioral code was affected, no scope creep.

## Issues Encountered

- **Pre-existing, out-of-scope:** raw `cd web && npx tsc --noEmit -p tsconfig.json` reports the same 3 errors (`Cannot find module 'vitest'`) documented in `120-03-SUMMARY.md`, in 3 "durable spec" files committed across Phases 74/83/97, deliberately without `vitest` installed. Neither file this plan touches was involved; the count was 3 before and after both tasks. `pnpm build` — the authoritative gate per this plan's own `<verification>` section — runs Next.js's own TypeScript check and passed cleanly (exit 0, 24 routes) both before this plan started and after both tasks, since Next's build-time check does not walk these orphaned spec files. Not fixed (installing `vitest` reverses a standing, explicit project decision, out of scope here).
- No unrelated merges landed on `master` during this plan's execution (checked `git log` before and after each commit — clean linear history on top of `10e22d6f`).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 05 can now `import { columns, TENANT_RESERVADO } from "./columns"` and `import { CriarTenantPanel } from "./criar-tenant-panel"` directly into `plataforma/page.tsx` — both files' prop contracts (`{ onEdit, onToggleAtivo }` for the column factory; `{ onCancel, onSubmit, isSubmitting }` for the panel) are fixed and type-checked against Plan 03's real `TenantAdminSummary`/`SetupInitializeRequest` types, so Plan 05 composes rather than infers.
- Neither new file is reachable yet — `/plataforma` is not a route until Plan 05 creates `page.tsx`; `pnpm build`'s route list is unchanged (24 routes) from Plan 03's baseline, as expected for this phase's sequencing (data layer → screen pieces → composed screen).
- `REQUIREMENTS.md`'s `PROV-02` checkbox remains `[ ]` Pending exactly as before this plan (see Decisions Made) — Plan 05 is expected to close it once the actual internal screen exists and is reachable. `PROV-03` remains `[x]` Complete, untouched by this plan.

---
*Phase: 120-frontend-consola-de-administra-o-de-tenants*
*Completed: 2026-07-29*

## Self-Check: PASSED

Both claimed created files confirmed present on disk (`web/src/app/(dashboard)/plataforma/columns.tsx`, `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx`), plus this SUMMARY.md. Both claimed commit hashes (`bc5d06b6`, `23c89c3d`) confirmed present in `git log --oneline --all`. `pnpm build` (24 routes) and `pnpm lint` (0 errors) re-confirmed clean; `git diff --stat -- web/package.json web/pnpm-lock.yaml` re-confirmed empty (zero new dependencies).
