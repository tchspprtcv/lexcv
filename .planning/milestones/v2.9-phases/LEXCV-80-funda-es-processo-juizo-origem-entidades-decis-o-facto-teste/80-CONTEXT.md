# Phase 80: Fundações — Processo.juizo/origem + Entidades Decisão/Facto/Testemunha - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss skipped)

<domain>
## Phase Boundary

A estrutura de dados jurídicos do processo (Juízo, Origem, Decisões, Factos, Testemunhas) existe na base de dados, estável e pronta para os endpoints e a UI construírem sobre ela, sem qualquer mudança visível para o utilizador ainda.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — this is a pure infrastructure phase (all success criteria are technical: columns/entities/repositories exist, app starts and persists cleanly). Use ROADMAP.md success criteria, REQUIREMENTS.md (PROC-01, PROC-06, PROC-09, PROC-11), and existing codebase conventions to guide implementation:

- `Processo.juizo` — free text column, same convention as existing `tribunal`/`tipoProcesso`/`areaJuridica`
- `Processo.origem` — new `OrigemProcesso` enum (Petição Inicial | Notificações Avulsas), stored as string per project convention (matches `estado`/`documento_tipo` — no `@Enumerated(EnumType.STRING)` gotchas, follow the exact pattern already used for `DocumentoTipo`)
- `Decisao`, `Facto`, `Testemunha` — mirror `Parte.java`'s lean shape exactly: `Integer` IDENTITY id, `processo_id` FK, **no own `tenant_id` column** (tenant isolation is transitive via the parent Processo, enforced at the controller layer in Phase 81 — this phase is data layer only)
- `Decisao.tipo` — new `TipoDecisao` enum (Despacho | Decisão Interlocutória | Sentença | Acórdão) — confirmed taxonomy from milestone research (PT/BR official sources)
- `Testemunha.tipo` — closed enum (Autor | Réu) — confirmed by explicit user decision during milestone requirements gathering (chose closed enum over free text)
- `Facto.ordem` — integer, scoped per `processo_id` (not global)
- `Decisao.anexo` — per PROC-07, the anexo is uploaded directly in the Decisão creation form in a later phase (Phase 84); this phase only needs a nullable FK/reference column on `Decisao` pointing to the existing `Documento` entity — no new file-storage mechanism

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/src/main/java/com/lexcv/models/Parte.java` — the exact structural template for `Decisao`/`Facto`/`Testemunha` (lean entity, Integer identity id, `processo_id` FK, no tenant_id)
- `backend/src/main/java/com/lexcv/models/Processo.java` — existing free-text fields (`tribunal`, `tipoProcesso`, `areaJuridica`) as the pattern for `juizo`
- `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` — existing enum-as-string convention to mirror for `OrigemProcesso` and `TipoDecisao`

### Established Patterns
- `ddl-auto=update` in dev — new entities/columns persist automatically without a manual migration script, consistent with how `Processo`'s existing columns were added
- Repositories are minimal Spring Data JPA interfaces (`findByProcessoId`, etc.), matching `ParteRepository`/`ProcessoFaseRepository`

### Integration Points
- None yet — this phase is data-layer only. Phase 81 wires these entities into `ResourceController.java` endpoints and the `listProcessos` enriched map.

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond the enum values already confirmed during milestone requirements gathering (see REQUIREMENTS.md PROC-06, PROC-11 and .planning/research/SUMMARY.md).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope (infrastructure-only, no grey areas to resolve).

</deferred>
