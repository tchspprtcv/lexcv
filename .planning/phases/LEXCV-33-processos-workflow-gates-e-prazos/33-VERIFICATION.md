---
phase: 33-processos-workflow-gates-e-prazos
verified: 2026-06-16T00:00:00Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Workflow card — estado, responsavel, proximo passo, transicao buttons"
    expected: "Card shows current estado badge, responsavelNome or 'Não atribuído', proximoPasso string, and only the transitions allowed for the current estado from the backend"
    why_human: "Visual rendering and state-specific button set cannot be verified without a running app"
  - test: "Critical transition Dialog enforces justificativa min-10 and server gate"
    expected: "Clicking 'Suspender' opens Dialog; submitting <10 chars shows red validation; valid justificativa posts and estado changes to SUSPENSO; user without processos:manage sees disabled button with tooltip"
    why_human: "RHF validation feedback and server 403/400 rejection require a live browser session"
  - test: "Illegal transition rejected 409 server-side"
    expected: "Attempting to POST /transicao/encerrar on a TRIAGEM processo returns 409 CONFLICT with 'Transição não é permitida'"
    why_human: "HTTP round-trip to a running backend; cannot be verified by source reading alone"
  - test: "Prazos list risk badges and concluido toggle"
    expected: "A past-date prazo shows red 'PRAZO VENCIDO' badge; a prazo due in 2 days (MEDIA) shows amber 'PRAZO PROXIMO'; toggling concluido flips the icon immediately (optimistic) and badge stays correct after server response with recomputed risco"
    why_human: "Date-relative risco and optimistic UI update require a live browser session with real data"
  - test: "Listing signals — Resp. sub-line, risco badge, escalonado icon"
    expected: "Processos listing shows 'Resp.: {nome}' sub-line; processos with proximo/vencido prazos show the PRAZO PROXIMO/PRAZO VENCIDO badge; processos with only ok prazos show no badge; escalonado AlertCircle appears when tem_prazo_escalonado is true"
    why_human: "Visual signal placement and conditional rendering require a live browser session with seeded data"
---

# Phase 33: Processos Workflow Gates e Prazos — Verification Report

