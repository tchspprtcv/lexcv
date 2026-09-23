---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 08
subsystem: web
tags: [tanstack-query, vitest, rbac, audit-log, pt-cv-copy]

# Dependency graph
requires:
  - phase: 128-07
    provides: "GET /api/v1/admin/rbac/auditoria: paginated, tenant-scoped RBAC audit query behind hasAuthority('rbac:manage')"
provides:
  - "AuditoriaRbacEntry/AuditoriaRbacListFilters/AuditoriaRbacPageResponse: the frontend wire contract for the audit endpoint"
  - "auditoriaEventoToSentence/auditoriaEventoToTexto/auditoriaCategoriaToLabel/auditoriaPermissoesDetalhe: pure PT-CV sentence composer, tested"
  - "useOfficeRbacAuditoria: read-only paginated query hook, cache-linked to every RBAC/user mutation"
affects: [128-09]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Record<AuditoriaRbacAcao, builder> sentence composer, same *ToLabel convention as lib/tipo-decisao.ts and lib/origem-processo.ts, but returning SegmentoFrase[] (text + destaque) instead of a single string, so Plan 09 can render actor/target/role names font-semibold without re-parsing"
    - "OFFICE_RBAC_AUDITORIA_KEY deliberately nested under OFFICE_RBAC_KEY so TanStack's prefix-match invalidation refreshes the audit list from the four existing role/matrix mutations with zero changes to them"

key-files:
  created:
    - web/src/types/auditoria-rbac.ts
    - web/src/lib/auditoria-rbac.ts
    - web/src/lib/auditoria-rbac.test.ts
  modified:
    - web/src/hooks/use-admin.ts

key-decisions:
  - "Wire contract built against the shipped Java code (AuditoriaRbacController/AuditoriaRbacEntradaDto/AuditoriaRbacService), not the UI-SPEC's assumptions -- confirmed papelId is a UUID filter (not a name) and path is /admin/rbac/auditoria (not /admin/rbac/audit), matching 128-07-SUMMARY.md's reconciliation note exactly."
  - "queryFn uses apiFetch<AuditoriaRbacPageResponse>(\"/admin/rbac/auditoria\" + buildAuditoriaSearch(filters)) -- string concatenation with a double-quoted literal, not a template literal, so the literal path string is greppable verbatim (matches the plan's own acceptance-criteria pattern)."
  - "nomeAntigo/nomeNovo (rename event) fall back to the same PAPEL_REMOVIDO constant as papelNome when null, even though the UI-SPEC's behavior table only tests the papelNome fallback explicitly -- consistent with the binding 'never null/undefined' invariant, extended to the one other role-name-shaped field the composer touches."
  - "auditoriaPermissoesDetalhe accepts a Pick<AuditoriaRbacEntry, 'permissoesAdicionadas' | 'permissoesRemovidas'> rather than the full entry type, so callers (and the test) can pass a minimal object without constructing every other required field."

requirements-completed: [AUDT-01, AUDT-02, AUDT-03]

# Metrics
duration: 15min
completed: 2026-09-22
---

# Phase 128 Plan 08: Auditoria frontend data layer (types, sentence composer, query hook) Summary

**The frontend contract for the Auditoria tab: `AuditoriaRbacEntry`/`ListFilters`/`PageResponse` types mirroring the shipped `GET /api/v1/admin/rbac/auditoria`, a pure `auditoriaEventoToSentence` composer that turns structured events into PT-CV sentences with a binding null-name fallback and an unknown-`acao` placeholder (17 vitest tests), and `useOfficeRbacAuditoria` — a read-only paginated hook whose cache is kept fresh by every RBAC and user mutation already in `use-admin.ts`.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-09-22 (approx, following 128-07 completion)
- **Completed:** 2026-09-22T16:04:00Z (approx)
- **Tasks:** 2
- **Files modified:** 4 (3 created, 1 modified)

## Accomplishments

