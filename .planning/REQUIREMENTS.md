# Requirements: LexCV

**Defined:** 2026-05-27
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Infrastructure & Config (INFRA)

- [ ] **INFRA-01**: Externalize JWT and DB secrets to environment variables
- [ ] **INFRA-02**: Fix risky Spring config (disable `include-message: always` and `ddl-auto: update` for prod)
- [ ] **INFRA-03**: Configure SAST and SCA scanning tools in CI/build pipeline

### Auth & Session Hardening (AUTH)

- [ ] **AUTH-01**: Migrate JWT storage from `localStorage` to `HttpOnly` cookies
- [ ] **AUTH-02**: Implement rate limiting on login/refresh endpoints
- [ ] **AUTH-03**: Implement refresh token rotation
- [ ] **AUTH-04**: Enforce password complexity rules on creation/reset

### Multi-Tenant Data Isolation (TENANT)

- [ ] **TENANT-01**: Add tenant verification to all `ResourceController` sub-resource endpoints (partes, fases, movimentações, honorários, pagamentos)
- [ ] **TENANT-02**: Scope `AdminController` operations to the admin's specific tenant
- [ ] **TENANT-03**: Implement Row-Level Security (RLS) policies in PostgreSQL

### Application Security & RBAC (APPSEC)

- [ ] **APPSEC-01**: Enforce RBAC (`@PreAuthorize`) on all `ResourceController` endpoints
- [ ] **APPSEC-02**: Implement input validation (`@Valid`) on all request bodies
- [ ] **APPSEC-03**: Implement path traversal protection and basic file type validation for uploads
- [ ] **APPSEC-04**: Configure HTTP security headers (CSP, HSTS, etc.)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Legal Compliance (COMPL)

- **COMPL-01**: Implement comprehensive audit logging (who, what, when, tenant)
- **COMPL-02**: Implement data export functionality for CNPD compliance
- **COMPL-03**: Implement soft-delete for legal documents

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Integração real com Keycloak | Adiar até existir backend de autenticação institucional |
| DPIA automation | Requires legal expert input, beyond scope of technical audit |
| Encryption at Rest | Requires infrastructure level changes, defer to DevOps setup |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 12 | Pending |
| INFRA-02 | Phase 12 | Pending |
| INFRA-03 | Phase 12 | Pending |
| AUTH-01 | Phase 14 | Pending |
| AUTH-02 | Phase 14 | Pending |
| AUTH-03 | Phase 14 | Pending |
| AUTH-04 | Phase 14 | Pending |
| TENANT-01 | Phase 13 | Pending |
| TENANT-02 | Phase 13 | Pending |
| TENANT-03 | Phase 13 | Pending |
| APPSEC-01 | Phase 15 | Pending |
| APPSEC-02 | Phase 15 | Pending |
| APPSEC-03 | Phase 15 | Pending |
| APPSEC-04 | Phase 15 | Pending |

**Coverage:**
- v1 requirements: 14 total
- Mapped to phases: 14
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-27*
*Last updated: 2026-05-27 after milestone v1.3 requirement definition*
