# Phase 64: Auditoria e Pesquisa Avançada - Pattern Map

**Mapped:** 2026-06-30
**Files analyzed:** 2 (both modified, no new files)
**Analogs found:** 2 / 2

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|---------------|
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (5 audit calls + new `/pesquisa` endpoint) | controller | event-driven (audit writes) + request-response (search) | `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | exact (same audit pattern, same controller family) |
| `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (new search query method) | repository | CRUD / query (JPQL JOIN + optional filters) | `backend/src/main/java/com/lexcv/repositories/UserRepository.java` (JOIN pattern) — no existing "optional param" analog exists in codebase | role-match (JOIN syntax only; optional-param idiom is new to codebase, use RESEARCH/CONTEXT-specified idiom) |

## Pattern Assignments

### `ParecerController.java` — audit log calls (event-driven writes)

**Analog:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`

**Field/imports pattern** (ResourceController.java, field declaration only — imports are `com.lexcv.models.AuditLog` and `com.lexcv.repositories.AuditLogRepository`):
```java
private final AuditLogRepository auditLogRepository;
```
`ParecerController.java` currently has NO `AuditLogRepository` field — it must be added to the constructor-injected field list (lines 37-42) alongside the existing repositories, and the two imports added to the import block (lines 3-13).

**Core audit-write pattern — `transicao_estado`** (ResourceController.java lines 1314-1322):
```java
// Audit record — T-34-04: autor_id from SecurityContext, never from request payload
auditLogRepository.save(AuditLog.builder()
        .tenantId(tenantId)
        .processoId(id)
        .acao("transicao_estado")
        .entidadeTipo("processo")
        .entidadeId(id.toString())
        .autorId(principal.getUserId())
        .build());
```

**Core audit-write pattern — `documento_download`** (ResourceController.java lines 2051-2058):
```java
auditLogRepository.save(AuditLog.builder()
        .tenantId(dlPrincipal.getTenantId())
        .processoId(doc.getProcessoId()) // nullable — documento may not be linked to a processo
        .acao("documento_download")
        .entidadeTipo("documento")
        .entidadeId(id.toString())
        .autorId(dlPrincipal.getUserId())
        .build());
```

**Core audit-write pattern — `conflict_check_decisao`** (ResourceController.java lines 1133-1141):
```java
// Audit record — T-34-03: record before response is returned
auditLogRepository.save(AuditLog.builder()
        .tenantId(tenantId)
        .processoId(id)
        .acao("conflict_check_decisao")
        .entidadeTipo("conflict_check_decisao")
        .entidadeId(saved.getId().toString())
        .autorId(decisorId)
        .build());
```

**Applying this pattern to the 5 Parecer transition endpoints (per CONTEXT.md decisions):**

| Endpoint (ParecerController.java) | `acao` | `entidadeTipo` | `entidadeId` | `processoId` |
|---|---|---|---|---|
| `createSolicitacao` (line ~132, after `parecerSolicitacaoRepository.save(solicitacao)`) | `parecer_criar` | `parecer_solicitacao` | `saved.getId().toString()` | `null` (pareceres not linked to Processo for audit) |
| `atribuirAdvogado` (line ~232, after save) | `parecer_atribuir` | `parecer_solicitacao` | `solicitacao.getId().toString()` | `null` |
| `createVersao` (line ~401, after `saved = parecerVersaoRepository.save(versao)`) | `parecer_versao_criar` | `parecer_versao` | `saved.getId().toString()` | `null` |
| `aprovarVersao` (line ~267, after `parecerVersaoRepository.save(versao)`) | `parecer_aprovar` | `parecer_versao` | `versao.getId().toString()` | `null` |
| `entregarSolicitacao` (line ~302, after `parecerSolicitacaoRepository.save(solicitacao)`) | `parecer_entregar` | `parecer_solicitacao` | `solicitacao.getId().toString()` | `null` |

`autorId` in every case: `UserPrincipal.getUserId()` obtained from `SecurityContextHolder.getContext().getAuthentication()` — note `createSolicitacao` and `atribuirAdvogado` currently do NOT extract `Authentication`/`UserPrincipal` in their method bodies (unlike `aprovarVersao`/`entregarSolicitacao`/`createVersao`, which already do via lines 254-255, 289-290, 344-345). Those two endpoints need the same two-line extraction added:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
```
(pattern already present in `getTenantId()` at lines 44-48 of the same file, and reused verbatim at lines 254-255, 289-290, 344-345).

