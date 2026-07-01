# Project Research Summary

**Project:** LexCV - Modulo de Parecer Juridico (Frontend UI), milestone v2.6
**Domain:** Internal legal practice management add-on - request/version/approval/delivery workflow UI over an already-shipped backend
**Researched:** 2026-07-01
**Confidence:** HIGH

## Executive Summary

This milestone is pure frontend integration work, not greenfield product design: the backend (ParecerController, ParecerSolicitacao, ParecerVersao) was fully built and audited in v2.5, and the only gap is that no UI exists to drive it. All four research tracks converge on the same conclusion: every capability needed (list/detail views, multipart file upload, timeline rendering, RBAC-gated actions, print-friendly summary views) already has a proven, directly-reusable pattern elsewhere in the LexCV codebase (Processos, Documentos, Clientes/Ficha Cliente). No new npm packages, no new architectural primitives, and no new UI paradigms (e.g. no Tabs library, no diff library, no dropzone library) should be introduced. The build is best understood as porting these five existing patterns into a new /pareceres route group, in dependency order: read-only list/detail first, then create/edit mutations, then versioning, then the two irreversible terminal actions (aprovacao, entrega), then advanced search.

The primary risk is not technical complexity but repeating a known defect class from this exact codebase: the v2.4 milestone shipped 9/19 broken requirements because of camelCase/snake_case field-naming drift between backend JSON and frontend types, caught only late because verification tested backend-in-isolation and frontend-code-presence separately, never a live JSON trace. The Parecer entities have zero JsonProperty overrides, meaning every field is plain camelCase on the wire (versaoFinalId, numeroVersao, caminhoAnexo, criadoPorId, aprovadoPorId, aprovadoEm) but it would be easy to guess snake_case by pattern-matching other Portuguese-domain fields in the app. A second major risk is RBAC/state-machine drift: aprovar requires the stricter pareceres:manage scope (not edit), and both entregar and criarVersao layer an additional instance-level check (ADMIN or the assigned advogado) on top of scope-based permissions that hasScopedPermission alone cannot express. Both risks are cheap to prevent (a live JSON trace before writing types; an explicit scope-to-endpoint table before writing action buttons) but expensive to discover late, since they produce silent failures that pass tsc/build checks.

