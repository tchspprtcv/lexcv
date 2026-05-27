# Security Audit Features — LexCV v1.3

> Research findings for security audit of a multi-tenant legal management platform
> Stack: Next.js 16 (App Router) + Spring Boot 3.4.1 + PostgreSQL + JWT (JJWT 0.12.5)

---

## Table Stakes (must-have security checks)

Every web application — regardless of domain — must pass these checks. These map to OWASP ASVS Level 1 and the OWASP Top 10.

### Authentication & Session Management

| Check | What to Audit | Codebase Finding |
|-------|--------------|-----------------|
| **Password hashing** | BCrypt/Argon2 with proper cost factor | ✅ BCrypt via `BCryptPasswordEncoder` (SecurityConfig.java) |
| **Brute-force protection** | Rate-limiting on `/auth/login` | ❌ **MISSING** — no rate limiting on login endpoint |
| **Token expiration validation** | Backend enforces `exp` claim | ✅ JJWT validates expiration in `validateToken()` |
| **Token storage** | Tokens should use HttpOnly cookies, NOT localStorage | ❌ **CRITICAL** — tokens stored in `localStorage` (auth.ts L6, L11) |
| **Session invalidation** | Logout must server-side invalidate tokens | ❌ **MISSING** — no token blacklist/revocation mechanism |
| **Password policy** | Minimum length, complexity requirements | ❌ **MISSING** — `change-password` accepts any string, no min length |
| **Credential exposure** | No hardcoded secrets in source | ❌ **CRITICAL** — JWT secret hardcoded in `application.yml` L30; DB password `password` in L12; seed credentials shown in login page L83 |

### Input Validation & Injection

| Check | What to Audit | Codebase Finding |
|-------|--------------|-----------------|
| **Server-side input validation** | `@Valid` + Bean Validation on DTOs | ❌ **MISSING** — controllers accept raw `@RequestBody` without `@Valid` annotations |
| **SQL Injection** | Parameterized queries only | ✅ Using Spring Data JPA (parameterized by default) |
| **XSS prevention** | No `dangerouslySetInnerHTML`; CSP headers | ⚠️ React auto-escapes; no CSP headers configured |
| **Path traversal** | File operations validate paths | ❌ **RISK** — file uploads use `UPLOAD_DIR + savedName` (ResourceController L395) without path canonicalization |
| **Mass assignment** | Don't bind sensitive fields from request body | ❌ **RISK** — `updateMe()` accepts arbitrary Map fields; user could potentially inject unexpected fields |

### Error Handling & Information Leakage

| Check | What to Audit | Codebase Finding |
|-------|--------------|-----------------|
| **Error messages** | Don't leak internals to client | ⚠️ `include-message: always` in application.yml L4 — exposes Spring error messages |
| **Stack traces** | Never sent to client | ⚠️ No global exception handler — default Spring behavior may leak stacks in dev |
| **API error consistency** | Uniform error response format | ⚠️ Mix of `Map.of("message", ...)` and raw exceptions |

### HTTP Security Headers

| Header | Purpose | Status |
|--------|---------|--------|
| **Content-Security-Policy** | XSS mitigation | ❌ Not configured |
| **Strict-Transport-Security** | Force HTTPS | ❌ Not configured |
| **X-Content-Type-Options** | MIME sniffing prevention | ❌ Not configured |
| **X-Frame-Options** | Clickjacking prevention | ❌ Not configured |
| **Referrer-Policy** | Control referrer leakage | ❌ Not configured |

### CSRF Protection

| Check | Finding |
|-------|---------|
| CSRF disabled in SecurityConfig | ✅ Intentional for stateless JWT — but **only safe if tokens are NOT in cookies** |
| Current risk | ⚠️ Since tokens are in localStorage + `credentials: "include"` is set in api.ts L23, the CSRF-disabled config is currently acceptable but fragile |

---

## Differentiators (legal/multi-tenant specific)

These are the security areas that differentiate a legal management platform from generic CRUD apps. **Tenant isolation is the single most critical requirement.**

### Multi-Tenant Isolation (CRITICAL)

