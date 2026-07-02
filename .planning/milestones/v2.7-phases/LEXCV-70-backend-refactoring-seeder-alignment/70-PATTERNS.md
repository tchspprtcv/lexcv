# Phase 70: Backend refactoring & Seeder Alignment - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 5 (3 modified, 2 removed)
**Analogs found:** 5 / 5 (all analogs are sibling files in the same package — this is a self-contained refactor)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` | model (enum) | CRUD | itself (add enum constant) | exact — trivial edit |
| `backend/src/main/java/com/lexcv/models/Cliente.java` | model (JPA entity) | CRUD | itself (remove field/converter usage) | exact — edit in place |
| `backend/src/main/java/com/lexcv/models/DadosTipo.java` | model (POJO, JSON-serialized) | file-I/O (JSON blob) | N/A — file to be **deleted** | exact — removal |
| `backend/src/main/java/com/lexcv/models/DadosTipoConverter.java` | utility (JPA `AttributeConverter`) | transform | N/A — file to be **deleted** | exact — removal |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (line 277, `updateCliente`) | controller | request-response (CRUD) | itself (remove one line referencing `dadosTipo`) | exact — edit in place |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (lines 96-126, cliente1/cliente2) | seed/config | batch (data seeding) | itself (align `.tipo(...)` values, add REG_COMERCIAL example) | exact — edit in place |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` (model/enum)

**Current full file** (7 lines):
```java
package com.lexcv.models;

public enum DocumentoTipo {
    NIF,
    CNI,
    PASSAPORTE
}
```

**Action:** Add `REG_COMERCIAL` as a fourth constant (per CLI-09, this represents "Registo Comercial" — the company-registration document type used when `Cliente.tipo` is `EMPRESA`/`COLETIVA`). No annotations or special handling needed elsewhere in the enum itself — it is a plain `EnumType.STRING` value referenced via `Cliente.documentoTipo` (see `Cliente.java` line 42-44) and consumed in `ResourceController.java` line 285 (`if (payload.getDocumentoTipo() == DocumentoTipo.NIF) { ... }` — no `switch` exhaustiveness to update, this is a simple `if`).

---

### `backend/src/main/java/com/lexcv/models/Cliente.java` (model, JPA entity, CRUD)

**Analog:** itself — this is an in-place removal, not a new file. The rest of the entity (`Documento.java`, full file above) shows the target flat-column style this entity should converge toward: plain `@Column`-mapped scalar fields, no JSON `@Convert` blobs for identification data.

**Field/annotation to remove** (current lines 65-68):
```java
    @Column(name = "dados_tipo", columnDefinition = "TEXT")
    @Convert(converter = DadosTipoConverter.class)
    @JsonProperty("dados_tipo")
    private DadosTipo dadosTipo;
```

**Fields to keep as-is (already flat-column, the target model)** (lines 30-63):
```java
    private String tipo;

    @Column(nullable = false)
    private String nome;

    private String nif;
    private String email;
    private String telefone;
    private String morada;
    private String localidade;
    private Boolean ativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo")
    private DocumentoTipo documentoTipo;

    @Column(name = "documento_numero")
    private String documentoNumero;

    @Column(name = "ramo_atividade")
    private String ramoAtividade;

    @Column(name = "detalhes_adicionais", length = 255)
    private String detalhesAdicionais;

    @Column(name = "numero_sequencial")
    private Integer numeroSequencial;

    @Column(name = "numero_cliente", length = 20)
    @JsonProperty("numero_cliente")
    private String numeroCliente;

    @Column(name = "avencado")
    private Boolean avencado;
```

**Other `@Convert`-based JSON fields that remain untouched** (out of scope — only `dadosTipo` is targeted per CLI-06): `documentosEntregues` (`DocumentosEntreguesConverter`), `documentosATratar` (`DocumentosATratarConverter`), `deslocacoes` (`DeslocacoesConverter`), `honorariosPropostos` (`HonorariosPropostosConverter`) at lines 78-95 — leave these as-is, do not remove.

**Import cleanup:** After removing the field, check whether `import com.fasterxml.jackson.annotation.JsonProperty;` (line 3) is still needed — it is, because `numeroCliente`, `procuracaoKey`, `descricaoCaso`, `documentosEntregues`, `documentosATratar`, `honorariosPropostos` still use `@JsonProperty`. No import removal needed for `Cliente.java` itself.

---

### `backend/src/main/java/com/lexcv/models/DadosTipo.java` (POJO — to delete)

