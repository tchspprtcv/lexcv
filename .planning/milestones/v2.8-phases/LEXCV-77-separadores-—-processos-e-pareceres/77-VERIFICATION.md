---
phase: LEXCV-77-separadores-processos-e-pareceres
verified: 2026-07-05T13:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 77: Separadores — Processos e Pareceres Verification Report

**Phase Goal:** O utilizador consulta os processos e pareceres do cliente diretamente a partir da ficha do cliente
**Verified:** 2026-07-05T13:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Utilizador clica no separador Processos e vê a lista de processos do cliente atual (Número, Estado, Área Jurídica, Data de Início) | VERIFIED | `ClienteProcessosTab` (page.tsx:1123-1192) renders a `Table` with exactly these 4 headers (NÚMERO, ESTADO, ÁREA JURÍDICA, DATA DE INÍCIO), populated from `processos.data.map(...)`. Mounted at `tab === "processos" ? <ClienteProcessosTab clienteId={id} /> : ...` (line 1068), gated by `canViewProcessos` (line 1067). |
| 2 | Utilizador clica no separador Pareceres e vê a lista de pareceres do cliente atual (Número/Título, Estado, Advogado Responsável, Data de Criação) | VERIFIED | `ClienteParecerTab` (page.tsx:1213-1278) renders a `Table` with headers NÚMERO/TÍTULO, ESTADO, ADVOGADO RESPONSÁVEL, DATA DE CRIAÇÃO, populated from `pareceres.data.map(...)`, advogado resolved via `advogadoNomeById` built from `useAdminUsers`. Mounted at `tab === "pareceres" ? <ClienteParecerTab clienteId={id} /> : ...` (line 1074), gated by `canViewPareceres` (line 1073). |
| 3 | Clicar numa linha de Processo navega para /processos/[id]; clicar numa linha de Parecer navega para /pareceres/[id] | VERIFIED | `<Link href={\`/processos/${encodeURIComponent(p.id)}\`}>` (line 1166) and `<Link href={\`/pareceres/${encodeURIComponent(s.id)}\`}>` (line 1253). Both target routes confirmed to exist: `web/src/app/(dashboard)/processos/[id]/page.tsx`, `web/src/app/(dashboard)/pareceres/[id]/page.tsx`. |
| 4 | As queries de processos e pareceres só disparam quando o respetivo separador é ativado (fetch lazy) | VERIFIED | `ClienteProcessosTab`/`ClienteParecerTab` are only referenced inside their respective `tab === "processos"`/`tab === "pareceres"` JSX branches (lines 1066-1077) — React only invokes the component (and therefore `useProcessos`/`usePareceres`, which call `useQuery` internally) once that branch mounts. Confirmed `use-processos.ts` and `use-pareceres.ts` were NOT modified in phase 77 (`git log` shows last touch at phases 67-69, pre-dating this phase) — true sub-component-mount lazy fetch, zero hook changes, matching the locked design decision in 77-01-PLAN.md. |
| 5 | Cada lista mostra estados de loading/erro/vazio consistentes com /processos e /pareceres | VERIFIED | Both components implement identical 3-state + table structure: `isLoading` → "A carregar..." (slate-500), `isError` → friendly Portuguese fallback in red-600 (raw error strings explicitly stripped per WR-03 fix, confirmed at lines 1131-1134 and 1232-1234), empty (`!data?.length`) → centered slate-500 message ("Nenhum processo/parecer associado a este cliente."). |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` — `function ClienteProcessosTab` | Component mounted in Processos tab branch | VERIFIED | 1 match at line 1123, fully implemented (4-column table, badge mapping, loading/error/empty), not a stub. |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` — `function ClienteParecerTab` | Component mounted in Pareceres tab branch | VERIFIED | 1 match at line 1213, fully implemented with advogado name resolution via `useAdminUsers`, not a stub. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ClienteProcessosTab` | `useProcessos({ cliente_id })` | hook call filtered by current client id | WIRED | `useProcessos({ cliente_id: clienteId })` at line 1124 — exact snake_case filter present, matches T-77-01 threat mitigation (grep-verifiable, prevents cross-client leak). |
| `ClienteParecerTab` | `usePareceres({ clienteId })` | hook call filtered by current client id | WIRED | `usePareceres({ clienteId: clienteId })` at line 1214 — exact camelCase filter present. |
| `tab === "processos"` / `tab === "pareceres"` branches | `ClienteProcessosTab` / `ClienteParecerTab` | conditional mount (lazy) replacing `PlaceholderEmBreve` | WIRED | Lines 1066-1077 — `PlaceholderEmBreve` no longer rendered for these two branches (still used for `documentosEntregues`/`documentosATratar`/`deslocacoes` at lines 1078-1083, confirming it wasn't removed, per success criteria). |
| `useProcessos`/`usePareceres` hooks | backend `/processos`, `/pareceres/solicitacoes` endpoints | `apiFetch` with `cliente_id`/`clienteId` query param | WIRED (Level 4 data-flow) | Traced `use-processos.ts:140-158` and `use-pareceres.ts:22-37` — real `apiFetch` calls building query strings from the filters, not static/empty returns. Backend endpoints (`ResourceController`, `ParecerController`) are tenant-scoped and gated `@PreAuthorize("hasAuthority('processos:view')")` / `hasAuthority('pareceres:view')`, matching frontend `permissions.can.view("processos")`/`("pareceres")` gating added in WR-02 fix — both layers agree. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `ClienteProcessosTab` | `processos.data` | `useProcessos({cliente_id})` → `GET /processos?cliente_id=...` via `apiFetch`, normalized via `normalizeProcesso` | Yes — real backend query, not static array | FLOWING |
| `ClienteParecerTab` | `pareceres.data` | `usePareceres({clienteId})` → `GET /pareceres/solicitacoes?clienteId=...` via `apiFetch` | Yes — real backend query | FLOWING |
| `ClienteParecerTab` | `advogadoNomeById` | `useAdminUsers({enabled: isAdmin})` → filtered by `ADVOGADO` role, mapped `id → nome` | Yes, conditionally fetched (admin-only endpoint, gated to avoid 403 for non-admins per WR-01 fix); falls back gracefully to "—" for non-admin roles | FLOWING (with intentional role-based gating) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TypeScript compiles across the whole app including the new components | `cd web && pnpm build` | `✓ Compiled successfully in 12.6s`, `Finished TypeScript`, all 23 routes generated | PASS |
| Lint passes on the phase's modified file (no new errors from phase 77 code) | `cd web && pnpm lint` | 5 errors / 18 warnings total in repo, all pre-existing (lines 351/1416/1561/1763 in `clientes/[id]/page.tsx` predate phase 77 per `git log`; other errors in unrelated files `dashboard-shell.tsx`, `user-profile-form.tsx`). Zero issues in the new code range (lines 1123-1278). | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|-------------|--------------|--------|----------|
| CLI-16 | 77-01-PLAN.md | Separador "Processos" lista processos do cliente (`useProcessos({cliente_id})`) | SATISFIED | `ClienteProcessosTab` fully implemented and wired, verified truths 1, 3, 4, 5 above. |
| CLI-17 | 77-01-PLAN.md | Separador "Pareceres" lista pareceres do cliente (`usePareceres({clienteId})`) | SATISFIED | `ClienteParecerTab` fully implemented and wired, verified truths 2, 3, 4, 5 above. |

No orphaned requirements — REQUIREMENTS.md maps only CLI-16 and CLI-17 to Phase 77, and both were declared in the plan's `requirements` frontmatter field.

**Documentation staleness note (non-blocking):** `.planning/REQUIREMENTS.md` still shows CLI-16 and CLI-17 as unchecked (`- [ ]`) and "Pending" in the traceability table (lines 19-20, 69-70), despite the phase being complete and code-verified. This is a documentation lag, not a code gap — it should be updated as part of phase closure but does not affect the phase goal's achievement in the codebase.

### Anti-Patterns Found

None in the phase's new code (lines 1123-1278 of `clientes/[id]/page.tsx`). No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers, no empty handlers, no hardcoded empty data feeding the tables, no console.log-only implementations.

### Human Verification Required

None. All truths are verifiable via static analysis (source read), build/lint execution, and grep — no visual, real-time, or external-service-dependent behavior in this phase's scope (read-only list rendering + client-side navigation).

### Gaps Summary

No gaps. All 5 observable truths verified against actual code (not SUMMARY.md claims), both artifacts are substantive (not stubs) and correctly wired end-to-end (component → hook → backend endpoint → tenant-scoped, permission-gated data), navigation targets exist, build and lint are clean for the phase's code, and the code review (77-REVIEW.md) already confirms 0 critical/0 warning findings across two independent fix-verification passes. The only non-blocking observation is a stale REQUIREMENTS.md checkbox/traceability status that should be updated during phase closure documentation but does not indicate any missing implementation.

---

_Verified: 2026-07-05T13:00:00Z_
_Verifier: Claude (gsd-verifier)_