**Phase Goal:** Estruturar o acompanhamento do processo com estados, gates de validacao, responsaveis e prazos operacionais com risco e escalonamento.
**Verified:** 2026-06-16
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                                             | Status     | Evidence                                                                                                                                                                                                                  |
|----|-----------------------------------------------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | State machine: TRANSICOES_PERMITIDAS is the single backend source of truth; GET /workflow returns transicoesDisponiveis; frontend renders them without hardcoding | VERIFIED   | `TRANSICOES_PERMITIDAS` static Map at RC line 85-97: keys ATIVO/SUSPENSO/ENCERRADO, no TRIAGEM key. `getWorkflow()` at line 959 reads from it via `getOrDefault`. Frontend `transicoesDisponiveis.map((t) => ...)` at page.tsx line 575 — no hardcoded transition list. |
| 2  | Gates: illegal transitions → 409; critical (suspender/encerrar/reabrir) require processos:manage AND justificativa (min 10 enforced backend); normal transitions at edit-or-manage floor; author from authenticated principal | VERIFIED   | RC line 989: `@PreAuthorize("hasAnyAuthority('processos:edit','processos:manage')")`. Estado gate returns `HttpStatus.CONFLICT` (RC 1009). Manage gate reads `SecurityContextHolder` authorities, returns `HttpStatus.FORBIDDEN` (RC 1020). Justificativa gate checks `trim().length() < 10`, returns `badRequest()` (RC 1029). Author resolved from `UserPrincipal` (RC 1048), never from payload. |
| 3  | Responsavel: Processo.responsavel_id exists; assignment validated to same tenant; surfaced in detail + listing                    | VERIFIED   | `Processo.java` line 27: `@Column(name = "responsavel_id")`. Tenant validation in `createProcesso` (RC 692-697) and `getWorkflow` (RC 972-976). `responsavel_nome` put in listing map (RC 657). `useWorkflow` hook surfaces `responsavelNome` in workflow card (page.tsx line 549). |
| 4  | Prazos: Prazo entity; risk (ok/proximo/vencido) derived in backend (computeRisco, no risco column); escalonado flag; PATCH /concluido returns recomputed risco; prazo creator/responsavel tenant-validated | VERIFIED   | `Prazo.java` has no `risco` field. `computeRisco()` at RC 1071-1078: null→ok, isBefore(hoje)→vencido, diasRestantes≤limiar→proximo. PATCH at RC 1185 returns `computeRisco()` result. `createPrazo` validates `responsavelId` tenant membership (RC 1126-1131). |
| 5  | Frontend: workflow card (estado/responsavel/proximo passo/transicoes + justificativa Dialog), prazos list with risk badges via lib/prazos.ts (single source), listing signals; risk mapping not re-implemented inline | VERIFIED   | `prazosRiscoToVariant` and `prazosRiscoToLabel` imported from `@/lib/prazos` in both `[id]/page.tsx` (line 54) and `page.tsx` (line 16). No `risco ===` or date-math found in either page. Dialog with RHF + `transicaoJustificativaFormSchema` at page.tsx line 617. Justificativa min(10) in `schemas/processos.ts` line 83. |
| 6  | Multi-tenant: transitions, workflow, prazos, responsavel resolution, listing enrichment all scoped by getTenantId()               | VERIFIED   | Every new handler opens with `UUID tenantId = getTenantId()` and the guard `!processo.getTenantId().equals(tenantId)`. `togglePrazoConcluido` at RC 1173 checks both `prazo.getTenantId()` and `prazo.getProcessoId()`. Listing enrichment batch-loads responsaveis filtered by `tenantId.equals(u.getTenantId())` (RC 636). |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact                                                                              | Expected                                             | Status    | Details                                                                            |
|---------------------------------------------------------------------------------------|------------------------------------------------------|-----------|------------------------------------------------------------------------------------|
| `backend/src/main/java/com/lexcv/models/Prazo.java`                                  | Entidade Prazo tenant-scoped, no risco column        | VERIFIED  | `@Table(name="t_prazo")`, `tenant_id` not-null, `data_limite` LocalDate, no risco field |
| `backend/src/main/java/com/lexcv/repositories/PrazoRepository.java`                  | findByTenantIdAndProcessoIdOrderByDataLimiteAsc      | VERIFIED  | Method declared at line 9; `findByTenantId` at line 10                             |
| `backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java`                         | DTO with nested TransicaoInfo                        | VERIFIED  | Nested record `TransicaoInfo` with 4 components: acao, label, permissaoNecessaria, requerJustificativa |
| `backend/src/main/java/com/lexcv/dtos/TransicaoRequest.java`                         | record(justificativa)                                | VERIFIED  | Single-field nullable record                                                       |
| `backend/src/main/java/com/lexcv/dtos/PrazoRequest.java`                             | record(descricao, dataLimite, prioridade, responsavelId) | VERIFIED | Four-field record with @NotBlank/@NotNull on required fields                      |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java`                | Endpoints workflow/transicao/prazos + listing enrichment | VERIFIED | All endpoints present; listing enrichment at RC 622-684                           |
| `web/src/lib/prazos.ts`                                                               | Single source for risco->variant/label               | VERIFIED  | Exports `prazosRiscoToVariant` and `prazosRiscoToLabel` with Record-based maps    |
| `web/src/hooks/use-processos.ts`                                                      | 5 hooks: useWorkflow + 4 more                        | VERIFIED  | All 5 hooks present at lines 448, 462, 487, 501, 521                              |
| `web/src/types/processos.ts`                                                          | WorkflowResponse, Prazo, TransicaoInfo, PrazoRisco   | VERIFIED  | All types declared; PrazoRisco union at line 3; WorkflowResponse at line 162      |
| `web/src/schemas/processos.ts`                                                        | transicaoJustificativaFormSchema min(10), prazoFormSchema | VERIFIED | Both schemas present; min(10) at line 83                                          |
| `web/src/components/ui/dialog.tsx`                                                    | DialogContent exported                               | VERIFIED  | `DialogContent` function declared and exported                                    |
| `web/src/components/ui/textarea.tsx`                                                  | Textarea exported                                    | VERIFIED  | `export function Textarea` at line 5                                              |
| `web/src/app/(dashboard)/processos/[id]/page.tsx`                                    | Workflow card + Prazos card                          | VERIFIED  | `useWorkflow` and `usePrazos` imported and called; Dialog with justificativa Textarea |
| `web/src/app/(dashboard)/processos/page.tsx`                                         | Listing signals via prazosRiscoToVariant             | VERIFIED  | `prazosRiscoToVariant` used for `risco_mais_critico`; `Resp.:` sub-line at line 336 |

### Key Link Verification

| From                                       | To                               | Via                               | Status   | Details                                                                                         |
|--------------------------------------------|----------------------------------|-----------------------------------|----------|-------------------------------------------------------------------------------------------------|
| `executarTransicao` in RC                  | `TRANSICOES_PERMITIDAS` map      | estado gate lookup                | WIRED    | RC 1003-1011: `TRANSICOES_PERMITIDAS.getOrDefault(estadoAtual, List.of())` then stream filter   |
| `executarTransicao` in RC                  | `movimentacaoRepository`         | TRANSICAO_ESTADO Movimentacao     | WIRED    | RC 1055-1060: `mov.setTipo("TRANSICAO_ESTADO")` + `movimentacaoRepository.save(mov)`            |
| `listPrazos` in RC                         | `computeRisco`                   | risco derived at response time    | WIRED    | RC 1091: `String risco = computeRisco(p.getDataLimite(), p.getPrioridade())`                    |
| `useExecutarTransicao`                     | `/processos/{id}/transicao/{acao}` | apiFetch POST                   | WIRED    | use-processos.ts line 468: template literal with `transicao/${args.acao}`                       |
| `usePrazos`                                | `/processos/{id}/prazos`         | apiFetch GET                      | WIRED    | use-processos.ts line 493-494                                                                   |
| `lib/prazos.ts`                            | `types/processos.ts PrazoRisco`  | type import                       | WIRED    | prazos.ts line 1: `import type { PrazoRisco } from "@/types/processos"`                        |
| `processos/[id]/page.tsx` workflow card    | `useExecutarTransicao`           | hook consumption + mutateAsync    | WIRED    | page.tsx lines 153, 233, 246                                                                    |
| `processos/[id]/page.tsx` prazos card      | `usePrazos/useCreatePrazo/useTogglePrazoConcluido` | hook consumption | WIRED    | page.tsx lines 152, 154, 155                                                                    |
| `processos/page.tsx`                       | `lib/prazos.ts`                  | prazosRiscoToVariant/Label        | WIRED    | page.tsx line 16 import + line 354 usage                                                        |

### Data-Flow Trace (Level 4)

| Artifact                                       | Data Variable             | Source                                          | Produces Real Data | Status    |
|------------------------------------------------|---------------------------|-------------------------------------------------|--------------------|-----------|
| `processos/[id]/page.tsx` Workflow card        | `workflow.data`           | `useWorkflow` → GET /processos/{id}/workflow    | Yes (DB via `processoRepository.findById` + `userRepository.findById`) | FLOWING   |
| `processos/[id]/page.tsx` Prazos card          | `prazos.data`             | `usePrazos` → GET /processos/{id}/prazos         | Yes (DB via `prazoRepository.findByTenantIdAndProcessoIdOrderByDataLimiteAsc`) | FLOWING   |
| `processos/page.tsx` listing signals           | `p.risco_mais_critico`, `p.responsavel_nome` | `useProcessos` → GET /processos enriched | Yes (N+1-free: `prazoRepository.findByTenantId` + in-memory group at RC 624-628) | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — all behavioral checks require a running backend with PostgreSQL and a seeded database. Items deferred to Human Verification section below.

### Probe Execution

Step 7c: No probe scripts discovered in `scripts/` for this phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status   | Evidence                                                                                                              |
|-------------|-------------|-------------|----------|-----------------------------------------------------------------------------------------------------------------------|
| PRC-27      | 33-01, 33-02, 33-03 | Gerir processo por estados com gates, responsaveis e validacoes por transicao | SATISFIED | State machine (TRANSICOES_PERMITIDAS), 3-gate POST /transicao, responsavel_id on Processo, workflow card on detail page |
| AGD-22      | 33-01, 33-02, 33-03 | Acompanhar prazos operacionais com SLA, prioridade, risco e escalonamento simples | SATISFIED | Prazo entity, computeRisco() in backend, escalonado auto-computed on create, prazos list card with risco badges       |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No TBD/FIXME/XXX/placeholder stubs found in phase-modified files | — | — |

No `TBD`, `FIXME`, or `XXX` markers were found in the phase-modified files. No `return null`/`return []`/stub patterns that flow to rendering without a real data source. The `prazos.ts` lib uses Record-based maps (not empty fallbacks). `computeRisco` has no hardcoded returns — it branches on real date math server-side.

### Human Verification Required

The plan's final task (`33-03 Task 3`) is a `checkpoint:human-verify` gate that explicitly defers live UI testing. The following items require a running backend + frontend session.

#### 1. Workflow Card — Visual and Behavioral

**Test:** Start backend (`cd backend && mvn spring-boot:run`, `SEED_ENABLED=true`), start frontend (`cd web && pnpm dev`), log in as `admin@lexcv.cv / admin123`. Open a processo in ATIVO state. Confirm the Workflow card shows: Estado badge (green ATIVO), a Responsável row (name or italic "Não atribuído"), a Próximo Passo row, and exactly the transitions defined by the backend for ATIVO (Suspender + Encerrar, both as outline/manage-level buttons).
**Expected:** Card renders these four elements; no hardcoded transition buttons present for states other than the current one.
**Why human:** Visual rendering and state-specific button set cannot be verified without a running app.

#### 2. Critical Transition Dialog — Justificativa Gate

**Test:** Click "Suspender" on an ATIVO processo. Confirm a Dialog opens with a Justificativa textarea. Submit with fewer than 10 characters. Then submit with a valid justificativa (≥10 chars).
**Expected:** Short submission shows inline red validation message "Justificativa deve ter pelo menos 10 caracteres". Valid submission changes estado to SUSPENSO (amber badge), records a movimentação of tipo TRANSICAO_ESTADO, and closes the Dialog.
**Why human:** RHF validation feedback and server round-trip (400 → 200 with estado change) require a live browser session.

#### 3. Permission Gate — manage-level transitions disabled for non-manage users

**Test:** Log in as a user with only `processos:edit` (not `processos:manage`). Navigate to an ATIVO processo.
**Expected:** Suspender and Encerrar buttons are disabled and show tooltip "Sem permissão: requer processos:manage". Ativar (edit-level, on a SUSPENSO processo) is enabled for this user.
**Why human:** Permission state requires a live auth session with a specific user role.

#### 4. Illegal Transition Rejected 409

**Test:** POST directly (via curl or devtools) to `/api/v1/processos/{id}/transicao/encerrar` where the processo is in TRIAGEM state.
**Expected:** Response is 409 CONFLICT with body `{ "message": "Transição 'encerrar' não é permitida no estado TRIAGEM" }`.
**Why human:** Requires an HTTP request to a running backend.

#### 5. Prazos List — Risk Badges and Toggle

**Test:** In the Prazos card, create a prazo with a past data_limite (any priority). Create another with data_limite in 2 days and prioridade MEDIA. Toggle concluido on the first prazo.
**Expected:** Past-date prazo shows red "PRAZO VENCIDO" badge. 2-day prazo shows amber "PRAZO PROXIMO" badge (threshold 3 days for MEDIA). Toggle flips icon immediately; after server response the badge remains correct (backend returns recomputed risco in PATCH /concluido).
**Why human:** Date-relative risco and optimistic UI update require a live browser session with real data.

#### 6. Listing Signals

**Test:** Go to http://localhost:3000/processos. Inspect the NÚMERO column and ESTADO column for processos with varying prazo states.
**Expected:** "Resp.:" sub-line appears for every row (name or "—"). Processos with proximo/vencido prazos show the PRAZO PROXIMO/PRAZO VENCIDO badge below the estado badge. Processos with only ok/no prazos show no risco badge. Escalonado AlertCircle appears when `tem_prazo_escalonado` is true.
**Why human:** Visual signal placement and conditional rendering require a live browser session with seeded data.

### Gaps Summary

No blocking gaps identified. All 6 must-have truths are verified in the codebase:
- Backend compile: clean (`mvn -q -DskipTests compile` exited 0).
- Frontend typecheck: clean (`pnpm exec tsc --noEmit` exited 0).
- State machine map, gates, risco helper, tenant scoping, and lib/prazos.ts single-source pattern are all confirmed present and correctly wired.

Status is `human_needed` exclusively because the plan's `Task 3` is a `checkpoint:human-verify` gate requiring a running app. All automated checks pass; no code changes are needed before human UAT.

---

_Verified: 2026-06-16_
_Verifier: Claude (gsd-verifier)_
