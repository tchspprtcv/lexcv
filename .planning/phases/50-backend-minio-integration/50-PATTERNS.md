# Phase 50: Backend MinIO Integration - Pattern Map

**Mapped:** 2026-06-19
**Files analyzed:** 7
**Analogs found:** 6 / 7

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/pom.xml` | config | — | `backend/pom.xml` (existing) | exact (self-modification) |
| `backend/src/main/java/com/lexcv/config/MinioProperties.java` | config | — | `backend/src/main/java/com/lexcv/config/SecurityConfig.java` (`@Value` pattern) | role-match |
| `backend/src/main/java/com/lexcv/config/MinioConfig.java` | config | — | `backend/src/main/java/com/lexcv/config/SecurityConfig.java` (`@Configuration` + `@Bean`) | exact |
| `backend/src/main/java/com/lexcv/services/StorageService.java` | service | file-I/O | `backend/src/main/java/com/lexcv/services/SetupService.java` | role-match |
| `backend/src/main/resources/application.yml` | config | — | `backend/src/main/resources/application.yml` (existing) | exact (self-modification) |
| `backend/.env.example` | config | — | `backend/.env.example` (existing) | exact (self-modification) |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | controller | file-I/O | `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (lines 1726–1879) | exact (self-modification) |

---

## Pattern Assignments

### `backend/pom.xml` (config — dependency management)

**Analog:** `backend/pom.xml` (self)

**Existing `<dependencyManagement>` block:** There is no `<dependencyManagement>` section yet. Add one before `<dependencies>`. The AWS SDK BOM must go there so that `<s3>` and `<url-connection-client>` children omit `<version>`.

**Existing `<properties>` block** (lines 19–22):
```xml
<properties>
    <java.version>23</java.version>
    <jjwt.version>0.12.5</jjwt.version>
</properties>
```
No AWS SDK version property needed — the BOM manages it. Add a `<awssdk.version>2.46.14</awssdk.version>` property here for clarity, then reference it in the BOM.

**Existing optional-dep pattern** (lines 52–55 — Lombok):
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```
Copy this `<optional>true</optional>` pattern for `spring-boot-configuration-processor`.

**Existing runtime-scope pattern** (lines 44–48 — PostgreSQL):
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```
No additional scopes needed for AWS SDK deps — default (compile) scope is correct.

**Changes to make:**
1. Add `<awssdk.version>2.46.14</awssdk.version>` to `<properties>`.
2. Add a new `<dependencyManagement>` section (before `<dependencies>`) importing the BOM.
3. Add three `<dependency>` entries inside `<dependencies>`: `s3`, `url-connection-client`, and `spring-boot-configuration-processor` (optional).

---

### `backend/src/main/java/com/lexcv/config/MinioProperties.java` (config — @ConfigurationProperties)

**Analog:** `backend/src/main/java/com/lexcv/config/SecurityConfig.java` (existing `@Value` config injection)

There is no existing `@ConfigurationProperties` class in the project. The closest pattern is `SecurityConfig` using `@Value("${app.cors.allowed-origins}")` on a field (line 32). `MinioProperties` replaces this per-field binding with a grouped class. The Spring Boot validation pattern comes from RESEARCH.md since there is no existing `@ConfigurationProperties` analog.

**Package and import pattern** (from `SecurityConfig.java` lines 1–19):
```java
package com.lexcv.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
```

**Core `@ConfigurationProperties` class pattern** — no existing analog; use RESEARCH.md pattern:
```java
@ConfigurationProperties(prefix = "minio")
@Validated
@Data
public class MinioProperties {
    @NotBlank private String endpoint;
    @NotBlank private String accessKey;
    @NotBlank private String secretKey;
    @NotBlank private String bucketName;
    private long presignedUrlExpiry = 3600L;
}
```
Notes:
- Use Lombok `@Data` (consistent with other model/config classes in the project — see `SetupService.java` which depends on `@Data`-annotated entities).
- `@Validated` triggers Spring Boot validation on startup so a missing env var throws a clear error.
- No constructor needed: Spring Boot binds by setter (Lombok `@Data` generates them).

---

### `backend/src/main/java/com/lexcv/config/MinioConfig.java` (config — @Bean factory)

**Analog:** `backend/src/main/java/com/lexcv/config/SecurityConfig.java`

**Imports + class structure pattern** (lines 1–27):
```java
package com.lexcv.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
// + AWS SDK imports
```

**`@Configuration` + `@Bean` method pattern** (from `SecurityConfig.java` lines 23–38):
```java
@Configuration
@RequiredArgsConstructor          // for constructor injection of MinioProperties
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public S3Client s3Client(MinioProperties props) { ... }

    @Bean
    public S3Presigner s3Presigner(MinioProperties props) { ... }
}
```
`@RequiredArgsConstructor` is the project-wide DI pattern (used in `SecurityConfig`, `SetupService`, `ResourceController`). Use it here too.

