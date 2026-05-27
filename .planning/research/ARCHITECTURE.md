# LexCV — Security Architecture Analysis

> Research date: 2026-05-27 | Read-only codebase analysis for security audit planning

---

## Attack Surface Map

### 1. Frontend (Next.js 16 — `/web`)

| Surface | Location | Risk |
|---------|----------|------|
| **Login form** | `src/app/(auth)/login/page.tsx` | Credential brute-force, credential stuffing |
| **Token storage (localStorage)** | `src/lib/auth.ts` — `lexcv_access_token`, `lexcv_refresh_token` | XSS → token theft (localStorage is JS-accessible) |
| **API client** | `src/lib/api.ts` — `apiFetch()` | Token attached via `Authorization: Bearer` header; also sends `credentials: "include"` |
| **Client-side validation** | `src/schemas/*.ts` (Zod) | Client-only; can be bypassed |
| **Seed credentials in UI** | Login page line 83: `admin@lexcv.cv / admin123` displayed to users | Information disclosure |
| **No Next.js middleware** | No `middleware.ts` file found | No server-side route protection; unauthenticated users can access dashboard routes (SSR renders, API calls fail) |
| **Legacy mock API routes** | `src/app/api-backup/v1/` (9 route groups: auth, admin, clientes, dashboard, documentos, eventos, honorarios, pagamentos, processos) | Orphaned code with mock JWT (`alg: "none"`) — potential bypass if re-enabled |
| **Mock server code** | `src/server/mock-jwt.ts` — creates unsigned JWTs (`alg: "none"`) | Dead code but in production bundle if not tree-shaken |

### 2. API Proxy Layer (Next.js Rewrites)

| Surface | Details |
|---------|---------|
| **Rewrite rule** | `next.config.ts` line 7-8: `/api/v1/:path*` → `http://localhost:8080/api/v1/:path*` |
| **Protocol** | HTTP (no TLS between Next.js and Spring Boot) |
| **No header filtering** | All client headers forwarded verbatim to backend |
| **No rate limiting** | No middleware or proxy-level throttling |

### 3. Backend (Spring Boot 3.4.1 — `/backend`)

| Surface | Location | Risk |
|---------|----------|------|
| **Auth endpoints (public)** | `/api/v1/auth/login`, `/api/v1/auth/refresh` | Brute-force, credential stuffing (no rate limit) |
| **CSRF disabled** | `SecurityConfig.java` line 39 | Acceptable for stateless JWT, but `credentials: "include"` on frontend is contradictory |
| **CORS** | Allows `http://localhost:3000` with credentials | Dev-only; needs production hardening |
| **JWT secret hardcoded** | `application.yml` line 30 — base64 secret in plaintext config | Secret in version control |
| **DB credentials hardcoded** | `application.yml` lines 10-12 — `postgres/password` | Credentials in version control |
| **Hibernate ddl-auto: update** | `application.yml` line 17 | Schema drift risk in production |
| **File upload** | `ResourceController.java` — uploads to `uploads/` directory (relative path) | Path traversal potential, no antivirus scan, no file type validation beyond extension |
| **File download** | Content-Disposition with user-supplied filename | Header injection if filename contains special chars |
| **Error messages** | `include-message: always` (application.yml line 4) | Stack trace / internal details in error responses |
| **Admin controller** | `AdminController.java` — `@PreAuthorize("hasRole('ADMIN')")` | Correctly gated, but `listUsers` returns ALL users across tenants (line 40: `userRepository.findAll()`) |
| **No input validation** | Controllers accept raw `@RequestBody` with no `@Valid` / Bean Validation annotations | Injection, data integrity issues |
| **Seed passwords** | `DatabaseSeeder.java` — `admin123`, `assist123` | Weak default passwords |

### 4. Database (PostgreSQL)

| Surface | Details |
|---------|---------|
| **Multi-tenant model** | Shared database, shared schema, `tenant_id` column per table (discriminator pattern) |
| **No DB-level RLS** | No PostgreSQL Row-Level Security policies — isolation is purely application-enforced |
| **Unique constraint: email only** | `User.email` is `unique = true` but not scoped to tenant — cross-tenant email collision |

---

## Trust Boundaries

