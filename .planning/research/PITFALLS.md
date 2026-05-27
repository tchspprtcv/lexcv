# Security Audit Pitfalls — LexCV (Next.js 16 + Spring Boot 3.4)

> Research date: 2026-05-27 | Read-only codebase analysis

---

## Common False Positives (to avoid wasting time)

These are things an automated scanner or naive audit will flag, but are **not real issues** in this project's context:

### 1. "CSRF disabled" — Not a vulnerability here
- `SecurityConfig.java:39` disables CSRF: `.csrf(AbstractHttpConfigurer::disable)`
- **Why it's safe**: The app uses stateless JWT auth with `SessionCreationPolicy.STATELESS`. CSRF protections are only meaningful for cookie/session-based auth. This is correct.
- **Audit trap**: Tools like OWASP ZAP and Fortify will flag this as HIGH. It's a false positive.

### 2. "Hardcoded credentials in seed data" — Expected for dev
- `DatabaseSeeder.java:114` seeds `admin123` and `assist123` passwords
- `mock-db.ts:172-184` has plaintext passwords in mock data
- `login/page.tsx:83` displays "Credenciais seed: admin@lexcv.cv / admin123"
- **Why it's fine for now**: These are dev/demo seeds. The real risk is if they ship to production — flag as an environment management issue, not a code vulnerability.

### 3. "CORS allows all methods" — Scoped correctly
- `SecurityConfig.java:57` allows GET/POST/PUT/DELETE/OPTIONS/PATCH but only from `localhost:3000`
- **Audit trap**: Scanners flag wildcard-ish method lists. This is correctly scoped to a single origin with credentials enabled.

### 4. "JWT secret in config file" — It's application.yml, not .env
- The secret is in `application.yml:30` — this is Spring's standard config. The real risk is whether this file gets committed (it is), not that it exists.

### 5. "localStorage for tokens" — Acceptable trade-off
- `auth.ts:6,11` stores JWT in localStorage. While httpOnly cookies are theoretically more secure against XSS, localStorage is the standard SPA pattern. Flag as "defense-in-depth opportunity," not vulnerability.

---

## Frequently Missed Vulnerabilities

### 🔴 CRITICAL: Sub-resource endpoints lack tenant isolation

**This is the #1 real vulnerability in the codebase.**

The following endpoints query by `processoId` or `honorarioId` WITHOUT verifying the parent resource belongs to the current tenant:

| Endpoint | File:Line | Issue |
|---|---|---|
| `GET /processos/{id}/partes` | ResourceController.java:211 | `parteRepository.findByProcessoId(id)` — no tenant check on processo |
| `POST /processos/{id}/partes` | ResourceController.java:216 | Creates parte without verifying processo belongs to tenant |
| `GET /processos/{id}/fases` | ResourceController.java:222 | No tenant verification on processo before listing fases |
| `POST /processos/{id}/fases` | ResourceController.java:246 | Same — creates fase without tenant check |
| `PUT /processos/{id}/fases/{faseId}` | ResourceController.java:266 | Updates fase, checks processo_id match but NOT tenant |
| `GET /processos/{id}/movimentacoes` | ResourceController.java:289 | `findByProcessoId` — no tenant check |
| `POST /processos/{id}/movimentacoes` | ResourceController.java:294 | Creates mov without tenant check |
| `POST /honorarios` | ResourceController.java:481 | `honorarioRepository.save(hon)` — NO tenant check at all, accepts any processoId |
| `POST /pagamentos` | ResourceController.java:491 | Saves pagamento with NO tenant validation — accepts any honorarioId |
| `GET /honorarios/{id}/pagamentos` | ResourceController.java:486 | Lists pagamentos by honorarioId without tenant check |

**Attack vector**: Tenant A's user can access/create resources for Tenant B's processos by guessing/enumerating UUIDs.

### 🔴 CRITICAL: AdminController lists ALL users across ALL tenants

- `AdminController.java:40`: `userRepository.findAll()` returns users from **every tenant**, not just the admin's tenant.
- Similarly, `deleteUser` at line 188 can delete users from other tenants.
- `updateUser` at line 126 can modify users from other tenants.

### 🟠 HIGH: Path traversal in document upload/download

