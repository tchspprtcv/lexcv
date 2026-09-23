# Phase 128: Auditoria de Atribuições de Papéis - Pattern Map

**Mapped:** 2026-09-22
**Files analyzed:** 11 (backend: model, repository, 2 controllers with 7 write paths, 1 new paginated endpoint, migration, tests; frontend: settings tab, hook, types)
**Analogs found:** 9 / 11 exact-or-close, 2 flagged "No Analog Found" (narrowed repository, structural immutability gate)

No RESEARCH.md for this phase (skipped per config). This map is CODE analogs only — a UI-SPEC is being produced in parallel; do not duplicate its design decisions here.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/.../models/AuditLog.java` | model | CRUD (append-only) | itself (add `detalhe`, `@Immutable`, vocabulary comments) | exact — modify in place |
| `backend/.../repositories/AuditLogRepository.java` | repository | CRUD (write-only + query) | itself, narrowing base interface — no precedent for `Repository<T,ID>` anywhere in this codebase (see No Analog Found) | partial — narrowing itself is novel, method list is not |
| `OfficeRolesController.createRole/renameRole/deleteRole` (write paths) | controller | CRUD | itself (needs `@Transactional` added, currently absent) + `ParecerController`/`ResourceController`'s ten `auditLogRepository.save(AuditLog.builder()...)` sites | exact — compose two known patterns |
| `AdminController.updateRbac/createUser/updateUser/deleteUser` (write paths) | controller | CRUD | itself (needs `@Transactional` added, currently absent — `updateRbac` has an explicit "Sem @Transactional, de propósito" comment that stops applying once a second write (audit) is added) + the same ten audit-write sites | exact — compose two known patterns |
| New `GET` paginated RBAC-audit query endpoint | controller | request-response (paginated read) | `NotificacaoController.listar` (lines 69-89) + `NotificacaoRepository.buscarPorFiltros` (Page/Pageable idiom) | exact |
| `backend/migrations/128-*.sql` + README rows | migration | batch/DDL | `backend/migrations/124-add-permission-catalogo-columns.sql` (idempotent `ADD COLUMN IF NOT EXISTS`) + `backend/migrations/README.md` inventory tables | exact |
| Backend tests (write-path audit, tenant isolation) | test | request-response | `OfficeRolesControllerTest` (two-tenant isolation, `lenient()` + `verify(..., never())`) + `AdminControllerAtribuicaoPapeisEscritorioTest` (same idiom, cited in `127-PATTERNS.md`) | exact |
| Backend structural immutability test/gate | test | n/a | none in this codebase — first "prove an API surface has no mutating method" backend test | no analog — composed guidance only |
| `web/src/app/(dashboard)/settings/page.tsx` (new "Auditoria RBAC" tab) | component | request-response | `settings/page.tsx`'s own tab-wiring block (lines 79-200, `hasRbacManage`-gated `rbac` tab) + `processos/[id]/page.tsx`'s "Auditoria" `TabsContent` (lines 2323-2378, literal audit-list rendering) | exact — compose two blocks already in this codebase |
| `web/src/hooks/use-admin.ts` (new paginated audit hook) | hook | request-response | `web/src/hooks/use-notificacoes.ts` (`useNotificacoes`, full pagination query-key/filter shape) + `use-processos.ts`'s `useAuditLog` (simpler non-paginated audit shape) | exact |
| New types (`AuditLogEntry`-style + page envelope) | type | n/a | `web/src/types/processos.ts#AuditLogEntry` (lines 300-307) + `web/src/types/notificacoes.ts#NotificacoesPageResponse` (lines 32-38) | exact |

---

## Pattern Assignments

### 1. `AuditLog.java` — the entity to extend