**Full current file** (33 lines, shown above in Step-1 read) — a plain Lombok `@Data`/`@Builder` POJO with `@JsonInclude(NON_NULL)` holding `idade`, `sexo`, `nacionalidade`, `biPassaporte` (Particular fields) and `nomeComercial`, `nif`, `sede`, `representanteLegal`, `cargoRepresentante` (Empresa fields).

**Action:** Delete this file entirely. Per CLI-06, the JSON blob is being replaced by flat columns already present on `Cliente` (`nif`, `documentoTipo`, `documentoNumero`, `ramoAtividade`, `detalhesAdicionais`). Confirm no data-migration is needed for existing `dados_tipo` column contents (infra phase, no explicit migration requirement stated in CONTEXT.md — flag to planner if a Flyway/DDL migration step is required given `ddl-auto=update` in dev).

---

### `backend/src/main/java/com/lexcv/models/DadosTipoConverter.java` (AttributeConverter — to delete)

**Full current file** (36 lines, shown above) — standard Jackson `ObjectMapper`-based `AttributeConverter<DadosTipo, String>`, structurally identical to the four sibling converters that remain (`DocumentosEntreguesConverter`, `DocumentosATratarConverter`, `DeslocacoesConverter`, `HonorariosPropostosConverter`).

**Action:** Delete this file entirely — it becomes dead code once `Cliente.dadosTipo` is removed.

---

### `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (controller, request-response/CRUD)

**Analog:** itself — single-line removal in `updateCliente` (lines 257-293).

**Current PUT handler excerpt** (lines 257-293):
```java
    @PreAuthorize("hasAuthority('clientes:edit')")
    @PutMapping("/clientes/{id}")
    public ResponseEntity<?> updateCliente(@PathVariable UUID id, @RequestBody Cliente payload) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        cliente.setNome(payload.getNome());
        cliente.setTipo(payload.getTipo());
        cliente.setEmail(payload.getEmail());
        cliente.setTelefone(payload.getTelefone());
        cliente.setMorada(payload.getMorada());
        cliente.setLocalidade(payload.getLocalidade());
        cliente.setAtivo(payload.getAtivo());
        cliente.setDocumentoTipo(payload.getDocumentoTipo());
        cliente.setDocumentoNumero(payload.getDocumentoNumero());
        cliente.setRamoAtividade(payload.getRamoAtividade());
        cliente.setDetalhesAdicionais(payload.getDetalhesAdicionais());
        cliente.setAvencado(payload.getAvencado());
        cliente.setDadosTipo(payload.getDadosTipo());   // <-- REMOVE this line

        if (payload.getDescricaoCaso() != null) cliente.setDescricaoCaso(payload.getDescricaoCaso());
        if (payload.getDocumentosEntregues() != null) cliente.setDocumentosEntregues(payload.getDocumentosEntregues());
        if (payload.getDocumentosATratar() != null) cliente.setDocumentosATratar(payload.getDocumentosATratar());
        if (payload.getDeslocacoes() != null) cliente.setDeslocacoes(payload.getDeslocacoes());
        if (payload.getHonorariosPropostos() != null) cliente.setHonorariosPropostos(payload.getHonorariosPropostos());

        if (payload.getDocumentoTipo() == DocumentoTipo.NIF) {
            cliente.setNif(payload.getDocumentoNumero());
        } else if (payload.getNif() != null) {
            cliente.setNif(payload.getNif());
        }

        Cliente saved = clienteRepository.save(cliente);
        return ResponseEntity.ok(saved);
    }
```

**Action:** Delete the `cliente.setDadosTipo(payload.getDadosTipo());` line only. All other setters follow the flat-column pattern already and should remain unchanged. No other `Cliente`-related endpoint in this controller (e.g. `POST /clientes` creation — check for a sibling `createCliente` handler above this range) references `dadosTipo`; confirmed via full-file grep (only match is line 277).

