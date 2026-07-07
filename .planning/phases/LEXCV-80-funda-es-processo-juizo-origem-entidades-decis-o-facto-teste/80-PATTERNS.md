# Phase 80: Fundações — Processo.juizo/origem + Entidades Decisão/Facto/Testemunha - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 10 (3 enums, 3 entities, 3 repositories, 1 modified entity)
**Analogs found:** 10 / 10

This phase is pure data-layer (JPA entities, enums, Spring Data repositories) — no controller/endpoint changes. All analogs below come from `backend/src/main/java/com/lexcv/models/` and `backend/src/main/java/com/lexcv/repositories/`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|---------------|
| `backend/src/main/java/com/lexcv/models/OrigemProcesso.java` | model (enum) | CRUD | `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` | exact |
| `backend/src/main/java/com/lexcv/models/TipoDecisao.java` | model (enum) | CRUD | `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` | exact |
| `backend/src/main/java/com/lexcv/models/TipoTestemunha.java` (new — needed for `Testemunha.tipo`, not explicitly named in orchestrator's file list but required by CONTEXT.md's closed-enum decision) | model (enum) | CRUD | `backend/src/main/java/com/lexcv/models/TipoCliente.java` | exact |
| `backend/src/main/java/com/lexcv/models/Decisao.java` | model (entity) | CRUD | `backend/src/main/java/com/lexcv/models/Parte.java` | exact (shape) |
| `backend/src/main/java/com/lexcv/models/Facto.java` | model (entity) | CRUD | `backend/src/main/java/com/lexcv/models/Parte.java` | exact (shape) |
| `backend/src/main/java/com/lexcv/models/Testemunha.java` | model (entity) | CRUD | `backend/src/main/java/com/lexcv/models/Parte.java` | exact (shape) |
| `backend/src/main/java/com/lexcv/repositories/DecisaoRepository.java` | repository | CRUD | `backend/src/main/java/com/lexcv/repositories/ParteRepository.java` | exact |
| `backend/src/main/java/com/lexcv/repositories/FactoRepository.java` | repository | CRUD | `backend/src/main/java/com/lexcv/repositories/ParteRepository.java` | exact |
| `backend/src/main/java/com/lexcv/repositories/TestemunhaRepository.java` | repository | CRUD | `backend/src/main/java/com/lexcv/repositories/ParteRepository.java` | exact |
| `backend/src/main/java/com/lexcv/models/Processo.java` (modified) | model (entity) | CRUD | itself (add fields following its own existing `tribunal`/`estado`/`documentoTipo`-style conventions) | exact |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/OrigemProcesso.java` (model, enum)

**Analog:** `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` (full file, 8 lines)

```java
package com.lexcv.models;

public enum DocumentoTipo {
    BI,
    CNI,
    PASSAPORTE,
    REG_COMERCIAL
}
```

**Pattern to copy:** plain Java enum, no annotations on the enum itself, ALL_CAPS_SNAKE constants (stable ASCII codes — Portuguese-accented display labels are mapped only in the frontend, never stored). Package `com.lexcv.models`.

**Apply as:**
```java
package com.lexcv.models;

public enum OrigemProcesso {
    PETICAO_INICIAL,
    NOTIFICACOES_AVULSAS
}
```

---

### `backend/src/main/java/com/lexcv/models/TipoDecisao.java` (model, enum)

**Analog:** same as above (`DocumentoTipo.java`), same pattern.

**Apply as (values confirmed in CONTEXT.md: Despacho | Decisão Interlocutória | Sentença | Acórdão):**
```java
package com.lexcv.models;

public enum TipoDecisao {
    DESPACHO,
    DECISAO_INTERLOCUTORIA,
    SENTENCA,
    ACORDAO
}
```

---

### `backend/src/main/java/com/lexcv/models/TipoTestemunha.java` (model, enum — required but not named in the orchestrator's file list)

CONTEXT.md's `## Decisions` states: *"Testemunha.tipo — closed enum (Autor | Réu) — confirmed by explicit user decision during milestone requirements gathering (chose closed enum over free text)."* This requires an enum file exactly like `TipoDecisao`/`OrigemProcesso`. Flag for the planner: this file was not in the orchestrator's explicit `Files expected to be created` list but is required by the locked CONTEXT.md decision — include it as a sibling task.