**Full current file** (`backend/src/main/java/com/lexcv/models/AuditLog.java`, 51 lines, read in full):
```java
@Entity
@Table(name = "t_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // nullable: documents not always linked to a processo (Pitfall #2)
    @Column(name = "processo_id")
    private UUID processoId;

    // Values: transicao_estado | conflict_check_decisao | documento_download | documento_eliminacao
    @Column(name = "acao", nullable = false)
    private String acao;

    // Values: processo | documento | conflict_check_decisao
    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;

    // String to accommodate both UUID and Integer IDs across entities
    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;

    // nullable: system events without a user actor
    @Column(name = "autor_id")
    private UUID autorId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}
```
Three concrete additions required by CONTEXT.md:
1. A new nullable `detalhe` column/field (Decision 2) — additive, so `@Column(name = "detalhe")` with no `nullable = false`, keeping the ten existing `.builder()` call sites (which never set it) valid without changes.
2. Update the two "Values:" comments on `acao`/`entidadeTipo` to add the new RBAC vocabulary (e.g. `papel_criar | papel_renomear | papel_apagar | papel_permissoes_alterar | papel_atribuir | papel_retirar` for `acao`, and `tenant_role | user_tenant_role` — or whatever names Claude's Discretion settles on — for `entidadeTipo`) — this is literally what Decision 1 asks for ("Actualizar esses comentários com os novos valores").
3. Add `@Immutable` from `org.hibernate.annotations.Immutable` at class level (Decision 6). **Confirmed nowhere else in this codebase** (`grep -rn "@Immutable"` across `backend/src` returns nothing) — this is the first use. Hibernate version is **6.6.4.Final** (resolved transitively by `spring-boot-starter-parent:3.4.1`, confirmed present in the local `.m2` alongside 6.5.3/6.6.18/6.6.33/6.6.41/6.6.53 — no explicit override in `pom.xml`), where `@Immutable` on an entity disables dirty-checking (no `UPDATE` is ever generated for a loaded instance) and is fully compatible with `@Builder`/`@AllArgsConstructor`-based construction plus `@PrePersist`, which still fires normally on first `save()` — only post-load mutation tracking is suppressed. No known behavioral caveat for this Hibernate version affects a straightforward "insert-only" entity like this one.

---

### 2. `AuditLogRepository.java` — narrowing, and the complete inventory of methods that must survive

**Full current file** (`backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java`, 10 lines):
```java
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId);
}
```

**Every call site found by grep across `backend/src` (main AND test), verified line-by-line — inherited methods actually used:**

| Caller | File:line | Method used |
|---|---|---|
| `ParecerController` (5 sites) | `ParecerController.java:188,345,394,448,572` | `save(AuditLog)` |
| `ResourceController` (5 sites) | `ResourceController.java:1168,1430,1629,2927,2972` | `save(AuditLog)` |
| `ResourceController.getAuditLog` | `ResourceController.java:2364-2365` | `findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID, UUID)` (the repo's own declared query, not inherited) |
| Four test files (`ParecerControllerEntregaProveniencaTest`, `ParecerControllerProveniencaPapelTest`, `ResourceControllerProveniencaPapelTest`, `ResourceControllerUploadDocumentoTest`) | `@Mock private AuditLogRepository auditLogRepository;` + `lenient().when(auditLogRepository.save(any())).thenReturn(null);` | `save(AuditLog)` only |

**Conclusion: the ONLY inherited `JpaRepository` methods used anywhere in `backend/src` (main or test) are `save(S)`.** No caller anywhere uses `findAll`, `findById`, `saveAll`, `delete*`, `count`, or any other inherited method. This means the narrowed interface Decision 6 asks for needs to declare exactly:
```java
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog save(AuditLog auditLog);
    List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId);
    // + whatever new paginated RBAC-scoped finder Plan work adds, e.g.:
    // Page<AuditLog> findByTenantIdAndEntidadeTipoIn(UUID tenantId, Collection<String> tipos, Pageable pageable);
}
```
`org.springframework.data.repository.Repository<T, ID>` is the correct base (Spring Data Commons' marker interface with zero declared methods) — extending it and declaring `save`/finders explicitly is the standard Spring Data idiom for "expose only what's needed," confirmed compatible with Spring Data JPA's repository-proxy mechanism (it still detects and implements query-derivation and `@Query` methods the same way `JpaRepository` does; only the inherited convenience methods disappear from the compiled API). **This exact narrowing pattern has no precedent anywhere else in this codebase** — `grep -rn "extends Repository<\|extends CrudRepository<"` across `backend/src/main/java/com/lexcv/repositories` returns nothing; every other repository in this codebase extends `JpaRepository`. Flagged under "No Analog Found" below — the ten write call sites (`.save(...)`) and the one query call site require zero changes to compile once the narrowed interface declares those two method signatures; the four test files' `@Mock`/`when(...).save(any())` stubs also continue to compile unchanged, since Mockito mocks against the interface's own declared methods.

---

### 3. The ten `auditLogRepository.save(AuditLog.builder()...)` sites — the write pattern to copy

**Representative excerpt** (`ResourceController.java:1163-1178`, `atribuirResponsavel` — WR-03, Phase 87 code review):
```java
// WR-03 (Phase 87 code review, iteration 2): audit record, mirroring
// ParecerController.atribuirAdvogado's identical pattern for the same
// class of action (reassigning a responsible party).
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
auditLogRepository.save(AuditLog.builder()
        .tenantId(tenantId)
        .processoId(saved.getId())
        .acao("processo_atribuir")
        .entidadeTipo("processo")
        .entidadeId(saved.getId().toString())
        .autorId(principal.getUserId())
        .build());
```
**Second excerpt** (`ParecerController.java:185-195`, `createSolicitacao` — PARA-01):
```java
// Audit record — PARA-01: autor_id from SecurityContext
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
auditLogRepository.save(AuditLog.builder()
        .tenantId(tenantId)
        .processoId(saved.getProcessoId())
        .acao("parecer_criar")
        .entidadeTipo("parecer_solicitacao")
        .entidadeId(saved.getId().toString())
        .autorId(principal.getUserId())
        .build());
```
Every one of the ten sites follows this exact shape: resolve `principal` from `SecurityContextHolder` (never trust a body value — matches CONTEXT.md Decision 3's "O autor e o tenant vêm do principal autenticado, nunca do corpo do pedido"), call `.save(AuditLog.builder()...)` **after** the domain write it describes has already been persisted, never before. `processoId` is set when the event is processo-scoped; RBAC events have no `processoId` (the field is already nullable — "Pitfall #2" comment — so simply omit `.processoId(...)` in the new RBAC builder calls, same as how `entidadeId` accepts a `String` specifically to fit both UUID (`TenantRole.id`) and other id shapes). The new `detalhe` field (Decision 2) is the one addition every new RBAC call makes that none of the existing ten make.

---

### 4. `OfficeRolesController`'s write paths — current transaction boundary, verbatim

**Confirmed by grep: no `@Transactional` import and no `@Transactional` annotation anywhere in `OfficeRolesController.java`.** Full imports block (lines 1-34) has no `org.springframework.transaction.annotation.Transactional`. Each handler currently makes exactly **one** repository write:

- `createRole` (`OfficeRolesController.java:119-174`): validates, then `tenantRoleRepository.save(novoPapel)` inside a `try { } catch (DataIntegrityViolationException ex) { ... }` (for the concurrent-duplicate-name race), returns 201.
- `renameRole` (`:186-220`): validates, then `tenantRoleRepository.save(tenantRole)`, same `try/catch` shape, returns 200.
- `deleteRole` (`:253-304`): three cheap-to-expensive guards (cross-tenant 404, protected-role 409, assignment-count 409), then `tenantRoleRepository.deleteById(id)` inside its own `try { } catch (DataIntegrityViolationException ex) { ... }` (for a concurrent assignment race caught at the FK), returns 204.

**Consequence for Decision 3 ("o evento e a mudança gravam na mesma transacção"):** today each handler's single write is atomic on its own (Spring Data's `SimpleJpaRepository` wraps each repository method in its own transaction by default), but adding a *second* write (the audit `save`) after it, inside a handler with no `@Transactional`, means the two writes commit as **two separate transactions** — the exact "pode faltar quando a mudança aconteceu" failure Decision 3 forbids. **The planner must add `@Transactional` (from `org.springframework.transaction.annotation.Transactional`) at the method level on `createRole`, `renameRole`, and `deleteRole`**, wrapping both the domain write and the new audit `save` in one boundary. Note the existing `catch (DataIntegrityViolationException ex)` blocks inside each handler already run *inside* what will become the new transactional boundary — verify in review that catching a `DataIntegrityViolationException` thrown by a flush inside a now-`@Transactional` method still returns a clean 409 rather than an already-marked-rollback-only transaction breaking the response (Spring's default `@Transactional` marks the transaction rollback-only on any unchecked exception propagating out of the method, but a *caught* exception that lets the method return normally does not trigger this — the existing try/catch shape is compatible, this is a note for the plan/review to verify against the actual DB flush timing, not a blocker).

---

### 5. `AdminController`'s write paths — current transaction boundary, verbatim, including one explicit "no transaction" design comment that changes meaning

**Confirmed by grep: `@Transactional` does not appear anywhere in `AdminController.java`** (only two prose mentions of the word inside comments, both explaining its *absence*). Four handlers in scope:

- **`updateRbac`** (`:741-850`) carries this doc-comment immediately above it (`:734-739`):
```java
// Sem @Transactional, de proposito: cada guarda abaixo (id desconhecido, chave de permissao
// desconhecida, papel de plataforma, piso do administrador) recusa o pedido INTEIRO antes de
// tocar em tenantRoleRepository.save -- a fase de validacao (validate-then-write, mesmo idioma
// de PlatformAdminController.updateMoldes) e inteiramente sem efeitos secundarios, por isso um
// pedido recusado nunca deixa nada parcialmente escrito, mesmo sem uma transaccao a envolver
// as chamadas.
```
Its body does validate-then-write (`:768-836` builds a fully-validated `resolvido` map, only THEN `:838-842` loops and calls `tenantRoleRepository.save(tenantRole)` once per role). **This design comment's premise (no side effects until validation completes) survives Phase 128 unchanged, but its conclusion does not**: the comment's whole argument is that a rejected request never writes anything, so a transaction was never needed to prevent partial writes — audit logging does not change that argument for *rejected* requests (Decision 3 confirms: "Um pedido recusado ... não grava evento"). But for an *accepted* request that updates **multiple** roles in the `for` loop (`:838-842`), each iteration is its own `save()` — if a new audit-write-per-role is added inside that same loop, or one batched audit write after it, the whole loop-plus-audit sequence needs `@Transactional` to guarantee all role saves and the audit event(s) commit atomically. **This is the one write path where "no transaction because validate-then-write" was previously a complete argument and now is not** — the plan must update this comment, not just add the annotation silently, or a future reader will trust a rationale that no longer holds.
- **`createUser`** (`:324-422`): single `user = userRepository.save(user)` at line 408, no earlier writes.
- **`updateUser`** (`:424-567`): single `user = userRepository.save(user)` at line 564, no earlier writes (all mutation is in-memory on the loaded `user` before this line).
- **`deleteUser`** (`:569-598`): single `userRepository.deleteById(id)` at line 596.

All four need `@Transactional` added at the method level to satisfy Decision 3 once an audit `save` is added alongside each domain write.

---

### 6. `ResourceController.getAuditLog` — the exact analog for the new RBAC-scoped read, including name-resolution

**Full handler** (`ResourceController.java:2355-2378`):
```java
@PreAuthorize("hasAuthority('processos:manage')")
@GetMapping("/processos/{id}/audit")
public ResponseEntity<?> getAuditLog(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    List<AuditLog> entries = auditLogRepository
            .findByTenantIdAndProcessoIdOrderByTimestampDesc(tenantId, id);
    List<Map<String, Object>> result = entries.stream().map(e -> {
        String autorNome = resolveAutorNome(e.getAutorId(), tenantId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("acao", e.getAcao());
        m.put("entidadeTipo", e.getEntidadeTipo());
        m.put("entidadeId", e.getEntidadeId());
        m.put("autorNome", autorNome);
        m.put("timestamp", e.getTimestamp());
        return m;
    }).toList();
    return ResponseEntity.ok(result);
}
```
**`autor_id` → display name resolution** (`ResourceController.java:144-152`, `resolveAutorNome`, reused for `movimentacao`/`decisao`/`evento` too — lines 2314, 2339, 2367):
```java
/**
 * Resolves the display name of a user by their ID, scoped to the current tenant.
 * Returns null if autorId is null, user not found, or user belongs to a different tenant.
 */
private String resolveAutorNome(UUID autorId, UUID tenantId) {
    if (autorId == null) return null;
    User user = userRepository.findById(autorId).orElse(null);
    return (user != null && tenantId.equals(user.getTenantId())) ? user.getNome() : null;
}
```
This is a load-and-resolve-per-row helper, not a JOIN — for a paginated result set the new RBAC-audit endpoint should copy this exact idiom (resolve `autorNome` per page-of-results, not per full table), and the CONTEXT.md constraint "resolvido para apresentação na leitura" for `entidade_id`/target-user display names (Decision 2) should follow the identical pattern: resolve the target user's display name at read time from their id, never store it in `detalhe`.

**No pagination on this existing endpoint** — it returns the full `List<AuditLog>` for one `processoId`, which is naturally small. The new RBAC endpoint is tenant-wide and Decision 5 explicitly requires pagination (see pattern 8 below) — copy the response *shape* (flat `Map`/DTO per row: `id, acao, entidadeTipo, entidadeId, autorNome, timestamp`, now also `detalhe`) but not the unpaginated `List` return type.

---

### 7. Frontend read side — `useAuditLog` hook + `processos/[id]/page.tsx` rendering, the direct UI analog

**Type** (`web/src/types/processos.ts:300-307`):
```typescript
export interface AuditLogEntry {
  id: number;
  acao: string;
  entidadeTipo: string;
  entidadeId: string;
  autorNome?: string;
  timestamp: string;
}
```
**Hook** (`web/src/hooks/use-processos.ts:799-811`):
```typescript
export function useAuditLog(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);
  return useQuery({
    queryKey: ["processos", "audit", processoId],
    queryFn: () =>
      apiFetch<AuditLogEntry[]>(`/processos/${encodeURIComponent(processoId)}/audit`),
    enabled,
    staleTime: 30_000,
  });
}
```
**Rendering** (`web/src/app/(dashboard)/processos/[id]/page.tsx:2323-2378`) — loading/error/empty states, then per-row layout with a color-coded `Badge` keyed off `acao`, timestamp, entity type, truncated entity id, and optional author name:
```tsx
{auditLog.isLoading ? (
  <p className="text-sm text-slate-500 py-4">A carregar auditoria...</p>
) : auditLog.isError ? (
  <p className="text-red-600 text-sm py-4">Erro ao carregar a auditoria. Tente novamente.</p>
) : (auditLog.data ?? []).length === 0 ? (
  <div className="py-12 text-center">
    <p className="text-sm font-medium text-slate-500">Sem registos de auditoria</p>
    <p className="text-xs text-slate-400 mt-1">...</p>
  </div>
) : (
  <div>
    {(auditLog.data ?? []).map((entry: AuditLogEntry) => {
      const acaoBadgeVariant = /* switch on entry.acao */;
      return (
        <div key={entry.id} className="flex items-center gap-3 py-2 border-b ...">
          <span className="text-xs text-slate-500 w-36 shrink-0">{formatDateTime(entry.timestamp)}</span>
          <Badge variant={acaoBadgeVariant} className="font-bold tracking-wide text-[11px]">{entry.acao}</Badge>
          <span className="text-xs text-slate-500">{entry.entidadeTipo}</span>
          <span className="text-xs text-slate-400 font-mono truncate max-w-[120px]">{entry.entidadeId}</span>
          {entry.autorNome ? <span className="text-xs text-slate-600 dark:text-slate-300">{entry.autorNome}</span> : null}
        </div>
      );
    })}
  </div>
)}
```
This loading/error/empty/list-of-rows structure is the direct analog for the new RBAC-audit tab's list rendering — but CONTEXT.md's `<specifics>` explicitly asks for human-readable Portuguese sentences ("Maria Silva retirou o papel Advogado a João Pires"), not the raw `acao`-as-Badge-text shown here, and asks for filters (target user, role) that this existing view does not have. Copy the loading/error/empty-state scaffolding and the row-list mechanics; do not copy the "badge shows the raw `acao` string" choice verbatim — that is a UI-SPEC decision, not a code-pattern one.

---

### 8. Pagination — the ONE precedent in this codebase, full stack (backend query + controller + frontend hook + types)

**`NotificacaoRepository.buscarPorFiltros`** (`backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:17-42`), a native `@Query` with a paired `countQuery`, optional filters via the `CAST(:param AS type) IS NULL OR ...` idiom (required because Postgres cannot infer the type of a bare null bind), returning `Page<Notificacao>`:
```java
@Query(value = "SELECT n.* FROM t_notificacao n " +
        "WHERE n.tenant_id = :tenantId " +
        "AND n.destinatario_id = :destinatarioId " +
        "AND (CAST(:categoria AS text) IS NULL OR n.categoria = CAST(:categoria AS text)) " +
        "AND (CAST(:lida AS boolean) IS NULL OR n.lida = CAST(:lida AS boolean)) " +
        "ORDER BY n.created_at DESC",
        countQuery = "SELECT count(*) FROM t_notificacao n WHERE n.tenant_id = :tenantId " +
                "AND n.destinatario_id = :destinatarioId " +
                "AND (CAST(:categoria AS text) IS NULL OR n.categoria = CAST(:categoria AS text)) " +
                "AND (CAST(:lida AS boolean) IS NULL OR n.lida = CAST(:lida AS boolean))",
        nativeQuery = true)
Page<Notificacao> buscarPorFiltros(@Param("tenantId") UUID tenantId, @Param("destinatarioId") UUID destinatarioId,
                                    @Param("categoria") String categoria, @Param("lida") Boolean lida,
                                    Pageable pageable);
```
The file's own header comment: "First use of Spring Data Pageable/Page in this backend — deliberately scoped to the /notificacoes history endpoint only". **This makes the new RBAC-audit query the SECOND use in the whole codebase** — copy this exact idiom for filtering by target user (`entidade_id`, if the audit query filters by target) and role, with `entidade_tipo IN (...)` (or `= ANY(:tipos)`) to restrict to RBAC events only, per Decision 5 ("Devolve apenas os eventos de RBAC").

**Controller** (`NotificacaoController.java:69-89`, `listar`):
```java
@PreAuthorize("hasAuthority('notificacoes:view')")
@GetMapping
public ResponseEntity<?> listar(
        @RequestParam(required = false) String categoria,
        @RequestParam(required = false) Boolean lida,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 100) {
        return ResponseEntity.badRequest().body(Map.of("message", "page deve ser >= 0 e size deve estar entre 1 e 100"));
    }
    Pageable pageable = PageRequest.of(page, size);
    Page<Notificacao> pageResult = notificacaoRepository.buscarPorFiltros(getTenantId(), getUserId(), categoria, lida, pageable);
    return ResponseEntity.ok(Map.of(
            "content", pageResult.getContent(),
            "totalElements", pageResult.getTotalElements(),
            "totalPages", pageResult.getTotalPages(),
            "page", pageResult.getNumber(),
            "size", pageResult.getSize()
    ));
}
```
Copy verbatim: the `page`/`size` bounds-check (`page < 0`, `size` clamped `1..100`), `PageRequest.of(page, size)`, and the flat `Map.of("content", ..., "totalElements", ..., "totalPages", ..., "page", ..., "size", ...)` response envelope (no dedicated wrapper DTO class exists for this — it's built inline every time). The new endpoint additionally needs `hasAuthority('rbac:manage')` (Decision 5) instead of `notificacoes:view`, and the tenant-only scoping (no `destinatarioId` dimension — RBAC audit is tenant-shared, not per-recipient-private).

**Frontend hook** (`web/src/hooks/use-notificacoes.ts:1-40`, `useNotificacoes` + `buildNotificacoesSearch`):
```typescript
function buildNotificacoesSearch(filters: NotificacoesListFilters): string {
  const params = new URLSearchParams();
  if (filters.categoria) params.set("categoria", filters.categoria);
  if (filters.lida !== undefined) params.set("lida", String(filters.lida));
  if (filters.page !== undefined) params.set("page", String(filters.page));
  if (filters.size !== undefined) params.set("size", String(filters.size));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export function useNotificacoes(filters: NotificacoesListFilters = {}, opts: { poll?: boolean } = {}) {
  return useQuery({
    queryKey: ["notificacoes", "list", filters.categoria ?? "", filters.lida ?? null, filters.page ?? 0, filters.size ?? 20],
    queryFn: () => apiFetch<NotificacoesPageResponse>(`/notificacoes${buildNotificacoesSearch(filters)}`),
    enabled: typeof window !== "undefined",
    staleTime: 30_000,
  });
}
```
**Types** (`web/src/types/notificacoes.ts:25-38`):
```typescript
export type NotificacoesListFilters = { categoria?: NotificacaoCategoria; lida?: boolean; page?: number; size?: number; };
export interface NotificacoesPageResponse { content: Notificacao[]; totalElements: number; totalPages: number; page: number; size: number; }
```
Copy this whole file's shape for the new RBAC-audit hook/types: a `*ListFilters` type (swap `categoria`/`lida` for `entidadeId`/`tenantRoleId`-or-whatever CONTEXT.md's "pelo menos filtrar por utilizador alvo e por papel" resolves to), a `*PageResponse` type with the identical five fields, `queryKey` array including every filter dimension plus `page`/`size` (so each filter combination gets its own cache entry, exactly as `useNotificacoes` does), and `enabled: typeof window !== "undefined"` (SSR guard, consistent across every hook in this codebase per `127-PATTERNS.md`'s Shared Patterns).

---

### 9. Frontend tab wiring — where the new "Auditoria RBAC" surface plugs into Definições

**Full tab-list + tab-id type** (`web/src/app/(dashboard)/settings/page.tsx:79-170`, already summarized above in the File Classification table) — the pattern to copy for adding a sixth tab:
1. Extend `type TabId = "profile" | "security" | "users" | "rbac" | "notificacoes"` with a new id (e.g. `"auditoria-rbac"`).
2. Compute a gate variable the same way `hasRbacManage = can.manage("rbac")` is computed (`settings/page.tsx:94`) — Decision 5 gates the new query by the same `rbac:manage` authority, so the tab-visibility gate can literally be `hasRbacManage` reused, no new permission check needed.
3. Add a `{hasRbacManage && (<button onClick={() => setActiveTab("auditoria-rbac")} className={...}>...)}` block, copying the exact `className` ternary from the existing `rbac` button (`:145-156`).
4. Add a `{activeTab === "auditoria-rbac" && hasRbacManage && (<div className="animate-in fade-in duration-200"><AuditoriaRbacTab /></div>)}` block in the panels section, mirroring `:192-196`'s `RbacTab` wiring exactly.

The new `AuditoriaRbacTab` component itself composes pattern 7 (loading/error/empty/row-list scaffolding from the processos audit view) with pattern 8 (pagination controls — page/size state, "totalPages" driving a Próximo/Anterior pager or similar) — no existing component in this codebase combines both, so this composition is the plan's own work, not a direct copy.

---

## Shared Patterns

### `@Transactional` boundary wrapping a domain write + its audit write
**Source:** absent everywhere in `OfficeRolesController.java` and `AdminController.java` today (confirmed by grep — zero occurrences); the one place this codebase DOES reason explicitly about transaction boundaries for a similar reason is `NotificacaoRepository.inserirSeNaoDuplicado`'s `@Transactional` (lines 98-115, a different problem — self-invocation bypassing the Spring proxy — but the same underlying principle that a second write needs an explicit boundary)
**Apply to:** `OfficeRolesController.createRole/renameRole/deleteRole` and `AdminController.updateRbac/createUser/updateUser/deleteUser` — every write path named in CONTEXT.md Decision 3.

### Principal-derived tenant + author, never request body
**Source:** every one of the ten existing `auditLogRepository.save(AuditLog.builder()...)` sites (`SecurityContextHolder.getContext().getAuthentication()` → `(UserPrincipal) auth.getPrincipal()` → `.tenantId(tenantId)` / `.autorId(principal.getUserId())`)
**Apply to:** all new RBAC audit-write call sites.

### `resolveAutorNome`-style id-to-display-name resolution at read time, never stored
**Source:** `ResourceController.java:148-152`
**Apply to:** rendering the new RBAC-audit endpoint's `autorId` AND the target-user id inside `detalhe`/`entidadeId` (Decision 2's "resolvido para apresentação na leitura").

### `Page<T>`/`Pageable` with a paired native `countQuery`, `CAST(:param AS type) IS NULL` for optional filters
**Source:** `NotificacaoRepository.buscarPorFiltros` (full method, lines 17-42) — the only precedent in this codebase
**Apply to:** the new RBAC-audit repository query.

### Flat `Map.of("content", ..., "totalElements", ..., "totalPages", ..., "page", ..., "size", ...)` page envelope, no wrapper DTO class
**Source:** `NotificacaoController.listar` (lines 82-88)
**Apply to:** the new RBAC-audit paginated response.

### `*ListFilters` + `*PageResponse` TypeScript type pair, `queryKey` array over every filter + page + size
**Source:** `web/src/types/notificacoes.ts` (full file) + `web/src/hooks/use-notificacoes.ts` (full file)
**Apply to:** the new RBAC-audit hook and types.

### Provenance (`moldeId`), never name comparison, for the protected admin role
**Source:** `OfficeRolesController.deleteRole` (lines 262-269) + `AdminController.updateRbac`'s piso-do-administrador block (lines 798-833) — both already established in Phase 127, cited in full in `127-PATTERNS.md`
**Apply to:** any new guard this phase adds that needs to recognize "the office's own admin role" (not directly required by CONTEXT.md's six decisions, but relevant if `detalhe`'s rendering needs to special-case this role).

### Two-tenant isolation test via `lenient()` + `verify(..., never())`
**Source:** `OfficeRolesControllerTest` (full pattern: `autenticarComoPrincipalDoTenant`, `novoProxyComMethodSecurity` for real `@PreAuthorize` proxy testing, `OUTRO_TENANT_ID` constant, e.g. lines 410-422 `deleteRole_idDeOutroTenantERecusadoComQuatroZeroQuatroENuncaConsultaContagem`) — same idiom already cited for `AdminControllerAtribuicaoPapeisEscritorioTest` in `127-PATTERNS.md`
**Apply to:** the new paginated RBAC-audit query endpoint's AUDT-03 proof (two tenants, assert the second tenant's `findByTenantId`-style call is never made with the first tenant's id, or that returned `content` never includes the other tenant's rows).

---

## No Analog Found

| File/Concern | Role | Data Flow | Reason |
|---|---|---|---|
| `AuditLogRepository extends Repository<AuditLog, Long>` (narrowed, no `JpaRepository`) | repository | CRUD | No repository in this codebase extends anything narrower than `JpaRepository` (`grep -rn "extends Repository<\|extends CrudRepository<"` across `backend/src/main/java/com/lexcv/repositories` returns nothing). The two methods it must declare (`save`, `findByTenantIdAndProcessoIdOrderByTimestampDesc`, plus a new paginated finder) are individually ordinary Spring Data idioms; only the choice of base interface is novel. Composed guidance given in pattern 2 above, not a literal copy target. |
| Structural test/gate proving the audit log has no mutating surface (Decision 6's "teste ou gate estrutural que falhe se aparecer um endpoint de escrita... ou uma chamada de remoção") | test | n/a | No backend equivalent of the frontend's `web/scripts/verify-bloqueio-rbac.mjs`/`verify-consola-moldes.mjs` structural-assertion scripts exists on the Java side. The closest available mechanism is the narrowed repository interface itself (compile-time enforcement: `delete*`/`saveAll` simply do not exist as callable methods once the interface no longer extends `JpaRepository`) plus an ordinary JUnit reflection test asserting `AuditLogRepository.class.getMethods()` contains no method named `delete*`/`saveAll` — no such reflection-over-repository-interface test exists elsewhere in this codebase to copy from. This is the plan's own composition, not a carry-forward. |
| Human-readable Portuguese sentence rendering of an audit event ("Maria Silva retirou o papel Advogado a João Pires") | component / formatting utility | n/a | No existing code in this codebase turns a structured audit/event record into a natural-language sentence — `processos/[id]/page.tsx`'s audit tab shows the raw `acao` string as a Badge label (pattern 7 above), and the notification system's `titulo`/`mensagem` fields (`Notificacao` model) are pre-formatted strings assembled by `NotificacaoService` at write time, not derived from structured fields at read time. This is new UI-SPEC/plan work, not a pattern to copy. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{models,repositories,controllers,services}`, `backend/src/test/java/com/lexcv/controllers`, `backend/migrations`, `web/src/app/(dashboard)/{settings,processos/[id]}`, `web/src/hooks`, `web/src/types`
**Files scanned:** `AuditLog.java` (full), `AuditLogRepository.java` (full), `OfficeRolesController.java` (full), `AdminController.java` (targeted: imports, createUser, updateUser, deleteUser, getRbac/updateRbac full bodies), `ResourceController.java` (targeted: audit-write site, `resolveAutorNome`, `getAuditLog` full), `ParecerController.java` (targeted: one audit-write site), `NotificacaoController.java` (full), `NotificacaoRepository.java` (full), `OfficeRolesControllerTest.java` (targeted: header, helpers, two-tenant cases), `backend/pom.xml` (targeted, confirmed Spring Boot 3.4.1 / Hibernate 6.6.4.Final via local `.m2`), `backend/migrations/124-add-permission-catalogo-columns.sql` (full), `backend/migrations/README.md` (full), `SetupService.java` (targeted, confirmed `provisionTenant` is already `@Transactional` at line 126 — relevant to CONTEXT.md Decision 4's discretionary provisioning-audit item), `web/src/app/(dashboard)/settings/page.tsx` (targeted: tab wiring block), `web/src/app/(dashboard)/processos/[id]/page.tsx` (targeted: audit tab rendering), `web/src/hooks/use-processos.ts` (targeted: `useAuditLog`), `web/src/hooks/use-notificacoes.ts` (full), `web/src/types/processos.ts` (targeted: `AuditLogEntry`), `web/src/types/notificacoes.ts` (full). Plus full reuse of `127-PATTERNS.md` for already-documented shared conventions (real `@PreAuthorize` proxy test technique, `moldeId`-provenance discrimination, tenant-scoping via `principal.getTenantId()`) not re-derived here.
**Pattern extraction date:** 2026-09-22