**Placement convention:** audit `save()` call goes immediately after the primary entity `save()`/mutation and before `return ResponseEntity...`, matching the comment convention `// Audit record — <ticket-ref>: <rationale>` seen in ResourceController.java lines 1133 and 1314.

---

### `ParecerController.java` — new `GET /api/v1/pareceres/pesquisa` endpoint (request-response, search)

**Analog for endpoint shape:** `listSolicitacoes` in ParecerController.java itself (lines 136-150) — same controller, same `@PreAuthorize("hasAuthority('pareceres:view')")` scope, same `@RequestParam(required = false)` style for optional filters:
```java
@PreAuthorize("hasAuthority('pareceres:view')")
@GetMapping("")
public ResponseEntity<?> listSolicitacoes(
        @RequestParam(required = false) UUID clienteId,
        @RequestParam(required = false) UUID advogadoId,
        @RequestParam(required = false) String status
) {
    UUID tenantId = getTenantId();
    List<ParecerSolicitacao> result = parecerSolicitacaoRepository.findByTenantId(tenantId).stream()
            .filter(p -> clienteId == null || clienteId.equals(p.getClienteId()))
            .filter(p -> advogadoId == null || advogadoId.equals(p.getAdvogadoId()))
            .filter(p -> status == null || status.isBlank() || status.equals(p.getStatus()))
            .toList();
    return ResponseEntity.ok(result);
}
```
**Note:** CONTEXT.md explicitly decided the new `/pesquisa` endpoint must delegate filtering to a JPQL `@Query` (JOIN + LIKE) rather than stream-filtering like `listSolicitacoes` does — the stream approach above is the *shape* to copy (params, `@PreAuthorize`, `getTenantId()`, `ResponseEntity.ok(result)`), not the filtering mechanism. Add `dataInicio`/`dataFim` as `@RequestParam(required = false) LocalDateTime` (or `LocalDate` depending on `createdAt` type) and `texto` as `@RequestParam(required = false) String`, then call the new repository method instead of stream filters.

Route note: this endpoint's path is `/api/v1/pareceres/pesquisa`, which does NOT match the controller's existing `@RequestMapping("/api/v1/pareceres/solicitacoes")` base path — it needs its own `@GetMapping("/api/v1/pareceres/pesquisa")` with a full path override (Spring allows absolute paths in `@GetMapping` to bypass the class-level `@RequestMapping` prefix), or the endpoint should live in a second small controller. Given ParecerController.java is already the sole controller for this domain (per Fases 61-63), the pragmatic choice is an absolute-path `@GetMapping` on the same class.

---

### `ParecerSolicitacaoRepository.java` — new search query method

**Analog for JOIN JPQL syntax:** `backend/src/main/java/com/lexcv/repositories/UserRepository.java` lines 16-20:
```java
@Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName")
List<User> findByTenantIdAndRoleName(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);
```
This is the only existing JOIN-based `@Query` in the codebase and establishes: `@Query` + `@Param`-annotated method params, JPQL entity-alias JOIN syntax (`JOIN u.roles r`), imports `org.springframework.data.jpa.repository.Query` and `org.springframework.data.repository.query.Param`.

**No existing analog for optional-parameter `(:param IS NULL OR ...)` idiom** — this idiom is not present anywhere in the current repository layer (confirmed via codebase-wide search for `IS NULL OR` and `@Query`, only 5 `@Query` methods exist total, none with optional params). CONTEXT.md's `## Established Patterns` section prescribes the idiom directly: `(:param IS NULL OR campo = :param)`. Construct the new method using this idiom combined with the JOIN syntax from `UserRepository`, e.g. (illustrative, not final):
```java
@Query("SELECT DISTINCT s FROM ParecerSolicitacao s JOIN ParecerVersao v ON v.solicitacaoId = s.id " +
       "WHERE s.tenantId = :tenantId " +
       "AND (:clienteId IS NULL OR s.clienteId = :clienteId) " +
       "AND (:advogadoId IS NULL OR s.advogadoId = :advogadoId) " +
       "AND (:status IS NULL OR s.status = :status) " +
       "AND (:dataInicio IS NULL OR s.createdAt >= :dataInicio) " +
       "AND (:dataFim IS NULL OR s.createdAt <= :dataFim) " +
       "AND (:texto IS NULL OR v.conteudo ILIKE CONCAT('%', :texto, '%'))")
List<ParecerSolicitacao> pesquisar(@Param("tenantId") UUID tenantId,
                                    @Param("texto") String texto,
                                    @Param("clienteId") UUID clienteId,
                                    @Param("advogadoId") UUID advogadoId,
                                    @Param("status") String status,
                                    @Param("dataInicio") LocalDateTime dataInicio,
                                    @Param("dataFim") LocalDateTime dataFim);
```
Caveats the planner must resolve (not resolved by any existing analog, flag explicitly in the plan):
- CONTEXT.md scopes the text search to "só a versão mais recente de cada solicitação" — the naive JOIN above matches against ALL versions' `conteudo`, which would violate that scope. The query needs either a subquery restricting to `MAX(numeroVersao)` per `solicitacaoId` (see `ParecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId`, lines 13-14, for the existing max-version aggregate pattern) or an equivalent correlated condition.
- `ILIKE` is Postgres-specific and not part of standard JPQL — likely needs a native query (`@Query(value = "...", nativeQuery = true)`) rather than JPQL, or `LOWER(v.conteudo) LIKE LOWER(CONCAT(...))` if staying in JPQL to remain portable. CONTEXT.md explicitly says "ILIKE nativo do Postgres", implying `nativeQuery = true` is likely required — no existing native `@Query` example exists in the codebase to copy from (all 5 existing `@Query` methods are JPQL, not native SQL), so this is a genuinely new pattern for the codebase.

