# Phase 111: Backend — Pesquisa Global Cross-Entity (API) - Pattern Map

**Mapped:** 2026-07-18
**Files analyzed:** 9 (2 new core files, 4 modified repositories, 1 new migration, 2 new test files)
**Analogs found:** 9 / 9

## Naming note (verify before planning)

CONTEXT.md's Integration Points section locks the literal class names `SearchController` and `SearchResultDto` (English domain nouns) and the literal route `GET /api/v1/pesquisa` (Portuguese path segment, matching `ParecerPesquisaController`'s precedent). This is a deliberate mix — every other controller in this codebase pairs a Portuguese domain noun with the `Controller`/`Repository` suffix (`ParecerPesquisaController`, `NotificacaoController`, `ClienteRepository`), whereas `SearchController`/`SearchResultDto` use an English noun. This appears intentional in CONTEXT.md (stated twice, under both Decisions and Integration Points) rather than an oversight, so this map uses those exact names — but it is worth a final confirmation with the user/planner since it's a one-off deviation from `CLAUDE.md`'s "domain language is Portuguese" convention. The **route path** (`/api/v1/pesquisa`) and the **method name** (suggested `pesquisar()`, matching `ParecerPesquisaController.pesquisarSolicitacoes()`) stay Portuguese either way.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/controllers/SearchController.java` (NEW) | controller | request-response (read aggregation) | `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java` (shape) + `ResourceController.getTimeline` (merge logic) | exact |
| `backend/src/main/java/com/lexcv/dtos/SearchResultDto.java` (NEW) | model/dto | transform (discriminated union) | `backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java` | exact |
| `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` (MODIFIED — add 1 method) | repository | CRUD (native read) | `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (`pesquisar()`) | exact |
| `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java` (MODIFIED — add 1 method) | repository | CRUD (native read) | same | exact |
| `backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java` (MODIFIED — add 1 method) | repository | CRUD (native read) | same | exact |
| `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (MODIFIED — add 1 new method, see tension note) | repository | CRUD (native read) | itself (existing `pesquisar()`, same file) | exact |
| `backend/migrations/111-enable-search-extensions.sql` (NEW) | migration | batch (schema DDL) | `backend/migrations/96-add-notificacao-snoozed-until.sql` (header/shape) | exact (wrapper) / no in-repo precedent for `CREATE EXTENSION` content itself |
| `backend/src/test/java/com/lexcv/repositories/SearchRepositoryIT.java` (NEW, suggested name) | test | CRUD (tenant-isolation integration) | `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` | exact |
| `backend/src/test/java/com/lexcv/controllers/SearchControllerTest.java` (NEW, suggested name) | test | request-response (RBAC branch unit test) | `backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java` | role-match |

**Explicitly out of scope for this phase** (per CONTEXT.md `## Phase Boundary`: "Backend apenas; nenhuma UI nesta fase"): no frontend files. `GlobalSearchDialog`, `useGlobalSearch`, `use-debounced-value.ts`, `types/search.ts`, and the `dashboard-shell.tsx` edit from ARCHITECTURE.md's Recommended Project Structure belong to Phase 112, not this phase.

---

## Pattern Assignments

### `backend/src/main/java/com/lexcv/controllers/SearchController.java` (controller, request-response)

**Analog 1 (dedicated-controller shape + tenant/routing idiom):** `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java` (full file, 58 lines)

**Imports pattern** (lines 1-18):
```java
package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.repositories.ParecerSolicitacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
```
For `SearchController`, add `com.lexcv.dtos.SearchResultDto` and the 4 repositories (`ClienteRepository`, `ProcessoRepository`, `DocumentoRepository`, `ParecerSolicitacaoRepository`) in place of the single one above; add `java.util.ArrayList` for the merge list.

**Class header + routing pitfall warning — copy this comment's *intent*, not its literal words** (lines 20-33):
```java
/**
 * Separate controller for the top-level /api/v1/pareceres/pesquisa route.
 * ParecerController is mapped at /api/v1/pareceres/solicitacoes (class-level
 * @RequestMapping), so a sibling top-level route cannot live there — Spring
 * concatenates class-level and method-level mappings regardless of a leading
 * "/", producing /api/v1/pareceres/solicitacoes/api/v1/pareceres/pesquisa
 * instead of the intended path. This was a routing bug present since v2.5
 * (Phase 64) that made pesquisar() unreachable at its documented path;
 * fixed during v2.6 milestone integration audit (Phase 69).
 */
@RestController
@RequestMapping("/api/v1/pareceres/pesquisa")
@RequiredArgsConstructor
public class ParecerPesquisaController {

    private final ParecerSolicitacaoRepository parecerSolicitacaoRepository;
```
`SearchController` must mirror this exact shape: `@RequestMapping("/api/v1/pesquisa")` at class level (bare, no trailing path segment), one bare `@GetMapping` on the single method — never a sub-path that could get concatenated, and never a method added to `ResourceController` (Anti-Pattern 5 in ARCHITECTURE.md; `ResourceController` is 3,296 lines / 170KB).

