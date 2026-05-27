# Security Tooling & Stack Research — LexCV

> Research date: 2026-05-27 | Milestone: v1.3 Security Check

## Project Stack Summary

| Layer | Technology | Version | Key Dependencies |
|-------|-----------|---------|------------------|
| Frontend | Next.js App Router | 16.2.6 | React 19.2.4, TanStack Query 5.87, Zod 4.1, React Hook Form 7.62 |
| Backend | Spring Boot | 3.4.1 | Java 17, Spring Security, Spring Data JPA, JJWT 0.12.5 |
| Database | PostgreSQL | local | Hibernate DDL auto-update |
| Auth | JWT Stateless | JJWT 0.12.5 | HMAC-SHA signing, BCrypt passwords |
| Build | Maven (backend) / npm (frontend) | — | Lombok, Tailwind 4, shadcn/ui |

### Known Observations from Codebase

- **Hardcoded DB password** in `application.yml`: `password: password`
- **JWT secret hardcoded** in `application.yml` as Base64 string (not externalized to env vars)
- **CSRF disabled** in `SecurityConfig.java` (expected for stateless JWT, but requires careful validation)
- **CORS** locked to `http://localhost:3000` only (good for dev, needs env-specific config for prod)
- **`server.error.include-message: always`** — exposes error details in responses (info leak risk)
- **No `spring-boot-starter-validation`** dependency — input validation may be missing
- **No ESLint security plugins** configured — only `eslint-config-next` core-web-vitals + typescript
- **`hibernate.ddl-auto: update`** — unsafe for production, can cause schema drift

---

## Tool Recommendations

### 1. Static Analysis — Java Spring Boot

#### SpotBugs + Find Security Bugs (PRIMARY — Zero Cost)

| Tool | Version | Purpose |
|------|---------|---------|
| `spotbugs-maven-plugin` | **4.9.8.2** | Bytecode-level static analysis for Java |
| `findsecbugs-plugin` | **1.14.0** | Security-focused SpotBugs extension (SQLi, XSS, crypto, SSRF) |

**Maven integration** — add to `backend/pom.xml` `<build><plugins>`:

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.9.8.2</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <failOnError>false</failOnError> <!-- set true after baseline -->
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>1.14.0</version>
            </plugin>
        </plugins>
    </configuration>