- `web/src/types/auditoria-rbac.ts`: `AuditoriaRbacAcao` (the six known codes), `AuditoriaRbacCategoria`, `AuditoriaRbacMotivo`, `AuditoriaRbacEntry` (field set and nullability verified against `AuditoriaRbacEntradaDto.java` field-by-field — `acao` typed `string`, not the union, because an unknown value must remain representable rather than a type error), `AuditoriaRbacListFilters`, `AuditoriaRbacPageResponse` (the `NotificacoesPageResponse` envelope shape).
- `web/src/lib/auditoria-rbac.ts` (pure, no `react` import): `auditoriaEventoToSentence` — a `Record<AuditoriaRbacAcao, builder>` lookup (six templates: criar/renomear/apagar/permissoes_alterar/atribuir/retirar) returning `SegmentoFrase[]` (`{ texto, destaque }`) so Plan 09 can render actor/target/role names `font-semibold` without re-parsing the sentence. Null-name fallback (`um administrador removido` / `um utilizador removido` / `um papel removido`) is applied per-field via dedicated helpers, always `destaque: true`. `papel_atribuir` branches on `motivo === "provisionamento"` to render "A plataforma atribuiu..." with no person subject, even though `autorNome` is null by design for that event. `papel_retirar` branches on `motivo === "utilizador_eliminado"` to append "— utilizador eliminado.". An `acao` not present in the `Record` (checked via `Object.prototype.hasOwnProperty.call`, not `in`, to avoid a prototype-chain false positive) returns the fixed placeholder `"Evento de auditoria não reconhecido."` and never includes the raw code. `auditoriaEventoToTexto` joins segments for tests/aria-labels. `auditoriaCategoriaToLabel` maps `"papel"`/`"atribuicao"` to `"Papel"`/`"Atribuição"`. `auditoriaPermissoesDetalhe` builds `"Acrescentou: {lista} · Retirou: {lista}"` from key arrays plus a `rotulos` catalogue map, falling back to the raw key when a label is missing, returning `null` when both lists are empty or null.
- `web/src/lib/auditoria-rbac.test.ts`: 17 vitest tests (plan required ≥13), one per `<behavior>` bullet plus the two `auditoriaCategoriaToLabel` cases — every sentence template, both motivo branches, the provisioning no-person-subject case, all three null-name fallbacks individually and combined (asserting no `"null"`, `"undefined"`, `alvoId` or `papelId` leaks into the text), the unknown-`acao` placeholder, and both `auditoriaPermissoesDetalhe` branches.
- `web/src/hooks/use-admin.ts`: added `OFFICE_RBAC_AUDITORIA_KEY = ["admin", "rbac", "auditoria"] as const`, nested under the existing `OFFICE_RBAC_KEY` so TanStack's prefix-match invalidation means the four existing role/matrix mutations (`useSaveOfficeRbac`/`useCreateOfficeRole`/`useRenameOfficeRole`/`useDeleteOfficeRole`) refresh the audit list unchanged. Added private `buildAuditoriaSearch(filters)` (mirrors `buildNotificacoesSearch`). Added `export function useOfficeRbacAuditoria(filters = {})`: `useQuery` with a queryKey over every filter dimension, `queryFn: () => apiFetch<AuditoriaRbacPageResponse>("/admin/rbac/auditoria" + buildAuditoriaSearch(filters))`, `enabled: typeof window !== "undefined"`, `staleTime: 30_000`, `placeholderData: keepPreviousData` (imported from `@tanstack/react-query`) so paging doesn't flash a loading state. `useAdminSaveUser` and `useAdminDeleteUser` `onSuccess` now also invalidate `OFFICE_RBAC_AUDITORIA_KEY`, since assigning/removing/deleting a user writes `atribuicao_papel` audit events that the two role/matrix-only invalidation chains don't cover.

## Task Commits

Each task was committed atomically:

1. **Task 1: Types and the sentence composer with vitest** - `d19be0ca` (feat)
2. **Task 2: useOfficeRbacAuditoria hook and cache invalidation** - `f299e21e` (feat)

## Files Created/Modified

- `web/src/types/auditoria-rbac.ts` - wire types mirroring `AuditoriaRbacEntradaDto`/`AuditoriaRbacController`/`AuditoriaRbacPageResponse`
- `web/src/lib/auditoria-rbac.ts` - pure PT-CV sentence composer and permission-detail/category label helpers
- `web/src/lib/auditoria-rbac.test.ts` - 17 vitest tests, one per behavior bullet
- `web/src/hooks/use-admin.ts` - `useOfficeRbacAuditoria` query hook, `OFFICE_RBAC_AUDITORIA_KEY`, two new cache invalidations

## Decisions Made