**Tenant helper — duplicate verbatim, this idiom is intentionally copy-pasted 3× already** (lines 37-41, identical to `ResourceController.java:127-131` and `NotificacaoController.java:57-61`):
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```

**RBAC + query dispatch pattern** (lines 43-57):
```java
@PreAuthorize("hasAuthority('pareceres:view')")
@GetMapping
public ResponseEntity<?> pesquisarSolicitacoes(
        @RequestParam(required = false) String texto,
        ...
) {
    UUID tenantId = getTenantId();
    List<ParecerSolicitacao> result = parecerSolicitacaoRepository.pesquisar(
            tenantId, texto, clienteId, advogadoId, status, dataInicio, dataFim);
    return ResponseEntity.ok(result);
}
```
`SearchController` needs a coarser gate than this single-scope example — see **Analog 2** and **Shared Patterns > Per-Branch RBAC Gating** below for the actual 4-branch shape required by CONTEXT.md.

---

**Analog 2 (multi-entity merge-into-one-discriminated-list — the actual core orchestration pattern):** `ResourceController.getTimeline` (`backend/src/main/java/com/lexcv/controllers/ResourceController.java:2272-2322`)

```java
@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/timeline")
public ResponseEntity<?> getTimeline(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }

    List<TimelineItemDto> items = new ArrayList<>();

    // 1. Movimentacoes ...
    List<Movimentacao> movs = movimentacaoRepository.findByProcessoId(id);
    for (Movimentacao m : movs) {
        String autorNome = resolveAutorNome(m.getAutorId(), tenantId);
        String tipo = "TRANSICAO_ESTADO".equals(m.getTipo()) ? "transicao" : "movimentacao";
        items.add(new TimelineItemDto(tipo, String.valueOf(m.getId()), m.getData(), titulo, m.getDescricao(), autorNome));
    }

    // 2. Eventos ... 3. Documentos ... 4. ConflictCheckDecisao (each appended the same way)

    items.sort(Comparator.comparing(TimelineItemDto::timestamp,
            Comparator.nullsLast(Comparator.reverseOrder())));

    return ResponseEntity.ok(items);
}
```
This is the *only* existing precedent in the codebase for "N entity sources → one flat discriminated `List<Dto>` response," and `SearchController` is structurally the same shape, generalized from one processo's sub-entities to the whole tenant's top-level entities. Key transferable details: build a mutable `List<SearchResultDto>`, `.add(...)` per source (per Pattern 2 below, each source is conditionally skipped), single `ResponseEntity.ok(items)` at the end — no per-branch try/catch, no partial-failure handling (list-building can't throw here since it's plain repository calls + stream ops).

**Reference shape combining both analogs, adjusted for this phase's exact CONTEXT.md decisions** (ARCHITECTURE.md `Pattern 1`, already grounded in the two analogs above — adjust the route/method name per the Naming Note, and the "≥2 chars, ≤200 chars, always 200 OK" validation per CONTEXT.md):
```java
@PreAuthorize("hasAnyAuthority('clientes:view','processos:view','documentos:view','pareceres:view')")
@GetMapping
public ResponseEntity<?> pesquisar(@RequestParam(required = false) String q) {
    String termo = q == null ? "" : q.trim();
    if (termo.length() > 200) termo = termo.substring(0, 200);   // CONTEXT.md: defense against abuse
    if (termo.length() < 2) return ResponseEntity.ok(List.of()); // CONTEXT.md: <2 chars / missing / empty -> 200 OK, []

    UUID tenantId = getTenantId();
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    List<SearchResultDto> resultados = new ArrayList<>();
    if (hasAuthority(auth, "clientes:view"))   resultados.addAll(pesquisarClientes(tenantId, termo));
    if (hasAuthority(auth, "processos:view"))  resultados.addAll(pesquisarProcessos(tenantId, termo));
    if (hasAuthority(auth, "documentos:view")) resultados.addAll(pesquisarDocumentos(tenantId, termo));
    if (hasAuthority(auth, "pareceres:view"))  resultados.addAll(pesquisarPareceres(tenantId, termo));
    return ResponseEntity.ok(resultados);
}
```
Note the class-level `@PreAuthorize("hasAnyAuthority(...)")` — this exact multi-arg `hasAnyAuthority` SpEL form already has one precedent in this codebase (not a novel construct): `ResourceController.java:1527` — `@PreAuthorize("hasAnyAuthority('processos:edit', 'processos:manage')")`. Per CONTEXT.md, a caller with **zero** of the 4 scopes should still get `200 OK` + `[]`, not `403` — re-check this against `hasAnyAuthority`'s actual behavior during planning (it will 403 a zero-scope caller, which CONTEXT.md's "sem nenhum dos 4 scopes... 200 OK, lista vazia" wording may or may not intend literally; flagging for the planner to confirm the precise HTTP-level behavior wanted here, since `hasAnyAuthority` on an authenticated-but-scopeless user is a 403 by Spring Security's normal semantics, not a 200).

**Error handling:** None needed at this layer — every branch here is a `boolean` check + a repository call + a stream/list build, none of which throw under normal operation. The app-wide `GlobalExceptionHandler` (`backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java`, full file) remains the safety net for any unexpected exception (`@ExceptionHandler(Exception.class)` → 500 with `{error, message}` body) — no per-method try/catch required, consistent with every other simple read endpoint in `ResourceController`.

---

### `backend/src/main/java/com/lexcv/dtos/SearchResultDto.java` (model/dto, transform)

**Analog:** `backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java` (full file, 22 lines)
```java
package com.lexcv.dtos;

