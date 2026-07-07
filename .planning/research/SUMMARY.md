# Project Research Summary

**Project:** LexCV — v2.9 Melhoria Módulo Processos
**Domain:** Legal practice management (litigation/"processos" module deepening) — multi-tenant Spring Boot + Next.js SaaS for Cape Verde law firms
**Researched:** 2026-07-07
**Confidence:** HIGH

## Executive Summary

v2.9 is a pure "apply existing pattern to a new module" milestone, not a new-capability milestone. All seven target features — a free-text `juizo` field, a required `origem` enum, three new child entities (Decisão, Facto, Testemunha), a dedicated Documentos tab, auto-created Honorário on formalization, and a printable Termo de Honorários — map directly onto patterns already shipped and validated in v2.4 (Ficha Cliente printable view) and v2.8 (Documentos upload, lazy-mount tabs, `ClienteContacto`/`ClienteNota`-style child entities). No new dependency, library, or architectural pattern is required on either the backend (Spring Boot 3.4.1/Java 23) or frontend (Next.js 16/React 19). All new work lands inside the existing `ResourceController` (child-entity CRUD, tenant-check-via-parent pattern) and the existing button-toggle tab UI on the Processo detail page — introducing a new controller, a `Tabs` primitive, a PDF library, or a JSON-blob storage pattern would all be regressions against decisions this project has already made and logged in PROJECT.md.

The real risk in this milestone is not technical novelty but **integration discipline**: this codebase has a documented, recurring bug class (three prior incidents) where a new field is added to the entity and UI but silently dropped in the hand-maintained camelCase/snake_case mapping layer (`normalizeProcesso`/`toProcessoApiPayload` in `use-processos.ts`) or in the hand-built `listProcessos` enriched map. A second cluster of risk is **money and authorization correctness** around the Honorário auto-creation: the auto-created record must be idempotent (existence-check, not just the state guard) and must never auto-populate a real currency `valorTotal` without explicit `financeiro:edit`-gated confirmation — the task brief itself flags this as the single highest-severity risk in the feature set. A third cluster is **IDOR-adjacent authorization**: the three new child entities must copy the harder `ProcessoFase` PUT pattern (parent tenant check + child-row `processoId` re-check) rather than the simpler `Parte`/`Movimentacao` pattern, since PUT/DELETE will be needed for all three and their IDs are likely sequential integers.

Recommended approach: build in four dependency-ordered phases (entity/enum foundations → backend endpoints → frontend types/hooks → frontend UI), matching the same ordering discipline used in v2.8 Phase 74→75. Two open domain-modeling questions (enum values for `Decisao.tipo` and `Testemunha.tipo`) need a decision before backend entity work can be finalized, and one design decision (Documento↔Decisão FK direction) should be made explicitly rather than improvised during implementation.

## Key Findings

### Recommended Stack

No new dependency is required for this milestone — confirmed independently by STACK, ARCHITECTURE, and FEATURES research. The full existing stack (Spring Boot 3.4.1/Java 23, PostgreSQL with `ddl-auto=update`, Next.js 16.2.6, React 19.2.4, TanStack Query ^5.87.4, react-hook-form ^7.62.0, Zod ^4.1.5) is sufficient. Three tempting-but-rejected additions were explicitly evaluated and ruled out: a PDF-generation library (jsPDF/iText/wkhtmltopdf — the existing CSS `@media print` + `window.print()` pattern from Ficha Cliente is materially simpler and already audited), a `docx` template-generation library (same reasoning), and the shadcn/Radix `Tabs` primitive (already explicitly rejected project-wide in the v2.8 Key Decision log in favor of the button-toggle `TabKey` pattern already used throughout Processos and Clientes).

**Core technologies (unchanged):**
- Spring Boot 3.4.1 (Java 23) + PostgreSQL — backend REST API, `ResourceController` extended in place
- Next.js 16 (App Router) / React 19 — frontend, new routes/components follow existing file-per-page conventions
- TanStack Query — new hooks follow the exact `useProcessoPartes`/`useAddProcessoParte` naming/shape convention already established
- react-hook-form + Zod — new form schemas (`decisaoFormSchema`, `factoFormSchema`, `testemunhaFormSchema`) follow existing `processoFormSchema` conventions