| Check | What to Audit | Codebase Finding |
|-------|--------------|-----------------|
| **Tenant context source** | tenant_id from JWT claims, not headers/URLs | ✅ `getTenantId()` extracts from `UserPrincipal` which comes from JWT (ResourceController L49-53) |
| **Tenant filter on ALL queries** | Every data query includes tenant_id | ⚠️ **GAPS FOUND**: Sub-resources `partes`, `fases`, `movimentacoes` queried by `processoId` without verifying the parent processo belongs to current tenant (ResourceController L211-213, L222, L289-290) |
| **Honorario tenant leak** | Honorario creation doesn't verify processo belongs to tenant | ❌ **CRITICAL** — `createHonorario()` at L481-483 saves directly without ANY tenant check |
| **Pagamento tenant leak** | Pagamento creation doesn't verify tenant ownership chain | ❌ **CRITICAL** — `createPagamento()` at L491-513 saves without verifying tenant ownership |
| **Honorario pagamentos listing** | Listing pagamentos for an honorario doesn't check tenant | ❌ **RISK** — `listHonorarioPagamentos()` at L486-488 returns data without tenant filter |
| **Admin cross-tenant leak** | Admin `listUsers()` returns ALL users, not just current tenant | ❌ **CRITICAL** — AdminController L40 uses `findAll()` instead of `findByTenantId()` |
| **Admin user operations** | Admin can modify/delete users from other tenants | ❌ **CRITICAL** — `updateUser()` L126 and `deleteUser()` L176 don't verify tenant ownership |
| **Document upload isolation** | Uploaded files stored in flat `uploads/` dir | ⚠️ No tenant-prefixed storage — files from all tenants in same directory |
| **Database-level isolation** | Row-Level Security (RLS) in PostgreSQL | ❌ Not implemented — relying entirely on application-level filtering |

### Legal Domain Security

| Check | Why It Matters | Status |
|-------|---------------|--------|
| **Audit trail** | Legal systems need immutable logs of all CRUD operations (who, what, when) | ❌ **MISSING** — no audit logging |
| **Document access logging** | Track every view/download of privileged documents | ❌ **MISSING** — downloads not logged |
| **Soft-delete** | Legal documents should never be permanently deleted | ❌ Uses hard-delete (`documentoRepository.delete()`, file `Files.deleteIfExists()`) |
| **Privilege tagging** | Attorney-client privileged documents need special classification | ❌ Not implemented |
| **Ethical walls** | Conflict-of-interest isolation within a tenant | ❌ Not implemented (acceptable for MVP) |
| **Data encryption at rest** | Sensitive client data and documents | ❌ Not implemented — plaintext storage |
| **File type validation** | Only allow safe document types (PDF, DOCX, etc.) | ❌ **MISSING** — any file type accepted for upload |
| **File size validation** | Prevent DoS via large uploads | ✅ Spring config limits to 50MB (application.yml L24-25) |

### RBAC & Authorization Depth

| Check | What to Audit | Codebase Finding |
|-------|--------------|-----------------|
| **Controller-level RBAC** | Permission checks on endpoints | ⚠️ Only AdminController has `@PreAuthorize("hasRole('ADMIN')")` — ResourceController has NO permission checks |
| **Permission granularity** | RBAC system defines fine-grained permissions | ✅ System defines `clientes:view`, `clientes:edit`, etc. (AdminController L204-216) |
| **Permission enforcement** | Defined permissions actually enforced on endpoints | ❌ **CRITICAL** — permissions defined but NOT enforced; any authenticated user can access any CRUD endpoint |
| **Role escalation prevention** | Users can't self-assign roles | ⚠️ `updateMe()` doesn't allow role changes (good), but no explicit guard |

---

## Anti-Features (security anti-patterns to flag)

These are specific anti-patterns found in the current codebase that the audit must flag.

### 🔴 CRITICAL Anti-Patterns

1. **JWT Tokens in localStorage** (`web/src/lib/auth.ts`)
   - Tokens stored via `window.localStorage.setItem()`
   - XSS attack = full session hijacking
   - **Fix**: Move to HttpOnly cookies set by the backend

2. **Hardcoded JWT Secret** (`backend/src/main/resources/application.yml:30`)
   - `secret: dGhpc2lzYW1hdm...` is a base64 string committed to source
   - **Fix**: Use environment variable `${JWT_SECRET}`

3. **Hardcoded Database Password** (`application.yml:12`)
   - `password: password` in source control
   - **Fix**: Use environment variable or secrets manager

4. **No Tenant Isolation on Sub-Resources**
   - `partes`, `fases`, `movimentacoes` are fetched by `processoId` without verifying the processo belongs to the current tenant
   - **Exploit**: Authenticated user from Tenant B can read Tenant A's process sub-data by guessing/knowing the UUID

5. **Admin Controller Returns ALL Tenants' Users**
   - `userRepository.findAll()` in `AdminController.listUsers()`
   - Admin of Tenant A can see users from Tenant B

6. **Financial Endpoints Skip Tenant Verification**
   - `createHonorario()` and `createPagamento()` save without any tenant check
   - An attacker could attach honorários to another tenant's processes

### 🟡 HIGH Anti-Patterns