import java.time.LocalDateTime;

/**
 * Unified timeline entry DTO for GET /processos/{id}/timeline.
 * tipo discriminates the source entity:
 *   "movimentacao" — regular process movement
 *   "transicao"    — state transition (Movimentacao with tipo=TRANSICAO_ESTADO)
 *   "evento"       — agenda event linked to processo
 *   "documento"    — document uploaded/linked to processo
 *   "decisao"      — conflict check decision
 */
public record TimelineItemDto(
        String tipo,
        String id,
        LocalDateTime timestamp,
        String titulo,
        String descricao,
        String autorNome
) {}
```
CONTEXT.md locks the field set: `tipo, id, titulo, subtitulo, rota` (any *additional* internal fields are Claude's Discretion — CONTEXT.md doesn't ask for a timestamp field, unlike `TimelineItemDto`, since ordering/dedup happens server-side before serialization, not client-side). Direct translation:
```java
package com.lexcv.dtos;

/**
 * Unified cross-entity search-result DTO for GET /api/v1/pesquisa.
 * tipo discriminates the source entity: "cliente" | "processo" | "documento" | "parecer".
 * Never includes Honorario/financeiro fields — see PITFALLS.md Pitfall 2.
 */
public record SearchResultDto(
        String tipo,
        String id,
        String titulo,
        String subtitulo,
        String rota
) {}
```
Follow `TimelineItemDto`'s `String id` convention (stringify the UUID with `.toString()`/`String.valueOf(...)` at construction time, as `getTimeline` already does for every branch) — do **not** type `id` as `UUID` on the record, for consistency with the one existing discriminated-union DTO in this codebase. Per ARCHITECTURE.md Anti-Pattern 7: this must be a plain `record` (camelCase JSON via Jackson defaults), never a hand-built `Map<String,Object>` — this is a brand-new contract with no legacy snake_case consumers to match.

Suggested `rota` values per entity (all 4 detail routes already verified to exist in `web/src/app/(dashboard)/{clientes,processos,documentos,pareceres}/[id]/page.tsx`, per ARCHITECTURE.md Sources): `/clientes/{id}`, `/processos/{id}`, `/documentos/{id}`, `/pareceres/{id}`.

---

### `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` (repository, CRUD native read — MODIFIED)

**Current file (full, 20 lines) — new method is additive, nothing existing changes:**
```java
package com.lexcv.repositories;

import com.lexcv.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    List<Cliente> findByTenantId(UUID tenantId);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome);
    List<Cliente> findByTenantIdAndNif(UUID tenantId, String nif);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCaseAndNif(UUID tenantId, String nome, String nif);
    Optional<Cliente> findByTenantIdAndDocumentoNumero(UUID tenantId, String documentoNumero);

    @Query("SELECT MAX(c.numeroSequencial) FROM Cliente c WHERE c.tenantId = :tenantId")
    Optional<Integer> findMaxNumeroSequencialByTenantId(@Param("tenantId") UUID tenantId);
}
```

**Analog for the new method — CAST-null-guard + tenant-first native-query idiom:** `ParecerSolicitacaoRepository.pesquisar()` (`backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java:41-58`):
```java
@Query(value = "SELECT s.* FROM t_parecer_solicitacao s " +
        "LEFT JOIN t_parecer_versao v ON v.solicitacao_id = s.id " +
        "AND v.numero_versao = (SELECT MAX(v2.numero_versao) FROM t_parecer_versao v2 WHERE v2.solicitacao_id = s.id) " +
        "WHERE s.tenant_id = :tenantId " +
        "AND (CAST(:clienteId AS uuid) IS NULL OR s.cliente_id = CAST(:clienteId AS uuid)) " +
        "AND (CAST(:texto AS text) IS NULL OR v.conteudo ILIKE '%' || CAST(:texto AS text) || '%')",
        nativeQuery = true)
