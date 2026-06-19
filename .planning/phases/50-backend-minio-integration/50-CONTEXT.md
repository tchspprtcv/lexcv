# Phase 50: Backend MinIO Integration - Context

**Gathered:** 2026-06-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace filesystem-based document storage (`uploads/` directory) with MinIO object storage. The three document endpoints in `ResourceController` — upload, download, delete — are the only changes. All other modules are unaffected. The `Documento.caminhoArquivo` field transitions from storing a filesystem path to storing a MinIO object key. No schema migration needed.

</domain>

<decisions>
## Implementation Decisions

### SDK & Architecture
- Use `software.amazon.awssdk:s3` v2 (S3-compatible with MinIO, official presigned URL support)
- Create a dedicated `StorageService` Spring bean — extracted from `ResourceController` for single responsibility
- Config via `@ConfigurationProperties` class `MinioProperties` grouping all MinIO env vars
- MinIO config injected via new `MINIO_*` env vars in `.env` and `application.yml` — consistent with existing pattern

### Object Key & DB Field
- Object key format: `{tenantId}/{documentoId}/{filename}` (matches MIN-04, simple)
- `caminhoArquivo` column reused to store the MinIO object key string — no schema/DB migration needed
- Presigned URL expiry: 1 hour (3600s)
- Bucket name via `MINIO_BUCKET_NAME` env var — no hardcoded bucket names

### Download Response Contract
- `GET /documentos/{id}/download` returns JSON `{"url": "...", "expiresIn": 3600}` — frontend opens URL directly, no binary stream through Spring
- Keep existing download audit log (`documento_download` in `t_audit_log`) — legal platform requires access audit
- Error when MinIO is unreachable: `503 Service Unavailable` with message "Storage service unavailable"
- File size limits: keep existing Spring Boot defaults (no new explicit cap)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ResourceController` lines 1727–1879: existing upload/download/delete endpoints to refactor
- `Documento` entity (`com.lexcv.models.Documento`): `caminhoArquivo` (String) stores the path/key; no schema change needed
- `AuditLog` builder: already used in download and delete; keep the same pattern
- `getTenantId()` helper on controller: use to scope object keys

### Established Patterns
- Config: env vars in `backend/.env` loaded via `application.yml` with `${VAR}` placeholders
- Services: no service layer yet (logic is in controller); `StorageService` will be the first service class
- Error responses: `Map.of("message", "...")` pattern throughout controller
- Repository: `documentoRepository` is a standard Spring Data JPA repo

### Integration Points
- `pom.xml`: add `software.amazon.awssdk:s3` and `software.amazon.awssdk:s3-transfer-manager` (optional) dependencies
- `application.yml`: add `minio.*` property group
- `backend/.env.example`: add `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`
- `ResourceController`: inject `StorageService`; replace 3 filesystem operations with service calls
- Download endpoint contract change: was `Resource` body → now `Map<String, Object>` with presigned URL

</code_context>

<specifics>
## Specific Ideas

- `StorageService` should expose: `upload(tenantId, documentoId, filename, inputStream, contentType, size)` → returns object key; `presignedDownloadUrl(objectKey)` → returns URL string; `delete(objectKey)` → void
- Object key construction: `tenantId.toString() + "/" + documentoId.toString() + "/" + filename`
- `MinioProperties` fields: `endpoint`, `accessKey`, `secretKey`, `bucketName`, `presignedUrlExpiry` (default 3600s)
- On startup: verify bucket exists and create if absent (prevents silent failure on first upload)

</specifics>

<deferred>
## Deferred Ideas

- Migration script for existing files in `uploads/` → MinIO bucket (out of scope per REQUIREMENTS.md)
- Bucket lifecycle policies / expiry rules
- Versioning support in MinIO

</deferred>