- `ResourceController.java:395`: `Paths.get(UPLOAD_DIR + savedName)` — while `savedName` is a UUID + extension, the extension is extracted from the original filename. A crafted filename like `file.../../../../etc/passwd` could theoretically manipulate the path.
- `ResourceController.java:436`: `new File(doc.getCaminhoArquivo())` — the stored path from DB is used directly. If an attacker can manipulate the DB record, they can read arbitrary files.
- **Missing**: No validation that the resolved path stays within UPLOAD_DIR.

### 🟠 HIGH: No password complexity validation

- `AuthController.java:190`: Password change only checks current password match, no minimum length/complexity for new password.
- `AdminController.java:101,146`: Admin can set passwords without complexity requirements.
- Legal systems need strong password policies.

### 🟡 MEDIUM: No rate limiting on auth endpoints

- Login (`/api/v1/auth/login`) and refresh (`/api/v1/auth/refresh`) have no rate limiting.
- Brute-force attacks are trivial.

### 🟡 MEDIUM: Error messages reveal stack details

- `application.yml:4`: `include-message: always` — this exposes internal error messages in production, which can leak schema/implementation details.

### 🟡 MEDIUM: No input validation on many endpoints

- `ResourceController.java:79`: `createCliente` accepts `@RequestBody Cliente` with no validation annotations (`@Valid`, `@NotBlank`, etc.)
- Same pattern across createProcesso, createEvento, createMovimentacao, etc.
- Missing `@Valid` + Bean Validation annotations on all `@RequestBody` parameters.

### 🟡 MEDIUM: Content-Disposition header injection

- `ResourceController.java:444`: `"attachment; filename=\"" + doc.getNome() + "\""` — if `doc.getNome()` contains special chars or CRLF, this enables HTTP header injection. Should use `ContentDisposition.builder()`.

---

## Multi-Tenant Specific Risks

### Architecture Assessment

The app uses a **shared database, shared schema** multi-tenancy model with `tenant_id` columns. This is the most common but most error-prone approach.

### Critical Gaps

1. **No Hibernate filter / RLS**: There's no database-level row security (PostgreSQL RLS) or JPA `@Filter` to enforce tenant isolation. Everything relies on correct `WHERE tenant_id = ?` in queries. One missed filter = data leak.

2. **Inconsistent tenant filtering pattern**: 
   - ✅ `ClienteRepository` has `findByTenantId` methods — good
   - ✅ `EventoRepository` has `findByTenantId` methods — good
   - ❌ `ParteRepository` only has `findByProcessoId` — no tenant check
   - ❌ `MovimentacaoRepository` only has `findByProcessoId` — no tenant check
   - ❌ `HonorarioRepository` only has `findByProcessoId` — no tenant check
   - ❌ `PagamentoRepository` only has `findByHonorarioId` — no tenant check
   - ❌ `ProcessoFaseRepository` only has `findByProcessoId` — no tenant check

3. **No tenant ownership verification on child resources**: When accessing `/processos/{id}/partes`, the controller doesn't verify the processo belongs to the caller's tenant before returning parties.

4. **Admin cross-tenant access**: `AdminController.listUsers()` uses `findAll()` instead of filtering by admin's tenant. An admin from TenantA sees all users from TenantB.

5. **No tenant_id in JWT claims used for validation**: The JWT contains `tenant_id`, but the `JwtAuthenticationFilter` re-fetches the user from DB (correct for freshness), yet the tenant_id from JWT is never cross-validated against the user's current tenant_id in the database.

### Recommended Fix Priority
1. Add PostgreSQL Row-Level Security policies as a safety net
2. Create a `TenantContext` ThreadLocal or Spring `@Filter` that automatically scopes all queries
3. Add tenant verification to ALL sub-resource endpoints
4. Filter admin operations by tenant

---

## JWT Pitfalls

### Findings in LexCV

1. **JWT secret hardcoded in application.yml and committed to git**
   - `application.yml:30`: `secret: dGhpc2lzYW1hdmVsb3VzbHlzdHJvbmdhbmRzZWN1cmVqd3RzZWNyZXRrZXlmb3JsZXhjdmFwcGxpY2F0aW9uMjAyNg==`
   - Base64-decoded: `thisisamavelouslystrongandndsecurejwtsecretkeyforlexcvapplication2026`
   - This is a **predictable, human-readable secret** committed to version control.
   - **Fix**: Use environment variables or a secrets manager.