**No analog for S3Client/S3Presigner construction** — use RESEARCH.md pattern (Pattern 2, lines 253–291):
- `pathStyleAccessEnabled(true)` on both S3Client and S3Presigner.
- `chunkedEncodingEnabled(false)` on S3Client only.
- `checksumValidationEnabled(false)` on S3Presigner only.
- `Region.of("us-east-1")` as the required-but-ignored placeholder.
- `UrlConnectionHttpClient.builder()` on S3Client (avoids Netty).

---

### `backend/src/main/java/com/lexcv/services/StorageService.java` (service — file-I/O)

**Analog:** `backend/src/main/java/com/lexcv/services/SetupService.java`

**Package + imports pattern** (lines 1–20):
```java
package com.lexcv.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
// domain-specific imports...
```

**Class declaration + DI pattern** (lines 23–35):
```java
@Service
@RequiredArgsConstructor
public class StorageService implements ApplicationRunner {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;
    // no @Autowired needed — @RequiredArgsConstructor generates constructor
```

**Validation / guard pattern** (from `SetupService` lines 88–113):
```java
// SetupService throws typed exceptions with Portuguese messages
if (isBlank(request.getClientName())) {
    throw new IllegalArgumentException("O nome da empresa/cliente é obrigatório.");
}
```
For `StorageService`, the equivalent guard is: catch `SdkException` and throw a custom `StorageUnavailableException` — a new unchecked exception class (no existing analog; a one-liner `RuntimeException` subclass suffices).

**Error handling pattern** — the project uses `Map.of("message", "...")` in controllers, not in services. Services throw; controllers catch. Mirror `SetupService`'s approach: let the service throw, handle in controller.

**`@Transactional` pattern** (from `SetupService` line 43): `StorageService` methods do NOT need `@Transactional` — they interact with MinIO (external), not JPA.

**`ApplicationRunner` pattern** — no existing analog. Implement `run(ApplicationArguments args)` for the startup bucket check (see RESEARCH.md Pattern 3 lines 357–369). Catch `SdkException` and log a warning; do NOT rethrow (MinIO may start after the Spring app in Docker Compose).

---

### `backend/src/main/resources/application.yml` (config — property groups)

**Analog:** `backend/src/main/resources/application.yml` (self)

**Existing property group pattern** (lines 29–37):
```yaml
app:
  seed:
    enabled: ${SEED_ENABLED}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}
  jwt:
    secret: ${JWT_SECRET}
    expiration: ${JWT_ACCESS_EXPIRATION_MS}
    refresh-expiration: ${JWT_REFRESH_EXPIRATION_MS}
```
Add a sibling top-level `minio:` group at the end of the file using the same `${VAR}` placeholder pattern:
```yaml
minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ${MINIO_BUCKET_NAME}
  presigned-url-expiry: ${MINIO_PRESIGNED_EXPIRY:3600}
```
Note: `${MINIO_PRESIGNED_EXPIRY:3600}` uses Spring's `${VAR:default}` syntax — the only property with a default (since expiry is optional). All other `MINIO_*` vars are required (enforced by `@NotBlank` on `MinioProperties`).

---

### `backend/.env.example` (config — env var documentation)

**Analog:** `backend/.env.example` (self, lines 1–11)

**Existing pattern:**
```
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=5432
...
SEED_ENABLED=false
```
Append four new vars after the existing block, keeping the same `KEY=example-value` style with no quotes:
```
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=lexcv-documentos
```
`MINIO_PRESIGNED_EXPIRY` is omitted from `.env.example` because it has a default in `application.yml`; document the default via a comment if needed.

---

### `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (controller — file-I/O refactor)

**Analog:** Self — the three existing document endpoints (lines 1726–1879).

**Existing `@PreAuthorize` + handler pattern** (lines 1726–1728):
```java
@PreAuthorize("hasAuthority('documentos:edit')")
@PostMapping(value = "/documentos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> uploadDocumento(...)
```
Keep all `@PreAuthorize` annotations unchanged. Permissions are unchanged.

**Existing tenant validation pattern** (lines 1759–1762):
```java
documento = documentoRepository.findById(replaceId).orElse(null);
if (documento == null || !documento.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento a substituir não encontrado"));
}
```
Copy this exact null-check + tenant-equality guard for all three endpoints. Do not change this pattern.

**Existing audit log pattern** (lines 1828–1837 — download; lines 1863–1871 — delete):
```java
Authentication dlAuth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal dlPrincipal = (UserPrincipal) dlAuth.getPrincipal();
auditLogRepository.save(AuditLog.builder()
        .tenantId(dlPrincipal.getTenantId())
        .processoId(doc.getProcessoId())
        .acao("documento_download")
        .entidadeTipo("documento")
        .entidadeId(id.toString())
        .autorId(dlPrincipal.getUserId())
        .build());
```
Keep audit log writes exactly as-is in both download and delete endpoints. Placement: before the storage operation (as the existing comment notes: "placed before response so record is written even if downstream error occurs").

