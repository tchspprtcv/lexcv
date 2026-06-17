# Phase 40: Mapeamento e Alinhamento Casing (Agenda) - Context

**Gathered:** 2026-06-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Refatorar o data layer da Agenda (tipos, schemas, hooks de query e páginas) para usar camelCase, alinhando com a serialização padrão Jackson do Spring Boot.

</domain>

<decisions>
## Implementation Decisions

### Casing Alignment
- Rename all occurrences of `data_inicio` to `dataInicio` in frontend models, validation schemas, React Query parameters, forms, and pages.
- Rename all occurrences of `data_fim` to `dataFim` in frontend models, validation schemas, React Query parameters, forms, and pages.
- Rename all occurrences of `processo_id` to `processoId` in frontend models, validation schemas, React Query parameters, forms, and pages.
- Rename all occurrences of `tenant_id` to `tenantId` in frontend models, validation schemas, React Query parameters, forms, and pages.

### Date Formatting
- When serializing inputs from `datetime-local` HTML fields, strip the timezone offset suffix (e.g., `Z` or `+01:00`) and format as a local ISO-8601 string (`YYYY-MM-DDTHH:mm:ss`) to prevent Spring Boot parsing issues.

### the agent's Discretion
- Adjust the layout elements (e.g., grid spacing, borders) to keep alignment with the "Anti-Safe Harbor" design guidelines.
- Handle fallback mapping for old mock values if any remain.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/hooks/use-permissions.ts` — Permissões de agenda (`agenda:view`, `agenda:edit`).
- `web/src/lib/api.ts` — `apiFetch` utility for cookie-based auth requests.

### Established Patterns
- CamelCase naming convention already established in Phase 32/33 for entities like `ConflictCheckDecisao` and `Prazo`.

### Integration Points
- `/agenda` (list/calendar page), `/agenda/novo` (creation form), `/agenda/[id]` (detail page), and `/agenda/[id]/editar` (edit form).

</code_context>

<specifics>
## Specific Ideas

- Ensure all TanStack query invalidations use `["eventos", "list"]` query keys consistently.

</specifics>

<deferred>
## Deferred Ideas

- Unified deadlines view in calendar (deferred to Phase 42).
- Date range ordering validations (deferred to Phase 41).

</deferred>