2. **No JWT token revocation mechanism**
   - Once a JWT is issued, it's valid until expiration (24 hours for access, 30 days for refresh).
   - If a user is deactivated or their roles change, the token remains valid.
   - The filter does check `user.getAtivo()` on every request (JwtAuthenticationFilter:43), which partially mitigates this. But role changes in the JWT claims won't be reflected until re-login.

3. **Refresh token has no rotation or family tracking**
   - `AuthController.java:91`: Refresh generates new access + refresh tokens, but the old refresh token is never invalidated.
   - An attacker who steals a refresh token has 30 days of access, and the legitimate user will never know.
   - **Missing**: Refresh token rotation with family detection (if old refresh token is used after rotation, invalidate entire family).

4. **Access and refresh tokens use the same signing key and structure**
   - Both `generateAccessToken` and `generateRefreshToken` use identical signing. There's no `token_type` claim to differentiate them. A refresh token could potentially be used as an access token if the expiry is valid.

5. **Mock JWT on frontend (alg: "none") coexists with real JWT**
   - `mock-jwt.ts:23`: Creates tokens with `alg: "none"` — this is the classic JWT bypass vulnerability.
   - While this is only in the frontend mock, if the backend ever accepts unsigned tokens (e.g., during testing), this is catastrophic.
   - **Verify**: Ensure backend ALWAYS validates signatures and NEVER accepts `alg: "none"`.

6. **No `jti` (JWT ID) claim**
   - Tokens lack unique identifiers, making it impossible to implement token blocklists/revocation.

7. **Token stored in localStorage vulnerable to XSS**
   - `auth.ts:6,11,16-17`: Tokens in localStorage are accessible to any JavaScript running on the page.
   - If any XSS vulnerability exists (dependency-injected or otherwise), tokens can be exfiltrated.

---

## Environment & Secrets Risks

### Current State

1. **Database credentials in committed application.yml**
   - `application.yml:11-12`: `username: postgres`, `password: password`
   - These are checked into git. Even for dev, this sets a bad pattern.

2. **JWT secret in committed application.yml** (as noted above)

3. **No .env files found** — but also no .env.example or environment variable pattern
   - The `.gitignore` correctly excludes `.env*` files, but the app doesn't use environment variables at all.
   - Spring's `application.yml` should use `${ENV_VAR:default}` syntax for secrets.

4. **No Spring profiles for environment separation**
   - There's only one `application.yml` — no `application-dev.yml`, `application-prod.yml`, or `application-staging.yml`.
   - Production will run with the same hardcoded credentials unless overridden.

5. **`ddl-auto: update` in production**
   - `application.yml:17`: `ddl-auto: update` — Hibernate will auto-modify the database schema in production. This is dangerous for data integrity and can cause data loss.

6. **File uploads stored in relative path `uploads/`**
   - `ResourceController.java:47`: `UPLOAD_DIR = "uploads/"` — relative to CWD. In production, this is fragile and may expose files if the web server serves static content from the app root.

### What's Missing
- No secrets management (Vault, AWS Secrets Manager, etc.)
- No environment variable usage for any secret
- No Docker/deployment configuration visible
- No HTTPS/TLS configuration
- No security headers middleware (CSP, HSTS, X-Frame-Options)

---

## OWASP Top 10 Analysis (Java + TypeScript Stack)

| # | OWASP Category | Status | LexCV-Specific Finding |
|---|---|---|---|
| A01 | Broken Access Control | 🔴 CRITICAL | Multi-tenant isolation failures (sub-resources, admin cross-tenant). No RBAC enforcement on most resource endpoints — only admin endpoints use `@PreAuthorize` |
| A02 | Cryptographic Failures | 🟠 HIGH | JWT secret committed to git; predictable value. DB passwords in plaintext config. No password complexity enforcement |
| A03 | Injection | 🟢 LOW | Spring Data JPA parameterizes queries. No raw SQL found. Low risk. |
| A04 | Insecure Design | 🟠 HIGH | No defense-in-depth for tenant isolation. No audit logging. No DPIA capability. |
| A05 | Security Misconfiguration | 🟠 HIGH | `include-message: always`, `ddl-auto: update`, hardcoded secrets, no security headers |
| A06 | Vulnerable Components | 🟡 MEDIUM | Spring Boot 3.4.1 and JJWT 0.12.5 are current. Next.js 16.2.6 is current. No known CVEs, but no dependency scanning in place. |
| A07 | Auth Failures | 🟠 HIGH | No rate limiting, no account lockout, no MFA, no password complexity, token doesn't expire on role change |
| A08 | Data Integrity Failures | 🟡 MEDIUM | No SBOM, no dependency verification, no CI/CD security checks visible |
| A09 | Logging & Monitoring | 🔴 CRITICAL | Zero audit logging. No access logs, no auth event logs, no tenant-scoped activity tracking. For a legal app, this is a compliance failure. |
| A10 | SSRF | 🟢 LOW | Next.js rewrites proxy to localhost:8080. No user-controlled URL fetching. |

