# Phase 15: AppSec & RBAC - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Fortify application-level security, enforce RBAC per method, and set secure HTTP headers.
</domain>

<decisions>
## Implementation Decisions

### RBAC Enforcement
- Replace generic `@PreAuthorize("hasRole('ADMIN')")` or lack of authorization with specific permissions like `@PreAuthorize("hasAuthority('clientes:view')")` on controller methods in `ResourceController`.

### Security Headers
- Configure Spring Security to emit strict headers: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`.
- Configure Next.js headers in `next.config.js` for the frontend.

### Input Validation
- Basic input validation is handled by Next.js `zod` schemas and Spring Data JDBC parameterized queries (protecting against SQLi).
- For XSS, Next.js auto-escapes React rendering.

</decisions>