**Note on `DocumentoTipo.NIF` special-casing (lines 285-289):** This is the existing pattern for deriving `nif` from `documentoTipo`/`documentoNumero`. When `REG_COMERCIAL` is added, consider (at Claude's discretion per CONTEXT.md) whether an analogous branch is needed for Empresa clients — e.g. `else if (payload.getDocumentoTipo() == DocumentoTipo.REG_COMERCIAL) { ... }` — but CLI-09 only requires the enum value and number storage, not necessarily new derivation logic. Leave this decision to the planner/implementer; flag it as an open question in the plan.

---

### `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (seed/config, batch)

**Analog:** itself — align existing `Cliente.builder()` calls (lines 96-126) with the flat-column model and demonstrate `REG_COMERCIAL` usage for the Empresa-type seed client.

**Current excerpt** (lines 95-126):
```java
        // 5. Clientes
        Cliente cliente1 = Cliente.builder()
                .tenantId(tenantId)
                .tipo("SINGULAR")
                .nome("João Andrade (PostgreSQL Real)")
                .nif("123456789")
                .email("joao.andrade@email.com")
                .telefone("+238 991 1234")
                .morada("Achada Santo António, Praia")
                .documentoTipo(DocumentoTipo.NIF)
                .documentoNumero("123456789")
                .ramoAtividade("Serviços")
                .detalhesAdicionais("Cliente habitual de consultoria")
                .build();
        cliente1 = clienteRepository.save(cliente1);
        UUID clienteId1 = cliente1.getId();

        Cliente cliente2 = Cliente.builder()
                .tenantId(tenantId)
                .tipo("COLETIVA")
                .nome("Empresa Atlântico, SA")
                .nif("512345678")
                .email("info@atlanticosa.cv")
                .telefone("+238 231 5678")
                .morada("Avenida Marginal, Mindelo")
                .documentoTipo(DocumentoTipo.NIF)
                .documentoNumero("512345678")
                .ramoAtividade("Comércio")
                .detalhesAdicionais("Empresa líder no setor marítimo")
                .build();
        cliente2 = clienteRepository.save(cliente2);
        UUID clienteId2 = cliente2.getId();
```

**Findings:**
1. **No `.dadosTipo(...)` builder call exists in the seeder today** — so removing the field from `Cliente` requires no deletion in `DatabaseSeeder.java`. The seeder is already "clean" of the field being removed.
2. **Misalignment to fix (CLI-09 / model consistency):** `cliente2` represents an "Empresa" (`tipo("COLETIVA")`, company name "Empresa Atlântico, SA") but uses `.documentoTipo(DocumentoTipo.NIF)` — the same document type as the individual client `cliente1`. Per CLI-09, Empresa-type clients should use `DocumentoTipo.REG_COMERCIAL` with the registration number in `documentoNumero`. Update `cliente2`'s builder chain to `.documentoTipo(DocumentoTipo.REG_COMERCIAL)`.
3. **`tipo` field uses raw strings `"SINGULAR"`/`"COLETIVA"`**, not the `TipoCliente` enum (`PARTICULAR`/`EMPRESA` — see `backend/src/main/java/com/lexcv/models/TipoCliente.java`, full file: `public enum TipoCliente { PARTICULAR, EMPRESA }`). Note `Cliente.tipo` is typed as `private String tipo;` (not `@Enumerated`), so `TipoCliente` enum values don't directly match the seeded strings `"SINGULAR"`/`"COLETIVA"`. This inconsistency exists in the current codebase already (frontend/backend terminology drift between `TipoCliente` enum names and the free-text `tipo` column) — CONTEXT.md scope is limited to `dados_tipo` removal + `REG_COMERCIAL` addition; do **not** attempt to reconcile `tipo` string values with `TipoCliente` enum unless the plan explicitly re-scopes this (flag as an observation, not an action item, unless planner decides otherwise).

---

## Shared Patterns

### JPA flat-column entity style (target pattern for `Cliente`)
**Source:** `backend/src/main/java/com/lexcv/models/Documento.java` (full file, shown above) and `Cliente.java` lines 30-63 (existing flat fields)
**Apply to:** `Cliente.java` after `dadosTipo` removal — plain `@Column`-annotated scalar fields (`String`, `Boolean`, `Integer`), `@Enumerated(EnumType.STRING)` for enum-backed columns like `documentoTipo`. No `@Convert`/JSON blob for identification data going forward.

### `@Convert` + `AttributeConverter` pattern (still valid, but not for `dadosTipo`)
**Source:** `backend/src/main/java/com/lexcv/models/DocumentosEntreguesConverter.java`, `DeslocacoesConverter.java`, `HonorariosPropostosConverter.java` (structurally identical to the deleted `DadosTipoConverter.java`)
**Apply to:** N/A for this phase — these remain untouched. Referenced here only so the planner/implementer does not accidentally delete the wrong converter.

### Multi-tenant scoping in controller
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 249-254 (`getCliente`) and 260-263 (`updateCliente`)
```java
Cliente cliente = clienteRepository.findById(id).orElse(null);
if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
}
```
**Apply to:** No new endpoints are being added in this phase, but any incidental touch to `Cliente`-related controller code must preserve this tenant-scoping check unchanged.

## No Analog Found

None — this phase only modifies/deletes existing files; there are no genuinely new files requiring an external analog.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/models/`, `backend/src/main/java/com/lexcv/seed/`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Files scanned:** 37 model files (via Glob), 1 seeder file, 1 controller (targeted grep + range reads)
**Pattern extraction date:** 2026-07-01