**Existing error response pattern** (lines 1793–1795):
```java
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("message", "Erro ao gravar o arquivo localmente."));
```
Replace filesystem IOException catch with `StorageUnavailableException` catch returning `503`:
```java
} catch (StorageUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Storage service unavailable"));
}
```

**Upload: object key construction** — replace filesystem path logic (lines 1741–1755) with:
```java
// Remove: File uploadFolder = new File(UPLOAD_DIR); ...uploadFolder.mkdirs()
// Remove: Path path = Paths.get(UPLOAD_DIR + savedName); Files.write(path, file.getBytes());
// Add:
String objectKey = storageService.upload(
    getTenantId(), documento.getId(), savedName,
    file.getInputStream(), file.getContentType(), file.getSize());
```
Note: `savedName` (= `fileId + extension`) continues as the filename component of the key. The `fileId` UUID becomes the `documentoId` for new uploads. For replace flows, use the existing `documento.getId()` as the `documentoId` component.

**Upload: replace flow old-object cleanup** — replace (line 1764):
```java
// Old: Files.deleteIfExists(Paths.get(documento.getCaminhoArquivo()));
// New:
storageService.delete(documento.getCaminhoArquivo());
```

**Download: response contract change** — replace lines 1839–1843 (binary stream) with presigned URL JSON:
```java
// Old: Resource resource = new FileSystemResource(file); return ResponseEntity.ok()...body(resource)
// New:
String url = storageService.presignedDownloadUrl(doc.getCaminhoArquivo());
return ResponseEntity.ok(Map.of("url", url, "expiresIn", props.getPresignedUrlExpiry()));
```
Remove the `File file = new File(...); if (!file.exists())` check (lines 1822–1825) — the file no longer lives on disk.

**Delete: filesystem removal** — replace (line 1874):
```java
// Old: Files.deleteIfExists(Paths.get(doc.getCaminhoArquivo()));
// New:
storageService.delete(doc.getCaminhoArquivo());
```
Wrap in the same `StorageUnavailableException` catch returning 503.

**Inject `StorageService`** — add to the existing `final` field block (lines 52–68):
```java
private final StorageService storageService;
```
`@RequiredArgsConstructor` (line 49) already handles constructor injection — no annotation needed on the field.

**Remove imports no longer needed after refactor:**
- `org.springframework.core.io.FileSystemResource` (line 18)
- `org.springframework.core.io.Resource` (line 19)
- `org.springframework.http.HttpHeaders` (line 21)
- `java.io.File` (line 31)
- `java.io.IOException` (line 33) — only remove if no other `IOException` usage remains
- `java.nio.file.Files` (line 34)
- `java.nio.file.Path` (line 35) — only if no other usage
- `java.nio.file.Paths` (line 36) — only if no other usage

---

## Shared Patterns

### @RequiredArgsConstructor for DI
**Source:** Every existing class (`SecurityConfig.java` line 26, `SetupService.java` line 22, `ResourceController.java` line 49)
**Apply to:** `MinioConfig`, `StorageService`
```java
@RequiredArgsConstructor
public class Foo {
    private final SomeDep dep; // injected via constructor, no @Autowired
}
```

### Tenant isolation guard
**Source:** `ResourceController.java` lines 1759–1762, 1817–1819, 1849–1852
**Apply to:** The three document endpoints in `ResourceController` — existing guard pattern is already correct; do not weaken it.
```java
if (doc == null || !doc.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "..."));
}
```

### Error response shape
**Source:** `ResourceController.java` lines 1737, 1762, 1793–1795, 1820, 1857
**Apply to:** All new 503 error returns in document endpoints
```java
return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("message", "Storage service unavailable"));
```

### application.yml env var placeholder
**Source:** `backend/src/main/resources/application.yml` lines 12–14
**Apply to:** New `minio:` block in application.yml
```yaml
key: ${ENV_VAR_NAME}            # required var — no default
optional-key: ${VAR:default}    # optional var with fallback
```

### @Configuration + @Bean
**Source:** `SecurityConfig.java` lines 23–38
**Apply to:** `MinioConfig.java`
```java
@Configuration
@RequiredArgsConstructor
public class MinioConfig {
    @Bean
    public SomeType beanName(MinioProperties props) { ... }
}
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `backend/src/main/java/com/lexcv/config/MinioProperties.java` | config | — | No `@ConfigurationProperties` class exists yet; project uses `@Value` per-field injection. Use RESEARCH.md Pattern 1. |
| `backend/src/main/java/com/lexcv/services/StorageService.java` (AWS SDK calls) | service | file-I/O | No S3/external-storage service exists. `SetupService` covers the service structure; AWS SDK usage comes from RESEARCH.md Patterns 2–3. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/` (config/, services/, controllers/)
**Files scanned:** 7 (SecurityConfig, SetupService, ResourceController lines 1–80 + 1720–1879, application.yml, .env.example, pom.xml)
**Pattern extraction date:** 2026-06-19
