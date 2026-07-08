# Phase 81: Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 2 (1 heavily modified, 1 lightly modified)
**Analogs found:** 9 / 9 (all patterns found in-file, same controller)

All analogs live inside the same file being modified (`ResourceController.java`) — this phase adds new methods to an existing 2504-line controller rather than creating new files, per `ARCHITECTURE.md`'s explicit recommendation (no new controller). The only other file touched is `FactoRepository.java` (needs one new derived/`@Query` method for the `ordem` sequence).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog (same file unless noted) | Match Quality |
|---|---|---|---|---|
| `ResourceController.java` — 12 new endpoints for `/processos/{id}/decisoes\|factos\|testemunhas` (GET list, POST, PUT, DELETE ×3) | controller | CRUD | `listFases`/`createProcessoFase`/`updateProcessoFase` (ProcessoFase, lines 1541-1621) | exact |
| `ResourceController.java` — Decisão POST multipart upload branch | controller | file-I/O | `uploadDocumento` (lines 1982-2070) | exact |
| `ResourceController.java` — Facto POST `ordem` auto-increment | controller | CRUD (concurrent-safe sequence) | `createCliente`'s `numeroSequencial` (lines 218-247) + `ParecerController`'s `numeroVersao` (lines 449-462) | exact |
| `ResourceController.java` — `createProcesso` (juizo/origem persist) | controller | CRUD | existing method body itself (lines 957-971) — direct edit, no external analog needed | n/a (direct edit) |
| `ResourceController.java` — `updateProcesso` (juizo persist, origem excluded) | controller | CRUD | existing method body itself (lines 983-1004), pattern = the `estado`-exclusion comment already present | n/a (direct edit, follow existing in-method precedent) |
| `ResourceController.java` — `createProcessoIntake` (origem required + persist) | controller | CRUD | existing method body itself (lines 1022-1029) — currently validates nothing; new validation logic modeled on `formalizarProcesso`'s `camposEmFalta` 422 pattern (lines 1196-1217) | role-match |
| `ResourceController.java` — `CAMPOS_MINIMOS_POR_TIPO` static map | config | transform | existing map literal itself (lines 72-80) — direct edit | n/a (direct edit) |
| `ResourceController.java` — `listProcessos` enriched `LinkedHashMap` builder | controller | transform | existing method body itself (lines 909-952) — direct edit, add two `m.put(...)` lines | n/a (direct edit) |
| `FactoRepository.java` — new `findMaxOrdemByProcessoId` query | repository | CRUD | `ClienteRepository.findMaxNumeroSequencialByTenantId` (lines 17-18) / `ParecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId` (lines 14-15) | exact |

## Pattern Assignments

### 1. `GET/POST /processos/{id}/decisoes`, `/factos`, `/testemunhas` (list + create, controller, CRUD)

**Analog:** `listPartes`/`createParte` (lines 1520-1539) for the simple single-check shape, but per `81-CONTEXT.md` decisions and `PITFALLS.md` Pitfall 3, **use the stricter `ProcessoFase` double-check pattern for every write**, not the simpler Parte/Movimentacao single-check.

**List pattern** (`listFases`, lines 1541-1548 — omit the fase-specific catalog lookup/map-building, just return the repository list directly like `listPartes`/`listMovimentacoes` do):
```java
@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/partes")
public ResponseEntity<?> listPartes(@PathVariable UUID id) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    return ResponseEntity.ok(parteRepository.findByProcessoId(id));
}
```
For Facto's list, prefer `factoRepository.findByProcessoIdOrderByOrdemAsc(id)` (already exists on `FactoRepository`, see Phase 80 summary) instead of the unordered `findByProcessoId`.