</plugin>
```

**Run:** `mvn spotbugs:check` or `mvn spotbugs:gui` (interactive viewer)

**What it catches for LexCV:**
- SQL injection in JPA queries (if raw `@Query` used)
- Insecure cryptographic operations
- Hardcoded credentials/passwords
- Spring-specific misconfigurations
- XSS in responses
- Path traversal in file operations (document upload/download)

#### Semgrep (RECOMMENDED — Free OSS tier)

| Tool | Version | Purpose |
|------|---------|---------|
| `semgrep` CLI | Latest (`semgrep ci`) | Multi-language SAST with framework-aware rules |

**Run for Spring Boot:**
```bash
semgrep scan --config "p/java" --config "p/spring" backend/
```

**Run for Next.js:**
```bash
semgrep scan --config "p/typescript" --config "p/nextjs" --config "p/react" web/
```

**Advantages over SpotBugs:**
- Works on source code (not bytecode) — catches config issues
- Cross-language: single tool covers Java AND TypeScript
- Registry of 2000+ community rules specifically for Spring and Next.js
- Custom YAML rule authoring for multi-tenant patterns (e.g., "every repository method must filter by tenantId")

#### SonarQube Community Build (OPTIONAL — Infrastructure overhead)

| Tool | Version | Purpose |
|------|---------|---------|
| SonarQube Community Build | **26.5.0** | Continuous code quality + security dashboard |

**Verdict for LexCV:** Skip for now. SonarQube requires running a server (Docker or standalone) and is overkill for a single-developer project. SpotBugs + Semgrep cover the same ground with zero infrastructure. Revisit if team grows or CI/CD pipeline is established.

> **Note:** SonarQube scanner now requires Java 21 runtime (even for scanning Java 17 code).

---

### 2. Static Analysis — Next.js / TypeScript

#### ESLint Security Plugins

| Plugin | Status | Action |
|--------|--------|--------|
| `eslint-config-next` (core-web-vitals + typescript) | ✅ Already configured | Keep |
| `eslint-plugin-security` | ❌ Unmaintained since 2020 | Do NOT install |
| `eslint-plugin-secure-coding` | ✅ Active, OWASP-aware | **Install** |
| `@typescript-eslint` (strict rules) | ✅ Already via eslint-config-next | Ensure `strict` mode enabled in tsconfig |

**Recommended addition** to `web/eslint.config.mjs`:
```js
import secureCoding from "eslint-plugin-secure-coding";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  secureCoding.configs.recommended,
  // ...existing config
]);
```

**What it catches for LexCV:**
- `dangerouslySetInnerHTML` usage (XSS vector)
- Unsafe `eval()` or `Function()` calls
- Regex DoS patterns
- Insecure randomness (`Math.random()` for security contexts)
- Missing input sanitization patterns

#### Semgrep for Next.js (same tool as backend)

Covers framework-specific patterns:
- Server Components leaking sensitive data to client bundles
- Missing `'server-only'` imports
- API route input validation gaps
- Unsafe cookie/token handling
- CSP misconfigurations

---

### 3. Dependency Vulnerability Scanners

#### Backend — OWASP Dependency-Check (PRIMARY)

| Tool | Version | Purpose |
|------|---------|---------|
| `dependency-check-maven` | **12.2.2** | Scan Maven deps against NVD for known CVEs |

**Maven integration** — add to `backend/pom.xml`:

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>12.2.2</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS> <!-- fail on HIGH+ -->
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Run:** `mvn dependency-check:check`
**Output:** HTML report in `target/dependency-check-report.html`

> **First run takes ~20 minutes** (downloads NVD database). Subsequent runs are fast.

**Current LexCV dependencies to audit:**
- `spring-boot-starter-*` 3.4.1 — check for Spring Framework CVEs
- `jjwt-*` 0.12.5 — check for JWT library vulnerabilities
- `postgresql` JDBC driver — check for driver-level CVEs
- `lombok` — check (low risk, compile-time only)

#### Frontend — npm audit + Supplemental Tools

| Tool | Version | Purpose |
|------|---------|---------|
| `npm audit` | Built-in | Baseline CVE check against GitHub Advisory DB |
| Renovate or Dependabot | N/A | Automated dependency update PRs |

**Run:** `cd web && npm audit`
**For detailed report:** `npm audit --json`

**Current LexCV frontend deps to audit:**
- `next` 16.2.6 — critical (RSC vulnerabilities have been found in recent versions)
- `react` / `react-dom` 19.2.4 — check React 19 advisories
- `zod` 4.1.5 — relatively new major version, check for issues
- `@tanstack/react-query` 5.87.4 — check for data-fetching related CVEs

---

### 4. Secret Detection Tools

#### Gitleaks (PRIMARY — Pre-commit gate)

| Tool | Version | Purpose |
|------|---------|---------|
| `gitleaks` | **8.30.x** | Fast regex-based secret detection in git history |

**Scan entire repo history:**
```bash
gitleaks detect --source . --verbose
```

**Scan only staged files (pre-commit hook):**
```bash
gitleaks protect --staged
```

**Known LexCV secrets to flag:**
- `application.yml` line 12: `password: password` (DB credential)
- `application.yml` line 30: JWT secret key (Base64-encoded, hardcoded)
- Any tokens in `web/src/` (check for mock tokens in API handlers)

#### TruffleHog (SUPPLEMENTAL — Deep audit)

| Tool | Version | Purpose |
|------|---------|---------|
| `trufflehog` | **3.95.x** | Deep scan with credential verification |

**Use for one-time historical audit:**
```bash
trufflehog git file://. --only-verified
```

**Recommendation:** Run Gitleaks in pre-commit (fast). Run TruffleHog as a one-time deep audit.

---

## OWASP Top 10 Mapping for Stack

| # | OWASP Category | Risk Level for LexCV | Specific Threats | Where to Look |
|---|---------------|---------------------|-----------------|--------------|
| **A01** | Broken Access Control | 🔴 **CRITICAL** | Cross-tenant data leakage; BOLA in `/api/v1/{resource}/{id}` endpoints; missing `tenantId` filtering | `controllers/`, `repositories/`, `SecurityConfig.java` |
| **A02** | Cryptographic Failures | 🟠 **HIGH** | JWT secret hardcoded in `application.yml`; DB password in plaintext; no key rotation | `application.yml`, `JwtTokenProvider.java` |
| **A03** | Injection | 🟡 **MEDIUM** | SQL injection via raw `@Query`; HQL injection; potential OS command injection in document handling | `repositories/`, document upload endpoints |
| **A04** | Insecure Design | 🟡 **MEDIUM** | No rate limiting on `/auth/login`; no JWT blacklist/revocation; 30-day refresh token | `SecurityConfig.java`, `JwtTokenProvider.java` |
| **A05** | Security Misconfiguration | 🟠 **HIGH** | `server.error.include-message: always`; `hibernate.ddl-auto: update`; CSRF disabled; missing security headers | `application.yml`, `SecurityConfig.java`, `next.config.ts` |
| **A06** | Vulnerable Components | 🟡 **MEDIUM** | Outdated deps with potential CVEs; no automated dependency updates | `pom.xml`, `package.json` |
| **A07** | Identification & Auth Failures | 🟠 **HIGH** | No account lockout; no password complexity; JWT exception swallowing; no MFA | `JwtTokenProvider.java`, auth controllers |
| **A08** | Software & Data Integrity | 🟡 **MEDIUM** | No subresource integrity on frontend; no SBOM; `npm install` without lockfile enforcement | `package.json`, build pipeline |
| **A09** | Security Logging & Monitoring | 🟠 **HIGH** | No security event logging; failed auth not tracked; no audit trail | Entire backend (absent) |
| **A10** | Server-Side Request Forgery | 🟢 **LOW** | Limited external API calls; Next.js rewrites proxy to localhost only | `next.config.ts` |

---

## Integration Points

### Recommended Tool Pipeline

| Phase | Tool | Trigger | Blocking? |
|-------|------|---------|-----------|
| Pre-commit | Gitleaks | Every commit | Yes — block secrets |
| Dev build | SpotBugs + Find Security Bugs | `mvn verify` | No (warning only initially) |
| Dev build | ESLint + secure-coding plugin | `npm run lint` | Yes — block unsafe patterns |
| Manual audit | Semgrep | On-demand / weekly | No |
| Manual audit | OWASP Dependency-Check | On-demand / weekly | No (report only) |
| Manual audit | npm audit | On-demand / weekly | No |
| One-time | TruffleHog historical scan | Once, then quarterly | No |

---

## What NOT to Do

### ❌ Do NOT install SonarQube for this project size
SpotBugs + Semgrep provide equivalent security coverage with zero ops overhead.

### ❌ Do NOT use `eslint-plugin-security`
Unmaintained since 2020, excessive false positives. Use `eslint-plugin-secure-coding` instead.

### ❌ Do NOT rely solely on `npm audit`
Layer it with Semgrep SCA or Socket.dev for production.

### ❌ Do NOT run OWASP Dependency-Check in every build
Use a Maven profile (`-Psecurity`) or run weekly.

### ❌ Do NOT hardcode the JWT secret or DB credentials
Move to environment variables:
```yaml
# BAD (current)
password: password
secret: dGhpc2lzYW1hdmVsb3VzbHk...

# GOOD
password: ${DB_PASSWORD}
secret: ${JWT_SECRET}
```

### ❌ Do NOT keep `server.error.include-message: always` in production

### ❌ Do NOT keep `hibernate.ddl-auto: update` in production
Use Flyway or Liquibase for managed migrations.

### ❌ Do NOT suppress JWT validation exceptions silently
Log them for security monitoring.

### ❌ Do NOT skip tenant isolation testing
Every endpoint must be tested with Tenant A JWT against Tenant B resources.

---

## Quick Start Checklist

- [ ] Run `gitleaks detect --source . --verbose` — identify all committed secrets
- [ ] Run `cd web && npm audit` — check frontend CVEs
- [ ] Add SpotBugs + FindSecBugs to `backend/pom.xml` and run `mvn spotbugs:check`
- [ ] Add OWASP Dependency-Check to `backend/pom.xml` and run `mvn dependency-check:check`
- [ ] Install `eslint-plugin-secure-coding` in `web/` and run lint
- [ ] Run `semgrep scan --config auto .` from project root
- [ ] Review findings and map to OWASP Top 10 categories above