The recommended approach is to build strictly in the dependency order laid out in ARCHITECTURE.md: (1) types/schemas plus read-only list/detail, verified against a live backend response; (2) creation/edit mutations; (3) versioning (multipart upload, reusing Documentos' low-level patterns but NOT its delete/replace capability, which does not exist for immutable versoes); (4) aprovacao/entrega as a combined high-scrutiny phase sharing one centralized status-derivation helper (never scattered inline status checks); (5) advanced search last, as a purely additive read capability. The single highest-leverage, lowest-cost feature is the dedicated "Parecer Entregue" view, which directly closes the named v2.5 audit gap (PARC-09) and is mostly a formatting layer over data the API already returns.

## Key Findings

### Recommended Stack

No new libraries are required. The existing stack (Next.js 16 App Router, React 19, TypeScript strict, Tailwind 4, TanStack Query 5, react-hook-form + zod) covers 100% of this milestone's needs. All UI primitives needed already exist in web/src/components/ui/. This should be additive source-file-only work (new route files, one new hook file, one new schema file, one new type file, permission scope additions).

Core technologies:
- @tanstack/react-query: all 12 /api/v1/pareceres/* calls, same useQuery/useMutation + invalidateQueries pattern as use-documentos.ts/use-processos.ts
- react-hook-form + zod: solicitacao/versao/aprovacao/entrega forms, mirrors schemas/clientes.ts and schemas/processos.ts
- Manual tab state (useState<TabKey> + button toggles): no Radix/shadcn Tabs primitive exists in the repo; reuse the Processos detail page's exact pattern rather than introducing a second tab paradigm
- XHR-based upload hook (from use-documentos.ts): required for upload-progress reporting, which plain fetch cannot reliably provide

### Expected Features

Must have (table stakes) - v2.6 launch:
- /pareceres lista with status badges, filters (status, advogado, cliente/processo)
- /pareceres/[id] detalhe with timeline de versoes (direct port of Processos' timeline/auditoria tab)
- Criacao de solicitacao form (cliente/processo linkage, advogado atribuicao via searchable user-picker)
- Criacao de versao form (conteudo + optional anexo, reusing Documentos upload UX)
- Aprovacao action (ADMIN-only, gated pareceres:manage)
- Entrega action with irreversibility confirm dialog
- Vista dedicada "Parecer Entregue" - closes the named audit gap PARC-09
- Pesquisa avancada UI mirroring backend pesquisar
- RBAC gating via hasScopedPermission, mirrored exactly to backend PreAuthorize

Should have (differentiators, v2.7+):
- Diff/comparacao entre versoes (simple text/line diff only, explicitly not clause-level redline)
- Notificacoes in-app for parecer events (existing v2.1 notification infra)
- Rich text editor for versao content (pending a Markdown vs. HTML content-format decision)

Defer (v3+ or reject):
- Ficha de parecer imprimivel (only if real office demand emerges; pattern exists cheaply via Ficha Cliente)
- Clause-level redline diff, real-time co-editing, editable/deletable versoes, reversible entrega, external e-signature routing, configurable multi-step approval engine - all explicitly anti-features that contradict the backend's immutable/single-gate design

### Architecture Approach

The /pareceres module is a straight application of LexCV's existing route-group plus hooks plus schemas plus types pattern (as used by processos/clientes/documentos) - integration, not invention. The one real design decision is modeling the solicitacao to versao to aprovacao to entrega lifecycle as TanStack Query state, since it's more stateful than flat CRUD (closer to processos+fases/movimentacoes than to clientes). Critically, pareceres types should be pure camelCase with no snake_case/camelCase normalization shim like use-processos.ts's normalizeProcesso - that shim exists only due to processos' historical mismatch and does not apply here.

Major components:
1. use-pareceres.ts / use-parecer-versoes.ts - nested-resource hooks scoped by parent id, with query keys for list, detail(id), and versoes(id), plus a separate pesquisa query-key namespace (never conflated with list)
2. Dedicated terminal-action mutation hooks (useAtribuirAdvogado, useAprovarVersao, useEntregarSolicitacao) - not a generic PUT wrapper, since each has different RBAC tier and invalidation footprint
3. components/pareceres/entrega-view.tsx - conditionally rendered inside the existing detail page when status is CONCLUIDO (not a separate route), resolving PARC-09
4. Multipart useCreateParecerVersao - diverges from the JSON apiFetch pattern used everywhere else, mirrors Documentos' FormData/XHR approach instead

### Critical Pitfalls

1. camelCase/snake_case field drift (repeat of v2.4 defect class) - ParecerSolicitacao/ParecerVersao have zero JsonProperty overrides, so all fields are camelCase on the wire. Trace a live JSON response before writing types/pareceres.ts; never infer casing from the Java class or from other (snake_case DB column) conventions in the app.
2. UI implying editability after irreversible entregar - backend guards CONCLUIDO inconsistently (scattered per-endpoint checks; updateSolicitacao has NO guard at all). Build one centralized status-derivation helper and treat CONCLUIDO as fully read-only in the UI regardless of backend gaps.
3. RBAC scope/action drift - aprovar requires pareceres:manage (not edit); entregar and criarVersao require pareceres:edit plus an instance-level check (ADMIN or the assigned advogado) that hasScopedPermission cannot express alone. Build the gating logic from the exact endpoint-scope table, not by pattern-matching other modules.
4. File upload/download pattern misapplication - Documentos supports delete/replace and richer form fields; Parecer versoes are immutable/append-only with only conteudo (optional) plus file (optional, at least one required). Write use-parecer-versoes.ts fresh, reusing only low-level FormData/presigned-URL patterns from Documentos, not its higher-level CRUD shape.
5. Unbounded list without pagination - listSolicitacoes/pesquisar return unbounded, in-memory-filtered results with no backend pagination. Not a v2.6 blocker, but avoid building client logic that assumes pagination exists.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Foundation - types, schemas, read-only list + detail
Rationale: Nothing else can be verified/tested without a place to observe solicitacao state; also the cheapest point to catch the camelCase/snake_case risk before it propagates into forms.
Delivers: types/pareceres.ts, schemas/pareceres.ts, use-pareceres.ts (list/detail hooks), use-parecer-versoes.ts (read hook), /pareceres list page, /pareceres/[id] detail page (read-only, status badges, version timeline), nav item gated by pareceres:view
Addresses: Lista de solicitacoes, status badges, detalhe da solicitacao, timeline de versoes (all table stakes from FEATURES.md)
Avoids: Pitfall 1 (field-naming drift) - must trace a live JSON response here before anything downstream depends on the types

### Phase 2: Creation + editing mutations
Rationale: Solicitacao lifecycle starts here; depends on Phase 1's list/detail to display results.
Delivers: /pareceres/novo creation form, /pareceres/[id]/editar edit form, useCreateParecerSolicitacao, useUpdateParecerSolicitacao, useAtribuirAdvogado (advogado picker reusing Cliente intake's user-linking pattern)
Uses: react-hook-form + zod (STACK.md), cliente/processo picker hooks already existing
Implements: Component boundaries pareceres/novo/page.tsx, pareceres/[id]/editar/page.tsx from ARCHITECTURE.md

### Phase 3: Versionamento (elaboracao)
Rationale: Requires a solicitacao to exist (Phase 2) and a place to display versions (Phase 1).
Delivers: components/pareceres/versao-form.tsx (multipart conteudo + optional anexo), useCreateParecerVersao, useDownloadParecerAnexo, wired into the detail page's version timeline
Addresses: Criacao de versao (table stakes)
Avoids: Pitfall 4 (file upload/download pattern misapplication) - build fresh, do not port Documentos' delete/replace affordances

### Phase 4: Aprovacao e Entrega (terminal actions)
Rationale: Depends on versions existing (Phase 3); highest-risk phase for RBAC/state-machine bugs since CONCLUIDO is irreversible. Should not be interleaved with Phase 3 - both are high-stakes, distinct RBAC boundaries (manage vs edit+ownership-check).
Delivers: useAprovarVersao (gated pareceres:manage), useEntregarSolicitacao (gated pareceres:edit + ownership check), components/pareceres/entrega-view.tsx (closes PARC-09), centralized status-derivation helper for action visibility
Addresses: Aprovacao (ADMIN-only), entrega with irreversibility warning, Vista "Parecer Entregue" (all P1 in FEATURES.md)
Avoids: Pitfall 2 (implied editability post-entrega) and Pitfall 3 (RBAC scope/action drift) - both must be resolved with the shared helper and the exact scope table before this phase is considered done

### Phase 5: Pesquisa avancada
Rationale: Purely additive read capability with no dependents; safest to build last, benefits from stable status/lifecycle vocabulary from Phases 1-4.
Delivers: /pareceres/pesquisa page, usePesquisarPareceres hook with its own separate query-key namespace
Addresses: Pesquisa/filtros (table stakes) - avoids repeating the "backend built, unused" mistake flagged from v2.5

### Phase Ordering Rationale

- Strict dependency order: cannot version/approve/deliver a solicitacao that does not exist in the UI yet, and mutations cannot be usefully tested without a working list/detail to observe results
- Phases 3 and 4 are deliberately kept separate despite both being action phases, because they represent genuinely distinct RBAC boundaries (manage vs edit+ownership) whose conflation was flagged as a specific risk
- Phase 1 is where the single highest-probability defect (field-naming drift) must be caught, since it is cheapest to fix before any downstream code depends on the wrong shape
- Phase 5 is ordered last purely because it has no dependents, not because it is low value - it closes a known unused-backend-feature gap and should not be skipped

### Research Flags

Phases likely needing deeper research during planning:
- Phase 4 (Aprovacao e Entrega): RBAC/state-machine nuance is high enough (scope-tier asymmetry, instance-level checks, inconsistent backend guards) that a short explicit checklist step cross-referencing each frontend gate against ParecerController's PreAuthorize lines should be built into the phase plan, not left implicit
- Phase 3 (Versionamento): Needs a deliberate go/no-go decision on whether to port the XHR upload-progress variant vs. accept a simpler no-progress mutation - depends on expected attachment size, not purely a coding pattern choice

Phases with standard patterns (skip research-phase):
- Phase 1: Direct, well-documented port of existing Processos/Documentos list-detail-timeline patterns; only non-standard step is the live-JSON-trace tripwire, which is a checklist item, not a research question
- Phase 2: Standard react-hook-form + zod + TanStack Query mutation pattern, identical to every other module in the app
- Phase 5: Standard read-only query hook + filter UI, same shape as Processos/Agenda search/filter bars

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Direct repository inspection of existing hooks, components, and package.json - no external/training-data dependency |
| Features | MEDIUM-HIGH | Backend contract and codebase precedent are HIGH (read directly); general UI convention validation used external vendor sources (Cflow, MyCase, Spellbook) at MEDIUM confidence since no single authoritative source covers this exact workflow shape |
| Architecture | HIGH | Read directly from ParecerController, entity classes, and existing use-processos.ts/permissions.ts/dashboard-shell.tsx - no inference |
| Pitfalls | HIGH | Derived from direct reads of backend source and prior milestone audit documents (v2.4, v2.5), not general domain knowledge |

Overall confidence: HIGH

### Gaps to Address

- Content format for conteudo (plain text vs. Markdown vs. HTML) is undecided - FEATURES.md recommends Markdown if a rich editor is added later, but this should be confirmed/locked before Phase 3 if a rich text editor is in scope for v2.6 (currently deferred to v2.7, so likely not blocking)
- Backend gap: updateSolicitacao (PUT /{id}) has no CONCLUIDO guard server-side - flagged as a note for backend review, but the frontend must independently enforce read-only behavior regardless of whether the backend gap is ever fixed
- No pagination on listSolicitacoes/pesquisar - not a v2.6 blocker at current tenant scale, but should be flagged as a backend follow-up if solicitacao volume grows; frontend should avoid over-engineering client-side virtualization prematurely

## Sources

### Primary (HIGH confidence - direct repository/backend inspection)
- backend/src/main/java/com/lexcv/controllers/ParecerController.java - full 12-endpoint contract, PreAuthorize scopes, state-machine guards, instance-level authorization checks
- backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java, ParecerVersao.java - confirms no JsonProperty overrides, pure camelCase JSON
- web/src/hooks/use-documentos.ts, web/src/hooks/use-processos.ts - upload/download, timeline, invalidation patterns
- web/src/app/(dashboard)/processos/[id]/page.tsx, web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx - tab-state and print-CSS patterns
- web/src/lib/permissions.ts, web/src/components/shared/dashboard-shell.tsx - RBAC scope registration, nav pattern
- .planning/v2.5-MILESTONE-AUDIT.md, .planning/milestones/v2.4-MILESTONE-AUDIT.md, .planning/PROJECT.md - backend API surface, named gaps (PARC-09, PARV-03), documented prior defect class

### Secondary (MEDIUM confidence)
- Cflow legal opinion approvals template, Spellbook legal document version control guide, MyCase legal document management overview, SuiteFiles document approval workflow comparison - used to validate general table-stakes/anti-feature conventions for request-approval-delivery workflows in legal practice tooling

---
Research completed: 2026-07-01
Ready for roadmap: yes