List<ParecerSolicitacao> pesquisar(@Param("tenantId") UUID tenantId, @Param("texto") String texto, ...);
```
Note every nullable bind is `CAST(:param AS type)` — required because PostgreSQL cannot infer the type of a bare null bind inside `(:param IS NULL OR ...)`; the comment at lines 38-40 of that file explains why. `tenant_id = :tenantId` is always the leading `WHERE` predicate — never build a query that puts it anywhere else.

**What's genuinely new here (no in-repo precedent, grounded in STACK.md instead):** the `unaccent()` wrap and the tiered exact/prefix/substring `ORDER BY ... CASE WHEN` are **first use in this codebase** — neither `ParecerSolicitacaoRepository.pesquisar()` nor `NotificacaoRepository.buscarPorFiltros()` rank or accent-fold their results. STACK.md's Installation section gives the exact illustrative shape to follow (already grounded in this codebase's `tenant_id`/`CAST` idiom, just extended):
```sql
SELECT c.* FROM t_cliente c
WHERE c.tenant_id = :tenantId
  AND unaccent(c.nome) ILIKE unaccent('%' || CAST(:texto AS text) || '%')
ORDER BY
  CASE WHEN lower(c.nome) = lower(CAST(:texto AS text)) THEN 0
       WHEN lower(c.nome) LIKE lower(CAST(:texto AS text)) || '%' THEN 1
       ELSE 2 END,
  c.created_at DESC
LIMIT :limit
```
For `Cliente` specifically, CONTEXT.md's ranking decision ("Correspondências exatas/prefixo em identificadores estruturados... sempre ordenadas antes de correspondências por substring") means the `CASE WHEN` tier must also check `numero_cliente` and `nif` (both structured IDs on `Cliente`, confirmed fields: `numero_cliente VARCHAR(20)`, `nif` with a `^\d{9}$` pattern — see `Cliente.java:39-41,64-66`) ahead of the free-text `nome` substring tier, e.g. tier 0 = exact `numero_cliente`/`nif` match, tier 1 = `numero_cliente` prefix, tier 2 = `nome` prefix (accent-folded), tier 3 = substring anywhere. `Cliente` also has its own unique index backing `(tenant_id, documento_numero)` and `(tenant_id, numero_sequencial)` (`Cliente.java:15-18`), unlike `Processo.numeroProcesso` (see below — no index at all).

Suggested method (name/signature at Claude's Discretion, matching CONTEXT.md's "Claude's Discretion" note): `List<Cliente> pesquisarGlobal(@Param("tenantId") UUID tenantId, @Param("termo") String termo, @Param("limit") int limit);` — cap via a `LIMIT :limit` bind (CONTEXT.md: 5 per entity type), following STACK.md's "What NOT to Use" guidance against a paginated `Page`/`Pageable` shape for this UX (that machinery is reserved for `NotificacaoRepository.buscarPorFiltros` and a future "ver todos os resultados" page, not this quick-filter endpoint).

---

### `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java` (repository, CRUD native read — MODIFIED)

**Current file (full, 12 lines):**
```java
package com.lexcv.repositories;

import com.lexcv.models.Processo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessoRepository extends JpaRepository<Processo, UUID> {
    List<Processo> findByTenantId(UUID tenantId);
    List<Processo> findByClienteId(UUID clienteId);
}
```
**Warning carried over from PITFALLS.md Pitfall 1:** `findByClienteId(UUID)` takes **no tenant parameter** — safe today only because every existing call site additionally filters by a tenant-checked `clienteId`. Do not reach for this method inside the new search query; write the new native `@Query` method (tenant-first) instead, same idiom as `ClienteRepository` above.

Same idiom applies, searching `numero_processo` (structured ID — flag per PITFALLS.md Pitfall 4: **`Processo.numeroProcesso` has no uniqueness constraint and no index at all**, confirmed via `Processo.java:30-31` — an "exact match" tier against it is still a full scan; acceptable for current row counts per STACK.md's Scaling section, but not free), `descricao`, `tribunal`, `area_juridica` (all confirmed plain `String` fields, `Processo.java:33-53`), ordered exact-`numero_processo` first, then accent-folded `descricao`/free-text substring, then `created_at DESC`.

---

### `backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java` (repository, CRUD native read — MODIFIED)

**Current file (full, 13 lines):**
```java
package com.lexcv.repositories;

import com.lexcv.models.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
    List<Documento> findByTenantId(UUID tenantId);
    List<Documento> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
    List<Documento> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);
}
```
Same idiom, searching `nome`/`tipo` (`Documento.java:29-30`, both plain `String`, no structured-ID tier needed — `Documento` has no equivalent of `numero_cliente`/NIF). Per STACK.md's "What NOT to Use": this searches `Documento.nome`/`Documento.tipo` metadata only, never file content/OCR — `Documento.caminhoArquivo` is a MinIO object key, not extracted text.

---

### `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (repository, CRUD native read — MODIFIED)

**Current file (full, 59 lines, already shown above as the primary idiom analog).** The new method goes in the same file, alongside (not replacing) the existing `pesquisar()`.

**Tension between the two research documents — flag for planning, not silently resolved here:**
- STACK.md's Integration Notes says: *"for Pareceres, prefer calling the existing `ParecerSolicitacaoRepository.pesquisar(...)` ... rather than writing a second parallel query against the same table."*
- ARCHITECTURE.md's Anti-Pattern 6 says the opposite in effect: *"Global search's parecer category does a shallow match on `ParecerSolicitacao.descricao` only ... Do not join `ParecerVersao.conteudo`."*

