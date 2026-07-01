# Phase 63: Aprovação e Entrega - Pattern Map

**Mapped:** 2026-06-30
**Files analyzed:** 3 (1 controller modification, 2 model modifications)
**Analogs found:** 3 / 3 (all analogs are within the same files being modified — internal-pattern reuse)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (add `/aprovar`, `/entregar`) | controller | request-response (state transition) | same file: `atribuirAdvogado` (`/{id}/atribuir`, lines 193-232) | exact |
| `backend/src/main/java/com/lexcv/models/ParecerVersao.java` (add `aprovado`/`aprovadoPorId`/`aprovadoEm`) | model | CRUD (entity field addition) | same file: existing `criadoPorId`/`createdAt` audit fields (lines 36-45) | exact |
| `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` (add `versaoFinalId`) | model | CRUD (entity field addition) | same file: existing `advogadoId` nullable FK-style UUID field (line 33-34) | exact |

No new repository methods are strictly required (`findById` is inherited from `JpaRepository` on both `ParecerVersaoRepository` and `ParecerSolicitacaoRepository`), but the section below documents the existing repository shape for reference.

## Pattern Assignments

### `ParecerController.java` — `aprovar` endpoint (controller, request-response)

**Analog:** `atribuirAdvogado` in the same file, `backend/src/main/java/com/lexcv/controllers/ParecerController.java` lines 193-232.

**Imports already present** (file lines 1-28) — no new imports needed beyond what's already imported (`PreAuthorize`, `ResponseEntity`, `HttpStatus`, `Map`, `UUID`, `Authentication`/`SecurityContextHolder`/`UserPrincipal` for the actor's id, `LocalDateTime` will need to be added if not already imported — currently `ParecerController.java` has no `java.time` import, so add `import java.time.LocalDateTime;`).

**Method signature + auth pattern** (mirrors lines 193-196):
```java
@PreAuthorize("hasAuthority('pareceres:manage')")
@PutMapping("/{id}/versoes/{versaoId}/aprovar")
public ResponseEntity<?> aprovarVersao(@PathVariable UUID id, @PathVariable UUID versaoId) {
    UUID tenantId = getTenantId();
```
Note: CONTEXT.md decision specifies scope `pareceres:manage` (ADMIN-only), not `pareceres:edit` as used by `atribuir` — this is the one deviation from the direct analog and must be applied.

**Lookup + tenant-scoping pattern** (mirrors lines 212-215, repeated with `versao` lookup mirroring `getVersao` lines 245-258):
```java
ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
}

ParecerVersao versao = parecerVersaoRepository.findById(versaoId).orElse(null);
if (versao == null || !versao.getSolicitacaoId().equals(id)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Versão não encontrada"));
}
```

**CONCLUIDO-blocking guard** (copy verbatim pattern from lines 217-220):
```java
if ("CONCLUIDO".equals(solicitacao.getStatus())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "Não é possível aprovar uma versão de um parecer concluído"));
}
```

**Actor id extraction** (mirrors `createVersao`, lines 273-275):
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
```

**State mutation + conditional status transition** (mirrors lines 228-231, with CONTEXT.md's conditional-only-if-PENDENTE/EM_ELABORACAO rule):
```java
versao.setAprovado(true);
versao.setAprovadoPorId(principal.getUserId());
versao.setAprovadoEm(LocalDateTime.now());
parecerVersaoRepository.save(versao);

if ("PENDENTE".equals(solicitacao.getStatus()) || "EM_ELABORACAO".equals(solicitacao.getStatus())) {
    solicitacao.setStatus("EM_REVISAO");
    parecerSolicitacaoRepository.save(solicitacao);
}

