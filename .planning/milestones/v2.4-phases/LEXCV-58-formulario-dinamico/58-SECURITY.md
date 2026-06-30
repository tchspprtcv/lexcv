---
phase: 58
slug: formulario-dinamico
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-30
---

# Phase 58 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Form → API (POST /api/v1/clientes) | ClienteCreateRequest must never carry numero_cliente (backend assigns it) | Cliente creation payload |
| Form → API (PUT /api/v1/clientes/{id}) | ClienteUpdateRequest must never carry numero_cliente | Cliente update payload |
| API response → listing/detail render | numero_cliente and avencado come from backend; rendered as display-only | Non-sensitive sequential ID, boolean flag |
| API → form.reset (edit form) | dados_tipo from backend populates form fields; never routed through user-editable numero_cliente field | Structured tipo-specific client data |
| npm registry | New Radix packages installed from npm registry | @radix-ui/react-radio-group, @radix-ui/react-switch |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-58-01 | Tampering | ClienteCreateRequest / ClienteUpdateRequest types | mitigate | `numero_cliente` confirmed absent from both `web/src/types/clientes.ts` interfaces (verified by direct read: lines 74-93 and 95-113 contain no `numero_cliente` field) | closed |
| T-58-SC | Tampering | pnpm add @radix-ui/react-radio-group @radix-ui/react-switch | accept | Both are official Radix UI scoped packages published under the `@radix-ui` org | closed |
| T-58-02-01 | Information Disclosure | numero_cliente display (listing/detail pages) | accept | Non-sensitive sequential ID (CLI-0001 format); both pages already require `clientes:view` permission (RBAC gate) | closed |
| T-58-03-01 | Tampering | ClienteCreateRequest payload (novo/page.tsx) | mitigate | `numero_cliente` absent from `ClienteFormValues` Zod schema (`web/src/schemas/clientes.ts` — grep confirms no match), so it can never appear in `onSubmit` payload spread; verified by direct read of `onSubmit` at novo/page.tsx:90-113 | closed |
| T-58-03-02 | Tampering | dados_tipo field clearing on tipo switch | accept | `confirmTipoChange` clears opposite-tipo fields client-side before submit; backend treats `dados_tipo` as a replace operation | closed |
| T-58-04-01 | Tampering | ClienteUpdateRequest payload (editar/page.tsx) | mitigate | `numero_cliente` only referenced as read-only badge display (`cliente.data?.numero_cliente`, editar/page.tsx:159-161) — never assigned into form defaultValues, form.reset, or submit payload; same schema-level exclusion as T-58-03-01 | closed |
| T-58-04-02 | Tampering | form.reset with dados_tipo from API | accept | `dados_tipo` is structured data from the same tenant's own client record; no cross-tenant risk; RBAC gate (`clientes:edit`) enforced at route level | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-58-01 | T-58-SC | Radix UI packages are official, scoped, widely-used primitives; standard supply-chain trust level for this project | gsd-secure-phase (plan-time) | 2026-06-29 |
| R-58-02 | T-58-02-01 | numero_cliente is a non-sensitive sequential identifier; display already gated by clientes:view permission at both render sites | gsd-secure-phase (plan-time) | 2026-06-29 |
| R-58-03 | T-58-03-02 | Client-side field clearing on tipo switch is a UX safeguard; backend authoritatively replaces dados_tipo on update, so no stale-data risk crosses the trust boundary | gsd-secure-phase (plan-time) | 2026-06-29 |
| R-58-04 | T-58-04-02 | dados_tipo round-trip (API → form.reset) stays within the same tenant's own record; RBAC (clientes:edit) already enforced at route level | gsd-secure-phase (plan-time) | 2026-06-29 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-30 | 7 | 7 | 0 | gsd-secure-phase (orchestrator, plan-time register verified against implementation) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-30
