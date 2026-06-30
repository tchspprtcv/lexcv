---
phase: 60
slug: ficha-imprimivel
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-30
---

# Phase 60 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| URL directa → /clientes/{id}/ficha | Utilizador autenticado mas sem `clientes:view` pode tentar aceder directamente à URL | Dados completos do cliente (PII) |
| Nova aba → API /api/v1/clientes/{id} | Cookie de sessão httpOnly segue na nova aba (mesmo domínio, `credentials: "include"`) — acesso à API controlado pelo backend | Dados completos do cliente (PII) |
| Listagem → Link ficha (target=_blank) | Link abre URL em nova aba; `cliente.id` (UUID opaco) vem da API autenticada, não de input do utilizador | UUID do cliente na URL |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-60-01 | Elevation of Privilege | `FichaPage` (`web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx`) | mitigate | Guard `if (!permissions.isLoading && !canViewClientes) return <AccessDeniedState ... />` bloqueia render antes de qualquer fetch de dados (linhas 53-63) — idêntico ao padrão em `/clientes/[id]/page.tsx` | closed |
| T-60-02 | Information Disclosure | Dados do cliente em nova aba | accept | Cookie httpOnly segue na nova aba (mesmo domínio); backend valida sessão em cada request; sem risco adicional vs. página de detalhe normal | closed |
| T-60-03 | Information Disclosure | CSS `@media print` inline via `dangerouslySetInnerHTML` | accept | `PRINT_CSS` é string literal estática do componente (linhas 22-35), sem interpolação de dados do utilizador ou da API — sem vector de XSS | closed |
| T-60-04 | Information Disclosure | Link `target=_blank` com `cliente.id` (`clientes/page.tsx`, `clientes/[id]/page.tsx`) | accept | `cliente.id` é UUID opaco vindo da API autenticada, envolvido em `encodeURIComponent`; a página de destino tem o seu próprio guard de permissão (T-60-01) | closed |
| T-60-05 | Spoofing | `rel="noopener noreferrer"` em Links `target=_blank` | mitigate | `rel="noopener noreferrer"` confirmado presente em ambos os Links: `clientes/[id]/page.tsx:107` e `clientes/page.tsx:574` — previne acesso da nova aba ao `window.opener` da aba original | closed |
| T-60-SC (60-01) | Tampering | npm/pip/cargo installs | accept | Plano 60-01 não instala nenhum pacote novo; sem risco de supply chain | closed |
| T-60-SC (60-02) | Tampering | npm/pip/cargo installs | accept | Plano 60-02 não instala nenhum pacote novo; sem risco de supply chain | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-60-01 | T-60-02 | Cookie httpOnly de sessão segue automaticamente para a nova aba por ser mesmo domínio; backend valida a sessão em cada pedido — não introduz exposição além da já existente na página de detalhe do cliente | gsd-secure-phase audit | 2026-06-30 |
| AR-60-02 | T-60-03 | CSS injectado é string literal estática do componente (`PRINT_CSS`), nunca contém dados do utilizador ou da API — sem vector de injecção | gsd-secure-phase audit | 2026-06-30 |
| AR-60-03 | T-60-04 | UUID opaco do cliente vindo de fonte autenticada; página de destino re-valida permissão de forma independente | gsd-secure-phase audit | 2026-06-30 |
| AR-60-04 | T-60-SC (60-01, 60-02) | Nenhuma das duas plans introduziu novas dependências de pacotes | gsd-secure-phase audit | 2026-06-30 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-30 | 7 | 7 | 0 | gsd-secure-phase (independent verification against current source: `ficha/page.tsx`, `clientes/[id]/page.tsx`, `clientes/page.tsx`) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-30