### Expected Features

**Must have (table stakes, all P1):**
- Campo Juízo (texto livre) — matches PT/CV court-hierarchy convention (Comarca → Tribunal → Juízo); flat text field, not a normalized hierarchy table
- Campo Origem (Petição Inicial | Notificações Avulsas, obrigatório) — a procedural distinction, not a marketing/referral-source field; must not be conflated with lead-attribution tracking
- Sub-secção Decisões (data, tipo enum, resumo, anexo opcional) — `tipo` should use the closed PT/BR taxonomy (Despacho, Decisão Interlocutória, Sentença, Acórdão), not free text
- Sub-secção Factos (descrição, data, ordem) — flat, ordered, unlinked list; genuine differentiator vs. Clio/MyCase/PracticePanther (none model this)
- Sub-secção Testemunhas (nome, contacto, tipo/arrolada por, notas) — must be a separate entity from `Parte`, not a role flag on it (procedurally a witness is not a party under PT/BR civil law)
- Aba Documentos dedicada no processo — pure pattern reuse; backend endpoint (`GET /processos/{id}/documentos`) already exists
- Criação automática de Honorário ao TRIAGEM→ATIVO — additive hook in the existing `formalizarProcesso()` state transition
- Termo de Honorários imprimível — reuses the Ficha Cliente CSS-print pattern verbatim

**Should have (fast-follow within v2.9 or immediate next iteration):**
- Decisões surfaced in the existing Timeline aggregator (alongside movimentações/transições/eventos/documentos)
- Fase catalog conditionally informed by Origem (Petição Inicial vs. Notificação Avulsa may imply different early-phase relevance)

