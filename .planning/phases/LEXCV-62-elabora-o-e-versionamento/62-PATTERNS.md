# Phase 62: Elaboração e Versionamento - Pattern Map

**Mapped:** 2026-06-30
**Files analyzed:** 3 (1 new entity, 1 new repository, 1 modified controller)
**Analogs found:** 3 / 3

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|---------------|
| `backend/src/main/java/com/lexcv/models/ParecerVersao.java` | model | CRUD (immutable/append-only) | `backend/src/main/java/com/lexcv/models/Documento.java` (anexo field) + `backend/src/main/java/com/lexcv/models/Cliente.java` (sequential numbering field) | role-match (composite) |
| `backend/src/main/java/com/lexcv/repositories/ParecerVersaoRepository.java` | model (repository) | CRUD | `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` (`findMaxNumeroSequencialByTenantId`) + `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (style) | exact (structure) |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (additions: nested `/versoes` endpoints) | controller | request-response + file-I/O (multipart upload, presigned download) | `ResourceController.java` `/processos/{id}/fases` (nesting) + `/documentos/upload` & `/documentos/{id}/download` (file-I/O) + `Cliente` `synchronized` sequential numbering block | exact (composite, multiple analogs combined) |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/ParecerVersao.java` (model, CRUD/append-only)

**Analogs:** `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` (entity skeleton/conventions, same module) + `backend/src/main/java/com/lexcv/models/Documento.java` (anexo field naming) + `backend/src/main/java/com/lexcv/models/Cliente.java` (sequential numbering field)

**Entity skeleton pattern** — copy from `ParecerSolicitacao.java` lines 1-22 (package, imports, Lombok annotations, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `tenantId` column):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_parecer_versao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParecerVersao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "solicitacao_id", nullable = false)
    private UUID solicitacaoId;
```

**Sequential numbering field pattern** — copy from `Cliente.java` lines 55-56 (`numeroSequencial` as plain `Integer` column, no DB sequence — computed at controller level):
```java
    @Column(name = "numero_sequencial")
    private Integer numeroSequencial;
```
Rename to `numeroVersao` for this entity per CONTEXT.md decision (`numero_versao` column).

**Anexo/file-reference field pattern** — copy from `Documento.java` lines 35-36 (`caminhoArquivo`) and 43-44 (`mimeType`):
```java
    @Column(name = "caminho_arquivo")
    private String caminhoArquivo;
```
Rename to `caminhoAnexo` (nullable) per CONTEXT.md decision (`caminho_anexo` column). Also consider mirroring `Documento.tamanho` (`Long`) and `mimeType` (`String`) if anexo metadata is needed, though CONTEXT.md only requires the path.

**Content field** — new pattern not present in analogs (CONTEXT.md decision): `TEXT`, nullable.
```java
    @Column(columnDefinition = "TEXT")
    private String conteudo;
```

**Timestamp pattern** — copy from `ParecerSolicitacao.java` lines 48-54 (`createdAt` + `@PrePersist`, no `updatedAt` since versions are immutable):
```java
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
```

**Author field** — new field needed (not in analogs): track which advogado created the version, analogous to `ParecerSolicitacao.advogadoId` (line 33-34):
```java
    @Column(name = "criado_por_id")
    private UUID criadoPorId;
```

---

### `backend/src/main/java/com/lexcv/repositories/ParecerVersaoRepository.java` (repository, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (full file, 11 lines) combined with `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` lines 17-18 (max-sequential query).

**Full pattern** — copy structure from `ParecerSolicitacaoRepository.java`:
```java
package com.lexcv.repositories;

import com.lexcv.models.ParecerVersao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParecerVersaoRepository extends JpaRepository<ParecerVersao, UUID> {
    List<ParecerVersao> findBySolicitacaoId(UUID solicitacaoId);

    @Query("SELECT MAX(v.numeroVersao) FROM ParecerVersao v WHERE v.solicitacaoId = :solicitacaoId")
    Optional<Integer> findMaxNumeroVersaoBySolicitacaoId(@Param("solicitacaoId") UUID solicitacaoId);
}
```
Note: the max-version query is scoped by `solicitacaoId`, not `tenantId` (versioning is per-solicitação, not per-tenant, per CONTEXT.md: `MAX(numeroVersao)+1` per solicitação). Tenant isolation for `ParecerVersao` records is enforced indirectly via the parent `ParecerSolicitacao.tenantId` check in the controller (same approach `ResourceController` uses for `ProcessoFase`, which has no own `tenantId` column).

---

### `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (additions — controller, request-response + file-I/O)