---

## Cape Verde Legal/Compliance Considerations

### Applicable Law
- **Law No. 133/V/2001** (Data Protection), updated by **Law No. 121/IX/2021**
- Overseen by **CNPD** (Comissão Nacional de Proteção de Dados) — Cape Verde's data protection authority
- Closely aligned with EU GDPR

### Compliance Gaps in LexCV

1. **No audit trail** — Cape Verde law requires traceability of who accessed/modified personal data. LexCV has zero audit logging.

2. **No data export/portability** — Data subjects (clients in legal cases) have the right to data portability. No export functionality exists.

3. **No data deletion capability** — Right to erasure ("right to be forgotten") is required. The `DELETE /clientes/{id}` exists but doesn't cascade to all associated data (processos, documentos, etc.) in a compliant way.

4. **No consent management** — Processing personal data requires informed consent with affirmative action.

5. **No breach notification workflow** — CNPD must be notified within 72 hours of a data breach. No mechanism exists for this.

6. **No DPIA** — Data Protection Impact Assessment is required for high-risk processing (legal case management qualifies).

7. **Attorney-client privilege** — Legal documents and case data have heightened protection requirements. The flat file upload system with no encryption at rest is insufficient.

8. **Cross-border data transfer** — If hosted outside Cape Verde, additional safeguards are required under the law.

9. **DPO requirement** — Organizations processing sensitive legal data likely need a designated Data Protection Officer.

---

## Prevention Strategy by Phase

### Phase 1: Immediate (Before Any New Features)
- [ ] Move ALL secrets to environment variables
- [ ] Add `application-dev.yml` / `application-prod.yml` profiles
- [ ] Remove seed credentials display from login page
- [ ] Change `ddl-auto: update` to `validate` for production
- [ ] Change `include-message: always` to `never` for production

### Phase 2: Tenant Isolation Fix (Week 1)
- [ ] Add tenant verification to ALL sub-resource endpoints (partes, fases, movimentacoes, honorarios, pagamentos)
- [ ] Filter AdminController operations by tenant_id
- [ ] Consider adding PostgreSQL Row-Level Security as defense-in-depth
- [ ] Add integration tests that verify cross-tenant access is blocked

### Phase 3: Auth Hardening (Week 2)
- [ ] Add rate limiting to `/auth/login` and `/auth/refresh`
- [ ] Implement refresh token rotation with family tracking
- [ ] Add `token_type` claim to differentiate access/refresh tokens
- [ ] Add `jti` claim for future revocation support
- [ ] Add password complexity validation
- [ ] Add account lockout after N failed attempts

### Phase 4: Audit & Compliance (Week 3-4)
- [ ] Implement comprehensive audit logging (who, what, when, tenant)
- [ ] Add security headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)
- [ ] Add input validation (`@Valid`, Bean Validation) to all endpoints
- [ ] Fix Content-Disposition header injection
- [ ] Add path traversal protection for document upload/download
- [ ] Begin DPIA documentation

### Phase 5: RBAC Enforcement (Week 4-5)
- [ ] Add `@PreAuthorize` or permission checks to ALL resource endpoints (currently only AdminController has it)
- [ ] Verify frontend permission checks cannot be bypassed via direct API calls
- [ ] Add API-level permission enforcement matching the RBAC model defined in the system

### Phase 6: Compliance (Ongoing)
- [ ] Implement data export functionality for CNPD compliance
- [ ] Add consent management system
- [ ] Create breach notification workflow
- [ ] Document data processing activities
- [ ] Conduct formal DPIA
- [ ] Implement encryption at rest for legal documents