- Built the wire contract against `AuditoriaRbacController.java`/`AuditoriaRbacEntradaDto.java`/`AuditoriaRbacService.java` directly rather than the UI-SPEC's "Data Contract Assumptions" section, per Critical Invariant 1 and 128-07-SUMMARY.md's own reconciliation note: path is `/admin/rbac/auditoria`, role filter is `papelId` (UUID), not `papel` (name).
- Used string concatenation (`"/admin/rbac/auditoria" + buildAuditoriaSearch(filters)`) instead of a template literal for the `apiFetch` call, so the plan's own acceptance-criteria grep for the literal `"/admin/rbac/auditoria"` (with surrounding double quotes) matches verbatim — caught by running the grep during Task 2 rather than assuming the `useNotificacoes` template-literal precedent would satisfy it.
- Extended the null-name fallback to `nomeAntigo`/`nomeNovo` (the rename event's two role-name fields), reusing `PAPEL_REMOVIDO`, even though only `papelNome`'s fallback is explicitly tested — consistent with the binding "never null/undefined" invariant applied to every role-name-shaped field the composer touches, not just the ones in the behavior table.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `apiFetch` call used a template literal, failing the plan's own grep acceptance criterion**
- **Found during:** Task 2, acceptance-criteria verification
- **Issue:** The first implementation followed the `useNotificacoes` precedent exactly (`` `/admin/rbac/auditoria${buildAuditoriaSearch(filters)}` ``, a template literal). The plan's acceptance criteria require `grep -F "\"/admin/rbac/auditoria\"" web/src/hooks/use-admin.ts` to match — a double-quoted literal substring that a template literal never contains.
- **Fix:** Changed to `apiFetch<AuditoriaRbacPageResponse>("/admin/rbac/auditoria" + buildAuditoriaSearch(filters))`, matching the plan's own `<action>` text verbatim (`apiFetch<AuditoriaRbacPageResponse>("/admin/rbac/auditoria" + search)`), which specifies concatenation, not interpolation.
- **Files modified:** `web/src/hooks/use-admin.ts`
- **Commit:** `f299e21e` (fixed before this commit was made; no separate commit needed)

## Issues Encountered

None beyond the one auto-fixed issue above, caught by running the plan's own acceptance-criteria greps rather than assuming the `useNotificacoes` pattern would satisfy them verbatim.

## Verification Evidence

- `pnpm -C web exec vitest run src/lib/auditoria-rbac.test.ts` — 17 tests passed (plan required ≥13).
- `pnpm -C web exec vitest run` (full suite) — 6 test files, **54 tests passed** (baseline 37 from `phase_context` + 17 new).
- `pnpm -C web exec tsc --noEmit -p tsconfig.json` — `TypeScript: No errors found`, confirmed exit 0 via `echo $?` (per the project memory note on rtk false-clean reports); `web/node_modules` was already present, no install needed.
- `pnpm -C web lint` — `✖ 20 problems (0 errors, 20 warnings)`, exit 0. All 20 warnings are pre-existing, in files this plan did not touch (React Compiler incompatible-library notices, `<img>` LCP warnings) — none in `auditoria-rbac.ts`, `auditoria-rbac.test.ts`, `types/auditoria-rbac.ts` or `use-admin.ts`.
- `pnpm -C web build` — `✓ Compiled successfully`, TypeScript and all 27 routes generated with no errors.
- All six `verify:*` gates (`verify:juizo-origem`, `verify:limite-utilizadores`, `verify:consola-tenants`, `verify:papeis-escritorio`, `verify:relatorio-utilizacao`, `verify:consola-moldes`) — every check `PASS`, exit 0 each.
- Acceptance-criteria greps (Grep tool, not bash grep, per the project memory note on complex-pattern false negatives): `"um administrador removido"`, `"um utilizador removido"`, `"um papel removido"`, `"Evento de auditoria não reconhecido."`, `"retirou o papel"` all present in `auditoria-rbac.ts`; `from "react"` absent (pure module confirmed). `export function useOfficeRbacAuditoria` present; `"/admin/rbac/auditoria"` present (after the Task 2 fix above); `OFFICE_RBAC_AUDITORIA_KEY` occurs 6 times (declaration + comment + hook queryKey + two mutation invalidations + one more reference) — plan required ≥4.
- Stub scan (`TODO`/`FIXME`/"coming soon"/"not available"/"placeholder", case-insensitive) over all four files — the only hits are the legitimate uses of the English word "placeholder" (documenting the intentional unknown-`acao` fallback pattern, and TanStack Query's own `placeholderData` API option), not stub markers.
- Threat surface scan: no new surface beyond the plan's own `<threat_model>` (T-128-38 through T-128-41) — this plan is pure client-side data-layer code (types, a pure function, a `useQuery`-only hook) with no new network endpoint, auth path, or schema change. `auditoriaEventoToSentence` returns plain-text segments (never HTML), and `useOfficeRbacAuditoria` has no `useMutation` counterpart, matching T-128-38/T-128-40's mitigation exactly.

## User Setup Required

None.

## Next Phase Readiness

- `AuditoriaRbacEntry`/`ListFilters`/`PageResponse`, `auditoriaEventoToSentence`/`auditoriaEventoToTexto`/`auditoriaCategoriaToLabel`/`auditoriaPermissoesDetalhe`, and `useOfficeRbacAuditoria` are all ready for Plan 09 to compose into the `AuditoriaTab` component per `128-UI-SPEC.md` §2-3.
- Plan 09's verify script should assert the sentence segments are rendered as React text children (never `dangerouslySetInnerHTML`) and that no mutation hook is ever added against the audit endpoint, per T-128-38/T-128-40.
- No blockers for Plan 09.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 4 created/modified source files verified present on disk. All three commit hashes (`d19be0ca`, `f299e21e`, `30166c05`) verified present in `git log --oneline --all`. No missing items.