**Defer (do not build this milestone):**
- Normalized Tribunal→Juízo hierarchy catalog (disproportionate for Cape Verde's small, stable court system)
- Facts↔Documentos↔Testemunhas cross-linking / "favorability" tagging (Casefleet/CaseMap+-tier litigation-support scope, wildly disproportionate to firm scale)
- Deposition/testimony status tracking on Testemunha (a common-law procedural concept that doesn't map to PT/CV civil procedure; witness appearance already covered by Evento/Agenda)
- Full unattended engagement-letter automation with e-signature/auto-send/auto-activate (not even Clio/MyCase/PracticePanther do this natively)
- Marketing/referral-source tracking bundled into "Origem" (different concept entirely — keep Origem strictly procedural)

### Architecture Approach

All new backend work (3 entities, their repositories, and ~12 new endpoints) lands inside the existing `ResourceController.java` — the `ParecerPesquisaController` split is *not* precedent for a new controller here; it fixed a genuine Spring routing collision that does not apply to `ResourceController`'s bare `/api/v1` mapping. The three new entities (Decisão, Facto, Testemunha) should mirror `Parte.java`'s lean shape exactly: `Integer` IDENTITY id, `processo_id` FK, **no** `tenant_id` column — tenant isolation is enforced transitively by loading and checking the parent `Processo` first, the same pattern already used by `Parte`/`ProcessoFase`/`Movimentacao`. All three need full CRUD (list+create+update+delete), a deliberate addition beyond the append-only `Parte`/`Movimentacao` precedent, because none of the three are pure event logs — corrections, reordering, and removal are all realistic.

**Major components (all additions, no restructuring):**
1. `Processo.java` + new `OrigemProcesso` enum — two additive columns (`juizo` String, `origem` enum) on the existing entity, threaded through `createProcesso`/`updateProcesso`/`createProcessoIntake` **and** the hand-built `listProcessos` enriched map (the single highest-risk integration gap flagged by architecture research)
2. `Decisao`/`Facto`/`Testemunha` entities + repositories + `ResourceController` endpoints — child-entity CRUD scoped through parent tenant check, following the harder `ProcessoFase` PUT pattern (parent tenant check + child `processoId` re-check), not the simpler `Parte`/`Movimentacao` single-check pattern
3. Honorário auto-creation inside `formalizarProcesso()` — direct in-process `honorarioRepository.save()` call at the existing TRIAGEM→ATIVO hook point (~line 1233), wrapped in `@Transactional` (a new requirement this feature introduces, since the method today does only one write)
4. Processo detail page (`[id]/page.tsx`) — `TabKey` union grows by four (`decisoes`, `factos`, `testemunhas`, `documentos`), new tab bodies follow whichever inline-vs-subcomponent pattern the file uses at implementation time
5. New printable route `[id]/termo-honorarios/page.tsx` — structural clone of `clientes/[id]/ficha/page.tsx`'s `PRINT_CSS`/`window.print()`/`BLANK` placeholder pattern, sourcing data from three already-existing hooks (`useProcesso`, `useCliente`, `useHonorarios`)

### Critical Pitfalls

1. **"origem"/"juizo" silently dropped in the hand-maintained mapping layer** — `normalizeProcesso()`/`toProcessoApiPayload()` in `use-processos.ts` have no type-level enforcement that every backend field is mapped; this exact bug class has already recurred three times in this project. Prevention: treat wiring both mapping functions plus `types/processos.ts` as an explicit, separately-reviewed task, and do a hard-refresh round-trip test rather than trusting `tsc`/build success.
2. **"origem obrigatório" enforced in only one of two independent, already-disagreeing validation layers** — the frontend step-1 Zod schema and the backend's `CAMPOS_MINIMOS_POR_TIPO` (checked only inside `formalizarProcesso`, not at intake) do not share a definition today; `createProcessoIntake()` currently validates nothing at all. Prevention: decide explicitly where origem is enforced and implement all three layers (intake backend validation — genuinely new code; `CAMPOS_MINIMOS_POR_TIPO` for every `tipo_processo` key including `"default"`; frontend `z.enum()` at step 1) as one reviewed unit.
3. **Cross-processo IDOR on Decisão/Facto/Testemunha PUT/DELETE** — copying the simpler `Parte`/`Movimentacao` tenant-only check instead of the `ProcessoFase` pattern (parent tenant check + child-row `processoId` re-check) leaves a same-tenant authorization gap, made worse if the new entities use guessable sequential `Integer` IDs like `ProcessoFase`/`Honorario` do. Prevention: mandatory double-check on every PUT/DELETE, written into each entity's phase acceptance criteria explicitly (3x, not once).
4. **Auto-created Honorário is not idempotent** — the `estado != TRIAGEM → 409` guard alone does not prevent duplicate Honorário rows under retry/replay scenarios; no DB constraint prevents two Honorário rows per processo today. Prevention: explicit `honorarioRepository.findByProcessoId(id)` existence-check immediately before creation inside `formalizarProcesso`, independent of the state guard, plus a two-call regression test.
5. **Auto-created Honorário must never auto-populate a real `valorTotal`** — `Cliente.honorariosPropostos` is a per-cliente (not per-processo) soft estimate captured at intake; blindly copying it into a hard financial record with no confirmation step is the single highest-severity pitfall in this feature set (explicitly flagged in the task brief itself). Prevention: backend-created stub must have `valorTotal = null`; any pre-fill happens only in a UI the user must explicitly submit under `financeiro:edit` (a different RBAC scope than `processos:manage`, which gates `formalizar` itself).

## Implications for Roadmap

Based on combined research, the four architecture-recommended phases below map cleanly onto a roadmap structure. Ordering follows a strict dependency chain: entity/enum foundations must be stable before endpoints reference their shape, and endpoints must be stable before frontend types/hooks are written — mirroring the v2.8 Phase 74→75 lesson that changing backend enum values after frontend Zod schemas exist breaks every dependent form.

### Phase 1: Foundations — Processo fields + new entities (no UI-visible change)
**Rationale:** Every other phase depends on these shapes being final; changing them later cascades into endpoints, types, and forms already built.
**Delivers:** `Processo.juizo`/`Processo.origem` columns + `OrigemProcesso` enum; `Decisao`/`Facto`/`Testemunha` entities + `TipoDecisao` enum + repositories, all mirroring `Parte.java`'s lean shape (no `tenant_id`).
**Addresses:** Campo Juízo, Campo Origem, Sub-secção Decisões/Factos/Testemunhas (data layer only) from FEATURES.md.
**Avoids:** Pitfall 8 (enum label/value mismatch — store stable ASCII codes, map to accented Portuguese labels only in frontend); Pitfall 9 (Facto `ordem` scoped per `processo_id`, not globally).
**Blocking decision before this phase can be finalized:** `TipoDecisao` enum values and `Testemunha.tipo` ("arrolada por") values need explicit domain-expert/legal input — not resolvable from the codebase alone.

### Phase 2: Backend endpoints — CRUD + validation + Honorário hook
**Rationale:** Depends entirely on Phase 1's entity shapes; must land before frontend hooks are written against a stable contract.
**Delivers:** `/processos/{id}/decisoes|factos|testemunhas` full CRUD (12 endpoints) inside `ResourceController`, reusing existing `processos:view`/`processos:edit` scopes; `juizo`/`origem` wired into `createProcesso`/`updateProcesso`/`createProcessoIntake` **and** the `listProcessos` enriched map; Honorário auto-creation hooked into `formalizarProcesso()` with `@Transactional` added.
**Uses:** Existing `processos:*` RBAC scopes (no new scopes/`DatabaseSeeder` changes); existing `t_documento` table via nullable FK for Decisão's optional anexo.
**Implements:** Child-entity CRUD scoped through parent tenant check (Pattern 1); server-side auto-creation inside an existing state-transition endpoint (Pattern 3).
**Avoids:** Pitfall 1 (list-endpoint enrichment gap — explicit anti-pattern #4 in ARCHITECTURE.md); Pitfall 2 (dual validation layers — implement intake validation, `CAMPOS_MINIMOS_POR_TIPO` for every tipo, and note this is genuinely new backend logic, not a copy-paste); Pitfall 3 (IDOR — copy `ProcessoFase`'s double-check pattern, not `Parte`'s single-check, for every PUT/DELETE); Pitfall 4 (idempotency — existence-check before Honorário creation); Pitfall 5 (money safety — `valorTotal` stub starts `null`, never auto-populated from `honorariosPropostos`); Pitfall 7 (decide Documento↔Decisão FK direction explicitly, re-apply Phase 79's ownership-validation pattern).

### Phase 3: Frontend types/schemas/hooks
**Rationale:** Depends on Phase 2's endpoint contracts being stable; must land before UI work consumes these hooks.
**Delivers:** `types/processos.ts` extended with `Processo.juizo`/`origem` + `Decisao`/`Facto`/`Testemunha` types; `schemas/processos.ts` extended with `decisaoFormSchema`/`factoFormSchema`/`testemunhaFormSchema` and `origem` promoted from `optionalTrimmedString` to `z.enum(...)`; `use-processos.ts` extended with the list/create/update/delete hook quad per new entity.
**Uses:** TanStack Query, existing `queryKey` convention (`["processos", "<subresource>", id]`).
**Avoids:** Pitfall 1 — this phase is precisely where `normalizeProcesso()`/`toProcessoApiPayload()` must be updated for `juizo`/`origem` in the same reviewed change as the type definitions, with a hard-refresh round-trip test as acceptance criteria, not an assumed side effect.

### Phase 4: Frontend UI — tabs, intake field, print page
**Rationale:** Depends entirely on Phases 2-3 being stable; pure UI wiring against a proven contract.
**Delivers:** Required "Origem" field in intake step 1; Juízo/Origem display rows on the "Dados" card (Origem not editable post-intake); four new tabs (Decisões, Factos, Testemunhas, Documentos) added to the existing `TabKey` button-toggle group; new `[id]/termo-honorarios/page.tsx` printable route.
**Implements:** Pattern 4 (CSS-print via `window.print()`, no PDF library) — clone of `clientes/[id]/ficha/page.tsx`.
**Avoids:** Pitfall 6 (gate the print page's render on all 3 hooks — Cliente, Processo, Honorario — not just the primary one; block/flag printing when `valorTotal` is null rather than silently rendering `BLANK`); Pitfall 7 frontend half (re-apply Phase 79's ownership-validation pattern to any new upload path in the Documentos tab); the tab-switch dialog-reset gotcha documented for Clientes v2.8 (reset any open "add" dialog's draft state on tab change, since sub-components unmount rather than hide).

### Phase Ordering Rationale

- Strict dependency chain (entities → endpoints → frontend types/hooks → frontend UI) mirrors the project's own v2.8 Phase 74→75 precedent, specifically to avoid backend enum values changing after frontend Zod schemas are already written.
- Honorário auto-creation (part of Phase 2) has no dependency on the Decisão/Facto/Testemunha work and can be built/shipped in parallel if the roadmap wants to split phases further — flagged in ARCHITECTURE.md as independently parallelizable.
- The Termo de Honorários print page (part of Phase 4) depends only on `Honorario` existing (already true today via manual creation) — it does not hard-depend on the auto-creation hook being done first, though sequencing them together is a natural convenience.
- Documentos tab work is lower-effort than the analogous Clientes v2.8 tab because its backend endpoint (`GET /processos/{id}/documentos`) already exists — it is folded into Phase 4 as pure frontend wiring, not a separate phase.

### Research Flags

Needs deeper research/domain-expert input during planning:
- **Phase 1:** `TipoDecisao` enum values and `Testemunha.tipo` values are not resolvable from the codebase — needs a legal-domain decision before entity work is finalized. This is a roadmap-blocking question, not a research gap this synthesis can close.
- **Phase 2:** Documento↔Decisão FK direction (nullable `decisao_id` on `Documento` vs. `documento_id` FK on `Decisao`) is a real design choice with security implications (ownership-check pattern differs slightly) — should be an explicit decision recorded in the phase plan, not improvised mid-implementation. Also confirm whether `formalizarProcesso()` currently carries an explicit `@PreAuthorize` (not conclusively visible in the researched excerpt).

Phases with standard, well-documented patterns (skip `--research-phase`):
- **Phase 1 (entity/enum mechanics):** Direct mirror of `Parte.java`/`ClienteContacto.java` — pattern fully confirmed against source.
- **Phase 3 (frontend types/hooks):** Direct mirror of existing `useProcessoPartes` family — pattern fully confirmed against source.
- **Phase 4 (print page, tab UI):** Direct mirror of `clientes/[id]/ficha/page.tsx` and the Clientes v2.8 button-toggle tab pattern — both fully confirmed against source, including the specific gotchas (dialog-reset-on-tab-switch, 3-hook loading gate) already solved once in this codebase.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified directly against `package.json`/`pom.xml`-equivalent inspection and PROJECT.md's Key Decisions log; "no new dependency" conclusion is independently corroborated by all four research files |
| Features | MEDIUM-HIGH | Grounded in official PT/BR judicial-system sources (tribunais.org.pt, TJDFT, TJPR — HIGH confidence primary sources) plus named competitor product docs (Clio/MyCase/PracticePanther/Casefleet — MEDIUM confidence, several WebSearch-extracted due to 403s on direct fetch) |
| Architecture | HIGH | All findings verified directly against current repository source (line numbers cited), not training-data assumptions — includes exact hook points, existing entity shapes, and precedent clarification (`ParecerPesquisaController` is not a splitting precedent) |
| Pitfalls | HIGH | Derived directly from reading actual code (`ResourceController.java`, `Processo.java`, `Honorario.java`, `Documento.java`, `Cliente.java`, `use-processos.ts`, intake wizard, Ficha Cliente) — includes citation of three prior real incidents of the mapping-layer bug class in this specific project |

**Overall confidence:** HIGH

### Gaps to Address

- **`TipoDecisao` enum values** (Despacho/Decisão Interlocutória/Sentença/Acórdão is the FEATURES.md-recommended PT/BR taxonomy, corroborated by official TJDFT/TJPR sources, but not yet confirmed as the project's final choice) — resolve explicitly at Phase 1 planning, not left implicit.
- **`Testemunha.tipo` ("arrolada por") values** (likely `AUTOR`/`REU` or similar) — same treatment, resolve at Phase 1 planning.
- **Documento↔Decisão FK direction** — a real architectural choice with a security-pattern implication (see Phase 2 above); should be a recorded decision, not discovered mid-build.
- **Whether `formalizarProcesso()` has an explicit `@PreAuthorize` today** — verify at Phase 2 implementation time; affects who can trigger the Honorário side-effect and whether the existing RBAC gate is sufficient or needs strengthening.
- **Whether Decisões should feed the existing Timeline aggregator in this milestone or a fast-follow** — FEATURES.md scopes it as a P2/fast-follow; ARCHITECTURE.md separately flags a naming collision (`TimelineItemType` already has a string literal `"decisao"` used for `ConflictCheckDecisao`, a different concept from the new Decisão entity) that must be resolved before/if this integration happens, to avoid an accidental merge of unrelated concepts.
- **Processo `[id]/editar/page.tsx` remaining a separate route** (unlike Cliente, which was unified into a single view/edit component in v2.8 Phase 75) is out of this milestone's stated scope but is a known architectural divergence worth flagging to the roadmapper in case a future milestone wants parity — not a blocker for v2.9.

## Sources

### Primary (HIGH confidence — direct repository inspection)
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — formalizar/intake/conflict-check logic, Parte/Fase/Movimentacao/Honorario CRUD and tenant-check patterns, exact Honorário-hook line location
- `backend/src/main/java/com/lexcv/models/Processo.java`, `Parte.java`, `Movimentacao.java`, `Honorario.java`, `Documento.java`, `Cliente.java`, `ClienteContacto.java`, `ParecerVersao.java` — entity shapes, existing conventions
- `web/src/hooks/use-processos.ts`, `use-financeiro.ts`, `use-documentos.ts` — mapping-layer root cause of Pitfall 1, existing hook conventions
- `web/src/types/processos.ts`, `web/src/schemas/processos.ts` — current field requiredness, Timeline type naming collision
- `web/src/app/(dashboard)/processos/[id]/page.tsx`, `novo/page.tsx`, `[id]/editar/page.tsx` — existing tab pattern, intake wizard
- `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `[id]/ficha/page.tsx` — lazy-mount tab pattern, CSS-print precedent
- `.planning/PROJECT.md` — Key Decisions log (JSON-column reversal v2.7, Tabs-vs-button-toggle v2.8, `jakarta.persistence.validation.mode` gotcha, Phase 79 ownership-validation fix, no-new-dependency precedent v2.6)
- [tribunais.org.pt: Os Tribunais / Judicial](https://tribunais.org.pt/Os-Tribunais/Judicial) — defines comarca/tribunal/juízo hierarchy
- [TJDFT: Sentença, decisão interlocutória, despacho e acórdão](https://www.tjdft.jus.br/institucional/imprensa/campanhas-e-produtos/direito-facil/edicao-semanal/sentenca-decisao-interlocutoria-despacho-e-acordao) — official 4-type decision taxonomy
- [TJPR: Saiba a diferença entre sentença, decisão e despacho](https://www.tjpr.jus.br/noticias/-/asset_publisher/9jZB/content/saiba-a-diferenca-entre-sentenca-decisao-e-despacho/18319) — corroborating official source
- [Everchron: Witnesses](https://everchron.com/witnesses) — direct WebFetch, litigation-support-tier witness modeling comparison

### Secondary (MEDIUM confidence)
- [Clio: Create Matters / Custom Fields Help Center articles](https://help.clio.com/hc/en-us/articles/9285959663131-Create-Matters) — WebSearch-extracted, flat-field court/case modeling comparison
- [Casefleet / CaseMap+ / SmartAdvocate product pages](https://www.casefleet.com/use-cases/case-management-software) — litigation-support-tier feature comparison, anti-feature justification
- [Practiq.dev: Clio vs MyCase vs PracticePanther comparison 2026](https://practiq.dev/blog/clio-vs-mycase-vs-practicepanther-solo-small-firms) — engagement-letter automation gap analysis across market leaders
- [Portal TJPE: Órgãos Julgadores com PJe](https://portal.tjpe.jus.br/web/processo-judicial-eletronico/orgaos-julgadores-com-pje/unidades-com-pje) — Brazilian PJe terminology corroboration

### Tertiary (LOW confidence)
- None flagged — all research files report HIGH or MEDIUM confidence sourcing; no LOW-confidence claims were carried into roadmap-affecting conclusions.

---
*Research completed: 2026-07-07*
*Ready for roadmap: yes*
