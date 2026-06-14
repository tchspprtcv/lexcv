---
phase: 32-processos-intake-e-conflict-check
verified: 2026-06-13T00:00:00Z
status: human_needed
score: 7/7 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Run the 3-step wizard end-to-end in a browser with SEED_ENABLED=true: create intake, execute conflict check, register decisao, and formalizar."
    expected: "Processo nasce em TRIAGEM; conflict check returns matches; decisao is persisted with the logged-in user as decisor; formalizar succeeds and transitions to ATIVO."
    why_human: "No running database or e2e harness. Backend blocking rules are verified by source but runtime integration (HTTP flow, cookie auth, JPA ddl-auto schema creation for t_conflict_check_decisao) requires manual smoke test."
  - test: "In the wizard Step 3, attempt to click Formalizar while no decisao has been registered."
    expected: "Button is disabled with opacity-50 cursor-not-allowed and red reason text 'Não é possível formalizar: o conflict check ainda não tem uma decisão registada.'"
    why_human: "Disabled state and reason message depend on live decisaoData state in React — not verifiable without running the app."
  - test: "Register a decisao with nivel=impeditivo on a TRIAGEM processo, then attempt POST /api/v1/processos/{id}/formalizar."
    expected: "Server returns 409 with message 'Não é possível formalizar: existe um conflito impeditivo registado'. Estado remains TRIAGEM."
    why_human: "Requires a running DB + seeded data; the server-side blocking path is confirmed in source but must be verified against a real HTTP response."
  - test: "Attempt POST /api/v1/processos/{id}/formalizar without filling mandatory minimum fields for the process type."
    expected: "Server returns 422 with camposEmFalta list. Order of enforcement: campos minimos checked before conflict decision."
    why_human: "Requires running server and a processo with deliberate missing fields."
  - test: "Verify the EM TRIAGEM badge and filter option in /processos listing with at least one TRIAGEM processo present."
    expected: "Badge renders as purple 'EM TRIAGEM'; selecting 'Em triagem' in the estado filter narrows the list to TRIAGEM processos only."
    why_human: "Visual rendering and filter behavior require a running frontend with live data."
---

# Phase 32: Processos Intake e Conflict Check — Verification Report