**Analogs:**
1. `ResourceController.java` lines 1508-1535 (`/processos/{id}/fases` nested GET — parent existence/tenant check then child list)
2. `ResourceController.java` lines 1537-1560 (`/processos/{id}/fases` nested POST — parent validated, child built referencing parent id)
3. `ResourceController.java` lines 220-234 (`Cliente` synchronized sequential-numbering block)
4. `ResourceController.java` lines 1949-2022 (`/documentos/upload` — multipart upload with optional file, `StorageService.upload`, error handling for `StorageUnavailableException`/`IOException`)
5. `ResourceController.java` lines 2040-2065 (`/documentos/{id}/download` — presigned URL response shape)
6. `ParecerController.java` itself (existing file) lines 32-71 (`getTenantId()`, `validateAdvogado()` helpers to reuse) and lines 73-122 (`@PreAuthorize` + validation + allowlist-construct pattern)

**Nested parent-resource validation pattern** — copy from `ResourceController.java` lines 1508-1514 (adapted to use `ParecerSolicitacaoRepository`):
```java
@PreAuthorize("hasAuthority('pareceres:view')")
@GetMapping("/{solicitacaoId}/versoes")
public ResponseEntity<?> listVersoes(@PathVariable UUID solicitacaoId) {
    ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
    if (solicitacao == null || !solicitacao.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
    }
    List<ParecerVersao> versoes = parecerVersaoRepository.findBySolicitacaoId(solicitacaoId);
    return ResponseEntity.ok(versoes);
}
```

**Responsible-advogado-or-ADMIN authorization (in addition to `@PreAuthorize`)** — new pattern combining `ParecerController.validateAdvogado()` (existing, lines 42-53) with a role check. Reuse `getTenantId()` (lines 32-36) and add an inline check in the create-version method body, mirroring the existing in-body validation style (see `atribuirAdvogado`, lines 182-221):
```java
@PreAuthorize("hasAuthority('pareceres:edit')")
@PostMapping(value = "/{solicitacaoId}/versoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> createVersao(
        @PathVariable UUID solicitacaoId,
        @RequestParam(value = "conteudo", required = false) String conteudo,
        @RequestParam(value = "file", required = false) MultipartFile file) {
    UUID tenantId = getTenantId();
    ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
    if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    boolean isResponsavel = solicitacao.getAdvogadoId() != null
            && solicitacao.getAdvogadoId().equals(principal.getUserId());
    if (!isAdmin && !isResponsavel) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Apenas o advogado responsável ou ADMIN pode criar uma versão"));
    }

    if ((conteudo == null || conteudo.isBlank()) && (file == null || file.isEmpty())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "É necessário fornecer conteúdo ou anexo"));
    }
    // ... continue with sequential numbering + optional upload below
}
```
(Verify exact `UserPrincipal`/authority-name accessors against `backend/src/main/java/com/lexcv/config/UserPrincipal.java` and `SecurityConfig.java` before finalizing — `ResourceController`/`ParecerController` do not currently perform an explicit ADMIN role check inline, so this is a new but consistent composition of existing pieces.)

**Sequential numbering block** — copy from `ResourceController.java` lines 228-234, adapted to scope by `solicitacaoId` instead of `tenantId` and use `ParecerVersaoRepository`:
```java
synchronized (ParecerVersaoRepository.class) {
    int maxVersao = parecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId(solicitacaoId).orElse(0);
    int nextVersao = maxVersao + 1;
    versao.setNumeroVersao(nextVersao);
}
```

**Optional file upload pattern** — copy from `ResourceController.java` lines 1992-1996 (new-document branch of `uploadDocumento`, simplified — no replace-id branch needed since versions are immutable/append-only):
```java
String objectKey = null;
if (file != null && !file.isEmpty()) {
    UUID versaoId = UUID.randomUUID();
    try {
        InputStream inputStream = file.getInputStream();
        objectKey = storageService.upload(tenantId, versaoId, file.getOriginalFilename(),
                inputStream, file.getContentType(), file.getSize());
    } catch (IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erro ao ler ficheiro"));
    } catch (StorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Storage service unavailable"));
    }
}
```
Note: `ResourceController` generates the entity id manually (`UUID.fromString(fileId)`) before saving so it can be used as the `documentoId` namespace segment in the object key; replicate this for `ParecerVersao` (pre-generate `versaoId` and pass to `storageService.upload(...)`, then `builder().id(versaoId)...`) since JPA `GenerationType.UUID` ids aren't available before `save()`.

