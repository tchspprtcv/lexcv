# Phase 86: Infraestrutura de Notificações — Entidade, API e Targeting - Pattern Map

**Mapped:** 2026-07-08
**Files analyzed:** 8 (4 new backend classes, 1 new migration, 1 new test, 2 modified files)
**Analogs found:** 8 / 8 (2 are partial/first-of-kind — flagged explicitly, not silently treated as exact)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/models/Notificacao.java` | model | CRUD | `models/AuditLog.java` (polymorphic ref) + `models/Prazo.java` (UUID id/boolean/createdAt shape) | role-match (composed from 2 analogs, no single existing entity has both shapes) |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | repository | CRUD | `repositories/ParecerSolicitacaoRepository.java` (optional-filter native query) + `repositories/AuditLogRepository.java` (derived tenant-scoped finder) | role-match |
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | service | CRUD (write choke point) | `services/SetupService.java` (persistence-writing `@Service`) + `services/RiscoPrazoService.java` (immediately-prior sibling in same package) | role-match |
| `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` | controller | request-response | `controllers/ParecerPesquisaController.java` (dedicated-controller-extraction precedent) + `ResourceController.java` `togglePrazoConcluido`/`listEventos` excerpts | exact (extraction precedent) |
| `backend/migrations/86-create-notificacao-table.sql` | migration | batch (schema) | `backend/migrations/82-add-honorario-processo-unique-constraint.sql` | partial — header-comment convention is exact; no `CREATE TABLE` script exists anywhere in `backend/migrations/` to copy DDL shape from |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (modified) | config (seed) | batch | itself — `seedRbac()`, lines 293-349 | exact |
| `web/src/lib/permissions.ts` (modified) | config/utility | transform | itself — `KNOWN_SCOPES`, lines 6-13 | exact |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` (proposed name) | test | request-response (dual-scope auth logic) | `services/RiscoPrazoServiceTest.java` | partial — package/style/naming convention is exact; RiscoPrazoService has zero collaborators so it needed no mocks, this test does (first Mockito usage in the backend) |

---

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/Notificacao.java` (model, CRUD)

**Analogs:** `backend/src/main/java/com/lexcv/models/AuditLog.java` (full file, 52 lines) + `backend/src/main/java/com/lexcv/models/Prazo.java` (full file, 58 lines)

**Polymorphic reference pattern — copy verbatim from `AuditLog.java` lines 32-38:**
```java
// Values: processo | documento | conflict_check_decisao
@Column(name = "entidade_tipo", nullable = false)
private String entidadeTipo;