**Analog:** `backend/src/main/java/com/lexcv/models/TipoCliente.java` (full file, 6 lines) — same 2-constant closed-enum shape:
```java
package com.lexcv.models;

public enum TipoCliente {
    PARTICULAR,
    EMPRESA
}
```

**Apply as:**
```java
package com.lexcv.models;

public enum TipoTestemunha {
    AUTOR,
    REU
}
```

---

### `backend/src/main/java/com/lexcv/models/Decisao.java`, `Facto.java`, `Testemunha.java` (model, entity)

**Analog:** `backend/src/main/java/com/lexcv/models/Parte.java` (full file, 26 lines) — this is the exact structural template mandated by CONTEXT.md ("mirror Parte.java's lean shape exactly"):

```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "t_parte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String nome;

    private String tipo;
}
```

**Pattern to copy (verbatim structure):**
- `@Entity` + `@Table(name = "t_<entity>")` — table names follow `t_` prefix convention seen throughout (`t_parte`, `t_processo_fase`, `t_cliente_contacto`, `t_documento`)
- Lombok stack: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (no `@Data`, unlike what CLAUDE.md's generic guidance implies — `Parte.java` uses the explicit combo, follow it verbatim, not `@Data`)
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;` — **Integer** IDENTITY, not UUID (this is the key differentiator vs. `Documento`/`Cliente`/`Processo`, which all use `UUID` + `GenerationType.UUID`)
- `@Column(name = "processo_id", nullable = false) private UUID processoId;` — the sole FK, no `tenant_id` column
- No `@PrePersist`/`createdAt` on `Parte` itself (unlike `ClienteContacto`/`Documento`/`Processo`, which do have `createdAt`) — `Parte` has none; **decide per-entity**: `Decisao`/`Facto` carry a `data` (date) business field already, so a separate `createdAt` audit column is optional/discretionary, not mandated by the analog.

**Apply to `Decisao.java`** (fields per CONTEXT.md: `data`, `tipo` (TipoDecisao enum), `resumo`, nullable `documento_id` FK per ARCHITECTURE.md's Integration Points — *"Nullable FK column `documento_id` on `Decisao`, pointing at an existing `t_documento` row"*):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "t_decisao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Decisao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDecisao tipo;

    private String resumo;

    @Column(name = "documento_id")
    private UUID documentoId;
}
```

**Apply to `Facto.java`** (fields per CONTEXT.md: `descricao`, `data`, `ordem` int scoped per `processo_id`):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "t_facto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String descricao;

    private LocalDate data;

    @Column(nullable = false)
    private Integer ordem;
}
```

**Apply to `Testemunha.java`** (fields per FEATURES.md/SUMMARY.md: `nome`, `contacto`, `tipo` (TipoTestemunha enum — "arrolada por"), `notas`):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "t_testemunha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Testemunha {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String nome;

    private String contacto;

    @Enumerated(EnumType.STRING)
    private TipoTestemunha tipo;

    private String notas;
}
```

---

### `backend/src/main/java/com/lexcv/repositories/DecisaoRepository.java`, `FactoRepository.java`, `TestemunhaRepository.java` (repository)

**Analog:** `backend/src/main/java/com/lexcv/repositories/ParteRepository.java` (full file, 11 lines):

```java
package com.lexcv.repositories;

import com.lexcv.models.Parte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ParteRepository extends JpaRepository<Parte, Integer> {
    List<Parte> findByProcessoId(UUID processoId);
}
```

**Pattern to copy:** minimal Spring Data JPA interface, `JpaRepository<Entity, Integer>` (matches the `Integer` IDENTITY id), single `findByProcessoId(UUID processoId)` derived-query method. `ProcessoFaseRepository.java` confirms the exact same shape for a second child entity (also `Integer` id):