**Create pattern** (`createParte`, lines 1530-1539 — single tenant check is enough for POST since there's no existing child row to re-verify yet):
```java
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping("/processos/{id}/partes")
public ResponseEntity<?> createParte(@PathVariable UUID id, @RequestBody Parte parte) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    parte.setProcessoId(id);
    return ResponseEntity.status(HttpStatus.CREATED).body(parteRepository.save(parte));
}
```

### 2. `PUT/DELETE /processos/{id}/decisoes/{did}` etc. (update + delete, controller, CRUD)

**Analog:** `updateProcessoFase` (lines 1595-1621) — the mandatory double-check pattern per `81-CONTEXT.md` decision ("padrão `ProcessoFase`, não o padrão mais simples de `Parte`/`Movimentacao`") and `PITFALLS.md` Pitfall 3 (IDOR risk from sequential `Integer` IDs).

```java
@PreAuthorize("hasAuthority('processos:edit')")
@PutMapping("/processos/{id}/fases/{faseId}")
public ResponseEntity<?> updateProcessoFase(
        @PathVariable UUID id,
        @PathVariable Integer faseId,
        @RequestBody Map<String, Object> body) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    ProcessoFase pf = processoFaseRepository.findById(faseId).orElse(null);
    if (pf == null || !pf.getProcessoId().equals(id)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Fase não encontrada"));
    }

    if (body.containsKey("status")) {
        // ... field-by-field mutation from body/payload
    }

    return ResponseEntity.ok(processoFaseRepository.save(pf));
}
```
**Key structural rule to replicate exactly:** two independent `findById` + null/ownership checks — (a) `Processo` by tenant, (b) child row by `child.getProcessoId().equals(id)` — with two distinct 404 messages ("Processo não encontrado" vs. the child-specific "não encontrado"). For Facto's PUT, this is also where the CONTEXT.md-mandated explicit `ordem` overwrite from the payload happens (no `default`/max-recompute on PUT, only on POST).

**DELETE pattern — no exact child-entity precedent exists** (neither `Parte`, `ProcessoFase`, nor `Movimentacao` has DELETE today). Compose it from two pieces:
- The `ProcessoFase`-style double ownership check shown above (parent tenant + `child.getProcessoId().equals(id)`).
- The top-level `deleteProcesso`'s delete-and-respond shape (lines 1006-1016):
```java
@PreAuthorize("hasAuthority('processos:edit')")
@DeleteMapping("/processos/{id}")
public ResponseEntity<?> deleteProcesso(@PathVariable UUID id) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    processoRepository.delete(processo);
    return ResponseEntity.ok(Map.of("message", "Processo removido com sucesso!"));
}
```
Combine: fetch+check processo (tenant), fetch+check child (processoId match), then `xRepository.delete(x); return ResponseEntity.ok(Map.of("message", "<Entidade> removida com sucesso!"));`.

---

### 3. `POST /processos/{id}/decisoes` — multipart file upload branch (controller, file-I/O)

**Analog:** `uploadDocumento` (lines 1982-2070) — per `81-CONTEXT.md`, this is the Phase 79 tenant-ownership validation pattern to replicate for the internally-created `Documento`.

**Ownership validation excerpt to replicate** (lines 2001-2014 — apply the exact same `clienteId`/`processoId` pre-persist check style; for Decisão's internal upload, `processoId` is already known/validated from the path `{id}`, so only the *storage* + `Documento` construction pieces need copying, not the ownership-check branch, which is redundant here since `id` was already tenant-checked at the top of `createDecisao`):
```java
if (processoId != null) {
    Processo processo = processoRepository.findById(processoId).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "processoId não pertence a este tenant"));
    }
}
```

**Storage + Documento construction to replicate verbatim** (lines 2016-2062, "new document" branch only — Decisão never hits the `replaceId` branch):
```java
try {
    String fileId = UUID.randomUUID().toString();
    UUID documentoId = UUID.fromString(fileId);
    InputStream inputStream = file.getInputStream();
    String objectKey = storageService.upload(getTenantId(), documentoId,
            originalName, inputStream, file.getContentType(), file.getSize());

    Documento documento = Documento.builder()
            .id(documentoId)
            .tenantId(getTenantId())
            .processoId(processoId)
            .clienteId(clienteId)
            .nome(originalName)
            .tipo(tipo != null ? tipo : "ANEXO")
            .confidencialidade(confidencialidade != null ? confidencialidade : "PUBLICO")
            .caminhoArquivo(objectKey)
            .tamanho(file.getSize())
            .mimeType(file.getContentType())
            .versao(1)
            .build();

    Documento savedDoc = documentoRepository.save(documento);
    // decisao.setDocumentoId(savedDoc.getId());  — then save Decisao
} catch (StorageUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Storage service unavailable"));
} catch (IOException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", "Erro ao ler ficheiro"));
}
```
**Note (per PITFALLS.md Pitfall 7):** `Decisao.documentoId` is the FK direction already chosen at Phase 80 (raw nullable UUID column on `Decisao`, no JPA relationship) — this endpoint must validate the resulting `Documento` belongs to the same tenant AND same `processo` before setting `decisao.setDocumentoId(...)`, which is automatically true here since the `Documento` is created fresh inside this same request with `processoId`/`tenantId` taken from already-validated values — no separate re-fetch/re-check needed for the *creation* path (only relevant if a future endpoint lets you attach an *existing* Documento by ID instead of uploading a new one).

**Multipart method signature convention to copy** (lines 1982-1990):
```java
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping(value = "/processos/{id}/decisoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> createDecisao(
        @PathVariable UUID id,
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam("data") String data,
        @RequestParam("tipo") String tipo,
        @RequestParam(value = "resumo", required = false) String resumo) {
```
(Field names/requiredness to be finalized against `Decisao`'s actual shape — `data`, `tipo` non-null per entity `@Column(nullable = false)`; `resumo`/`file` optional.)

---

### 4. `POST /processos/{id}/factos` — server-computed `ordem` (controller, CRUD/concurrent-safe sequence)

**Analog A — `createCliente`'s `numeroSequencial`** (lines 218-247, most directly comparable: `synchronized` block + `findMax...` repository query + `orElse(0) + 1`):
```java
synchronized (ClienteRepository.class) {
    java.util.Optional<Integer> result = clienteRepository.findMaxNumeroSequencialByTenantId(getTenantId());
    int maxSeq = result.orElse(0);
    int nextSeq = maxSeq + 1;
    cliente.setNumeroSequencial(nextSeq);
    cliente.setNumeroCliente(String.format("CLI-%04d", nextSeq));
}
```

**Analog B — `ParecerController`'s `numeroVersao`** (lines 449-462, near-identical shape, scoped by a parent FK the same way `ordem` must be scoped by `processo_id`):
```java
synchronized (ParecerVersaoRepository.class) {
    int next = parecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId(solicitacaoId).orElse(0) + 1;

    ParecerVersao versao = ParecerVersao.builder()
            .tenantId(tenantId)
            .solicitacaoId(solicitacaoId)
            .conteudo(conteudo)
            .numeroVersao(next)
            .criadoPorId(principal.getUserId())
            .build();

    saved = parecerVersaoRepository.save(versao);
}
```

**Repository query to add** — `FactoRepository.java` currently only has:
```java
public interface FactoRepository extends JpaRepository<Facto, Integer> {
    List<Facto> findByProcessoId(UUID processoId);
    List<Facto> findByProcessoIdOrderByOrdemAsc(UUID processoId);
}
```
Add, following `ClienteRepository`'s exact `@Query`/`@Param` style (lines 17-18):
```java
@Query("SELECT MAX(f.ordem) FROM Facto f WHERE f.processoId = :processoId")
Optional<Integer> findMaxOrdemByProcessoId(@Param("processoId") UUID processoId);
```
(needs `import org.springframework.data.jpa.repository.Query;`, `import org.springframework.data.repository.query.Param;`, `import java.util.Optional;` added to `FactoRepository.java`.)

**Apply per `81-CONTEXT.md`:** scope the `synchronized` block + max-lookup by `processoId` (not tenant-wide like Cliente, not solicitacaoId-wide like Parecer — the closest-scoped variable is `processoId`, mirroring `ParecerVersao`'s `solicitacaoId` scoping exactly). `createFacto` (POST) ignores any client-supplied `ordem` and always computes `max+1`; `updateFacto` (PUT) accepts an explicit `ordem` from the payload with no recomputation (per CONTEXT.md — this is the deliberate reordering entry point).

---

### 5. `createProcesso`, `updateProcesso`, `createProcessoIntake` — juizo/origem wiring (controller, CRUD)

**`createProcesso`** (full current body, lines 957-971) — `processo` is bound directly from `@RequestBody`, so `juizo`/`origem` on the incoming JSON already populate the entity via Jackson automatically (no explicit wiring needed here beyond the entity already having the fields since Phase 80) — verify this is in fact sufficient (it should be, since this method does not selectively copy fields the way `updateProcesso` does):
```java
@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos")
public ResponseEntity<?> createProcesso(@RequestBody Processo processo) {
    UUID tenantId = getTenantId();
    processo.setTenantId(tenantId);
    if (processo.getResponsavelId() != null) {
        User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
        if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "responsavelId não pertence a este tenant"));
        }
    }
    Processo saved = processoRepository.save(processo);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
}
```

**`updateProcesso`** (full current body, lines 983-1004) — this is the field-by-field selective-copy method; `juizo` MUST be added as a new `processo.set...(payload.get...())` line, `origem` MUST be deliberately excluded following the exact existing comment convention for `estado`:
```java
@PreAuthorize("hasAuthority('processos:edit')")
@PutMapping("/processos/{id}")
public ResponseEntity<?> updateProcesso(@PathVariable UUID id, @RequestBody Processo payload) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }

    processo.setClienteId(payload.getClienteId());
    processo.setNumeroProcesso(payload.getNumeroProcesso());
    processo.setTipoProcesso(payload.getTipoProcesso());
    processo.setAreaJuridica(payload.getAreaJuridica());
    processo.setTribunal(payload.getTribunal());
    // estado is intentionally excluded: changes must go through /transicao or /formalizar
    processo.setDescricao(payload.getDescricao());
    processo.setDataInicio(payload.getDataInicio());
    processo.setDataFim(payload.getDataFim());
    processo.setLegalHold(payload.getLegalHold() != null ? payload.getLegalHold() : false);
    processo.setDataRetencao(payload.getDataRetencao());

    return ResponseEntity.ok(processoRepository.save(processo));
}
```
**Required edit:** add `processo.setJuizo(payload.getJuizo());` in the copy list, and add a new comment line `// origem is intentionally excluded: immutable after intake, only writable via POST /processos/intake` — do NOT add a `setOrigem` call (per `81-CONTEXT.md` "Imutabilidade de Origem" decision — silently ignored, no 400).

**`createProcessoIntake`** (full current body, lines 1022-1029) — currently validates nothing; per `81-CONTEXT.md` + `PITFALLS.md` Pitfall 2, `origem` becomes the **first field ever validated at this endpoint**, modeled on `formalizarProcesso`'s `camposEmFalta`/422 shape (lines 1196-1217) rather than copy-pasted wholesale (that map is per-`tipoProcesso` and keyed for the *formalizar* gate, not intake):
```java
@PreAuthorize("hasAuthority('processos:create')")
@PostMapping("/processos/intake")
public ResponseEntity<?> createProcessoIntake(@RequestBody Processo processo) {
    processo.setTenantId(getTenantId());
    processo.setEstado("TRIAGEM");
    Processo saved = processoRepository.save(processo);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
}
```
**Required edit — new validation, no existing precedent to copy verbatim, model the 422 response shape on `formalizarProcesso`'s:**
```java
if (processo.getOrigem() == null) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "message", "Não é possível criar processo: campo 'origem' é obrigatório no intake",
            "camposEmFalta", List.of("origem")
    ));
}
```
(placed after `processo.setTenantId(...)`, before `processo.setEstado("TRIAGEM")`, matching `formalizarProcesso`'s gate-then-mutate-then-save ordering.)

---

### 6. `CAMPOS_MINIMOS_POR_TIPO` — add `origem` to every key (config, transform)

**Full current map literal to edit** (lines 72-80):
```java
private static final Map<String, List<String>> CAMPOS_MINIMOS_POR_TIPO = Map.of(
        "civel", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
        "penal", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
        "laboral", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
        "administrativo", List.of("clienteId", "numeroProcesso", "areaJuridica", "dataInicio"),
        "familia", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
        "comercial", List.of("clienteId", "numeroProcesso", "areaJuridica", "dataInicio"),
        "default", List.of("clienteId", "tipoProcesso", "areaJuridica", "dataInicio")
);
```
**Required edit:** append `"origem"` to every `List.of(...)` value, including `"default"` (per `81-CONTEXT.md`, explicit — "TODAS as chaves"). Then extend `formalizarProcesso`'s `switch (campo)` block (lines 1202-1210) with a new `case "origem" -> { if (processo.getOrigem() == null) camposEmFalta.add(campo); }` arm, matching the existing `case` style exactly (each arm is a one-line null/blank check + `camposEmFalta.add(campo)`).

---

### 7. `listProcessos` enriched map — add `juizo`/`origem` (controller, transform)

**Full current enrichment loop** (lines 909-952) — the single highest-risk integration gap per `ARCHITECTURE.md` Anti-Pattern 4:
```java
List<Map<String, Object>> enriched = sorted.stream().map(p -> {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId());
    m.put("tenant_id", p.getTenantId());
    m.put("cliente_id", p.getClienteId());
    m.put("responsavel_id", p.getResponsavelId());
    m.put("numero_processo", p.getNumeroProcesso());
    m.put("tipo_processo", p.getTipoProcesso());
    m.put("area_juridica", p.getAreaJuridica());
    m.put("tribunal", p.getTribunal());
    m.put("estado", p.getEstado());
    m.put("data_inicio", p.getDataInicio());
    m.put("data_fim", p.getDataFim());
    m.put("descricao", p.getDescricao());
    m.put("created_at", p.getCreatedAt());

    // Resolve responsavel_nome from pre-loaded map
    User resp = p.getResponsavelId() != null ? responsaveisMap.get(p.getResponsavelId()) : null;
    m.put("responsavel_nome", resp != null ? resp.getNome() : null);

    // Compute risco_mais_critico and tem_prazo_escalonado from non-concluido prazos
    List<Prazo> prazosProcesso = prazosPorProcesso.getOrDefault(p.getId(), List.of());
    List<Prazo> ativos = prazosProcesso.stream()
            .filter(pr -> !Boolean.TRUE.equals(pr.getConcluido()))
            .toList();

    String riscoMaisCritico = "ok";
    boolean temEscalonado = false;
    for (Prazo pr : ativos) {
        String r = computeRisco(pr.getDataLimite(), pr.getPrioridade());
        if ("vencido".equals(r)) {
            riscoMaisCritico = "vencido";
        } else if ("proximo".equals(r) && !"vencido".equals(riscoMaisCritico)) {
            riscoMaisCritico = "proximo";
        }
        if (Boolean.TRUE.equals(pr.getEscalonado())) {
            temEscalonado = true;
        }
    }
    m.put("risco_mais_critico", riscoMaisCritico);
    m.put("tem_prazo_escalonado", temEscalonado);

    return m;
}).toList();
```
**Required edit:** insert two lines directly after `m.put("tribunal", p.getTribunal());` (keeping field order consistent with the entity's column order in `Processo.java`, where `juizo`/`origem` sit right after `tribunal`/`estado`):
```java
m.put("juizo", p.getJuizo());
m.put("origem", p.getOrigem());
```
`p.getOrigem()` returns the `OrigemProcesso` enum — Jackson serializes enums to their `name()` string by default, consistent with how `Processo`'s raw-entity JSON responses (`getProcesso`, `createProcesso`) will already render it — no extra `.name()`/`.toString()` call needed, keep it symmetrical with every other `m.put(...)` call in this loop (all pass the raw getter value, not a pre-stringified one, e.g. `m.put("data_inicio", p.getDataInicio())` passes a raw `LocalDate`).

---

## Shared Patterns

### Tenant/ownership double-check for child entities
**Source:** `ResourceController.java`, `updateProcessoFase` (lines 1601-1608)
**Apply to:** every `PUT`/`DELETE` for Decisão/Facto/Testemunha (6 endpoints total — mandatory per `81-CONTEXT.md` and `PITFALLS.md` Pitfall 3)
```java
Processo processo = processoRepository.findById(id).orElse(null);
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
XEntity x = xRepository.findById(xId).orElse(null);
if (x == null || !x.getProcessoId().equals(id)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "<Entidade> não encontrada"));
}
```

### Tenant/ownership single-check for parent-scoped GET/POST (no existing child row to re-verify)
**Source:** `ResourceController.java`, `listPartes`/`createParte` (lines 1521-1539)
**Apply to:** `GET`/`POST` for Decisão/Facto/Testemunha (6 endpoints)
```java
Processo processo = processoRepository.findById(id).orElse(null);
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
```

### RBAC scopes — no new scopes needed
**Source:** `DatabaseSeeder.java` (already seeded, per `ARCHITECTURE.md`) + every existing Processo-child endpoint in `ResourceController.java`
**Apply to:** all 12 new endpoints — reuse `processos:view` (GET), `processos:edit` (POST/PUT/DELETE) exactly as `Parte`/`ProcessoFase`/`Movimentacao` already do. No `@PreAuthorize` string is new; copy verbatim from the analog endpoint of the same HTTP verb.

### Concurrent-safe sequence-number generation (`synchronized` block + `findMax...` + `orElse(0)+1`)
**Source:** `ResourceController.java` `createCliente` (lines 230-236) and `ParecerController.java` (lines 450-462)
**Apply to:** `createFacto`'s `ordem` computation only (Decisão/Testemunha have no sequence field)
```java
synchronized (FactoRepository.class) {
    int nextOrdem = factoRepository.findMaxOrdemByProcessoId(id).orElse(0) + 1;
    facto.setOrdem(nextOrdem);
    // ... facto.setProcessoId(id); save
}
```

### Error-response shape (404/400/422) — `Map.of("message", "...")`, optionally + extra key
**Source:** used identically across the entire file (every analog above)
**Apply to:** all new endpoints — never introduce a new error envelope shape; 404 = `Map.of("message", "X não encontrado")`, 400 = `Map.of("message", "...")`, 422 (validation, only for intake origem check) = `Map.of("message", "...", "camposEmFalta", List.of(...))` mirroring `formalizarProcesso`'s exact shape.

## No Analog Found

None — every required change has a direct, same-file precedent. The only genuinely *new* logic pattern (not a copy of an existing method, but a new combination) is:
- `createProcessoIntake`'s origem-required validation (no field is validated at intake today — see Pitfall 2 in `PITFALLS.md`), modeled on `formalizarProcesso`'s 422 response shape rather than copied from an existing intake-time check.
- Child-entity DELETE (no existing Processo-child DELETE endpoint exists at all) — composed from `updateProcessoFase`'s double-check + `deleteProcesso`'s delete-and-respond shape, as shown above.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (full file, 2504 lines, read in targeted ranges: 1-100, 820-1236, 1500-1699, 1960-2110), `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (lines 430-470), `backend/src/main/java/com/lexcv/repositories/{Cliente,Facto,Decisao,Testemunha,ParecerVersao}Repository.java`, `backend/src/main/java/com/lexcv/models/{Processo,Decisao,Facto,Testemunha}.java`
**Files scanned:** 9
**Pattern extraction date:** 2026-07-07
