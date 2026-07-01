---
phase: 67-elabora-o-e-versionamento
plan: 01
subsystem: ui
tags: [react-hook-form, zod, xhr-upload, react-query, rbac, pareceres]

requires:
  - phase: 65-funda-o-listagem-e-detalhe
    provides: "pareceres/[id]/page.tsx detail page, useParecer/useParecerVersoes hooks, types/pareceres.ts"
  - phase: 66-criacao-de-solicitacao
    provides: "pareceres:edit RBAC pattern, superRefine 10-char validation convention"
provides:
  - "parecerVersaoCreateFormSchema (conteudo min-10 + required FileList) in web/src/schemas/pareceres.ts"
  - "useCreateParecerVersao XHR multipart upload hook with progress + 3-key cache invalidation"
  - "Nova Versão form section on /pareceres/[id] gated by pareceres:edit + instance check + status"
affects: [68-entrega-e-parecer-entregue]

tech-stack:
  added: []
  patterns:
    - "XHR multipart upload with progress replicated (not imported) per-domain, matching use-documentos.ts#useUploadDocumentoComProgresso shape"
    - "Cascading cache invalidation (3 query-key namespaces) after a mutation that could affect list/detail/nested-resource views"
    - "RBAC scope-permission + per-instance ownership check combined client-side (mirrors backend isAdmin || isResponsavel), card silently omitted (no dead buttons) when unauthorized"

key-files:
  created: []
  modified:
    - web/src/schemas/pareceres.ts
    - web/src/hooks/use-pareceres.ts
    - "web/src/app/(dashboard)/pareceres/[id]/page.tsx"

key-decisions:
  - "usePermissions() lifted to ParecerDetailPage and passed down to ParecerDetailContent to avoid a second network call for the edit-gate + instance check"
  - "Nova Versão card fully omitted (not disabled) when user lacks pareceres:edit or is neither ADMIN nor the responsavel — defense-in-depth mirrors backend 403"
  - "CONCLUIDO status renders a neutral read-only banner instead of the form, reinforcing immutability post-entrega"

requirements-completed: [PARV-05, PARV-06]

duration: ~20min
completed: 2026-07-01
---

# Phase 67 Plan 01: Elaboração e Versionamento Summary

**"Nova Versão" form on the parecer detail page lets the advogado responsável or ADMIN submit successive immutable versions (resumo + required anexo) via a new `useCreateParecerVersao` XHR-multipart hook with progress bar, reusing the Documentos upload pattern.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments
- `parecerVersaoCreateFormSchema` enforces `conteudo` >= 10 chars and a required single-file `FileList` at the UI layer (PARV-05)
- `useCreateParecerVersao` replicates the `useUploadDocumentoComProgresso` XHR+FormData+progress shape for the parecer-versão endpoint, invalidating `["pareceres","versoes",id]`, `["pareceres","detail",id]`, and `["pareceres","list"]` on success
- Detail page gains a "Nova Versão" card gated by `pareceres:edit` AND (ADMIN or `advogadoId === me.id`) AND status !== CONCLUIDO; silently omitted otherwise, replaced by a read-only banner when CONCLUIDO

## Task Commits

1. **Task 1: Schema de versão + hook de upload com progresso** - `6223b17` (feat)
2. **Task 2: Secção "Nova Versão" no detalhe com RBAC + instance check + upload** - `de662ee` (feat)

## Files Created/Modified
- `web/src/schemas/pareceres.ts` - added `parecerVersaoCreateFormSchema` + `ParecerVersaoCreateFormValues`
- `web/src/hooks/use-pareceres.ts` - added `API_BASE` import, `ParecerVersaoCreatePayload`, `useCreateParecerVersao`
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` - lifted `usePermissions()` to page level, added `NovaVersaoForm` component and conditional render (form / read-only banner / omitted)

## Decisions Made
- Followed PLAN.md/PATTERNS.md verbatim guidance for XHR shape, schema, and form markup (near-exact port from `documentos/novo/page.tsx`).
- No second `usePermissions()` call — passed the single instance down as a prop typed via `ReturnType<typeof usePermissions>`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

`pnpm lint` reports 5 pre-existing errors / 18 warnings across unrelated files (`use-toast.ts`, `dashboard-shell.tsx`, `documentos/novo/page.tsx`, `processos/*`, `settings/page.tsx`). Confirmed via `git stash`/`stash pop` that these are identical to master's baseline (23 problems before and after this plan's changes) — out of scope per SCOPE BOUNDARY rule, not introduced by this plan. Grep-checked that no lint output references `pareceres/[id]/page.tsx`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Elaboração/versionamento flow is fully wired end-to-end (create solicitação → submit versions → immutable timeline). Ready for Phase 68 (entrega e "Parecer Entregue" view), which will need to add the entrega action and a dedicated read-only view once `status === CONCLUIDO`.

---
*Phase: 67-elabora-o-e-versionamento*
*Completed: 2026-07-01*

## Self-Check: PASSED

- FOUND: web/src/schemas/pareceres.ts
- FOUND: web/src/hooks/use-pareceres.ts
- FOUND: web/src/app/(dashboard)/pareceres/[id]/page.tsx
- FOUND: 6223b17 in git log
- FOUND: de662ee in git log