```java
package com.lexcv.repositories;

import com.lexcv.models.ProcessoFase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessoFaseRepository extends JpaRepository<ProcessoFase, Integer> {
    List<ProcessoFase> findByProcessoId(UUID processoId);
}
```

**Apply to `DecisaoRepository.java`:**
```java
package com.lexcv.repositories;

import com.lexcv.models.Decisao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DecisaoRepository extends JpaRepository<Decisao, Integer> {
    List<Decisao> findByProcessoId(UUID processoId);
}
```

**Apply to `FactoRepository.java`** — note the CONTEXT.md decision that `ordem` is scoped per `processo_id`, so the planner should consider a sorted variant (`findByProcessoIdOrderByOrdemAsc`) for the Phase 81 controller's convenience, though a plain `findByProcessoId` is the minimum needed to match the analog exactly in this data-layer phase:
```java
package com.lexcv.repositories;

import com.lexcv.models.Facto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FactoRepository extends JpaRepository<Facto, Integer> {
    List<Facto> findByProcessoId(UUID processoId);
    List<Facto> findByProcessoIdOrderByOrdemAsc(UUID processoId);
}
```

**Apply to `TestemunhaRepository.java`:**
```java
package com.lexcv.repositories;

import com.lexcv.models.Testemunha;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TestemunhaRepository extends JpaRepository<Testemunha, Integer> {
    List<Testemunha> findByProcessoId(UUID processoId);
}
```

---

### `backend/src/main/java/com/lexcv/models/Processo.java` (modified — add `juizo` and `origem`)

**Current full file (34 lines of field declarations, verbatim — this is the exact file to modify):**

