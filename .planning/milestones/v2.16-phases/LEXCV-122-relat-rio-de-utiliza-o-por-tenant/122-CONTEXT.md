# Phase 122: Relatório de Utilização por Tenant - Context

**Gathered:** 2026-07-30
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

Dedicated codebase research (this session, prior to writing this file) confirms this phase needs **zero backend changes**. `GET /api/v1/platform/tenants` (Phase 120, `PlatformAdminController.listTenants()`) already returns, for every tenant unconditionally (no `ativo` filter — suspended tenants are already included), exactly the 6 fields UTIL-01 needs: `id`, `nome`, `plano`, `limiteUtilizadores`, `ativo`, `utilizadoresAtivos`. The response DTO's own doc-comment already names this phase explicitly: *"reutilizável, sem alterações, pelo relatório de utilização da Phase 122 — ambas as superfícies precisam exatamente destes mesmos 6 campos"* — this was designed in from Phase 120, not discovered now. `utilizadoresAtivos` is sourced from `UserRepository.countByTenantIdAndAtivoTrue`, confirmed (exhaustive grep) as the ONLY active-user-count implementation in the entire codebase (2 call sites total: this one and `AdminController.limiteUtilizadoresExcedido`) — there is no competing calculation a naive new report could accidentally duplicate instead.

**This phase is a new frontend screen only**, consuming the existing `useTenantsAdmin()` hook (`web/src/hooks/use-platform-admin.ts`) as-is, zero modification.