```
┌─────────────────────────────────────────────────────────────────┐
│ ZONE 0: Browser (Untrusted)                                    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ React SPA (Next.js client components)                     │  │
│  │ • localStorage: access_token, refresh_token               │  │
│  │ • Zod client-side validation (bypassable)                 │  │
│  │ • No CSP headers configured                               │  │
│  └─────────────────────┬─────────────────────────────────────┘  │
│                        │ BOUNDARY 1: Browser → Next.js Server  │
├────────────────────────┼────────────────────────────────────────┤
│ ZONE 1: Next.js Server (Semi-trusted)                          │
│  ┌─────────────────────┴─────────────────────────────────────┐  │
│  │ Next.js Rewrite Proxy                                      │  │
│  │ • No authentication check at this layer                    │  │
│  │ • No request filtering / sanitization                      │  │
│  │ • Forwards all headers including Authorization             │  │
│  │ • Legacy mock API routes still in codebase (api-backup/)   │  │
│  └─────────────────────┬─────────────────────────────────────┘  │
│                        │ BOUNDARY 2: Next.js → Spring Boot     │
│                        │ (HTTP, no TLS, localhost:8080)         │
├────────────────────────┼────────────────────────────────────────┤
│ ZONE 2: Spring Boot (Trusted Application)                      │
│  ┌─────────────────────┴─────────────────────────────────────┐  │
│  │ Security Filter Chain                                      │  │
│  │ • JwtAuthenticationFilter validates HMAC-SHA signed tokens │  │
│  │ • UserPrincipal carries userId, tenantId, roles, perms     │  │
│  │ • @PreAuthorize for admin routes                           │  │
│  │ • Manual tenantId checks in controller methods             │  │
│  │ • NO input validation framework (@Valid)                   │  │
│  └─────────────────────┬─────────────────────────────────────┘  │
│                        │ BOUNDARY 3: Application → Database    │
├────────────────────────┼────────────────────────────────────────┤
│ ZONE 3: PostgreSQL (Data Store)                                │
│  ┌─────────────────────┴─────────────────────────────────────┐  │
│  │ Single database: lexcvservice_db                           │  │
│  │ • Shared schema, tenant_id discriminator column            │  │
│  │ • No RLS policies                                          │  │
│  │ • No read-only replica separation                          │  │
│  │ • Superuser connection (postgres/password)                 │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Trust Boundary Violations Found

1. **B1 (Browser→Next.js)**: No Next.js middleware means dashboard routes render server-side without auth checks
2. **B2 (Next.js→Spring Boot)**: Unencrypted HTTP; no mutual auth; all headers forwarded blindly
3. **B3 (App→DB)**: Superuser DB credentials; no connection pool security; tenant isolation is purely application-side with no DB enforcement

---

## Data Flow Security

### Authentication Flow
```
Browser                    Next.js Proxy              Spring Boot              PostgreSQL
  │                           │                          │                        │
  │─POST /api/v1/auth/login──▶│──────────────────────────▶│                        │
  │  {email, password}        │  (plain HTTP forward)     │─findByEmail()─────────▶│
  │                           │                          │◀─User entity───────────│
  │                           │                          │  BCrypt.matches()       │
  │                           │                          │  Generate JWT (HMAC)    │
  │◀──{access_token,──────────│◀─────────────────────────│                        │
  │    refresh_token}         │                          │                        │
  │                           │                          │                        │
  │  localStorage.setItem()   │                          │                        │
```

**Security Issues in Auth Flow:**
- Tokens stored in localStorage (vulnerable to XSS)
- No httpOnly cookie option
- No token rotation on use
- Refresh token uses same HMAC key as access token (no separate key)
- Both tokens carry identical claims (roles, tenant_id)
- No jti (JWT ID) claim for revocation tracking
- No token blacklist mechanism

### Authenticated Request Flow
```
Browser                    Next.js Proxy              Spring Boot              PostgreSQL
  │                           │                          │                        │
  │─GET /api/v1/clientes─────▶│──────────────────────────▶│                        │
  │  Authorization: Bearer JWT│  (forwards all headers)   │  JwtAuthFilter:        │
  │                           │                          │  ├─validateToken(jwt)   │
  │                           │                          │  ├─extract userId       │
  │                           │                          │  ├─findById(userId)────▶│
  │                           │                          │  │◀─User entity─────────│
  │                           │                          │  └─set SecurityContext  │
  │                           │                          │                        │
  │                           │                          │  ResourceController:    │
  │                           │                          │  ├─getTenantId()        │
  │                           │                          │  └─findByTenantId()────▶│
  │                           │                          │◀─Clientes (filtered)───│
  │◀──[clientes]──────────────│◀─────────────────────────│                        │