// String to accommodate both UUID and Integer IDs across entities
@Column(name = "entidade_id", nullable = false)
private String entidadeId;
```
This is the *only* precedent in the codebase for referencing entities with mixed primary-key types (`UUID` for `Processo`/`Cliente`/`Documento`/`ParecerSolicitacao`, `Integer` for `Prazo`/`Evento`/`Honorario`). Reuse the exact comment too — it documents *why* `entidadeId` is `String` and not a typed FK, which is non-obvious.

**Standard entity shape (id generation, tenant_id, boolean flag, createdAt) — from `Prazo.java` lines 1-57:**
```java
@Entity
@Table(name = "t_prazo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prazo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    ...
    @Column(nullable = false)
    @Builder.Default
    private Boolean concluido = false;
    ...
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```
Use `GenerationType.UUID` (matches `Prazo`/`User`/`Processo`), **not** `AuditLog`'s `GenerationType.IDENTITY`/`Long id` — `.planning/research/ARCHITECTURE.md` (Pattern 4, line 244) already fixes `NotificacaoRepository extends JpaRepository<Notificacao, UUID>`, so the entity's `id` must be `UUID` to match.

**Composed proposal (not an existing file — assembled from the two analogs above + the field list ARCHITECTURE.md Pattern 4/Data Flow specifies):**
```java
@Entity
@Table(name = "t_notificacao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notificacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    // Values: FASE_ENTRADA | DOCUMENTO_NOVO | PROCESSO_ATRIBUIDO | PARECER_ATRIBUIDO | ...
    // ROADMAP.md Phase 86 success criterion 1 calls this filter "categoria" — no existing
    // field in this codebase is named "categoria" today (AuditLog uses "acao", Evento uses
    // "tipo"); this is a new field name, first use, chosen to match the locked ROADMAP wording.
    @Column(nullable = false)
    private String categoria;

    @Column(name = "entidade_tipo", nullable = false)   // mirrors AuditLog exactly
    private String entidadeTipo;

    @Column(name = "entidade_id", nullable = false)      // mirrors AuditLog exactly
    private String entidadeId;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean lida = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

---

### `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` (repository, CRUD)

**Analogs:** `repositories/AuditLogRepository.java` (full file, 11 lines) + `repositories/ParecerSolicitacaoRepository.java` (full file, 43 lines)

**Simple tenant-scoped derived finder — copy shape from `AuditLogRepository.java` (full file):**
```java
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId);
}
```
Directly gives the bounded/no-pagination bell-dropdown shape ARCHITECTURE.md specifies (Pattern 4, lines 244-247):
```java
List<Notificacao> findTop50ByTenantIdAndDestinatarioIdOrderByCreatedAtDesc(UUID tenantId, UUID destinatarioId);
long countByTenantIdAndDestinatarioIdAndLidaFalse(UUID tenantId, UUID destinatarioId);
```

**Optional-filter native query with `Pageable`, needed for `GET /notificacoes`'s categoria/lida filters + pagination — pattern from `ParecerSolicitacaoRepository.java` lines 24-41:**
```java
@Query(value = "SELECT s.* FROM t_parecer_solicitacao s " +
        "LEFT JOIN t_parecer_versao v ON v.solicitacao_id = s.id " +
        "AND v.numero_versao = (SELECT MAX(v2.numero_versao) FROM t_parecer_versao v2 WHERE v2.solicitacao_id = s.id) " +
        "WHERE s.tenant_id = :tenantId " +
        "AND (CAST(:clienteId AS uuid) IS NULL OR s.cliente_id = CAST(:clienteId AS uuid)) " +
        ...
        nativeQuery = true)
List<ParecerSolicitacao> pesquisar(@Param("tenantId") UUID tenantId, ...);
```
Note the two load-bearing details this pattern depends on: (1) `nativeQuery = true` is required because `ILIKE`/optional-filter idiom is Postgres-specific, not portable JPQL; (2) every nullable `@Param` is wrapped in an explicit `CAST(:param AS type)` — PostgreSQL cannot infer the type of a bare `null` bind inside `(:param IS NULL OR ...)` and fails with "could not determine data type of parameter" at runtime otherwise.

**Combining this with `Pageable`/`Page<Notificacao>` for the history endpoint has no existing precedent** — see "No Analog Found" below; it is a composition of two separately-precedented things, not a copy of one.

---

### `backend/src/main/java/com/lexcv/services/NotificacaoService.java` (service, CRUD)

**Analogs:** `services/SetupService.java` (full file, 126 lines) + `services/RiscoPrazoService.java` (full file, 47 lines)

**`@Service` + constructor injection + build-entity-then-save shape — from `SetupService.java` lines 21-35, 66-86:**
```java
@Service
@RequiredArgsConstructor
public class SetupService {
    private final SystemSettingRepository systemSettingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    ...
    Tenant tenant = Tenant.builder()
            .nome(request.getClientName().trim())
            ...
            .build();
    tenant = tenantRepository.save(tenant);

    User adminUser = User.builder()
            .tenantId(tenant.getId())
            ...
            .build();
    userRepository.save(adminUser);
```
This is the closest existing analog for a `@Service` whose job is "build an entity from inputs, then `repository.save(...)` it" — `RiscoPrazoService` is pure computation with zero repository dependency, so it does not demonstrate the save/persist half of what `NotificacaoService` needs, but it **is** the immediately-prior sibling in the exact same package and is the right analog for structural/comment conventions (see below).

**Structural/comment convention — copy from `RiscoPrazoService.java` (full file), since it's the newest file in `services/`:**
```java
@Service
@RequiredArgsConstructor
public class RiscoPrazoService {
    public static final String OK = "ok";
    ...
    public String computeRisco(LocalDate dataLimite, String prioridade, LocalDate hoje) {
        Objects.requireNonNull(hoje, "hoje não pode ser nulo");
        ...
    }
}
```
Portuguese inline comments explaining *why* a design choice was made (not just *what* the code does) is the established comment style in this package — replicate it in `NotificacaoService` (e.g. document why `criar(...)` is the sole save choke point).

**Fan-out recipient resolution — the ADMIN lookup already exists as a ready-made repository method, `UserRepository.java` lines 16-17:**
```java
@Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName")
List<User> findByTenantIdAndRoleName(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);
```
This is an exact, already-built precedent for "get every ADMIN in this tenant" — `NotificacaoService`'s "+ADMIN" fan-out rule (CONTEXT.md decisions, ROADMAP success criterion 3) should call `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` directly rather than reinventing role filtering.

**Recommended shape (composed from the above, matching ARCHITECTURE.md Pattern 1/4's specified API):**
```java
@Service
@RequiredArgsConstructor
public class NotificacaoService {
    private final NotificacaoRepository notificacaoRepository;
    private final UserRepository userRepository;

    // The ONE place notificacaoRepository.save(...) is allowed to be called from.
    public Notificacao criar(UUID tenantId, UUID destinatarioId, String categoria,
                              String titulo, String mensagem,
                              String entidadeTipo, String entidadeId, String linkUrl) {
        Notificacao n = Notificacao.builder()
                .tenantId(tenantId)
                .destinatarioId(destinatarioId)
                .categoria(categoria)
                .titulo(titulo)
                .mensagem(mensagem)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .linkUrl(linkUrl)
                .build();
        return notificacaoRepository.save(n);
    }

    // Fan-out: one row per current ADMIN of the tenant, in addition to any direct recipient.
    private void notificarAdmins(UUID tenantId, String categoria, String titulo,
                                  String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
        for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
            criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
        }
    }
}
```

---

### `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java` (full file, 59 lines) — this is the *exact* precedent CONTEXT.md names for "dedicated controller extracted instead of growing `ResourceController`."

**Imports + class shell + duplicated-per-controller `getTenantId()` helper (lines 1-41):**
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
...

@RestController
@RequestMapping("/api/v1/pareceres/pesquisa")
@RequiredArgsConstructor
public class ParecerPesquisaController {

    private final ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    private UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getTenantId();
    }

    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping
    public ResponseEntity<?> pesquisarSolicitacoes(...) { ... }
}
```
Note the header comment (lines 20-29) documents *why* this is a separate top-level `@RequestMapping` rather than a sub-path of an existing controller — a `NotificacaoController` class doc-comment referencing this same CONTEXT.md/ARCHITECTURE.md rationale (avoid growing the ~2900-line `ResourceController`) is the matching convention.

**CRITICAL — this controller needs a second identity helper `NotificacaoController` doesn't get from the analog:** every existing controller's `getTenantId()` reads `principal.getTenantId()`; this is the **first** controller that also needs `principal.getUserId()` (field confirmed on `UserPrincipal.java` line 18, `@Getter`-exposed as `getUserId()`) to scope every query by *both* tenant and recipient:
```java
private UUID getUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getUserId();
}
```

**PATCH-single-entity-by-id pattern (mark one notification as read) — from `ResourceController.java` lines 1501-1539 (`togglePrazoConcluido`):**
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")
@PatchMapping("/processos/{id}/prazos/{prazoId}/concluido")
public ResponseEntity<?> togglePrazoConcluido(
        @PathVariable UUID id,
        @PathVariable UUID prazoId,
        @RequestBody Map<String, Boolean> body) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    Prazo prazo = prazoRepository.findById(prazoId).orElse(null);
    if (prazo == null || !prazo.getTenantId().equals(tenantId) || !prazo.getProcessoId().equals(id)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Prazo não encontrado"));
    }
    boolean nowConcluido = Boolean.TRUE.equals(body.get("concluido"));
    prazo.setConcluido(nowConcluido);
    ...
    Prazo saved = prazoRepository.save(prazo);
    return ResponseEntity.ok(response);
}
```
For `PATCH /notificacoes/{id}/lida`, replicate this exactly but add the **second** scoping check this codebase has never needed before:
```java
Notificacao n = notificacaoRepository.findById(id).orElse(null);
if (n == null || !n.getTenantId().equals(tenantId) || !n.getDestinatarioId().equals(getUserId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Notificação não encontrada"));
}
n.setLida(true);
notificacaoRepository.save(n);
```
Returning 404 (not 403) when the notification belongs to a *different* destinatario in the *same* tenant matches this codebase's existing convention of not leaking existence across an authorization boundary (same 404-for-cross-tenant pattern `togglePrazoConcluido` already uses).

**GET-with-optional-filters pattern — from `ResourceController.java` lines 2113-2119 (`listEventos`):**
```java
@PreAuthorize("hasAuthority('agenda:view')")
@GetMapping("/eventos")
public ResponseEntity<?> listEventos(
        @RequestParam(required = false) String dataInicio,
        @RequestParam(required = false) String dataFim,
        @RequestParam(required = false) UUID processoId,
        @RequestParam(required = false) Boolean concluido) {
    UUID tenantId = getTenantId();
    List<Evento> eventos = eventoRepository.findByTenantId(tenantId);
    if (processoId != null) { eventos.removeIf(e -> ...); }
    ...
}
```
For `GET /notificacoes?categoria=&lida=&page=&size=`, the `@RequestParam(required = false)` per-filter shape is the same; the difference is the underlying query pushes filters into SQL via the `Pageable`+native-query composition (see repository section above) rather than in-memory `removeIf`, because the notification table is expected to grow unboundedly (ARCHITECTURE.md Scaling Considerations) whereas `Evento` rows are not.

**`unread-count` endpoint — trivial single-count precedent (shape only, no filters needed):**
```java
@PreAuthorize("hasAuthority('notificacoes:view')")
@GetMapping("/notificacoes/unread-count")
public ResponseEntity<?> unreadCount() {
    long count = notificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse(getTenantId(), getUserId());
    return ResponseEntity.ok(Map.of("count", count));
}
```

**`POST /notificacoes/ler-todas` (mark-all-read) — no bulk-update precedent exists anywhere in this codebase** (`grep "@Modifying"` across all of `com.lexcv` returns zero matches). Every existing write path in this codebase loads entities via `findBy...`, mutates them in Java, and calls `.save(...)` — never a raw `UPDATE ... WHERE` bulk statement. Follow that same load-mutate-save convention rather than introducing the first `@Modifying @Query` in this codebase:
```java
List<Notificacao> naoLidas = notificacaoRepository.findByTenantIdAndDestinatarioIdAndLidaFalse(tenantId, userId);
naoLidas.forEach(n -> n.setLida(true));
notificacaoRepository.saveAll(naoLidas);
```

---

### `backend/migrations/86-create-notificacao-table.sql` (migration, batch/schema)

**Analog:** `backend/migrations/82-add-honorario-processo-unique-constraint.sql` (full file, 22 lines; `81-add-facto-ordem-unique-constraint.sql` is the same shape one phase earlier)

**Header-comment convention — copy verbatim structure from `82-add-honorario-processo-unique-constraint.sql`:**
```sql
-- Phase 82: DB-level backstop for Honorario(processo_id) uniqueness
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds `uniqueConstraints = @UniqueConstraint(columnNames =
-- "processo_id")` to `Honorario.java`.
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-creates this constraint locally). `ddl-auto=validate`
-- never creates or alters schema — it only checks the existing schema is compatible at
-- startup. Without this script, ...
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase —
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that adds the constraint to the entity.

ALTER TABLE t_honorario ADD CONSTRAINT uk_honorario_processo UNIQUE (processo_id);
```

**No `CREATE TABLE` precedent exists** — all 3 existing manual migrations (`74-`, `81-`, `82-`) are `ALTER TABLE`/`UPDATE` scripts against tables that already exist from earlier `ddl-auto=update` dev runs. This is the **first** manual migration in the repository's history that must create a brand-new table from scratch. Compose the `CREATE TABLE` DDL from the `Notificacao` entity's own field list above (not from any migration file, since none exists), and reuse the exact header-comment rationale style shown above. `.planning/research/ARCHITECTURE.md` (Scaling Considerations, line 312) explicitly calls for the composite index `(tenant_id, destinatario_id, lida, created_at)` "from day one":
```sql
CREATE TABLE t_notificacao (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    destinatario_id UUID NOT NULL,
    categoria       VARCHAR(255) NOT NULL,
    entidade_tipo   VARCHAR(255) NOT NULL,
    entidade_id     VARCHAR(255) NOT NULL,
    titulo          VARCHAR(255) NOT NULL,
    mensagem        TEXT NOT NULL,
    link_url        VARCHAR(255),
    lida            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_notificacao_tenant_destinatario_lida_created
    ON t_notificacao (tenant_id, destinatario_id, lida, created_at);
```

---

### `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (modified — config/seed, batch)

**Analog:** itself — `seedRbac()`, lines 293-349 (already the file to modify, not a separate analog)

**Exact insertion points:**

`permKeys` list, line 294-303 — add `"notificacoes:view"`:
```java
List<String> permKeys = Arrays.asList(
        "clientes:view", "clientes:edit",
        "processos:view", "processos:edit",
        "processos:create", "processos:manage",
        "agenda:view", "agenda:edit",
        "documentos:view", "documentos:edit",
        "financeiro:view", "financeiro:edit",
        "rbac:manage", "users:manage",
        "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage",
        "notificacoes:view"
);
```

`ADMIN` gets it automatically (line 312, `upsertRolePermissions("ADMIN", permissionMap.values())` — all keys, no per-role list to edit).

Per-role grant lists (lines 314-348) — add `permissionMap.get("notificacoes:view")` to **all three** of `ASSISTENTE`, `TECNICO`, `ADVOGADO` (CONTEXT.md decision: this scope is granted identically to all 4 profiles, unlike every other module which differentiates by role):
```java
upsertRolePermissions("ASSISTENTE", Arrays.asList(
        permissionMap.get("clientes:view"),
        ...
        permissionMap.get("pareceres:view"),
        permissionMap.get("notificacoes:view")
));

upsertRolePermissions("TECNICO", Arrays.asList(
        ...
        permissionMap.get("pareceres:view"),
        permissionMap.get("notificacoes:view")
));

upsertRolePermissions("ADVOGADO", Arrays.asList(
        ...
        permissionMap.get("pareceres:edit"),
        permissionMap.get("notificacoes:view")
));
```
`upsertRolePermissions` (lines 351-359) is idempotent (`role.getPermissions().addAll(permissions)`, only saves `if (changed)`) — no special handling needed for re-running the seeder against an already-seeded DB.

---

### `web/src/lib/permissions.ts` (modified — config/utility, transform)

**Analog:** itself — `KNOWN_SCOPES`, lines 6-13

**Exact insertion point:**
```typescript
// Canonical scope registry, mirrored from backend DatabaseSeeder.seedRbac().
// Additive only — resolveScopedPermissions/hasScopedPermission remain scope-agnostic
// (typed as `scope: string`) so existing ad-hoc call sites keep working unchanged.
export const KNOWN_SCOPES = [
  "clientes",
  "processos",
  "agenda",
  "documentos",
  "financeiro",
  "pareceres",
  "notificacoes",
] as const;
```
No other change needed in this file — `resolveScopedPermissions`/`hasScopedPermission` (lines 23-35) are already scope-agnostic (`scope: string`), so `hasScopedPermission(perms, "notificacoes", "view")` works the moment the scope string exists in the backend-issued permission set; `KNOWN_SCOPES` itself is just the mirrored registry/type source, not a runtime gate.

---

### `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` (test, request-response/auth-isolation logic)

**Analog:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java` (full file, 119 lines) — **this is the only test file in the entire backend**, so it is the sole available style precedent, but it is a *partial* match: `RiscoPrazoService` has zero collaborators (pure function over primitives), so its test needs no mocks and no Spring context. `NotificacaoService` has real collaborators (`NotificacaoRepository`, `UserRepository`) — this test is the **first** in the codebase to need Mockito.

**Style/structure to copy exactly (package, class-per-service, `@Test` per case, Portuguese method names describing the scenario, fixed/deterministic test data) — from `RiscoPrazoServiceTest.java` lines 1-23:**
```java
package com.lexcv.services;

import org.junit.jupiter.api.Test;
...
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de caracterização (JUnit 5 puro, sem contexto Spring) ...
 */
class RiscoPrazoServiceTest {

    private final RiscoPrazoService service = new RiscoPrazoService();
    private static final LocalDate HOJE = LocalDate.of(2026, 1, 15);

    @Test
    void computeRisco_dataLimiteNull_retornaOk() {
        assertEquals("ok", service.computeRisco(null, "ALTA", HOJE));
    }
    ...
}
```

**What must be added — confirmed available on the classpath (`backend/pom.xml` line 108, `spring-boot-starter-test`, no Mockito exclusion found):** Mockito for repository mocking, since there is no test database (no H2/Testcontainers dependency in `pom.xml` — confirmed by inspection), so a `@DataJpaTest` against a real schema is not a realistic option here; unit-testing `NotificacaoService`/`NotificacaoController` logic with a mocked `NotificacaoRepository` is the pattern that fits this project's actual test infrastructure today:
```java
package com.lexcv.services;

import com.lexcv.models.Notificacao;
import com.lexcv.models.User;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock private NotificacaoRepository notificacaoRepository;
    @Mock private UserRepository userRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DESTINATARIO_A = UUID.randomUUID();
    private static final UUID DESTINATARIO_B = UUID.randomUUID();

    @Test
    void criar_doisDestinatariosDistintos_geramLinhasIndependentesComEstadoLidaProprio() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link");
        service.criar(TENANT_ID, DESTINATARIO_B, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link");

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        List<Notificacao> saved = captor.getAllValues();
        assertEquals(DESTINATARIO_A, saved.get(0).getDestinatarioId());
        assertEquals(DESTINATARIO_B, saved.get(1).getDestinatarioId());
        // Independent rows, not one shared row — proves Success Criteria 2 at the write side.
    }

    @Test
    void notificarComFanOutAdmin_umaLinhaPorAdminAtualDoTenant() {
        User admin1 = User.builder().id(UUID.randomUUID()).build();
        User admin2 = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN"))
                .thenReturn(List.of(admin1, admin2));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarAdmins(TENANT_ID, "DOCUMENTO_NOVO", "t", "m", "documento", "id-2", "/link");

        verify(notificacaoRepository, times(2)).save(any());
        // Fan-out: one row per current ADMIN, not one shared row with an "is admin" flag —
        // proves Success Criteria 3.
    }
}
```

**If the planner instead wants HTTP-layer dual-scoping coverage (tenant AND destinatario enforcement on read/mark-read)**, the same zero-Spring-context Mockito approach extends directly to `NotificacaoController`, since `@RequiredArgsConstructor` already generates a plain constructor Mockito/tests can call directly (no `MockMvc`/`@SpringBootTest` needed — that infrastructure does not exist in this project today and would be a much larger new precedent than adding Mockito). The one genuinely new technique required is manually seeding `SecurityContextHolder`, since every controller's `getTenantId()`/`getUserId()` reads it directly (`ResourceController.java` lines 117-121, `ParecerPesquisaController.java` lines 37-41) but no test in this repo has populated it yet:
```java
UserPrincipal principalA = UserPrincipal.builder().userId(DESTINATARIO_A).tenantId(TENANT_ID).build();
SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principalA, null, List.of()));
// ... call controller.marcarLida(notificacaoId, ...) as user A, then swap the Authentication
// for user B and assert the same notification row is unreachable/unaffected for B.
```
Either shape (service-level or controller-level) satisfies ROADMAP Phase 86 Success Criterion 2 ("dois utilizadores de teste... listas independentes... marcar lida por um nunca afeta o outro"); the service-level version above is recommended as the primary test because it requires the least new test infrastructure while still proving the exact isolation property.

---

## Shared Patterns

### Tenant scoping (`getTenantId()`)
**Source:** `ResourceController.java` lines 117-121, duplicated identically in `ParecerController.java` and `ParecerPesquisaController.java` lines 37-41 (no shared base class/utility — intentional per-controller duplication is the established convention, not an oversight).
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```
**Apply to:** `NotificacaoController` (copy the duplication convention — do not extract a shared base class as part of this phase).