**One real, if narrow, backend gap found:** while `listTenants()` demonstrably includes suspended tenants today (confirmed by reading the code — no filter exists), there is **no existing test asserting this** (`PlatformAdminControllerTest.java`'s 3 `listTenants_*` tests all use `.ativo(true)` fixtures only). A future refactor could silently add a filter with nothing to catch the regression. Adding this one test is genuinely this phase's job (it directly proves ROADMAP Success Criterion 3), even though it touches a file this phase doesn't otherwise need to change.

</domain>

<decisions>
## Screen placement: new route, not a new tab or a second permanent nav item

120-CONTEXT.md's own "Deferred Ideas" section already framed this exact distinction when Phase 120 was planned: *"Usage report / billing report... Phase 122 (UTIL-01), this phase's own list screen is the operational console, not the reporting view (some overlap in the underlying data is fine and expected, they can share the list endpoint)."* Combined with ROADMAP's own wording ("**um ecrã de relatório**" — a report screen, singular new noun), this phase gets its own route: `web/src/app/(dashboard)/plataforma/relatorio/page.tsx`.

**Reached via an in-context link from the existing `/plataforma` screen, NOT a second permanent sidebar nav item.** This mirrors the closest precedent in this codebase for exactly this situation — PROJECT.md's Key Decisions (v2.10, Phase 89): notifications got their own route but deliberately no new sidebar/bottom-nav entry, reached only via a link from the primary surface ("evita adicionar navegação permanente para uma página secundária"). `dashboard-shell.tsx`'s nav-splice mechanism (`isPlatformAdmin ? [...NAV, platformNavItem] : NAV`) could trivially add a second literal item, but a link/button in `/plataforma`'s own `CardHeader` (next to "Criar Tenant") is the better-precedented choice for a secondary, less-frequently-visited screen. Route-level RBAC guard on `/plataforma/relatorio` itself (same `useMe()` + `!me.isFetched` fail-closed pattern as `/plataforma/page.tsx:76-92`) is still the real requirement — criterion 1 says "acessível só a PLATAFORMA_ADMIN," not "linked from the sidebar."

## Visual pattern: reuse `/plataforma`'s Card + shared DataTable, minus the actions column

Reuse the exact `plataforma/columns.tsx` rendering conventions for `plano` (raw-enum Badge), `limiteUtilizadores`/`utilizadoresAtivos` (combined "X/Y" or "X · sem limite" cell, destructive color + "limite atingido" caption at/over limit), and `ativo` (humanized green "Ativo"/red "Suspenso" Badge) — these are already-reviewed, already-shipped visual decisions for the identical data, not new design surface. Drop the "Ações" column entirely (no suspend/edit/create actions belong on a read-only report). Phase 120's own UI-SPEC explicitly opted out of a KPI hero row for the console (120-UI-SPEC.md: "this screen mirrors that simpler shape deliberately") — this phase can revisit that choice independently since it's a different screen, but defaults to the same no-KPI-row simplicity unless the UI researcher finds a specific reason to diverge.

## Suspended tenants stay visible with state identified (Success Criterion 3)

Already true today by construction (no filter in `listTenants()`/`useTenantsAdmin()`/`plataforma/page.tsx`'s own search-filter logic — confirmed, only filters by `nome` substring). The report screen must not introduce a new filter that hides suspended tenants — reuse the same `ativo` Badge rendering `/plataforma` already uses so the state is visually identified, per the criterion's own wording ("com o seu estado identificado, em vez de desaparecerem da lista").

## Claude's Discretion

- Exact route path (`/plataforma/relatorio` suggested above, not locked).
- Whether to add a CSV export (precedent exists and is well-guarded: `financeiro/page.tsx`'s `exportHonorariosCsv`, including OWASP formula-injection escaping) — **not required** by any success criterion or by REQUIREMENTS.md's UTIL section (which only asks for on-screen display), so this is optional, additive scope. Given the phase's own goal text calls this screen "a base factual para emitir a fatura manual" (the factual basis for manual invoicing), an export could be genuinely useful in practice — but do not add it speculatively if it meaningfully grows the phase; a small, working on-screen report fully satisfies the stated requirement on its own.
- Exact link/button placement and copy for reaching `/plataforma/relatorio` from `/plataforma`.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets (zero modification needed)
- `GET /api/v1/platform/tenants` (`PlatformAdminController.java:106-113`, `toSummary()` at `:191-200`) — already returns every field this phase needs, already includes suspended tenants
- `TenantAdminSummaryResponse` (`backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java`) — doc-comment explicitly names this phase as a reuser
- `useTenantsAdmin()` (`web/src/hooks/use-platform-admin.ts:21-30`) — pure read query, no mutation coupling
- `TenantAdminSummary` type (`web/src/types/platform-admin.ts:12-19`)
- `UserRepository.countByTenantIdAndAtivoTrue` — the sole active-user-count source of truth, already wired

### Established Patterns
- `useMe()` + `if (!me.isFetched) return null;` then `AccessDeniedState` — page-level RBAC guard pattern (`plataforma/page.tsx:76-92`, also `dashboard-shell.tsx:91-95`)
- Shared `DataTable` (`web/src/components/shared/data-table/`) — every list screen in this app uses it
- Badge conventions for `plano`/`ativo` (`plataforma/columns.tsx:149-162, 195-204`) — reuse verbatim
- PROJECT.md Key Decisions (v2.10, Phase 89) — secondary screen gets a route but not a permanent nav entry, reached via an in-context link instead

### Integration Points
- New file: `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` (or similar — Claude's discretion on exact slug)
- New file: a columns/render definition for the report table (can likely reuse most of `plataforma/columns.tsx`'s cell renderers directly, minus the actions column)
- `web/src/app/(dashboard)/plataforma/page.tsx` — add a link/button to the new route in the existing `CardHeader`
- `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` — add one test proving a `.ativo(false)` tenant still appears in `listTenants()`'s response (closes the one real gap found — a regression-test addition, not a behavior change)

</code_context>

<specifics>
## Specific Ideas

None beyond what's captured above and in ROADMAP.md's own Success Criteria for Phase 122.

</specifics>

<deferred>
## Deferred Ideas

- CSV/printable export — genuinely optional per Claude's Discretion above, not required by any success criterion; add only if cheap given the well-precedented pattern already in `financeiro/page.tsx`, do not force it in.
- Any KPI/aggregate hero row (e.g., "total active users across all tenants") — not asked for by any success criterion; Phase 120's console already deliberately opted out of this shape for the identical data.

</deferred>