```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_processo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Processo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(name = "numero_processo")
    private String numeroProcesso;

    @Column(name = "tipo_processo")
    private String tipoProcesso;

    @Column(name = "area_juridica")
    private String areaJuridica;

    private String tribunal;
    private String estado;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    private String descricao;

    @Column(name = "legal_hold")
    @Builder.Default
    private Boolean legalHold = false;

    @Column(name = "data_retencao")
    private LocalDate dataRetencao;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

**`juizo` pattern to copy (line 39, `private String tribunal;`):** `juizo` is a bare, unannotated `String` field exactly like `tribunal` — no `@Column` needed since the derived snake_case column name (`juizo`) already matches Hibernate's default naming strategy (single-word field ⇒ same-name column, same as `tribunal`, `estado`, `descricao`).

**`origem` pattern to copy (Cliente.java lines 46-48, the `documentoTipo` field — the only other enum-typed entity field in the codebase):**
```java
@Enumerated(EnumType.STRING)
@Column(name = "documento_tipo")
private DocumentoTipo documentoTipo;
```
Apply as:
```java
@Enumerated(EnumType.STRING)
@Column(name = "origem")
private OrigemProcesso origem;
```

**Insertion point:** add both new fields immediately after `private String tribunal;` / `private String estado;` (line 40), consistent with where the other free-text/classification fields (`tipoProcesso`, `areaJuridica`, `tribunal`) are already grouped, before the date fields.

**CONTEXT.md caveat to carry into the plan:** `origem` is set once at intake and never exposed for edit (per ARCHITECTURE.md's Key Data Flow #2) — this is a **Phase 81 controller-layer** concern (`updateProcesso`'s field-copy list must exclude `origem`, matching how `estado` is already excluded), not something to encode in the entity itself. No `@Column(updatable = false)` should be added at the entity level, since `createProcessoIntake` still needs to write it once via the same `save()` path other fields use.

---

## Shared Patterns

### Lean child-entity shape (no `tenant_id`)
**Source:** `backend/src/main/java/com/lexcv/models/Parte.java` (whole file)
**Apply to:** `Decisao.java`, `Facto.java`, `Testemunha.java`
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Integer id;

@Column(name = "processo_id", nullable = false)
private UUID processoId;
```
Tenant isolation is transitive via the parent `Processo` — enforced at the controller layer in Phase 81, not encoded on these entities. Do **not** add a `tenant_id` column (see ARCHITECTURE.md Anti-Pattern 1 and Pitfall 3 in SUMMARY.md — copying `Documento`'s `tenant_id`-carrying shape here would be an inconsistency with zero security benefit, since every access path already tenant-checks via the parent).

### Enum-as-string storage convention
**Source:** `backend/src/main/java/com/lexcv/models/Cliente.java` lines 46-48 (`documentoTipo` field) + `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` (whole file)
**Apply to:** `Processo.origem` (`OrigemProcesso`), `Decisao.tipo` (`TipoDecisao`), `Testemunha.tipo` (`TipoTestemunha`)
```java
@Enumerated(EnumType.STRING)
@Column(name = "<snake_case_column>")
private <EnumType> <fieldName>;
```
Enum files themselves are plain, unannotated Java enums with ALL_CAPS_SNAKE constants (stable ASCII codes; Portuguese display labels — e.g. "Petição Inicial", "Notificações Avulsas", "Decisão Interlocutória" — are mapped only in the frontend, never stored as the DB/enum value). This exact pattern (`@Enumerated(EnumType.STRING)` + a bare Java `enum`) is the only enum-backed entity field in the current codebase (`Cliente.documentoTipo`) and is the one to replicate — there is no reason to deviate into ordinal storage or a `@Convert`-based custom type.

**Gotcha flagged by `backend/migrations/74-cleanup-nif-documento-tipo.sql`:** once a project ships with `@Enumerated(EnumType.STRING)`, removing/renaming an enum constant later requires a defensive data-cleanup migration (Hibernate throws on deserializing a row whose stored string no longer matches any Java constant). Not actionable for this phase (all three new enums are greenfield, no legacy rows), but worth the planner noting for future-proofing: pick final enum constant names carefully now, since CONTEXT.md's values are already locked (`PETICAO_INICIAL`/`NOTIFICACOES_AVULSAS`; `DESPACHO`/`DECISAO_INTERLOCUTORIA`/`SENTENCA`/`ACORDAO`; `AUTOR`/`REU`).

### Repository minimalism
**Source:** `backend/src/main/java/com/lexcv/repositories/ParteRepository.java`, `ProcessoFaseRepository.java` (both whole files)
**Apply to:** `DecisaoRepository.java`, `FactoRepository.java`, `TestemunhaRepository.java`
```java
public interface <Name>Repository extends JpaRepository<<Entity>, Integer> {
    List<<Entity>> findByProcessoId(UUID processoId);
}
```
No custom `@Query`, no service layer — Spring Data derived-query methods only, matching every other Processo child-entity repository in the codebase.

### `ddl-auto=update` — no manual migration file needed
**Source:** `application.yml` (`ddl-auto=update` in dev, per CLAUDE.md and confirmed by ARCHITECTURE.md/SUMMARY.md); `backend/migrations/` contains exactly one file (`74-cleanup-nif-documento-tipo.sql`), which is an explicitly-labeled **one-off defensive data cleanup script**, not a schema migration — there is no established convention of hand-writing DDL migration files for new columns/tables in this repo.
**Apply to:** all new columns (`Processo.juizo`, `Processo.origem`) and all new tables (`t_decisao`, `t_facto`, `t_testemunha`) — no `.sql` migration file is expected as a deliverable of this phase; Hibernate schema evolution handles it automatically in dev, and `ddl-auto=validate` in prod means a prod deploy of this phase requires the schema to already match (standard project deployment process, out of scope for this phase's file list).

## No Analog Found

None. All 10 files have a strong (exact-shape) analog in the current codebase.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/models/`, `backend/src/main/java/com/lexcv/repositories/`, `backend/migrations/`
**Files scanned:** `Parte.java`, `ProcessoFase.java`, `Processo.java`, `Cliente.java`, `ClienteContacto.java`, `Documento.java`, `DocumentoTipo.java`, `TipoCliente.java`, `ParteRepository.java`, `ProcessoFaseRepository.java`, `74-cleanup-nif-documento-tipo.sql`
**Pattern extraction date:** 2026-07-07