**Phase Goal:** Formalizar a abertura de processos com intake estruturado e conflict check bloqueante antes da criacao formal do matter/processo.
**Verified:** 2026-06-13
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Um processo criado pelo fluxo de intake nasce em estado TRIAGEM (nunca ATIVO) | VERIFIED | `ResourceController.createProcessoIntake` (line 644): `processo.setEstado("TRIAGEM")` hardcoded regardless of payload. Endpoint `@PostMapping("/processos/intake")` returns 201 with saved processo. |
| 2 | Correr o conflict check devolve matches (clientes + partes) e nivel sugerido, apenas dentro do tenant | VERIFIED | `runConflictCheck` at line 651: `UUID tenantId = getTenantId()` then tenant guard (line 654). All queries use `clienteRepository.findByTenantIdAndNif(tenantId, ...)`, `clienteRepository.findByTenantId(tenantId)`, and `processoRepository.findByTenantId(tenantId)`. Returns `ConflictCheckResponse(matches, nivelSugerido)` — 4 levels wired in enum: sem_conflito/potencial/sanavel/impeditivo. |
| 3 | A decisao e persistida com decisor (do SecurityContext), data, nivel, justificativa e referencia opcional | VERIFIED | `registarDecisaoConflito` (line 741): `decisorId = principal.getUserId()` from `SecurityContextHolder` (lines 767-769), never from payload. `dataDecisao = LocalDate.now()` (line 780). Upsert via `findByTenantIdAndProcessoId` (line 772-774). Entity `ConflictCheckDecisao` has all fields with correct nullability constraints. |
| 4 | A formalizacao (TRIAGEM->ATIVO) e rejeitada quando processo nao esta em TRIAGEM | VERIFIED | `formalizarProcesso` (line 795-800): pre-flight check `!"TRIAGEM".equalsIgnoreCase(processo.getEstado())` returns 409 before any other logic. |
| 5 | A formalizacao e rejeitada quando faltam campos minimos por tipo_processo (422 + camposEmFalta) — verificada ANTES da decisao | VERIFIED | `CAMPOS_MINIMOS_POR_TIPO` static Map (lines 62-70) with lowercase keys matching `.toLowerCase()` normalization at line 803. Validation loop (lines 807-817) collects all missing fields. Returns 422 with `camposEmFalta` list (lines 818-823) BEFORE the conflict decision check. |
| 6 | A formalizacao e rejeitada quando decisao ausente OU nivel impeditivo | VERIFIED | Lines 826-837: `findByTenantIdAndProcessoId` lookup; null -> 409 "ainda nao tem uma decisao registada"; `"impeditivo".equals(decisao.getNivel())` -> 409 "conflito impeditivo registado". Only after both pass does `setEstado("ATIVO")` execute (line 840). |
| 7 | RBAC: processos:create e processos:manage seeded; @PreAuthorize aplicado nos endpoints corretos | VERIFIED | `DatabaseSeeder.seedRbac()` (lines 294-297): both permissions in `permKeys`. ADVOGADO receives both (lines 335-336). ASSISTENTE (lines 313-319) and TECNICO (lines 321-329) receive neither processos:create nor processos:manage. Endpoints: intake+conflict-check have `@PreAuthorize("hasAuthority('processos:create')")`, decisao+formalizar have `@PreAuthorize("hasAuthority('processos:manage')")`, GET decisao has `@PreAuthorize("hasAuthority('processos:view')")`. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/ConflictCheckDecisao.java` | JPA entity tenant-scoped | VERIFIED | `@Entity @Table(name = "t_conflict_check_decisao")`, all required columns present with correct nullability, `@PrePersist onCreate()` sets `createdAt`. |
| `backend/src/main/java/com/lexcv/repositories/ConflictCheckDecisaoRepository.java` | Repo with findByTenantIdAndProcessoId | VERIFIED | Extends `JpaRepository<ConflictCheckDecisao, UUID>`, declares both `findByTenantId` and `findByTenantIdAndProcessoId`. |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | 5 endpoints + CAMPOS_MINIMOS_POR_TIPO | VERIFIED | All 5 endpoints present. CAMPOS_MINIMOS_POR_TIPO static Map with 6 type entries + "default" fallback. Dual-block enforcement in single formalizar method. |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` | processos:create and processos:manage seeded | VERIFIED | Both in permKeys; ADVOGADO gets both; ASSISTENTE/TECNICO do not. |
| `web/src/lib/conflict-check.ts` | Single source of truth nivel->variant/label | VERIFIED | Exports `conflictNivelToVariant` (sem_conflito->green, potencial->amber, sanavel->blue, impeditivo->red) and `conflictNivelToLabel` with the 4 Portuguese labels. |
| `web/src/types/processos.ts` | ConflictNivel, ConflictMatch, ConflictCheckResponse, ConflictCheckDecisao | VERIFIED | All 4 types present. `ConflictCheckDecisao` uses camelCase (`tenantId`, `processoId`, `createdAt`) aligning with Spring Boot Jackson default serialization. |
| `web/src/schemas/processos.ts` | conflictCheckDecisaoFormSchema with conditional justificativa | VERIFIED | `conflictNivelEnum` + `conflictCheckDecisaoFormSchema` with `superRefine` requiring justificativa when nivel is potencial or sanavel, emitting issue at path `["justificativa"]`. |
| `web/src/hooks/use-processos.ts` | 5 hooks for intake/conflict/decisao/formalizar | VERIFIED | All 5 hooks present: `useCreateIntake`, `useRunConflictCheck`, `useRegistarDecisaoConflito`, `useConflictCheckDecisao`, `useFormalizarProcesso`. Each calls the correct backend endpoint. |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | 3-step wizard consuming all hooks | VERIFIED | Step 1 destructures `estado` out of payload (`{ estado: _estado, ...intakeValues }`). Step 2 uses `useRunConflictCheck` + `useRegistarDecisaoConflito` + `conflictNivelToLabel`. Step 3 calls `useFormalizarProcesso`. Formalizar disabled when `!decisaoData || nivel==="impeditivo"` with red reason text. |
| `web/src/app/(dashboard)/processos/page.tsx` | EM TRIAGEM badge + filter | VERIFIED | `estadoVariant` switch: `TRIAGEM -> "purple"`. `estadoLabel`: `TRIAGEM -> "EM TRIAGEM"`. Filter `<select>` has `<option value="TRIAGEM">Em triagem</option>`. |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | Conflict Check section in detail | VERIFIED | `useConflictCheckDecisao(id)` + `useFormalizarProcesso(id)` imported and called. Card visible when `processo.data?.estado === "TRIAGEM" || decisao.data`. Badge uses `conflictNivelToVariant`/`conflictNivelToLabel`. Inline Formalizar gated by `canManageProcessos` + disabled+reason when blocked. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ResourceController.formalizarProcesso` | `ConflictCheckDecisaoRepository.findByTenantIdAndProcessoId` | decision lookup before setEstado("ATIVO") | WIRED | Line 826-827: `conflictCheckDecisaoRepository.findByTenantIdAndProcessoId(tenantId, id).orElse(null)` called at step (b), after campos minimos check, before any setEstado call. |
| `ResourceController.formalizarProcesso` | `CAMPOS_MINIMOS_POR_TIPO` | field validation before setEstado("ATIVO") | WIRED | Lines 803-823: `tipoProcesso.toLowerCase()` used as key, `getOrDefault(tipoProcesso, get("default"))` handles all types including unknown ones, 422 returned before any setEstado. |
| `ResourceController.runConflictCheck` | `clienteRepository.findByTenantId` / `findByTenantIdAndNif` | tenant-scoped match search | WIRED | Lines 673-700: NIF exact match via `findByTenantIdAndNif(tenantId, nifToSearch)`, name approximate match via `findByTenantId(tenantId)`. All filtered by `tenantId = getTenantId()`. |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | `useFormalizarProcesso` | Step 3 Formalizar button | WIRED | `formalizarProcesso.mutateAsync()` called in `onFormalizar` handler. On success `router.push`. Backend 4xx shown via `formalizarError` state. |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | `useRunConflictCheck` | Step 2 Executar Conflict Check button | WIRED | `runCheck.mutateAsync()` called in `onRunConflictCheck`, result stored in `conflictResult` state and rendered as match rows. |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | `useConflictCheckDecisao` | query for saved decision | WIRED | `decisao = useConflictCheckDecisao(id)` at line 89; `decisao.data` used to determine card visibility, badge, and Formalizar disabled state. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `ResourceController.runConflictCheck` | `matches` list | `clienteRepository.findByTenantId/findByTenantIdAndNif`, `parteRepository.findByProcessoId` | Yes — queries tenant-scoped DB tables | FLOWING |
| `ResourceController.getDecisaoConflito` | `ConflictCheckDecisao` entity | `conflictCheckDecisaoRepository.findByTenantIdAndProcessoId` | Yes — returns real entity or null | FLOWING |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | `conflictResult` | `useRunConflictCheck.mutateAsync()` -> apiFetch -> backend | Yes — populated by real API response | FLOWING |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | `decisaoData` | `useRegistarDecisaoConflito.mutateAsync()` -> apiFetch -> saved entity | Yes — set from API response `saved.dataDecisao` | FLOWING |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | `decisao.data` | `useConflictCheckDecisao(id)` -> GET /conflict-check/decisao | Yes — TanStack Query hydrates from API | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with all new classes | `cd backend && mvn -DskipTests compile` | BUILD SUCCESS | PASS |
| Frontend type-checks with all new types/hooks | `cd web && pnpm exec tsc --noEmit` | No errors (0 output) | PASS |
| `CAMPOS_MINIMOS_POR_TIPO` has "default" fallback | grep in ResourceController.java | Line 69: `"default", List.of(...)` present | PASS |
| `registarDecisaoConflito` derives decisorId from SecurityContext, not payload | grep ResourceController.java | Lines 767-769: `SecurityContextHolder.getContext().getAuthentication()` -> `principal.getUserId()` | PASS |
| ConflictCheckDecisaoRequest does NOT include decisorId field | Read ConflictCheckDecisaoRequest.java (via hooks) | `ConflictCheckDecisaoRequest` interface: `nivel`, `justificativa?`, `referenciaEvidencia?` only | PASS |

### Probe Execution

Step 7c: SKIPPED — no probe scripts defined for this phase. No `scripts/*/tests/probe-*.sh` found.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| INT-01 | 32-01, 32-02, 32-03 | Intake estruturado com campos minimos por tipo de processo antes da abertura formal | SATISFIED | `POST /processos/intake` forces TRIAGEM; `CAMPOS_MINIMOS_POR_TIPO` validated at formalizar; wizard provides 3-step structured intake UI. |
| CFL-01 | 32-01, 32-02, 32-03 | Conflict check estruturado; abertura bloqueada ate decisao registada | SATISFIED | `runConflictCheck` returns tenant-scoped matches; `formalizarProcesso` blocks (409) if no decisao or nivel=impeditivo; frontend reflects blocking with disabled state + reason. |

Both requirements INT-01 and CFL-01 are declared complete in REQUIREMENTS.md traceability at Phase 32. Code evidence confirms the implementation.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None found | — | — | — | — |

Scanned: ResourceController.java, ConflictCheckDecisao.java, DatabaseSeeder.java, conflict-check.ts, use-processos.ts, novo/page.tsx, page.tsx (processos), [id]/page.tsx. No TBD/FIXME/XXX/TODO/PLACEHOLDER markers found. No empty implementations. No hardcoded empty returns in endpoints.

**Notable observation (not a blocker):** The frontend wizard sends `tipo_processo: "outro"` as an option in the dropdown but `CAMPOS_MINIMOS_POR_TIPO` has no "outro" key. The backend falls back to the "default" entry via `getOrDefault(tipoProcesso, get("default"))`. This is correct design — "default" is the documented fallback.

### Human Verification Required

### 1. End-to-end intake wizard smoke test

**Test:** Start from /processos/novo as an ADVOGADO user. Complete Step 1 (fill all intake fields, submit). Check the created processo in DB has estado=TRIAGEM. Run Step 2 conflict check, observe match rows (or empty state). Register a decisao with nivel=sem_conflito. Proceed to Step 3, click "Formalizar Processo".
**Expected:** Processo transitions to ATIVO. Browser navigates to /processos/{id}. Detail page shows the conflict check section with the registered decisao.
**Why human:** Requires running DB + seeded ADVOGADO user + real HTTP cookie auth. The JPA ddl-auto=update table creation for t_conflict_check_decisao also needs to be confirmed.

### 2. Impeditivo blocking — UI and backend

**Test:** Register a decisao with nivel=impeditivo on a TRIAGEM processo. Observe Step 3 (wizard) and the detail page Conflict Check section.
**Expected:** "Formalizar Processo" button disabled with `opacity-50 cursor-not-allowed` and red text "Não é possível formalizar: existe um conflito impeditivo registado." Any direct POST to /formalizar returns 409 with the same message.
**Why human:** Real-time React state rendering + actual backend 409 response require a live environment.

### 3. Missing campos minimos blocking

**Test:** Create an intake processo for tipo=civel without filling `tribunal` or `numeroProcesso`. Then POST /api/v1/processos/{id}/formalizar (even with a valid decisao).
**Expected:** 422 response with `camposEmFalta: ["numeroProcesso", "tribunal"]` (exact field names). setEstado("ATIVO") never executes.
**Why human:** Requires DB + runnable server to confirm the HTTP response body including camposEmFalta array content.

### 4. EM TRIAGEM badge and filter (listing)

**Test:** With at least one processo in TRIAGEM state visible in the listing, observe the badge. Use the estado dropdown to select "Em triagem".
**Expected:** Badge renders as purple "EM TRIAGEM". Filter narrows list to TRIAGEM processos only (backend already supports estado filter via existing query params).
**Why human:** Visual badge rendering and filter behavior require a running frontend with live data.

### 5. Conflict check cross-tenant isolation

**Test:** With two tenant accounts seeded, run conflict check on a processo in tenant A. Verify no client or parte from tenant B appears in the matches.
**Expected:** Zero cross-tenant leakage in matches list. All matches belong to tenant A only.
**Why human:** Multi-tenant isolation requires seeded data in multiple tenants and live query execution.

### Gaps Summary

No gaps found. All 7 must-have truths are VERIFIED in source code. The phase goal is achieved in the codebase. The outstanding items are human verification requirements that cannot be confirmed without a running environment with a seeded database.

---

_Verified: 2026-06-13T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
