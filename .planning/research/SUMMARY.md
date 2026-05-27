# Security Research Synthesis — LexCV v1.3

> Synthesis of stack, features, architecture, and pitfalls research

## Executive Summary

The LexCV application has a foundational security architecture with BCrypt hashing and JWT authentication, but suffers from **critical multi-tenant isolation gaps** and **hardcoded secrets**. The shared database model relies entirely on application-level filtering, which is inconsistently applied across sub-resources and financial endpoints. The application also fails to comply with key Cape Verde data protection laws (Law No. 133/V/2001) due to a complete lack of audit logging.

## Core Vulnerabilities

1. **🔴 Multi-Tenant Data Leakage**: Sub-resources (Partes, Fases, Movimentações, Honorários, Pagamentos) are queried and created using parent IDs without verifying tenant ownership. An attacker can access/modify another tenant's data by guessing UUIDs.
2. **🔴 Admin Cross-Tenant Leak**: The `AdminController` returns all users across all tenants, and allows cross-tenant user modification/deletion.
3. **🔴 Hardcoded Secrets**: JWT secret and Database password (`password: password`) are committed in `application.yml`.
4. **🔴 JWT Stored in localStorage**: Tokens are vulnerable to XSS extraction.
5. **🟠 Lack of RBAC Enforcement**: Roles are defined but only enforced on the Admin controller. Any authenticated user can access any resource CRUD endpoint.
6. **🟠 Path Traversal Risk**: File uploads use predictable paths without validation against traversal.
7. **🟠 No Rate Limiting or Lockout**: Authentication endpoints are vulnerable to brute-force attacks.
8. **🟠 Missing Audit Trail**: No logging of security events or data access, failing legal compliance requirements.

## Security Tooling Strategy

- **SAST (Backend)**: SpotBugs + FindSecBugs (Maven plugin) + Semgrep
- **SAST (Frontend)**: ESLint (`eslint-plugin-secure-coding`) + Semgrep
- **SCA (Dependencies)**: OWASP Dependency-Check (Maven) + `npm audit`
- **Secrets Detection**: Gitleaks (pre-commit) + TruffleHog (deep scan)

## Remediation Roadmap

1. **Immediate Actions**: Externalize secrets to environment variables, disable `include-message: always` and `ddl-auto: update` in production.
2. **Data Isolation Fixes**: Add `tenant_id` verification to all sub-resource queries and mutations. Filter admin operations.
3. **Auth Hardening**: Move JWTs to HttpOnly cookies, implement rate limiting, and add password complexity rules.
4. **Authorization & Compliance**: Enforce RBAC on resource controllers, implement comprehensive audit logging for legal compliance.