### Dual scoping (`tenant_id` AND `destinatario_id`) — NEW pattern, first use in this codebase
**Source:** No existing controller does this; CONTEXT.md is explicit that this is a first ("não copiar o padrão de autorização de nenhum outro endpoint sem adicionar esta segunda dimensão"). Compose from `getTenantId()` above plus the equivalent `getUserId()` reading `UserPrincipal.getUserId()` (field confirmed at `UserPrincipal.java` line 18).
**Apply to:** Every `NotificacaoController` query and mutation, and every `NotificacaoRepository` method — there must be no method that filters by `tenantId` alone.

### RBAC seeding
**Source:** `DatabaseSeeder.java` `seedRbac()`, lines 293-349 (see full excerpt in Pattern Assignments above).
**Apply to:** `notificacoes:view`, granted identically to all 4 roles (unlike every other scope, which differentiates by role) — this is a locked CONTEXT.md decision, not a judgment call for the planner.

### Fan-out admin resolution
**Source:** `UserRepository.java` lines 16-17, `findByTenantIdAndRoleName(tenantId, "ADMIN")` — already exists, ready to use as-is.
**Apply to:** `NotificacaoService`'s "+ADMIN" rule for every notification-producing method (this phase only needs it to exist and work correctly against manual/test calls; real trigger call sites are Phase 87/88).

