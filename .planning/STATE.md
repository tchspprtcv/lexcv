---
gsd_state_version: 1.0
milestone: v2.6
milestone_name: Módulo de Parecer Jurídico — UI
status: milestone_complete
last_updated: 2026-07-01T20:04:33.218Z
last_activity: 2026-07-01 — Roadmap v2.6 criado (5 fases, 65–69, 12/12 requirements mapeados)
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 0
  completed_plans: 6
  percent: 0
stopped_at: Milestone complete (Phase 69 was final phase)
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-01)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Milestone complete

## Current Position

Phase: 69 of 69 (pesquisa avançada)
Plan: Not started
Status: Milestone complete
Last activity: 2026-07-01

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 6
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 58 | 4 | ~70 min | ~17 min |
| 59 | 6 | ~90 min | ~15 min |
| 60 | 2 | ~25 min | ~12 min |
| 65 | 2 | - | - |
| 66 | 1 | - | - |
| 67 | 1 | - | - |
| 68 | 1 | - | - |
| 69 | 1 | - | - |

## Accumulated Context

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.5 audit) Milestone v2.5 foi deliberadamente backend-only nas 4 fases — módulo de pareceres inutilizável via app até v2.6 entregar a UI. Ver `.planning/v2.5-MILESTONE-AUDIT.md`.
- (v2.6 research) Nenhuma nova dependência necessária — stack existente (TanStack Query, react-hook-form+zod, upload de Documentos) cobre 100% do milestone.
- (v2.6 research) Risco principal: repetir o defeito de casing camelCase/snake_case do v2.4 — entidades Parecer não têm `@JsonProperty`, logo JSON é 100% camelCase; deve ser confirmado por trace de resposta real antes de escrever tipos (Phase 65).
- (v2.6 research) RBAC de `aprovar` requer `pareceres:manage`; `entregar`/`criarVersao` requerem `pareceres:edit` + verificação de instância (ADMIN ou advogado responsável) que `hasScopedPermission` sozinho não expressa — tratado explicitamente na Phase 68.
- (v2.6 roadmap) PARV-05 torna o anexo de versão **obrigatório na UI** — mais restritivo que o backend, que trata como opcional. Decisão desta milestone, não do backend.
- (v2.6 roadmap) Aprovação interna (ADMIN, `pareceres:manage`) fica fora de âmbito nesta milestone (confirmado explicitamente pelo utilizador) — v2.6 cobre criação de versão + entrega direta + vista de entregue. Deferred para v2.7 (PARC-17).
- (v2.6 roadmap) 5 fases derivadas de 12 requisitos, ordem estrita de dependência: (65) fundação read-only, (66) criação de solicitação, (67) versionamento, (68) entrega+vista-entregue+RBAC (fase de maior risco), (69) pesquisa avançada. NOTF-05/06/07 distribuídas pelas fases correspondentes ao evento que as dispara, em vez de uma fase de notificações isolada.

### Pending Todos