return ResponseEntity.ok(versao);
```

---

### `ParecerController.java` — `entregar` endpoint (controller, request-response)

**Analog:** Same as above (`atribuirAdvogado`), plus request-param handling pattern from other `@RequestParam` usages in `listSolicitacoes` (lines 137-141) and `createVersao` (lines 264-266).

**Method signature** — CONTEXT.md specifies a query param `versaoFinalId`, matching the `@RequestParam` style already used elsewhere in the controller:
```java
@PreAuthorize("hasAuthority('pareceres:manage')")
@PutMapping("/{id}/entregar")
public ResponseEntity<?> entregarSolicitacao(@PathVariable UUID id, @RequestParam UUID versaoFinalId) {
    UUID tenantId = getTenantId();
```

**Lookup + tenant scoping** — identical pattern to above (solicitacao + versao lookups, with the versao's `solicitacaoId` validated against `id`).

**CONCLUIDO-blocking guard** — copy same guard text/structure as `/aprovar`, message adapted: `"Parecer já foi entregue"`.

**Authorization: advogado responsável OR ADMIN** (copy verbatim from `createVersao`, lines 273-281):
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
boolean isAdmin = principal.getRoles().contains("ADMIN");
boolean isResponsavel = solicitacao.getAdvogadoId() != null
        && solicitacao.getAdvogadoId().equals(principal.getUserId());
if (!isAdmin && !isResponsavel) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", "Apenas o advogado responsável ou ADMIN pode entregar o parecer"));
}
```

**State mutation** (sets `versaoFinalId` + `status = CONCLUIDO`, mirrors the `solicitacao.set...(); ...save();` shape at lines 228-231):
```java
solicitacao.setVersaoFinalId(versaoFinalId);
solicitacao.setStatus("CONCLUIDO");

return ResponseEntity.ok(parecerSolicitacaoRepository.save(solicitacao));
```

---

### `ParecerVersao.java` — add `aprovado`/`aprovadoPorId`/`aprovadoEm` fields (model)

**Analog:** Existing `criadoPorId`/`createdAt` audit-field pair in the same file, `backend/src/main/java/com/lexcv/models/ParecerVersao.java` lines 36-45.

**Field declaration pattern to copy** (mirrors lines 36-40, same `@Column(name = "...")` snake_case convention):
```java
@Column(nullable = false)
@Builder.Default
private Boolean aprovado = false;

@Column(name = "aprovado_por_id")
private UUID aprovadoPorId;

@Column(name = "aprovado_em")
private LocalDateTime aprovadoEm;
```
Note: unlike `createdAt`, `aprovadoEm` is NOT set via `@PrePersist` — it stays `null` until the `/aprovar` endpoint sets it explicitly (mirrors how `criadoPorId` is set explicitly by the controller at creation, not auto-populated). No `@PrePersist` changes needed; the existing `onCreate()` (lines 42-45) is left untouched.

`LocalDateTime` import is already present in this file (line 5) — no import changes required here.

---

### `ParecerSolicitacao.java` — add `versaoFinalId` field (model)

**Analog:** Existing `advogadoId` nullable UUID reference field in the same file, `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` lines 33-34.

**Field declaration pattern to copy** (mirrors line 33-34 exactly — nullable, no FK constraint, snake_case column name):
```java
@Column(name = "versao_final_id")
private UUID versaoFinalId;
```
Placement: add near other optional reference fields (after `advogadoId`, before `prioridade`), consistent with the file's existing grouping of nullable reference columns before status/business fields.

No import changes required (`UUID` already imported, line 7).

---

## Shared Patterns

### Tenant-scoping + 404 pattern
**Source:** `ParecerController.java`, repeated at lines 154-158 (`getSolicitacao`), 166-168 (`updateSolicitacao`), 213-215 (`atribuirAdvogado`), 239-241 / 250-252 (`listVersoes`/`getVersao`).
**Apply to:** Both new endpoints (`/aprovar`, `/entregar`) — always look up by id, check `null` and `tenantId` equality before any other logic, return 404 with `Map.of("message", "Solicitação não encontrada")` (or `"Versão não encontrada"` for the versao-level lookup).

### CONCLUIDO-blocking guard
**Source:** `ParecerController.java` lines 217-220 (`atribuirAdvogado`).
**Apply to:** `/entregar` (must reject if already `CONCLUIDO`) and `/aprovar` (CONTEXT.md implies approving a version of an already-delivered parecer should also be blocked, consistent with "Bloqueio de transição em CONCLUIDO... replicar a mesma guarda para qualquer escrita pós-entrega").
```java
if ("CONCLUIDO".equals(solicitacao.getStatus())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "<contextual message>"));
}
```

### Actor identification via UserPrincipal
**Source:** `ParecerController.java` lines 273-275 (`createVersao`).
**Apply to:** `/aprovar` (for `aprovadoPorId`) and `/entregar` (for the advogado-responsável-or-ADMIN authorization check).
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
```

### Advogado-responsável-or-ADMIN authorization
**Source:** `ParecerController.java` lines 276-281 (`createVersao`).
**Apply to:** `/entregar` only (per CONTEXT.md: "Quem pode entregar: advogado responsável ou ADMIN"). `/aprovar` instead uses a stricter class-level `@PreAuthorize("hasAuthority('pareceres:manage')")` since CONTEXT.md restricts approval to ADMIN only (the `pareceres:manage` scope is seeded only for ADMIN per Phase 61 — no extra in-method role check needed for `/aprovar` beyond the `@PreAuthorize` annotation).

### Explicit-allowlist state mutation (never trust request body for state fields)
**Source:** `ParecerController.java` lines 110-129 (`createSolicitacao`) and lines 179-190 (`updateSolicitacao`, note the comment at lines 187-188 explicitly excluding `status`/`advogadoId` from body-driven updates).
**Apply to:** Both new endpoints — `status`, `versaoFinalId`, `aprovado`, `aprovadoPorId`, `aprovadoEm` must be set programmatically by the controller, never bound directly from `@RequestBody`. `/entregar` takes `versaoFinalId` as a validated `@RequestParam UUID`, not from a request body DTO — this avoids the mass-assignment risk already guarded against in `updateSolicitacao`.

## No Analog Found

None. All three modified files have direct, exact-match analogs within their own current content (Phases 61-62 already established the controller's state-transition and audit-field conventions).

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`, `backend/src/main/java/com/lexcv/models/ParecerVersao.java`, `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java`, `backend/src/main/java/com/lexcv/repositories/ParecerVersaoRepository.java`, `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`
**Files scanned:** 5 (all fully read, no large-file truncation needed — largest file is 355 lines)
**Pattern extraction date:** 2026-06-30