```

**Security Issues in Request Flow:**
- JWT filter hits DB on EVERY request to load user (no caching, potential DoS)
- Tenant filtering is manual per-controller-method — easy to forget
- Sub-resources (partes, fases, movimentacoes) queried by `processoId` WITHOUT tenant check
- Honorarios/Pagamentos have NO tenant_id column — rely on indirect join through processo

### File Upload/Download Flow
```
Browser                    Next.js Proxy              Spring Boot              Filesystem
  │                           │                          │                        │
  │─POST /documentos/upload──▶│──────────────────────────▶│                        │
  │  multipart/form-data      │  (50MB max)              │  ├─UUID filename       │
  │                           │                          │  ├─no type validation  │
  │                           │                          │  └─Files.write()──────▶│ uploads/
  │                           │                          │  Save metadata to DB   │
```

**Security Issues in File Flow:**
- No file content validation (could upload executable, malware)
- Relative upload path (`uploads/`) — location predictable
- `caminhoArquivo` stored as absolute path — potential path traversal on download
- Content-Disposition header uses `doc.getNome()` directly (header injection)
- No antivirus scanning
- File size validated only at Spring level (50MB) but not per-tenant quota

---

## Recommended Audit Order

### Phase 1: Backend Security Core (HIGHEST PRIORITY)
**Why first**: This is the trust enforcement layer. If backend auth/authz is broken, nothing else matters.

| # | Area | Files | Key Checks |
|---|------|-------|------------|
| 1.1 | **JWT Implementation** | `JwtTokenProvider.java`, `JwtAuthenticationFilter.java` | Secret management, algorithm confusion, token validation completeness, expiry handling |
| 1.2 | **Authentication** | `AuthController.java` | Login brute-force, password policy, refresh token reuse, account lockout |
| 1.3 | **Authorization/RBAC** | `AdminController.java`, `SecurityConfig.java`, `UserPrincipal.java` | Role enforcement, permission bypass, privilege escalation |
| 1.4 | **Multi-Tenant Isolation** | All controllers + repositories | Tenant ID enforcement consistency, cross-tenant data access |
| 1.5 | **Input Validation** | All controllers | SQL injection (via JPA), mass assignment, type confusion |

### Phase 2: Multi-Tenant Data Isolation (CRITICAL)
**Why second**: Legal data leakage between tenants is the highest business risk.

| # | Area | Files | Key Checks |
|---|------|-------|------------|
| 2.1 | **Tenant-aware queries** | All repositories | Verify every query path filters by tenantId |
| 2.2 | **Sub-resource access** | Partes, Fases, Movimentacoes, Honorarios, Pagamentos | Cross-tenant access via sub-resource ID guessing |
| 2.3 | **Admin cross-tenant leak** | `AdminController.listUsers()` | Currently returns ALL users (no tenant filter) |
| 2.4 | **Database constraints** | Entity models | Missing foreign keys, missing tenant_id on sub-entities |

### Phase 3: API & Transport Security
| # | Area | Files | Key Checks |
|---|------|-------|------------|
| 3.1 | **CORS policy** | `SecurityConfig.java` | Production origins, credential handling |
| 3.2 | **Proxy configuration** | `next.config.ts` | Header forwarding, request smuggling |
| 3.3 | **Error handling** | `application.yml`, all controllers | Information disclosure, stack traces |
| 3.4 | **CSRF posture** | `SecurityConfig.java` + frontend `credentials: "include"` | Contradictory configuration |

### Phase 4: Frontend Security
| # | Area | Files | Key Checks |
|---|------|-------|------------|
| 4.1 | **Token storage** | `src/lib/auth.ts` | XSS vulnerability surface |
| 4.2 | **Route protection** | App Router layouts | No middleware.ts — unauthenticated access to routes |
| 4.3 | **Input sanitization** | Zod schemas, form components | Client-side only validation |
| 4.4 | **Credential exposure** | Login page | Seed credentials displayed in production UI |
| 4.5 | **Legacy mock code** | `src/server/`, `src/app/api-backup/` | Dead code with security anti-patterns (unsigned JWT) |

### Phase 5: File System & Infrastructure
| # | Area | Files | Key Checks |
|---|------|-------|------------|
| 5.1 | **File upload** | `ResourceController.uploadDocumento()` | Path traversal, type validation, size limits |
| 5.2 | **File download** | `ResourceController.downloadDocumento()` | Header injection, tenant isolation of files |
| 5.3 | **Secrets management** | `application.yml` | Hardcoded secrets, DB credentials in VCS |
| 5.4 | **Database config** | `application.yml` | ddl-auto:update, superuser access, connection security |

---

## Integration Points

### Documented Integration Points

| Integration | From | To | Mechanism | Security Concern |
|-------------|------|-----|-----------|-----------------|
| **Frontend→Backend** | Next.js :3000 | Spring Boot :8080 | HTTP rewrite proxy (`/api/v1/*`) | No TLS, no mutual auth |
| **Backend→Database** | Spring Boot | PostgreSQL :5432 | JDBC (plain, no SSL) | Superuser creds, no connection encryption |
| **File Storage** | Spring Boot | Local filesystem (`uploads/`) | `java.nio.file.Files.write()` | No object storage, no encryption at rest |
| **JWT Token Flow** | Backend→Client→Backend | Browser localStorage | Bearer token in Authorization header | XSS-accessible, no httpOnly cookies |

### Missing Security Infrastructure

| Expected Component | Status | Impact |
|-------------------|--------|--------|
| **Rate Limiting** | ❌ Not present | Auth endpoints vulnerable to brute-force |
| **Request Logging / Audit Trail** | ❌ Not present | No forensic capability for legal platform |
| **Content Security Policy** | ❌ Not configured | XSS amplification risk |
| **Next.js Middleware** | ❌ Not present | No server-side route guards |
| **DB Row-Level Security** | ❌ Not present | Tenant isolation is app-only |
| **API Versioning beyond URL** | ❌ No headers | Breaking changes uncontrolled |
| **Health/Readiness endpoints** | ❌ Not present | No operational monitoring |
| **Token Revocation** | ❌ No blacklist/jti | Cannot invalidate compromised tokens |
| **Password Policy** | ❌ No min length/complexity | Seed passwords are 8-9 chars, simple |
| **Account Lockout** | ❌ Not present | Unlimited login attempts |
| **Secrets Externalization** | ❌ Hardcoded in YAML | JWT secret and DB creds in version control |

### Critical Multi-Tenant Isolation Gaps (Pre-Audit Findings)

1. **`AdminController.listUsers()`** — calls `userRepository.findAll()` — returns users from ALL tenants
2. **`Parte` model** — has no `tenant_id` column; queried by `processoId` only without verifying processo ownership
3. **`Honorario` model** — no `tenant_id` column; financial data linked only through processo
4. **`Pagamento` model** — no `tenant_id` column; `createPagamento()` accepts any `honorarioId`
5. **`ProcessoFase` model** — no `tenant_id` column; phase updates don't verify processo tenant
6. **`Movimentacao` model** — no `tenant_id` column; created with only `processoId` check
7. **`ContaCorrente` model** — no `tenant_id` column; linked only through `clienteId`
8. **`createHonorario()`** — line 482: saves directly from request body with NO tenant or processo ownership check
9. **`createPagamento()`** — line 491-513: accepts any honorarioId, updates conta corrente without tenant verification
10. **`createParte()`** — line 216-218: creates parte on any processo without tenant verification

### RBAC Matrix (Implemented)

| Role | Clientes | Processos | Agenda | Documentos | Financeiro | RBAC | Users |
|------|----------|-----------|--------|------------|------------|------|-------|
| ADMIN | view+edit | view+edit | view+edit | view+edit | view+edit | manage | manage |
| ADVOGADO | view+edit | view+edit | view+edit | view+edit | view | - | - |
| TECNICO | view | view | view+edit | view | view | - | - |
| ASSISTENTE | view+edit | view | view | view | - | - | - |

**Note**: RBAC permissions exist in the data model but are NOT enforced at the controller level for resource endpoints. Only `AdminController` uses `@PreAuthorize`. The `ResourceController` has no permission checks — any authenticated user can perform all CRUD operations regardless of their role/permissions.