These conflict in practice: calling the existing `pesquisar(tenantId, texto, null, null, null, null, null)` with only `texto` bound does **not** match `s.descricao` at all — its `texto` parameter is wired exclusively to `v.conteudo ILIKE ...` (the `LEFT JOIN`ed latest `ParecerVersao`, see lines 41-50 above), never to `ParecerSolicitacao.descricao`. So reusing `pesquisar()` as-is would silently implement deep-content search, which is exactly what Anti-Pattern 6 says not to do for the quick global-search surface (duplicates `/pareceres/pesquisa`'s job, and is a slower query than a quick-filter needs). CONTEXT.md's own phrasing — *"Estrutura exata das 4 queries nativas por entidade"* (4 native queries, one per entity) — reads as siding with ARCHITECTURE.md: a **new, separate, lightweight** method scoped to `ParecerSolicitacao.descricao` only, following `pesquisar()`'s CAST-guard/`ILIKE` *style* without calling `pesquisar()` itself or touching `t_parecer_versao`. Recommend that shape; flag the STACK.md alternative to the user/planner explicitly rather than picking silently.

Suggested new method, same file:
```java
@Query(value = "SELECT s.* FROM t_parecer_solicitacao s " +
        "WHERE s.tenant_id = :tenantId " +
        "AND unaccent(s.descricao) ILIKE unaccent('%' || CAST(:termo AS text) || '%') " +
        "ORDER BY s.created_at DESC " +
        "LIMIT :limit",
        nativeQuery = true)
List<ParecerSolicitacao> pesquisarGlobal(@Param("tenantId") UUID tenantId, @Param("termo") String termo, @Param("limit") int limit);
```
No structured-ID tier needed here (`ParecerSolicitacao` has no user-facing identifier field comparable to `numero_cliente`/`numero_processo` — `status`/`prioridade` are enum-like short codes, not identifiers users search by).

---

### `backend/migrations/111-enable-search-extensions.sql` (migration, batch/schema — NEW)

**Analog (header/wrapper convention — this part is a strong, literal analog):** `backend/migrations/96-add-notificacao-snoozed-until.sql` (full file, 27 lines):
```sql
-- Phase 96 (NOTF-26): add snoozed_until column to t_notificacao
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds the `snoozedUntil` field to the `Notificacao` entity
-- (backend/src/main/java/com/lexcv/models/Notificacao.java).
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-adds this column locally from the entity mapping).
-- `ddl-auto=validate` never creates or alters schema — it only checks the existing
-- schema is compatible at startup. Without this script, the application will fail to
-- start in production (schema validation error: missing column `snoozed_until` on
-- table `t_notificacao`).
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase --
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that introduces `snoozedUntil`.

ALTER TABLE t_notificacao ADD COLUMN snoozed_until TIMESTAMP;
```
Every migration file in `backend/migrations/` (`74-cleanup-nif-documento-tipo.sql`, `81-`, `82-`, `86-`, `88-`, `91-`, `93-`, `96-`, all confirmed present) opens with this same 3-part header: (1) one-line phase/purpose summary, (2) the "REQUIRED manual production migration" + `ddl-auto: validate`-in-prod rationale paragraph (near-verbatim across all 8 files), (3) the "no Flyway/Liquibase, run manually per environment" paragraph. Copy this header verbatim, adjusted only for phase number/filename/entity name.

**No in-repo precedent for the SQL body itself** — zero existing migration issues a `CREATE EXTENSION` statement (confirmed: grepped every file under `backend/migrations/*.sql` for `CREATE INDEX`/`gin_trgm`/`to_tsvector`/extension statements — zero hits, per PITFALLS.md baseline). Use STACK.md's Installation section verbatim for the body:
```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```
Both are PostgreSQL contrib modules already compiled into the `postgres:16-alpine` image already in `docker-compose.yml` (verified against the official `docker-library/postgres` build source per STACK.md) — this is a metadata-only operation, no image/Dockerfile change. `pg_trgm` is enabled now but not used by any query yet in this phase (no GIN index migration needed yet — see STACK.md's Stack Patterns by Variant for when to add one).

Filename: per CONTEXT.md's own example, `111-enable-search-extensions.sql` — phase-number-prefixed (not sequentially counted; existing filenames `74-`, `81-`, `82-`, `86-`, `88-`, `91-`, `93-`, `96-` are all phase numbers, confirming this convention, not a plain incrementing counter).

---

### `backend/src/test/java/com/lexcv/repositories/SearchRepositoryIT.java` (test, CRUD/tenant-isolation integration — NEW, suggested name)

**Analog:** `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` (full file, 302 lines) — this is the literal, explicit precedent CONTEXT.md names ("replica o padrão já existente em `NotificacaoRepositoryIT`").

**Class header + Testcontainers harness** (lines 26-54):
```java
/**
 * Primeiro teste de integração com PostgreSQL real (Testcontainers) deste backend.
 * {@code NotificacaoRepository.buscarPorFiltros} é a primeira combinação nativeQuery=true +
 * Pageable do projeto, com o idioma {@code CAST(:param AS text/boolean) IS NULL} exigido pelo
 * Postgres para inferir o tipo de um bind nulo — um comportamento específico do motor que o H2
 * não reproduz com fidelidade suficiente (ver .planning/research/STACK.md).
 *
 * {@code @DataJpaTest} (não {@code @SpringBootTest}) contorna por construção o bloqueio
 * MINIO_ENDPOINT: esta fatia de contexto nunca instancia MinioConfig/SecurityConfig, logo os
 * respetivos placeholders de ambiente nunca são avaliados.
 *
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)} é obrigatório a par de
 * {@code @ServiceConnection} — sem ele o {@code @DataJpaTest} troca silenciosamente a
 * DataSource por uma base embutida, ignorando o contentor Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificacaoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private EntityManager entityManager;
```
For `SearchRepositoryIT`, `@Autowired` all 4 modified repositories (`ClienteRepository`, `ProcessoRepository`, `DocumentoRepository`, `ParecerSolicitacaoRepository`) instead of one; same `@DataJpaTest`/`@AutoConfigureTestDatabase(Replace.NONE)`/`@Testcontainers`/`postgres:16-alpine` container triplet — this is the project's *first* Testcontainers precedent and it works, no reason to deviate.

**Representative tenant-isolation test method** (lines 96-113):
```java
@Test
void buscarPorFiltros_escopaPorTenantEDestinatario_nuncaVazaOutroTenantOuOutroDestinatario() {
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    UUID destinatarioA = UUID.randomUUID();
    UUID destinatarioB = UUID.randomUUID();

    persistir(tenantA, destinatarioA, "FASE_ENTRADA", "id-1", false);
    persistir(tenantA, destinatarioB, "FASE_ENTRADA", "id-2", false);
    persistir(tenantB, destinatarioA, "FASE_ENTRADA", "id-3", false);

    Page<Notificacao> page = notificacaoRepository.buscarPorFiltros(
            tenantA, destinatarioA, null, null, PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals(destinatarioA, page.getContent().get(0).getDestinatarioId());
    assertEquals(tenantA, page.getContent().get(0).getTenantId());
}
```
CONTEXT.md's exact requirement: *"2 tenants com tokens de pesquisa coincidentes, asserting zero cross-tenant leakage em todos os 4 tipos."* Translate directly: build 2 tenants (A, B), seed a `Cliente` + `Processo` + `Documento` + `ParecerSolicitacao` under **each**, all containing the *same* distinctive search token (e.g. `"Zeta9k"`) in their searchable field (`nome`/`numero_processo`/`nome`/`descricao` respectively), call each of the 4 new `pesquisarGlobal(...)`-style repository methods as tenant A, and assert the result list contains only tenant A's row for that entity type — repeated once per entity type (4 test methods, or 1 parameterized/looped test — match this file's existing style of one `@Test` method per concern). Use a `private` builder-helper method per entity (mirroring `persistir(...)` at lines 61-73) to avoid repeating `Builder` boilerplate across test methods.

**Build/run note:** `*IT` classes run via `mvn verify` (Failsafe plugin), not `mvn test` (Surefire, `*Test` classes only) — confirmed in `backend/pom.xml:191-199`. Name the new file with the `IT` suffix, not `Test`, or it silently never runs in CI.

---

### `backend/src/test/java/com/lexcv/controllers/SearchControllerTest.java` (test, request-response unit — NEW, suggested name)

**Analog:** `backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java` (partial, lines 1-108 read) — the only existing controller-level test in this codebase, and the pattern for testing `@PreAuthorize`-guarded/authority-dependent controller logic **without** a Spring context.

**Why this file, not `SearchRepositoryIT`, for the RBAC role-matrix requirement:** CONTEXT.md's second test requirement ("Teste de matriz por role... confirmando que nenhum campo de `Honorario` aparece") tests the *controller's* per-branch `hasAuthority(auth, "...")` gating logic (Pattern 2 in ARCHITECTURE.md), not a repository query — `@DataJpaTest` (used by `SearchRepositoryIT`) never loads `SecurityConfig`/authorities at all, so it structurally cannot exercise this. This codebase's own convention for testing `@PreAuthorize`/authority-dependent controller code is a **plain Mockito unit test, no Spring context** — confirmed explicitly in this very analog file's own header comment:

> *"No MockMvc/`@SpringBootTest` harness exists anywhere in this codebase ... every existing test instantiates the class under test directly with Mockito-mocked collaborators and calls the method under test as a plain Java call, with no Spring context ... `@PreAuthorize` annotation is therefore inert here, same as in production unit tests of any other `@PreAuthorize`-guarded method in this codebase."*

**Mocked-Authentication/UserPrincipal pattern** (lines 57-58, 91-92, 99-108):
```java
@ExtendWith(MockitoExtension.class)
class ResourceControllerUploadDocumentoTest {

    @Mock private ClienteRepository clienteRepository;
    // ... one @Mock per repository/service constructor param ...

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ATOR_ID = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadDocumento_comProcessoId_notificaEquipaDoClienteMaisResponsavel() throws Exception {
        UserPrincipal ator = UserPrincipal.builder().userId(ATOR_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ator, null, List.of()));
        // ... when(...).thenReturn(...) on mocks ...
        ResourceController controller = new ResourceController(clienteRepository, /* ...all ctor params... */);
        ResponseEntity<?> response = controller.uploadDocumento(file, PROCESSO_ID, null, null, null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
```
Adapt directly for `SearchControllerTest`: since `SearchController`'s RBAC logic is a **programmatic** `hasAuthority(auth, "...")` check (not `@PreAuthorize`, which this test style can't exercise anyway), the third constructor arg to `UsernamePasswordAuthenticationToken` — `List.of()` in the analog, because that test doesn't need real authorities — must instead be populated with real `SimpleGrantedAuthority` instances per test case, e.g. `List.of(new SimpleGrantedAuthority("clientes:view"), new SimpleGrantedAuthority("processos:view"))` for an ADVOGADO-shaped caller missing `financeiro:view`-adjacent scopes, mirroring the exact per-role scope sets in `DatabaseSeeder.seedRbac()` (`backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:293-353`, see **Shared Patterns > RBAC Matrix** below). One test method per role (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) or one parameterized test over the 4 scope sets; assert the returned `List<SearchResultDto>` never contains a `tipo` outside `{"cliente","processo","documento","parecer"}` and, per CONTEXT.md, that `Honorario`/`financeiro` fields are structurally impossible to appear (true by construction if `SearchResultDto` has no financial fields at all — see Anti-Pattern 2/PITFALLS.md Pitfall 2).

---

## Shared Patterns

### Tenant resolution
**Source:** `ResourceController.java:127-131` / `ParecerPesquisaController.java:37-41` / `NotificacaoController.java:57-61` (byte-identical, duplicated 3× already)
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```
**Apply to:** `SearchController` — a 4th duplication of this exact method is the established, accepted style in this codebase (ARCHITECTURE.md explicitly notes this is "accepted, pre-existing style, not a new cost introduced here").

### Per-branch RBAC gating (net-new helper — first use in this codebase)
**Source:** ARCHITECTURE.md Pattern 2 (synthesized reference, not lifted from an existing file — confirmed via grep across `backend/src/main/java` for `hasAuthority(Authentication` / `private boolean hasAuthority`: zero existing matches)
```java
private boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(authority));
}
```
**Apply to:** `SearchController` only, guarding each of the 4 `if (hasAuthority(auth, "<scope>:view")) resultados.addAll(...)` branches (Anti-Pattern 2: never fetch-then-filter, always gate-before-fetch). `Authentication.getAuthorities()` returns the same `Set<SimpleGrantedAuthority>` `UserPrincipal.create(...)` builds from DB permissions (`backend/src/main/java/com/lexcv/config/UserPrincipal.java:27-51`) — no new security primitive, just a programmatic read of the same authority set `@PreAuthorize`'s SpEL evaluates declaratively.

### CAST-null-guard native `@Query` idiom
**Source:** `ParecerSolicitacaoRepository.pesquisar()` (`backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java:41-58`) and `NotificacaoRepository.buscarPorFiltros()` (`backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:26-42`)
```java
@Query(value = "SELECT x.* FROM t_x x WHERE x.tenant_id = :tenantId " +
        "AND (CAST(:param AS text) IS NULL OR x.col ILIKE '%' || CAST(:param AS text) || '%')",
        nativeQuery = true)
```
**Apply to:** all 4 new repository methods (`ClienteRepository`, `ProcessoRepository`, `DocumentoRepository`, `ParecerSolicitacaoRepository`). `tenant_id = :tenantId` is always the first, non-optional predicate — never `CAST`-guarded, never skippable (PITFALLS.md Pitfall 1). Every nullable/optional bind parameter must be `CAST(:param AS <type>)`-wrapped, or Postgres throws `could not determine data type of parameter` at runtime.

### Migration header boilerplate
**Source:** every file in `backend/migrations/*.sql` (8 existing files, near-identical 3-paragraph header — see `96-add-notificacao-snoozed-until.sql` full text above)
**Apply to:** `111-enable-search-extensions.sql` — copy the "REQUIRED manual production migration" + `ddl-auto: validate` + "no Flyway/Liquibase, run manually per environment" paragraphs verbatim, adjusted for this file's specific purpose (enabling `unaccent`/`pg_trgm`, not a table/column change — note this migration's DDL doesn't interact with `ddl-auto` validation the way column/table migrations do, since `CREATE EXTENSION` isn't part of Hibernate's schema model at all; rephrase the "why" paragraph accordingly rather than copying it unchanged).

### RBAC matrix (ground truth for the 4 branch-gate scopes + the excluded 5th)
**Source:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:293-353`

| Scope | ADMIN | ADVOGADO | TECNICO | ASSISTENTE |
|---|---|---|---|---|
| `clientes:view` | Y | Y | Y | Y |
| `processos:view` | Y | Y | Y | Y |
| `documentos:view` | Y | Y | Y | Y |
| `pareceres:view` | Y | Y | Y | Y |
| `financeiro:view` | Y | Y | Y | **N** |

**Apply to:** `SearchController`'s 4 branch gates use only the first 4 rows. `financeiro:view` is listed here only as the negative-control scope for the RBAC-matrix test — `Honorario`/`financeiro` must never be queried by `SearchController` at all (no branch exists for it), so this scope's presence/absence should have **zero** observable effect on any search response, for any role. All 4 seeded roles currently hold all 4 real target scopes — the per-branch gate is correctness-for-the-future (custom DB-managed roles via `rbac:manage`), not something today's fixtures alone would catch a regression in.

### Testcontainers IT harness
**Source:** `NotificacaoRepositoryIT.java:41-54` (class annotations + container declaration)
**Apply to:** `SearchRepositoryIT` — identical `@DataJpaTest` + `@AutoConfigureTestDatabase(Replace.NONE)` + `@Testcontainers` + `@Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")` quadruple. This is also where the `111-enable-search-extensions.sql` migration's `CREATE EXTENSION` statements need to actually run against the test container before the new repository methods are exercised — `@DataJpaTest` relies on `ddl-auto` (not the manual migration scripts) to create tables from entity mappings, but `CREATE EXTENSION` has no entity-mapping equivalent, so the test setup must execute those two statements itself (e.g. via `entityManager.createNativeQuery(...)` in a `@BeforeAll`/`@BeforeEach`, mirroring how `forcarCreatedAt` in the analog already uses `entityManager.createNativeQuery(...)` for out-of-band SQL, lines 192-197) — otherwise `unaccent()`/`ILIKE` calls in the new queries will fail against the fresh Testcontainers database with "function unaccent(text) does not exist."

### Controller unit test without Spring context
**Source:** `ResourceControllerUploadDocumentoTest.java` header comment + lines 57-108 (see full excerpt above)
**Apply to:** `SearchControllerTest` — `@ExtendWith(MockitoExtension.class)`, `@Mock` per repository, direct `new SearchController(...)` construction, `SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities))` before each call, `@AfterEach SecurityContextHolder.clearContext()` — this is the only controller-unit-test convention this codebase has; do not introduce MockMvc/`@SpringBootTest` for this (confirmed explicitly absent, and out of step with existing style).

---

## No Analog Found

None of the 9 files lack a usable analog outright. Two narrower gaps exist within otherwise strong analogs (already called out inline above, repeated here for visibility):

| File | Gap | Where the missing piece comes from instead |
|---|---|---|
| `backend/migrations/111-enable-search-extensions.sql` | No existing migration issues `CREATE EXTENSION` (all 8 prior files are `CREATE TABLE`/`ALTER TABLE`/`UPDATE`) | STACK.md's Installation section (exact 2-line SQL body); the header/wrapper convention is still a strong analog |
| `ClienteRepository`/`ProcessoRepository`/`DocumentoRepository`/`ParecerSolicitacaoRepository` new methods | `unaccent()` wrapping and tiered exact/prefix/substring `ORDER BY` ranking have no in-repo precedent (existing native queries are unranked) | STACK.md's Installation section + PITFALLS.md Pitfall 4's ranking guidance, layered on top of the proven CAST-guard/`ILIKE` idiom from `ParecerSolicitacaoRepository`/`NotificacaoRepository` |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{controllers,repositories,dtos,models,config,seed}/`, `backend/src/test/java/com/lexcv/{controllers,repositories}/`, `backend/migrations/`, `backend/pom.xml`
**Files scanned (read directly this session):** `ParecerSolicitacaoRepository.java`, `NotificacaoRepository.java`, `TimelineItemDto.java`, `ParecerPesquisaController.java`, `NotificacaoController.java`, `NotificacaoRepositoryIT.java`, `ResourceControllerUploadDocumentoTest.java` (partial), `ResourceController.java` (targeted sections: imports/class header, `getTenantId`, `listClientes`, `listProcessos`, `getTimeline`, `listDocumentos`/`listProcessoDocumentos`/`listClienteDocumentos`, `executarTransicao`'s `hasAnyAuthority` usage), `ClienteRepository.java`, `ProcessoRepository.java`, `DocumentoRepository.java`, `Cliente.java`, `Processo.java`, `Documento.java`, `ParecerSolicitacao.java`, `Honorario.java`, `UserPrincipal.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `DatabaseSeeder.java` (RBAC section), 3 migration files (`96-`, `86-`, `74-`), `pom.xml` (Testcontainers/Failsafe sections)
**Pattern extraction date:** 2026-07-18
