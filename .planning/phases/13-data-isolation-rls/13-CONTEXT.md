# Phase 13: Data Isolation & RLS - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Prevent cross-tenant data leakage and implement Row-Level Security
</domain>

<decisions>
## Implementation Decisions

### Data Isolation
- Enforce `tenantId` checking on all data mutations and queries for `Parte`, `Fase`, `Movimentacao`, `Honorario`, and `Pagamento`.
- Ensure `AdminController` endpoints filter actions by the authenticated admin's `tenantId` (except for root superadmin if it exists, but for now scope to tenant).

### Row-Level Security (RLS)
- Implement Postgres RLS policies using Spring Boot's connection session settings if possible, or fallback to application-level `@PostFilter` / Query modifications.
- Given the current stack, the primary mitigation will be JPA/Hibernate level `@Filter` or manual repository query adjustments to guarantee `tenantId` is always appended.

</decisions>

<code_context>
## Existing Code Insights

### Established Patterns
- JWT tokens currently contain `tenant_id`.
- Security context holds the authenticated user's details.

### Integration Points
- Spring Data JPA repositories.
- Controllers under `api/v1`.
</code_context>

<specifics>
## Specific Ideas
No specific UI requirements — this is a backend security hardening phase.
</specifics>

<deferred>
## Deferred Ideas
None
</deferred>