7. **No Input Validation on Controllers**
   - No `@Valid` annotations; no `@Size`, `@NotBlank`, `@Email` constraints on DTOs
   - Controllers accept Map<String, Object> (AdminController L67, L125, L228) — no schema enforcement

8. **No Security Headers**
   - No CSP, HSTS, X-Content-Type-Options, X-Frame-Options
   - Missing both in Spring Boot and Next.js configuration

9. **Mock JWT Code Still Present** (`web/src/server/mock-jwt.ts`)
   - Creates unsigned JWTs (`alg: "none"`)
   - Though backend is now real, mock code remains and could confuse or be reactivated

10. **Seed Credentials Displayed in UI** (`login/page.tsx:83`)
    - `admin@lexcv.cv / admin123` shown on login page

11. **24-Hour Access Token Lifetime** (`application.yml:31`)
    - `expiration: 86400000` (24 hours) — should be 5-15 minutes
    - Long-lived tokens maximize the damage window if stolen

12. **No Refresh Token Rotation**
    - `AuthController.refresh()` issues new tokens but doesn't invalidate the old refresh token
    - Stolen refresh tokens remain valid forever

### 🟠 MEDIUM Anti-Patterns

13. **CORS Allows localhost Only** — Correct for dev but will need updating for production

14. **`server.error.include-message: always`** — Leaks Spring error messages to API responses

15. **File Upload Without Type Validation** — Accepts any file type, no MIME checking beyond content-type header

16. **No Global Exception Handler** — `@ControllerAdvice` missing; unhandled exceptions may leak stack traces

17. **`ddl-auto: update`** — Hibernate auto-schema-update in application.yml is dangerous for production

---

## Complexity Notes

### Implementation Effort Estimates

| Feature | Complexity | Rationale |
|---------|-----------|-----------|
| Move JWT to HttpOnly cookies | **HIGH** | Requires backend cookie-setting on login/refresh, frontend auth flow rewrite, CSRF token addition |
| Externalize secrets (env vars) | **LOW** | Simple Spring property override + `.env` file |
| Add tenant filter to all queries | **MEDIUM** | ~15 endpoints need tenant ownership checks; consider a base service/interceptor pattern |
| Add `@Valid` + Bean Validation | **MEDIUM** | Requires creating/refining DTOs with constraints for every endpoint |
| Security headers (CSP, HSTS, etc.) | **LOW** | Few lines in SecurityConfig + next.config.ts `headers()` |
| Audit trail / activity logging | **HIGH** | New `AuditLog` entity + JPA `@EntityListeners` or Spring AOP for all mutations |
| Refresh token rotation + revocation | **MEDIUM** | New `RefreshToken` entity, token family tracking, reuse detection |
| Rate limiting on login | **LOW** | Spring Boot Starter + `@RateLimiter` or simple filter with in-memory counter |
| RBAC enforcement on ResourceController | **MEDIUM** | Add `@PreAuthorize` annotations referencing permissions |
| PostgreSQL Row-Level Security | **HIGH** | DBA-level changes + session variable for tenant context per connection |
| Soft-delete for documents | **LOW** | Add `deleted_at` column + modify delete endpoints |
| Password policy enforcement | **LOW** | Add validation in `changePassword()` and user creation |
| Global exception handler | **LOW** | Single `@ControllerAdvice` class |

### Recommended Priority Order

1. **P0 (fix immediately)**: Hardcoded secrets, admin cross-tenant leak, financial endpoint tenant gaps
2. **P1 (before any production use)**: JWT localStorage → cookies, sub-resource tenant checks, input validation, security headers
3. **P2 (production hardening)**: Token rotation, rate limiting, audit trail, RBAC enforcement, password policy
4. **P3 (future maturity)**: RLS in PostgreSQL, encryption at rest, ethical walls, privilege tagging

### OWASP ASVS Level 1 Coverage

| ASVS Section | Relevant? | Current Status |
|-------------|-----------|---------------|
| V2: Authentication | ✅ | ⚠️ Partial — BCrypt good, but no brute-force protection, no password policy |
| V3: Session Management | ✅ | ❌ Poor — 24h token, no rotation, no revocation |
| V4: Access Control | ✅ | ❌ Poor — tenant isolation gaps, RBAC not enforced |
| V5: Validation & Encoding | ✅ | ❌ Missing — no server-side validation |
| V8: Data Protection | ✅ | ❌ Missing — no encryption at rest, hardcoded secrets |
| V12: Files & Resources | ✅ | ⚠️ Partial — size limits but no type/path validation |
| V14: Configuration | ✅ | ❌ Missing — error exposure, ddl-auto, no security headers |

---

*Research conducted: 2026-05-27. Based on codebase analysis + OWASP ASVS v5.0 + industry standards for legal tech.*