- Plan Phase 53 (Shell Responsivo): dashboard-shell.tsx + sidebar + top bar + bottom nav
- Phase 59 follow-up (non-blocking): add content-type allowlist to POST /clientes/{id}/procuracao (59-SECURITY.md WR-02 — currently accepts arbitrary file types)
- Phase 59 follow-up (non-blocking, code review warnings): N+1 query in listClienteAdvogados/Administrativos; duplicate snake_case/camelCase fields in web/src/types/clientes.ts from merge artifacts; untyped date strings in intake list items — see 59-REVIEW.md
- Phase 60 follow-up (non-blocking, code review warnings): "Honorários — Totalidade" not formatted via formatMoneyCVE on ficha print page; isDadosTipoParticular type guard uses key-presence heuristic instead of isEmpresa discriminator; inconsistent blank-placeholder idiom — see 60-REVIEW.md
- Phase 60 follow-up (non-blocking, UX gap noted in 60-SECURITY.md): mobile card view in clientes listing has no ficha/Printer entry point (desktop-only for now)
- Pre-existing app-wide tenantId/createdAt-style snake_case/camelCase mismatches outside v2.4's scope were identified but intentionally not touched (out of milestone scope, broader blast radius) — candidate for a future cleanup phase.
- Phase 65 follow-up (non-blocking, UI review 18/24 — see 65-UI-REVIEW.md): status badges render raw enum values instead of Portuguese labels in both pareceres/page.tsx and pareceres/[id]/page.tsx; cliente name not resolved on detail page (raw UUID shown, list page already has the resolution pattern); AnexoLink download failure silently swallowed (no download.isError handling) — regression introduced when WR-04 removed the redundant toast without adding proper error UI
- Phase 65 human_verification pending (see 65-VERIFICATION.md): responsive dual-view rendering, anexo presigned-URL download flow, cross-role nav/access-denied behavior, cross-tenant IDOR 404 handling — deferred by user decision, needs live browser/backend test before shipping v2.6
- Phase 66 follow-up (non-blocking, UI review 19/24 — see 66-UI-REVIEW.md): processoId select has no loading/error state (unlike clienteId); focus ring uses blue-500 vs spec's declared blue-600/700 (pre-existing mismatch from processos/novo/page.tsx); CardTitle never gets text-lg font-bold override so "Dados da Solicitação" doesn't match declared 18px/700 typography
- Phase 66 human_verification pending (see 66-VERIFICATION.md): end-to-end create flow (submit → toast → redirect → list update), access-denied path for non-privileged users, WR-01/WR-02 live behavior (cliente-switch resets processoId, backend 400s mismatched cliente/processo)
- Phase 67 follow-up (non-blocking): `useUploadDocumentoComProgresso` in `use-documentos.ts` has the same missing `xhr.timeout` gap that was fixed in the new parecer-versão upload hook (WR-03) — left untouched to avoid unrelated drift, candidate for a small follow-up fix
- Phase 67 human_verification pending (see 67-VERIFICATION.md): form-submit blocking without anexo, real upload progress bar, toast display, live timeline append without reload, RBAC card visibility for a real unauthorized session, CONCLUIDO read-only banner with real data
- Phase 67 follow-up (non-blocking, UI review 17/24 — see 67-UI-REVIEW.md): accent color (`bg-blue-600`) leaks onto timeline dot marker and FileDropZone trigger text, violating the spec's accent-reservation rule; "Dados"/"Versões" CardTitles left at unstyled `h3` default while "Nova Versão" got the mandated `text-lg font-bold` fix — **this is the 3rd recurrence of the same CardTitle-missing-override defect class flagged in Phases 65 and 66; worth a dedicated cross-cutting fix across all `/pareceres` pages rather than continuing to patch per-phase**; no guard against a stale Nova Versão form if solicitação transitions to CONCLUIDO mid-session
- Phase 68 human_verification pending (see 68-VERIFICATION.md): irreversibility UX clarity, live status transition without reload, cross-role read-only enforcement post-entrega, visual typography/color correctness
- Phase 68 follow-up (non-blocking, UI review 20/24 — see 68-UI-REVIEW.md): recurring CardTitle typography defect (flagged in Phases 65/66/67) is now FULLY CLOSED across all /pareceres pages — 5/5 instances confirmed with text-lg font-bold, both accent-color leaks fixed (timeline dot + FileDropZone trigger text); remaining minor items: version-select in EntregarParecerDialog uses rounded-md instead of module's rounded-none convention; no stale-state guard if solicitação changes state while entrega dialog is open (same class as Phase 67's CONCLUIDO-mid-session gap, now on the higher-stakes irreversible action)
- Phase 69 follow-up (non-blocking, code review): 3 warnings found and fixed (WR-01 Aplicar didn't reset search mode, WR-02 inconsistent filters object shape, WR-03 pesquisa cache namespace not invalidated by mutations) — all fixed same-session, see 69-REVIEW.md/69-REVIEW-FIX.md
- Phase 69 human_verification pending (see 69-VERIFICATION.md): toggle interplay between simple filters and advanced search, live search rendering, empty-state with real data
- Phase 69 follow-up (non-blocking, UI review 15/24 — see 69-UI-REVIEW.md, lowest score this milestone): submitting the simple "Aplicar" filter bar silently discards an active search with no warning while the advanced panel stays open/populated (a UX side-effect of the WR-01 code-review fix that resolved a different bug — the panel should either close/clear when Aplicar is used, or the user should get a clear signal that search was replaced); zero-result empty-state heading uses font-medium/14px instead of spec's text-base font-bold (16px/700); no single focal point when both "Nova Solicitação" and "Pesquisar" CTAs are visible with two open filter cards
- Milestone v2.6: all 5 phases (65-69) complete — run `/gsd:audit-milestone` next

### Blockers/Concerns

None.

## Deferred Items

Items acknowledged and deferred at milestone v2.4 close on 2026-06-30:

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 50 (50-VERIFICATION.md) — pre-existing from v2.2, not part of v2.4 | human_needed |
| verification_gap | Phase 51 (51-VERIFICATION.md) — pre-existing from v2.2, not part of v2.4 | human_needed |

Items deferred at milestone v2.5 close (2026-06-30), scoped for v2.6/v2.7:

| Category | Item | Status |
|----------|------|--------|
| scope | UI frontend do módulo de pareceres — v2.5 foi backend-only | addressed_by_v2.6 |
| feature | PARC-17 Aprovação interna (ADMIN) na UI | deferred_to_v2.7 |
| feature | PARV-07 Diff/comparação entre versões | deferred_to_v2.7 |
| feature | PARV-08 Editor de texto formatado (rich text) | deferred_to_v2.7 |

Items acknowledged and deferred at milestone v2.6 close on 2026-07-01 (see .planning/v2.6-MILESTONE-AUDIT.md for full detail):

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 65 (65-VERIFICATION.md) — static verification passed 4/4, live browser/backend test not performed in this environment | human_needed |
| verification_gap | Phase 66 (66-VERIFICATION.md) — static verification passed 6/6, live browser/backend test not performed in this environment | human_needed |
| verification_gap | Phase 67 (67-VERIFICATION.md) — static verification passed 8/8, live browser/backend test not performed in this environment | human_needed |
| verification_gap | Phase 68 (68-VERIFICATION.md) — static verification passed 6/6, live browser/backend test not performed in this environment | human_needed |
| verification_gap | Phase 69 (69-VERIFICATION.md) — static verification passed 5/5, live browser/backend test not performed in this environment | human_needed |
| feature | NOTF-05/06/07 in-app notifications for parecer assignment/versioning/entrega | deferred — requires a future milestone to build a generic notification backend (none exists) |
| feature | PARC-17 Aprovação interna (ADMIN) na UI de pareceres | deferred_to_v2.7 (carried over from v2.5 close, still not built — out of v2.6 scope by explicit user decision) |
| ux | Phase 69 UI review (15/24, lowest this milestone): Aplicar on the simple filter bar silently discards an active search with no warning | non-blocking, candidate for quick follow-up |

## Operator Next Steps

- Milestone v2.6 complete — run `/gsd:new-milestone` to start the next milestone

</content>