**Existing max-version aggregate pattern to reuse for the "latest version only" subquery** (`ParecerVersaoRepository.java` lines 13-14):
```java
@Query("SELECT MAX(v.numeroVersao) FROM ParecerVersao v WHERE v.solicitacaoId = :solicitacaoId")
Optional<Integer> findMaxNumeroVersaoBySolicitacaoId(@Param("solicitacaoId") UUID solicitacaoId);
```

---

## Shared Patterns

### Tenant scoping (applies to both the audit writes and the search query)
**Source:** `ParecerController.java` lines 44-48 (`getTenantId()`), reused throughout the file and throughout `ResourceController.java`.
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```
Apply to: the new `/pesquisa` endpoint (pass `tenantId` into the repository query, per CONTEXT.md "tenant-scoped") and every audit-write call site (pass `tenantId` into `AuditLog.builder().tenantId(...)`).

### Authenticated-user extraction for `autorId`
**Source:** `ParecerController.java` lines 254-255, 289-290, 344-345 (already present in `aprovarVersao`, `entregarSolicitacao`, `createVersao`); same idiom in `ResourceController.java` lines 2049-2050.
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
```
Apply to: `createSolicitacao` and `atribuirAdvogado`, which currently lack this extraction and need it added to populate `autorId` on their new audit records.

### `@PreAuthorize` scope convention
**Source:** every endpoint in `ParecerController.java` (lines 85, 136, 152, 162, 194, 235, 270, 305, 316, 331, 404).
```java
@PreAuthorize("hasAuthority('pareceres:view')")
```
Apply to: new `/pesquisa` endpoint, per CONTEXT.md decision — same scope as `listSolicitacoes` ("pesquisa é uma variante de leitura").

### Empty-result convention
**Source:** `listSolicitacoes` (ParecerController.java line 149) returns `ResponseEntity.ok(result)` even when `result` is an empty list — no 404 branch.
Apply to: new `/pesquisa` endpoint, per CONTEXT.md decision "Resultado vazio: retorna lista vazia (200 OK), não 404".

## No Analog Found

| File/Pattern | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Optional-parameter JPQL idiom `(:param IS NULL OR ...)` | repository query | request-response | Not present anywhere in current repository layer; must be introduced fresh per CONTEXT.md's explicit prescription (see Established Patterns section of CONTEXT.md) |
| Native/`ILIKE` `@Query` | repository query | request-response | No native SQL `@Query` exists in the codebase; all 5 existing `@Query` methods are JPQL. Postgres `ILIKE` likely requires `nativeQuery = true`, which is new territory — planner should flag this as needing careful review/testing (dialect portability, SQL injection surface even with bind params, `DISTINCT`/latest-version subquery correctness) |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/`, `backend/src/main/java/com/lexcv/repositories/`, `backend/src/main/java/com/lexcv/models/AuditLog.java`
**Files scanned:** `ParecerController.java`, `ParecerSolicitacaoRepository.java`, `AuditLog.java`, `AuditLogRepository.java`, `ResourceController.java` (grep + targeted reads), `ClienteRepository.java`, `ParecerVersaoRepository.java`, `SystemSettingRepository.java`, `UserRepository.java`
**Pattern extraction date:** 2026-06-30