### Error/404 response shape
**Source:** `ResourceController.java` (`togglePrazoConcluido`, `listPartes`, etc.) — consistent `ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "..."))` for any "not found or not yours" case; unhandled exceptions fall through to `GlobalExceptionHandler.java` (`@RestControllerAdvice`, lines 15-50) which returns 500 with `{error, message}` for anything uncaught, and 400 with field-level errors for `MethodArgumentNotValidException`/`ConstraintViolationException`.
**Apply to:** `NotificacaoController` — return 404 (never 403) when a notification exists but belongs to a different tenant or a different destinatario than the caller, exactly matching the existing "don't leak existence across an authorization boundary" convention.

### Load-mutate-save over bulk SQL
**Source:** Absence of any `@Modifying` query anywhere in `com.lexcv` (confirmed by search) combined with `togglePrazoConcluido`'s find-then-save shape.
**Apply to:** `POST /notificacoes/ler-todas` — fetch the caller's unread rows and `saveAll(...)` them, rather than introducing the first raw bulk-update query in this codebase.

---

## No Analog Found

| File/Concern | Role | Data Flow | Reason |
|---|---|---|---|
| `CREATE TABLE t_notificacao` DDL shape | migration | batch | All 3 existing manual migrations (`74-`, `81-`, `82-`) are `ALTER TABLE`/`UPDATE` scripts against tables Hibernate already created via `ddl-auto=update` in dev; none creates a table from scratch. The header-comment convention is fully precedented; the DDL body must be composed from the entity's own field list (see Pattern Assignments) instead of copied from a migration file. |
| `Pageable`/`Page<Notificacao>` on the repository | repository | CRUD | Confirmed by direct search: zero uses of `Pageable`/`Page<` anywhere in `backend/src/main/java/com/lexcv` today. `.planning/research/ARCHITECTURE.md` (Pattern 4) explicitly flags this as a deliberate first-of-its-kind introduction, narrowly scoped to the `/notificacoes` history endpoint only — the bell/unread-count endpoints deliberately stay on the simpler bounded-`List`/`count` shape instead. |
| Mockito-based service/controller unit test | test | request-response | `RiscoPrazoServiceTest.java` is the only existing test and has zero collaborators to mock. `spring-boot-starter-test` (pom.xml line 108) brings Mockito transitively with no exclusions, so it is available — just never yet used. No `@DataJpaTest`/H2/Testcontainers infrastructure exists to justify a real-database test instead. |
| Bulk "mark all as read" | controller/repository | request-response | No `@Modifying` query exists anywhere in the codebase. Recommend the load-mutate-`saveAll` pattern instead of introducing the first bulk-update query (see Shared Patterns). |

