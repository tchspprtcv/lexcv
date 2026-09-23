---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 09
subsystem: ui
tags: [react, tanstack-query, rbac, audit-log, settings-tabs, pt-cv-copy]

# Dependency graph
requires:
  - phase: 128-08
    provides: "AuditoriaRbacEntry/ListFilters/PageResponse types, auditoriaEventoToSentence/auditoriaCategoriaToLabel/auditoriaPermissoesDetalhe PT-CV composer, useOfficeRbacAuditoria read-only paginated hook"
  - phase: 128-03/05/06
    provides: "backend audit writes for papel_criar/renomear/apagar/permissoes_alterar/atribuir/retirar"
  - phase: 128-07
    provides: "GET /admin/rbac/auditoria tenant-scoped paginated read endpoint behind rbac:manage"
provides:
  - "AuditoriaTab: the read-only 'Auditoria' tab in Definicoes (Filtros card, Resultados card, 20-per-page pager)"
  - "web/scripts/verify-auditoria-rbac.mjs: structural read-only and wiring gate, added as pnpm verify:auditoria-rbac"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AuditoriaTab reuses the notificacoes/page.tsx filter-card + Resultados-card + Pagination shape verbatim, generalized to two filters (Combobox for utilizador alvo, plain <select> for papel) instead of one"
    - "Page-clamp-on-shrink derived during render (no useEffect+setState), same technique as notificacoes/page.tsx's lastTotalPages guard"
    - "Sentence segments (SegmentoFrase[] from Plan 08's auditoriaEventoToSentence) rendered as individual <span> text children with conditional font-semibold, never string-interpolated into a single <p> and never dangerouslySetInnerHTML"
    - "verify-auditoria-rbac.mjs deliberately skips the stripComments preprocessing step used by verify-papeis-escritorio.mjs, because this plan's own acceptance criteria requires a bare comment (`// useMutation`) added to auditoria-tab.tsx to trip the gate -- comment-stripping would silently defeat that demonstration"

key-files:
  created:
    - web/src/app/(dashboard)/settings/auditoria-tab.tsx
    - web/scripts/verify-auditoria-rbac.mjs
  modified:
    - web/src/app/(dashboard)/settings/page.tsx
    - web/package.json

key-decisions:
  - "Combobox for 'Utilizador alvo' includes an explicit { value: \"\", label: \"Todos os utilizadores\" } option (matching documentos/page.tsx's processoOptions/clienteOptions precedent) rather than relying on an undefined value to show the placeholder -- keeps the field itself always able to clear back to 'Todos', independent of the page-level 'Limpar filtros' button."
  - "useAdminUsers({ enabled: hasUsersManage }) gates the target-user Combobox's data source on users:manage (not rbac:manage, which gates the tab itself) because GET /admin/users is authorized separately -- documented inline as a known limitation: a caller with only rbac:manage sees an empty 'Utilizador alvo' list (no 403 toast) instead of being unable to open the tab at all."
  - "verify-auditoria-rbac.mjs uses raw (non-comment-stripped) substring checks for every assertion, departing from verify-papeis-escritorio.mjs's stripComments precedent, so that the plan's own mutation-check demonstration (adding `// useMutation` as a bare comment) genuinely trips the gate -- confirmed by running it deliberately during Task 2 (see Verification Evidence)."

requirements-completed: [AUDT-01, AUDT-02, AUDT-03, AUDT-04]

# Metrics
duration: ~35min
completed: 2026-09-22
---

# Phase 128 Plan 09: Auditoria tab UI and read-only gate Summary

**The read-only "Auditoria" tab shipped in Definicoes: a Filtros card (Combobox for utilizador alvo, `<select>` for papel by id) and a Resultados card rendering PT-CV audit sentences (author/target/role names `font-semibold`) with a 20-per-page pager, wired additively into `settings/page.tsx` alongside a new structural verify gate (`verify:auditoria-rbac`) that is proven able to fail.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-09-22 (following 128-08 completion)
- **Completed:** 2026-09-22T16:17:00Z (approx)
- **Tasks:** 2 automated (Task 3, the human-verify checkpoint, is recorded below as pending)
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments

- `web/src/app/(dashboard)/settings/auditoria-tab.tsx`: `"use client"` component exporting `AuditoriaTab`. Filtros card with a `Combobox` (options `useAdminUsers({ enabled: hasUsersManage }).data` mapped to `{ value: id, label: nome }`, prefixed with `{ value: "", label: "Todos os utilizadores" }`) and a plain `<select>` for "Papel" (`useOfficeRbac().data?.papeis`, value = papel id per Plan 08's reconciliation that the filter is `papelId`, a UUID, not a name). "Limpar filtros" ghost button (X icon) appears only when a filter is active and resets filter state only, never the log. Resultados card covers loading/error/empty-with-filters/true-zero-Empty/results states in the exact order specified by 128-UI-SPEC.md §2. Each row (`AuditoriaRow`): fixed-width `w-40` timestamp column (`formatDateTime`, `toLocaleString("pt-CV")`, copied verbatim from `processos/[id]/page.tsx:190-195`), a `<p>` mapping `auditoriaEventoToSentence(entry)`'s `SegmentoFrase[]` to individual `<span>` text children with `font-semibold` only when `destaque` is true, an optional `text-xs` detail sub-line from `auditoriaPermissoesDetalhe(entry, rotulos)`, and a `<Badge variant="outline">` category label. Pager rendered only when `totalPages > 1`, copied verbatim from `notificacoes/page.tsx`. Filter changes reset `page` to 0; page is clamped when `totalPages` shrinks, using the same derived-during-render pattern as `notificacoes/page.tsx` (no `useEffect` for data or state adjustment). No row action, no `useMutation`, no `dangerouslySetInnerHTML`, no `DropdownMenu` anywhere in the file (Read-Only Guarantee, AUDT-04).
- `web/src/app/(dashboard)/settings/page.tsx`: purely additive. `TabId` extended with `"auditoria"`; `History` added to the existing `lucide-react` import list; `AuditoriaTab` imported from `./auditoria-tab`; a `{hasRbacManage && (<button>...)}` tab immediately after "Controlo de Acesso (RBAC)" and before "Notificações"; a `{activeTab === "auditoria" && hasRbacManage && (...)}` panel immediately after the rbac panel. `git diff --stat` confirms 23 insertions / 2 deletions (the two deletions are the edited lucide-import-list line and the edited `TabId` union line themselves) -- no line inside `RbacTab`, the module-scope draft cache, or the `beforeunload` effect was touched.
- `web/scripts/verify-auditoria-rbac.mjs`: structural gate in the style of `verify-papeis-escritorio.mjs`, with one deliberate departure -- it does **not** strip comments before running its literal substring checks (see Decisions). 11 assertions: (a) `auditoria-tab.tsx` contains none of `useMutation`, `dangerouslySetInnerHTML`, `DropdownMenu`, `Trash`, `Pencil`, or the four mutating `method: "..."` literals; (b) it contains `useOfficeRbacAuditoria(` and `auditoriaEventoToSentence(` and never renders `{entry.acao}` directly; (c) `use-admin.ts`'s `useOfficeRbacAuditoria` block (declaration to next `export function`) contains `useQuery(` and not the mutation hook; (d) `page.tsx` contains `"auditoria"`, `activeTab === "auditoria" && hasRbacManage`, `papeisComAlteracoesPorGravar` and `beforeunload`; (e) `lib/auditoria-rbac.ts` contains the three binding null-name fallback strings. Added `"verify:auditoria-rbac": "node scripts/verify-auditoria-rbac.mjs"` to `web/package.json`, after `verify:consola-moldes`.

## Task Commits

Each task was committed atomically:

1. **Task 1: AuditoriaTab component and additive page wiring** - `fe74dcfb` (feat)
2. **Task 2: Structural verify script for the Auditoria tab** - `10c6a49e` (feat)

Task 3 (`checkpoint:human-verify`, gate `blocking`) was **not executed** -- see "Pending Human Verification" below, per this plan's explicit `<checkpoint_handling>` instruction (no human present in this autonomous milestone run).

## Files Created/Modified

- `web/src/app/(dashboard)/settings/auditoria-tab.tsx` - `AuditoriaTab` component: Filtros card, Resultados card, `AuditoriaRow`
- `web/src/app/(dashboard)/settings/page.tsx` - additive-only: `"auditoria"` TabId, `History` import, tab button, gated panel
- `web/scripts/verify-auditoria-rbac.mjs` - structural read-only/wiring gate
- `web/package.json` - `verify:auditoria-rbac` script entry

## Decisions Made

See `key-decisions` in frontmatter: (1) the Combobox always offers an explicit "Todos os utilizadores" option rather than relying on `undefined`, matching the `documentos/page.tsx` precedent; (2) the target-user Combobox's data source is gated by `users:manage`, not `rbac:manage`, and this asymmetry is documented inline as a known, accepted limitation; (3) the new verify script deliberately does not strip comments before its substring checks, so that the plan's own mutation-check demonstration works as specified rather than being silently defeated.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Doc-comments in `auditoria-tab.tsx` initially contained the literal forbidden substrings they were describing as absent**
- **Found during:** Task 1, acceptance-criteria verification (`grep -F "useMutation"` / `"dangerouslySetInnerHTML"` / `"DropdownMenu"` were required to return nothing)
- **Issue:** The first draft's module and function doc-comments explained the Read-Only Guarantee by naming the forbidden APIs literally (e.g. "nao ha useMutation, DropdownMenu... (nunca `dangerouslySetInnerHTML`)"), which made the plan's own literal `grep -F` acceptance criteria fail even though no actual mutation/dropdown/unsafe-HTML code existed.
- **Fix:** Reworded both comments to describe the guarantee without using the literal English API names (e.g. "nenhum hook de gravacao do TanStack Query", "nenhum menu suspenso por linha", "nunca HTML nao escapado").
- **Files modified:** `web/src/app/(dashboard)/settings/auditoria-tab.tsx`
- **Verification:** Re-ran `grep -F "useMutation"` / `"dangerouslySetInnerHTML"` / `"DropdownMenu"` against the file -- all three returned nothing after the fix.
- **Committed in:** `fe74dcfb` (fixed before this commit was made; no separate commit needed)

**2. [Rule 1 - Bug] First draft of `verify-auditoria-rbac.mjs` used comment-stripping, which silently defeated its own required mutation-check demonstration**
- **Found during:** Task 2, running the plan's own acceptance criterion ("adding the line `// useMutation` to auditoria-tab.tsx makes the script exit non-zero")
- **Issue:** The first draft copied `verify-papeis-escritorio.mjs`'s `stripComments` preprocessing step verbatim. When the mutation-check demonstration was run (injecting `// useMutation` as a bare comment line), `stripComments` removed that entire line before the substring check ran, so the script still reported 11/11 PASS with exit 0 -- the opposite of what the plan requires and a gate that cannot actually be shown to fail.
- **Fix:** Removed `stripComments` entirely; all assertions now run literal substring checks against raw file content, matching the plan's own wording ("Assertions, all literal substring checks"). This is safe because Task 1's fix (Deviation 1 above) already ensures `auditoria-tab.tsx`'s own prose never contains the forbidden literal tokens.
- **Files modified:** `web/scripts/verify-auditoria-rbac.mjs`
- **Verification:** Re-ran the full injectand-revert cycle after the fix -- see "Verification Evidence" below for the real PASS -> FAIL -> PASS output.
- **Committed in:** `10c6a49e` (fixed before this commit was made; no separate commit needed)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs, both caught by the plan's own acceptance criteria before committing)
**Impact on plan:** Both fixes were necessary for the plan's literal acceptance criteria to hold and for the mutation-check demonstration to be genuine, not cosmetic. No scope creep.

## Issues Encountered

None beyond the two auto-fixed issues above, both caught by running the plan's own acceptance-criteria checks rather than assuming a prior plan's verify-script pattern would transfer unchanged.

## Verification Evidence

- `pnpm -C web lint` - `0 errors, 20 warnings`, exit 0. All 20 warnings pre-existing (React Compiler incompatible-library notices in `user-profile-form.tsx`/`data-table.tsx`, `<img>` LCP notices in `dashboard-shell.tsx`/`user-menu.tsx`) -- none in `auditoria-tab.tsx` or the modified `page.tsx`.
- `pnpm -C web build` - `✓ Compiled successfully`, TypeScript passed, all 27 routes generated (including `/settings`), no errors.
- `pnpm -C web exec tsc --noEmit -p tsconfig.json` - exit 0, no output (no errors).
- `pnpm -C web test` (vitest) - **6 test files, 54 tests passed** (unchanged from the 128-08 baseline -- this plan added no new test files, per its own scope).
- `git diff --stat "web/src/app/(dashboard)/settings/page.tsx"` - `23 insertions(+), 2 deletions(-)`; hunk headers at lines 19, 78, 162, 215 -- confirmed additive-only (the two deletions are the edited import-list and `TabId`-union lines themselves).
- `node web/scripts/verify-papeis-escritorio.mjs` (Phase 127 gate) - all 18 assertions `PASS`, exit 0. Confirms `RbacTab`, the module-scope draft cache, the `beforeunload` guard, and the dirty-count label survived this plan's edits untouched.
- `node web/scripts/verify-auditoria-rbac.mjs` (new gate, also run via `pnpm -C web run verify:auditoria-rbac`) - all 11 assertions `PASS`, exit 0.
- **Mutation-check demonstration (plan-required, Task 2 acceptance criteria):** ran the injectand-revert cycle for real:
  1. Baseline: `node web/scripts/verify-auditoria-rbac.mjs` -> 11/11 PASS, exit 0.
  2. Injected `// useMutation` as line 2 of `auditoria-tab.tsx` (`sed -i '1a // useMutation'`).
  3. Re-ran the script -> `FAIL sem-usemutation — auditoria-tab.tsx nao contem 'useMutation' (nem em codigo, nem em comentario)`, **10/11 PASS, exit 1**.
  4. Reverted with `git checkout -- "web/src/app/(dashboard)/settings/auditoria-tab.tsx"`.
  5. Re-ran the script -> 11/11 PASS, exit 0 again.
  This is a genuine demonstration, not a cosmetic one: the initial script draft (see Deviation 2 above) did NOT fail this same test, because its `stripComments` step silently removed the injected line before the check ran. The final script does not strip comments and correctly failed.
- All six pre-existing `verify:*` gates (`verify:juizo-origem`, `verify:limite-utilizadores`, `verify:consola-tenants`, `verify:papeis-escritorio`, `verify:relatorio-utilizacao`, `verify:consola-moldes`) plus the new `verify:auditoria-rbac` - every check `PASS`, exit 0 each, run via `pnpm -C web run <script>`.
- Backend suite: `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` -> **`Tests run: 424, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`**. Matches the phase's 424-test baseline exactly -- backend untouched by this plan (no backend files in `files_modified`).
- Acceptance-criteria greps (raw `grep -F`, not comment-stripped, per this plan's own literal wording): `export function AuditoriaTab`, `useOfficeRbacAuditoria(`, `auditoriaEventoToSentence(`, `Sem eventos registados`, `Todos os papéis` all present in `auditoria-tab.tsx`; `useMutation`, `dangerouslySetInnerHTML`, `DropdownMenu` all absent; `"auditoria"` and `activeTab === "auditoria" && hasRbacManage"` present in `page.tsx`; `papeisComAlteracoesPorGravar` and `beforeunload` still present in `page.tsx`.
- Stub scan (`TODO`/`FIXME`/"coming soon"/"not available"/"placeholder", case-insensitive) over the two new files -- no hits.
- `git status --short` after all commits -- clean working tree, no stray untracked files.

## Threat Flags

No new threat surface beyond this plan's own `<threat_model>` (T-128-42 through T-128-46, all `mitigate`, all addressed by the design already described above: sentence rendering as React text children, the verify script's forbidden-token checks, the `hasRbacManage` gate matching the server-side authority, `useAdminUsers({ enabled: hasUsersManage })` for the 403-avoidance mitigation, and the additive-only `page.tsx` diff for the Phase 127 non-regression mitigation). No new network endpoint, auth path, or schema change was introduced by this plan.

## Known Stubs

None. The tab is fully wired to live data (`useOfficeRbacAuditoria`, `useOfficeRbac`, `useAdminUsers`) with no hardcoded empty values or placeholder copy standing in for real data.

## User Setup Required

None - no external service configuration required. A running backend + `pnpm -C web dev` is required only for the pending human verification below (both are already runnable per `DEPLOYMENT.md`/`CLAUDE.md`, no new setup this plan).

## Pending Human Verification (Task 3, checkpoint:human-verify, gate="blocking")

**This checkpoint was intentionally NOT executed or marked passed.** Per this plan's own `<checkpoint_handling>` instructions: "You are inside an autonomous milestone run with no human present -- you cannot block waiting... Write the human-verify steps into SUMMARY.md as a clearly-labelled pending human verification section... Do NOT mark it passed and do NOT invent results you did not observe." All prerequisite automated gates for Task 3 (lint, build, both verify scripts, vitest, backend suite) were run above and are green -- only the human, browser-based steps remain.

A person with access to the running application must still:

1. Start the backend (`JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spring-boot:run`) and the web app (`pnpm -C web dev`); log in as an office administrator (e.g. `admin@alcv.cv` / `Pa$$w0rd`).
2. Definições -> "Controlo de Acesso (RBAC)": create a role "Teste Auditoria", rename it to "Teste Auditoria 2", tick one permission in the matrix and save.
3. Definições -> "Gestão de Utilizadores": assign "Teste Auditoria 2" to a user, then remove it.
4. Open "Auditoria" (right after the RBAC tab). Expect newest first: "... retirou o papel Teste Auditoria 2 a ...", "... atribuiu o papel ...", "... alterou as permissões do papel Teste Auditoria 2." with an "Acrescentou: <label>" sub-line, "... renomeou o papel Teste Auditoria para Teste Auditoria 2.", and "... criou o papel Teste Auditoria." No raw code (`papel_*`) anywhere.
5. Filter "Papel" = Teste Auditoria 2: all five events remain, including the create event written under the old name. Filter "Utilizador alvo" = that user: only the two assignment events. "Limpar filtros" restores the full list.
6. Confirm that no row has any menu, pencil, trash or clear action.
7. In the RBAC tab, tick a permission WITHOUT saving, switch to Auditoria and back: the unsaved edit and the dirty-count label are still there (Phase 127 fix intact).
8. Log in as a user of a second office (if one exists): their Auditoria tab does not show the events above. **Only exercisable if a second office already exists locally** -- record whether it was reachable.

This joins the similar live checklists left pending by Phases 124, 125 and 127, to be surfaced together at milestone close, per this plan's explicit instruction.

## Next Phase Readiness

- AUDT-01 through AUDT-04 are all implemented and automated-gate-verified: filterable, paginated, PT-CV, read-only audit consultation, additive to the existing Definições surface.
- This is the last plan of Phase 128 and of this milestone (per the executor's own framing: "This is the last plan of the milestone"). No further plans depend on this one (`affects: []`).
- Sole outstanding item before the milestone can be called fully verified end-to-end: the Task 3 human checklist above.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 3 created files (`web/src/app/(dashboard)/settings/auditoria-tab.tsx`, `web/scripts/verify-auditoria-rbac.mjs`, this SUMMARY.md) verified present on disk. Both task commit hashes (`fe74dcfb`, `10c6a49e`) verified present in `git log --oneline --all`. No missing items.