**Presigned download endpoint** — copy from `ResourceController.java` lines 2040-2065 (response shape `{url, expiresIn}`, audit-log-before-response note is `documentos`-specific and likely not required here unless CONTEXT.md mandates an audit trail for parecer anexos — omit unless specified):
```java
@PreAuthorize("hasAuthority('pareceres:view')")
@GetMapping("/{solicitacaoId}/versoes/{versaoId}/anexo")
public ResponseEntity<?> downloadAnexo(@PathVariable UUID solicitacaoId, @PathVariable UUID versaoId) {
    UUID tenantId = getTenantId();
    ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
    if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
    }
    ParecerVersao versao = parecerVersaoRepository.findById(versaoId).orElse(null);
    if (versao == null || !versao.getSolicitacaoId().equals(solicitacaoId) || versao.getCaminhoAnexo() == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Anexo não encontrado"));
    }
    try {
        String url = storageService.presignedDownloadUrl(versao.getCaminhoAnexo());
        return ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600));
    } catch (StorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Storage service unavailable"));
    }
}
```

**GET single version (detail)** — copy from `ParecerController.java` lines 140-148 (`getSolicitacao`), adapted to also validate the parent solicitação:
```java
@PreAuthorize("hasAuthority('pareceres:view')")
@GetMapping("/{solicitacaoId}/versoes/{versaoId}")
public ResponseEntity<?> getVersao(@PathVariable UUID solicitacaoId, @PathVariable UUID versaoId) {
    ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
    if (solicitacao == null || !solicitacao.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
    }
    ParecerVersao versao = parecerVersaoRepository.findById(versaoId).orElse(null);
    if (versao == null || !versao.getSolicitacaoId().equals(solicitacaoId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Versão não encontrada"));
    }
    return ResponseEntity.ok(versao);
}
```

**Constructor injection additions** — `ParecerController` currently injects (lines 27-30) `ParecerSolicitacaoRepository`, `UserRepository`, `ClienteRepository`, `ProcessoRepository` via `@RequiredArgsConstructor`. Add `ParecerVersaoRepository` and `StorageService` (the latter already exists as a Spring `@Service` bean used by `ResourceController` — same injection style, just add as a new `private final` field).

---

## Shared Patterns

### Tenant Isolation (mandatory — CLAUDE.md cross-cutting concern)
**Source:** `ParecerController.java` lines 32-36 (`getTenantId()`), reused in every existing handler in this file and in `ResourceController.java`.
**Apply to:** All new `ParecerVersao` endpoints. Since `ParecerVersao` has no direct tenant scoping decision documented in CONTEXT.md, tenant isolation MUST be enforced transitively through the parent `ParecerSolicitacao.tenantId` check (same pattern `ResourceController` uses for `ProcessoFase`/`Parte`, which are scoped via their parent `Processo`). Confirm during planning whether `ParecerVersao` should also carry its own `tenant_id` column for defense-in-depth (recommended, consistent with `ParecerSolicitacao` and `Documento`, both of which duplicate `tenantId` even though reachable via a parent).

### Authorization (`@PreAuthorize` + scope convention)
**Source:** `ParecerController.java` lines 73, 124, 140, 150, 182 — `hasAuthority('pareceres:create' | 'view' | 'edit')`.
**Apply to:** New versioning endpoints use `pareceres:view` (GET list/detail/download) and `pareceres:edit` (POST create version), per CONTEXT.md decision. No new scope needed.

### File Upload / Storage
**Source:** `backend/src/main/java/com/lexcv/services/StorageService.java` (full file) — `upload(tenantId, entityId, filename, inputStream, contentType, size)` returns object key; `presignedDownloadUrl(objectKey)` returns a time-limited URL; `delete(objectKey)`.
**Apply to:** `ParecerVersao` anexo upload/download. Exceptions to catch: `StorageUnavailableException` (503) and `IOException` (500) — see `ResourceController.java` lines 2015-2021 and 2063-2065 for exact response bodies.

### Error Response Shape
**Source:** Consistent across `ParecerController.java` and `ResourceController.java`: `ResponseEntity.status(<code>).body(Map.of("message", "<msg>"))`.
**Apply to:** All new endpoints — do not introduce a different error shape.

## No Analog Found

None — all required sub-patterns (entity skeleton, sequential numbering, anexo field, multipart upload, presigned download, nested-resource endpoint, advogado-or-admin authorization) have direct or composable analogs in the existing codebase.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/models/`, `backend/src/main/java/com/lexcv/repositories/`, `backend/src/main/java/com/lexcv/controllers/`, `backend/src/main/java/com/lexcv/services/`
**Files scanned:** `ParecerSolicitacao.java`, `ParecerSolicitacaoRepository.java`, `ParecerController.java`, `Cliente.java`, `ClienteRepository.java`, `Documento.java`, `StorageService.java`, `ResourceController.java` (targeted sections: clientes create/sequential-numbering ~L190-280, processos/fases ~L1495-1600, documentos upload/download ~L1946-2090)
**Pattern extraction date:** 2026-06-30