**Explicitly out of this phase's file list but worth flagging for the planner's awareness (per `.planning/research/ARCHITECTURE.md`'s broader Modified Files table, not per CONTEXT.md's locked decisions for *this* phase):** `AdminController.getRbac()` (lines 222, 228 show the `systemPermissions` display list pattern that `notificacoes:view` would also need to appear in for the admin RBAC-management screen to show/toggle it) and `UserPrincipal.java`'s hardcoded ADMIN bonus-permission list (lines 33-44, duplicating `seedRbac()`'s ADMIN grants). CONTEXT.md's decisions section for Phase 86 names only `DatabaseSeeder.seedRbac()` and `web/src/lib/permissions.ts` as the RBAC touch points for this phase — these two additional files are not in scope here, but since `notificacoes:view` is granted to ADMIN via `seedRbac()`'s `permissionMap.values()` regardless, the `UserPrincipal` hardcoded list is likely redundant for this specific scope (ADMIN gets it through normal seeding either way) and is safe to leave untouched.

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{models,repositories,services,controllers,config,seed,dtos}`, `backend/src/test/java/com/lexcv/services`, `backend/migrations`, `web/src/lib/permissions.ts`, `backend/pom.xml` (test-dependency check), `.planning/ROADMAP.md` (Phase 86 success criteria).
**Files scanned:** 28 repositories, 6 controllers, 3 services, 3 migrations, 1 test file, all read or grepped directly; `Notificacao`-adjacent models (`AuditLog`, `Prazo`, `User`) read in full.
**Pattern extraction date:** 2026-07-08
