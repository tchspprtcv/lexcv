package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ClienteMergeRequest;
import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.services.StorageService;
import com.lexcv.services.RiscoPrazoService;
import com.lexcv.services.NotificacaoService;
import com.lexcv.dtos.ConflictCheckDecisaoRequest;
import com.lexcv.dtos.ConflictCheckResponse;
import com.lexcv.dtos.TimelineItemDto;
import com.lexcv.dtos.TransicaoRequest;
import com.lexcv.dtos.WorkflowResponse;
import com.lexcv.dtos.WorkflowResponse.TransicaoInfo;
import com.lexcv.dtos.PrazoRequest;
import org.springframework.transaction.annotation.Transactional;
import com.lexcv.dtos.ConflictCheckResponse.ConflictMatchDto;
import com.lexcv.dtos.DashboardKpiResponse;
import com.lexcv.dtos.UserSummaryResponse;
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ResourceController {

    private final ClienteRepository clienteRepository;
    private final ClienteContactoRepository clienteContactoRepository;
    private final ClienteNotaRepository clienteNotaRepository;
    private final ContaCorrenteRepository contaCorrenteRepository;
    private final ProcessoRepository processoRepository;
    private final ParteRepository parteRepository;
    private final FaseProcessualRepository faseProcessualRepository;
    private final ProcessoFaseRepository processoFaseRepository;
    private final EventoRepository eventoRepository;
    private final DocumentoRepository documentoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final HonorarioRepository honorarioRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ConflictCheckDecisaoRepository conflictCheckDecisaoRepository;
    private final PrazoRepository prazoRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final StorageService storageService;
    private final RiscoPrazoService riscoPrazoService;
    private final NotificacaoService notificacaoService;
    private final ClienteAdvogadoRepository clienteAdvogadoRepository;
    private final ClienteAdministrativoRepository clienteAdministrativoRepository;
    private final DecisaoRepository decisaoRepository;
    private final TestemunhaRepository testemunhaRepository;
    private final FactoRepository factoRepository;
    // WR-02 (Phase 90 code review, iteration 3): used only by mergeClientes, to migrate
    // ParecerSolicitacao.clienteId (nullable = false, validated by ParecerController) off the
    // secondary client before it is deleted -- see the migration loop there for context.
    private final ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    // ==========================================
    // INTAKE & CONFLICT CHECK — campos mínimos por tipo_processo
    // ==========================================
    private static final Map<String, List<String>> CAMPOS_MINIMOS_POR_TIPO = Map.of(
            "civel", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio", "origem"),
            "penal", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio", "origem"),
            "laboral", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio", "origem"),
            "administrativo", List.of("clienteId", "numeroProcesso", "areaJuridica", "dataInicio", "origem"),
            "familia", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio", "origem"),
            "comercial", List.of("clienteId", "numeroProcesso", "areaJuridica", "dataInicio", "origem"),
            "default", List.of("clienteId", "tipoProcesso", "areaJuridica", "dataInicio", "origem")
    );

    // ==========================================
    // WORKFLOW — mapa de transições permitidas por estado
    // TRIAGEM -> ATIVO é tratado pelo endpoint /formalizar (Phase 32)
    // ==========================================
    private static final Map<String, List<TransicaoInfo>> TRANSICOES_PERMITIDAS = Map.of(
        "ATIVO", List.of(
            new TransicaoInfo("suspender", "Suspender",  "processos:manage", true),
            new TransicaoInfo("encerrar",  "Encerrar",   "processos:manage", true)
        ),
        "SUSPENSO", List.of(
            new TransicaoInfo("ativar",   "Ativar Processo", "processos:edit",   false),
            new TransicaoInfo("encerrar", "Encerrar",        "processos:manage", true)
        ),
        "ENCERRADO", List.of(
            new TransicaoInfo("reabrir",  "Reabrir",    "processos:manage", true)
        )
    );

    private String derivarProximoPasso(String estado) {
        if (estado == null) return null;
        return switch (estado.toUpperCase()) {
            case "TRIAGEM"   -> "Executar conflict check e formalizar o processo";
            case "ATIVO"     -> "Acompanhar prazos e movimentacoes";
            case "SUSPENSO"  -> "Reativar quando aplicavel ou encerrar";
            case "ENCERRADO" -> "Processo encerrado; reabrir se necessario";
            default          -> null;
        };
    }

    private UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getTenantId();
    }

    /**
     * Resolves the display name of a user by their ID, scoped to the current tenant.
     * Returns null if autorId is null, user not found, or user belongs to a different tenant.
     */
    private String resolveAutorNome(UUID autorId, UUID tenantId) {
        if (autorId == null) return null;
        User user = userRepository.findById(autorId).orElse(null);
        return (user != null && tenantId.equals(user.getTenantId())) ? user.getNome() : null;
    }

    private static boolean contains(String value, String queryLower) {
        if (value == null) return false;
        return value.toLowerCase().contains(queryLower);
    }

    private static LocalDateTime parseCreatedFrom(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDateTime parseCreatedTo(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim()).atTime(LocalTime.MAX);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ==========================================
    // CLIENTES
    // ==========================================
    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes")
    public ResponseEntity<?> listClientes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String nif,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String localidade,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo) {
        UUID tenantId = getTenantId();
        List<Cliente> clientes = clienteRepository.findByTenantId(tenantId);

        final String qNorm = q != null ? q.trim().toLowerCase() : null;
        final String nomeNorm = nome != null ? nome.trim().toLowerCase() : null;
        final String nifNorm = nif != null ? nif.trim().toLowerCase() : null;
        final String tipoNorm = tipo != null ? tipo.trim().toLowerCase() : null;
        final String localidadeNorm = localidade != null ? localidade.trim().toLowerCase() : null;

        final LocalDateTime from = parseCreatedFrom(createdFrom);
        final LocalDateTime to = parseCreatedTo(createdTo);

        List<Cliente> filtered = clientes.stream()
                .filter(c -> c.getTenantId().equals(tenantId))
                .filter(c -> {
                    if (qNorm == null || qNorm.isEmpty()) return true;
                    return contains(c.getNome(), qNorm)
                            || contains(c.getNif(), qNorm)
                            || contains(c.getEmail(), qNorm)
                            || contains(c.getTelefone(), qNorm);
                })
                .filter(c -> {
                    if (nomeNorm == null || nomeNorm.isEmpty()) return true;
                    return contains(c.getNome(), nomeNorm);
                })
                .filter(c -> {
                    if (nifNorm == null || nifNorm.isEmpty()) return true;
                    return contains(c.getNif(), nifNorm);
                })
                .filter(c -> {
                    if (tipoNorm == null || tipoNorm.isEmpty()) return true;
                    return (c.getTipo() != null ? c.getTipo() : "").trim().toLowerCase().equals(tipoNorm);
                })
                .filter(c -> {
                    if (ativo == null) return true;
                    return Boolean.TRUE.equals(c.getAtivo()) == ativo;
                })
                .filter(c -> {
                    if (localidadeNorm == null || localidadeNorm.isEmpty()) return true;
                    return contains(c.getLocalidade(), localidadeNorm) || contains(c.getMorada(), localidadeNorm);
                })
                .filter(c -> {
                    if (from == null) return true;
                    return c.getCreatedAt() != null && !c.getCreatedAt().isBefore(from);
                })
                .filter(c -> {
                    if (to == null) return true;
                    return c.getCreatedAt() != null && !c.getCreatedAt().isAfter(to);
                })
                .toList();

        return ResponseEntity.ok(filtered);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PostMapping("/clientes")
    public ResponseEntity<?> createCliente(@Valid @RequestBody Cliente cliente) {
        if (!isDocumentoTipoValidoParaTipo(cliente.getTipo(), cliente.getDocumentoTipo(), cliente.getDocumentoNumero())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Tipo de documento inválido para o tipo de cliente selecionado"));
        }
        // WR-03 (Phase 90 code review, iteration 2): validate the documento_numero unique
        // constraint explicitly, so the DataIntegrityViolationException catch below is reserved
        // for the numero_sequencial race it's actually meant to handle, instead of also catching
        // (and mislabeling as a "client number conflict") this permanent validation failure.
        if (cliente.getDocumentoNumero() != null
                && clienteRepository.findByTenantIdAndDocumentoNumero(getTenantId(), cliente.getDocumentoNumero()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Já existe um cliente com este número de documento"));
        }

        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): id is client-suppliable via the bound
        // entity; without clearing it, save() would route through merge() instead of persist()
        // for an attacker-guessed id, silently overwriting an existing (possibly cross-tenant) row.
        cliente.setId(null);
        cliente.setTenantId(getTenantId());
        if (cliente.getAtivo() == null) {
            cliente.setAtivo(true);
        }
        Cliente saved;
        try {
            synchronized (ClienteRepository.class) {
                java.util.Optional<Integer> result = clienteRepository.findMaxNumeroSequencialByTenantId(getTenantId());
                int maxSeq = result.orElse(0);
                int nextSeq = maxSeq + 1;
                cliente.setNumeroSequencial(nextSeq);
                cliente.setNumeroCliente(String.format("CLI-%04d", nextSeq));
                saved = clienteRepository.save(cliente);
            }
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Conflito ao atribuir número de cliente, tente novamente"));
        }

        // Auto initialize Cuenta Corriente
        ContaCorrente cc = ContaCorrente.builder()
                .clienteId(saved.getId())
                .saldo(BigDecimal.ZERO)
                .build();
        contaCorrenteRepository.save(cc);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}")
    public ResponseEntity<?> getCliente(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }
        return ResponseEntity.ok(cliente);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PutMapping("/clientes/{id}")
    public ResponseEntity<?> updateCliente(@PathVariable UUID id, @Valid @RequestBody Cliente payload) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        // Only NEW/changed submissions are validated; a documentoTipo/documentoNumero resent
        // identical to the stored value is a pre-existing combination tolerated per
        // 74-CONTEXT.md line 26 ("pre-existing invalid combinations are tolerated até serem
        // editados; só submissões novas são validadas").
        boolean documentoTipoUnchanged = java.util.Objects.equals(cliente.getDocumentoTipo(), payload.getDocumentoTipo())
                && java.util.Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero());

        if (!documentoTipoUnchanged && !isDocumentoTipoValidoParaTipo(payload.getTipo(), payload.getDocumentoTipo(), payload.getDocumentoNumero())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Tipo de documento inválido para o tipo de cliente selecionado"));
        }

        // WR-01 (Phase 90 code review, iteration 3): mirror createCliente's documentoNumero
        // uniqueness check here, so a PUT that changes documentoNumero to a value already used
        // by another client in this tenant surfaces as a clean 409 instead of an uncaught
        // DataIntegrityViolationException leaking raw JDBC/Hibernate text via the 500 catch-all.
        boolean documentoNumeroChanged = !java.util.Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero());
        if (documentoNumeroChanged && payload.getDocumentoNumero() != null
                && clienteRepository.findByTenantIdAndDocumentoNumero(getTenantId(), payload.getDocumentoNumero()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Já existe um cliente com este número de documento"));
        }

        cliente.setNome(payload.getNome());
        if (payload.getTipo() != null) cliente.setTipo(payload.getTipo());
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

        if (payload.getDescricaoCaso() != null) cliente.setDescricaoCaso(payload.getDescricaoCaso());
        if (payload.getDocumentosEntregues() != null) cliente.setDocumentosEntregues(payload.getDocumentosEntregues());
        if (payload.getDocumentosATratar() != null) cliente.setDocumentosATratar(payload.getDocumentosATratar());
        if (payload.getDeslocacoes() != null) cliente.setDeslocacoes(payload.getDeslocacoes());
        if (payload.getHonorariosPropostos() != null) cliente.setHonorariosPropostos(payload.getHonorariosPropostos());

        // nif is always required in the payload -- @Valid rejects null/blank before this line
        // executes, so this is intentionally unconditional (do not make it conditional like the
        // fields above; that would silently reopen the BLOCKER 1/2 stale-NIF defect class).
        cliente.setNif(payload.getNif());

        Cliente saved = clienteRepository.save(cliente);
        return ResponseEntity.ok(saved);
    }

    /**
     * Validates that tipo is a recognized cliente type and that documentoTipo (when present) is
     * an allowed value for that tipo. tipo must be PARTICULAR or EMPRESA regardless of whether
     * documentoTipo is present -- a missing/unrecognized tipo is always rejected. PARTICULAR
     * allows CNI/BI/PASSAPORTE; EMPRESA allows only REG_COMERCIAL. documentoTipo is optional; a
     * null value only passes if documentoNumero is also absent, since a document number with no
     * associated type is an orphaned/ambiguous state.
     */
    private boolean isDocumentoTipoValidoParaTipo(String tipo, DocumentoTipo documentoTipo, String documentoNumero) {
        if (!TipoCliente.PARTICULAR.name().equals(tipo) && !TipoCliente.EMPRESA.name().equals(tipo)) {
            return false;
        }
        if (documentoTipo == null) {
            return documentoNumero == null || documentoNumero.isBlank();
        }
        if (TipoCliente.PARTICULAR.name().equals(tipo)) {
            return Set.of(DocumentoTipo.CNI, DocumentoTipo.BI, DocumentoTipo.PASSAPORTE).contains(documentoTipo);
        }
        return documentoTipo == DocumentoTipo.REG_COMERCIAL;
    }

    // ==========================================
    // PROCURAÇÃO
    // ==========================================
    @PreAuthorize("hasAuthority('clientes:edit')")
    @PostMapping(value = "/clientes/{id}/procuracao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProcuracao(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ficheiro em falta"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nome do ficheiro em falta"));
        }

        try {
            String oldKey = cliente.getProcuracaoKey();
            UUID syntheticId = UUID.randomUUID();
            String newKey;
            try (InputStream inputStream = file.getInputStream()) {
                newKey = storageService.upload(getTenantId(), syntheticId,
                        originalName, inputStream, file.getContentType(), file.getSize());
            }

            // Upload new object before touching the DB reference — old remains intact if upload fails.
            cliente.setProcuracaoKey(newKey);
            clienteRepository.save(cliente);

            // Only delete the old object after the DB reference is committed (WR-02): if the
            // save above fails, the DB still points at oldKey, which must still exist.
            if (oldKey != null) {
                storageService.delete(oldKey);
            }
            return ResponseEntity.ok(Map.of("procuracaoKey", newKey));
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao ler ficheiro"));
        }
    }

    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}/procuracao/download")
    public ResponseEntity<?> downloadProcuracao(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }
        if (cliente.getProcuracaoKey() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Procuração não encontrada"));
        }

        try {
            String url = storageService.presignedDownloadUrl(cliente.getProcuracaoKey());
            return ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600));
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        }
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @DeleteMapping("/clientes/{id}/procuracao")
    public ResponseEntity<?> deleteProcuracao(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }
        if (cliente.getProcuracaoKey() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Procuração não encontrada"));
        }

        try {
            storageService.delete(cliente.getProcuracaoKey());
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        }

        cliente.setProcuracaoKey(null);
        clienteRepository.save(cliente);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // ADVOGADOS DO CLIENTE
    // ==========================================
    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}/advogados")
    public ResponseEntity<?> listClienteAdvogados(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        List<ClienteAdvogado> links = clienteAdvogadoRepository.findByClienteIdAndTenantId(id, getTenantId());
        List<User> users = links.stream()
                .map(link -> userRepository.findById(link.getUserId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PostMapping("/clientes/{id}/advogados/{userId}")
    public ResponseEntity<?> addClienteAdvogado(@PathVariable UUID id, @PathVariable UUID userId) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        if (user.getRoles().stream().noneMatch(r -> "ADVOGADO".equals(r.getNome()))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Utilizador não tem o papel ADVOGADO"));
        }

        if (clienteAdvogadoRepository.findByClienteIdAndUserIdAndTenantId(id, userId, getTenantId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Advogado já associado ao cliente"));
        }

        ClienteAdvogado link = ClienteAdvogado.builder()
                .clienteId(id)
                .userId(userId)
                .tenantId(getTenantId())
                .build();
        clienteAdvogadoRepository.save(link);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId));
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @Transactional
    @DeleteMapping("/clientes/{id}/advogados/{userId}")
    public ResponseEntity<?> removeClienteAdvogado(@PathVariable UUID id, @PathVariable UUID userId) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        clienteAdvogadoRepository.deleteByClienteIdAndUserIdAndTenantId(id, userId, getTenantId());
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // ADMINISTRATIVOS DO CLIENTE
    // ==========================================
    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}/administrativos")
    public ResponseEntity<?> listClienteAdministrativos(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        List<ClienteAdministrativo> links = clienteAdministrativoRepository.findByClienteIdAndTenantId(id, getTenantId());
        List<User> users = links.stream()
                .map(link -> userRepository.findById(link.getUserId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PostMapping("/clientes/{id}/administrativos/{userId}")
    public ResponseEntity<?> addClienteAdministrativo(@PathVariable UUID id, @PathVariable UUID userId) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        if (user.getRoles().stream().noneMatch(r -> "ASSISTENTE".equals(r.getNome()) || "TECNICO".equals(r.getNome()))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Utilizador não tem o papel ASSISTENTE ou TECNICO"));
        }

        if (clienteAdministrativoRepository.findByClienteIdAndUserIdAndTenantId(id, userId, getTenantId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Administrativo já associado ao cliente"));
        }

        ClienteAdministrativo link = ClienteAdministrativo.builder()
                .clienteId(id)
                .userId(userId)
                .tenantId(getTenantId())
                .build();
        clienteAdministrativoRepository.save(link);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId));
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @Transactional
    @DeleteMapping("/clientes/{id}/administrativos/{userId}")
    public ResponseEntity<?> removeClienteAdministrativo(@PathVariable UUID id, @PathVariable UUID userId) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        clienteAdministrativoRepository.deleteByClienteIdAndUserIdAndTenantId(id, userId, getTenantId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @DeleteMapping("/clientes/{id}")
    public ResponseEntity<?> deleteCliente(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        // Delete associated Conta Corrente if exists
        contaCorrenteRepository.findByClienteId(id).ifPresent(contaCorrenteRepository::delete);
        clienteRepository.delete(cliente);

        return ResponseEntity.ok(Map.of("message", "Cliente removido com sucesso!"));
    }

    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}/conta-corrente")
    public ResponseEntity<?> getClienteContaCorrente(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        ContaCorrente cc = contaCorrenteRepository.findByClienteId(id)
                .orElseGet(() -> contaCorrenteRepository.save(
                        ContaCorrente.builder().clienteId(id).saldo(BigDecimal.ZERO).build()
                ));

        return ResponseEntity.ok(Map.of(
                "cliente_id", id.toString(),
                "saldo", cc.getSaldo(),
                "updated_at", cc.getUpdatedAt() != null ? cc.getUpdatedAt() : LocalDateTime.now()
        ));
    }

    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}/contactos")
    public ResponseEntity<?> listClienteContactos(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        List<ClienteContacto> contactos = clienteContactoRepository.findByTenantIdAndClienteIdOrderByCreatedAtAsc(
                getTenantId(),
                id
        );
        return ResponseEntity.ok(contactos);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PostMapping("/clientes/{id}/contactos")
    public ResponseEntity<?> createClienteContacto(@PathVariable UUID id, @RequestBody ClienteContacto payload) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        if (payload.getValor() == null || payload.getValor().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "O valor é obrigatório"));
        }

        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        payload.setId(null);
        payload.setTenantId(getTenantId());
        payload.setClienteId(id);
        payload.setValor(payload.getValor().trim());
        if (payload.getTipo() != null) {
            payload.setTipo(payload.getTipo().trim());
        }

        ClienteContacto saved = clienteContactoRepository.save(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PutMapping("/clientes/{clienteId}/contactos/{contactoId}")
    public ResponseEntity<?> updateClienteContacto(
            @PathVariable UUID clienteId,
            @PathVariable UUID contactoId,
            @RequestBody ClienteContacto payload) {
        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        ClienteContacto contacto = clienteContactoRepository.findById(contactoId).orElse(null);
        if (contacto == null
                || !contacto.getTenantId().equals(getTenantId())
                || !contacto.getClienteId().equals(clienteId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Contacto não encontrado"));
        }

        if (payload.getValor() != null) {
            if (payload.getValor().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "O valor é obrigatório"));
            }
            contacto.setValor(payload.getValor().trim());
        }
        if (payload.getTipo() != null) {
            contacto.setTipo(payload.getTipo().trim());
        }

        ClienteContacto saved = clienteContactoRepository.save(contacto);
        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @DeleteMapping("/clientes/{clienteId}/contactos/{contactoId}")
    public ResponseEntity<?> deleteClienteContacto(@PathVariable UUID clienteId, @PathVariable UUID contactoId) {
        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        ClienteContacto contacto = clienteContactoRepository.findById(contactoId).orElse(null);
        if (contacto == null
                || !contacto.getTenantId().equals(getTenantId())
                || !contacto.getClienteId().equals(clienteId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Contacto não encontrado"));
        }

        clienteContactoRepository.delete(contacto);
        return ResponseEntity.ok(Map.of("message", "Contacto removido com sucesso!"));
    }

    @PreAuthorize("hasAuthority('clientes:view')")
    @GetMapping("/clientes/{id}/notas")
    public ResponseEntity<?> listClienteNotas(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        List<ClienteNota> notas = clienteNotaRepository.findByTenantIdAndClienteIdOrderByUpdatedAtDesc(
                getTenantId(),
                id
        );
        return ResponseEntity.ok(notas);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PostMapping("/clientes/{id}/notas")
    public ResponseEntity<?> createClienteNota(@PathVariable UUID id, @RequestBody ClienteNota payload) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        if (payload.getConteudo() == null || payload.getConteudo().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "O conteúdo é obrigatório"));
        }

        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        payload.setId(null);
        payload.setTenantId(getTenantId());
        payload.setClienteId(id);
        payload.setConteudo(payload.getConteudo().trim());
        if (payload.getTitulo() != null) {
            payload.setTitulo(payload.getTitulo().trim());
        }

        ClienteNota saved = clienteNotaRepository.save(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @PutMapping("/clientes/{clienteId}/notas/{notaId}")
    public ResponseEntity<?> updateClienteNota(
            @PathVariable UUID clienteId,
            @PathVariable UUID notaId,
            @RequestBody ClienteNota payload) {
        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        ClienteNota nota = clienteNotaRepository.findById(notaId).orElse(null);
        if (nota == null
                || !nota.getTenantId().equals(getTenantId())
                || !nota.getClienteId().equals(clienteId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Nota não encontrada"));
        }

        if (payload.getConteudo() != null) {
            if (payload.getConteudo().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "O conteúdo é obrigatório"));
            }
            nota.setConteudo(payload.getConteudo().trim());
        }
        if (payload.getTitulo() != null) {
            nota.setTitulo(payload.getTitulo().trim());
        }

        ClienteNota saved = clienteNotaRepository.save(nota);
        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @DeleteMapping("/clientes/{clienteId}/notas/{notaId}")
    public ResponseEntity<?> deleteClienteNota(@PathVariable UUID clienteId, @PathVariable UUID notaId) {
        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }

        ClienteNota nota = clienteNotaRepository.findById(notaId).orElse(null);
        if (nota == null
                || !nota.getTenantId().equals(getTenantId())
                || !nota.getClienteId().equals(clienteId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Nota não encontrada"));
        }

        clienteNotaRepository.delete(nota);
        return ResponseEntity.ok(Map.of("message", "Nota removida com sucesso!"));
    }

    @PreAuthorize("hasAuthority('clientes:edit')")
    @Transactional
    @PostMapping("/clientes/merge")
    public ResponseEntity<?> mergeClientes(@RequestBody ClienteMergeRequest payload) {
        if (payload == null || payload.primaryId() == null || payload.secondaryId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Payload inválido"));
        }
        if (payload.primaryId().equals(payload.secondaryId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Clientes devem ser diferentes"));
        }

        UUID tenantId = getTenantId();
        Cliente primary = clienteRepository.findById(payload.primaryId()).orElse(null);
        Cliente secondary = clienteRepository.findById(payload.secondaryId()).orElse(null);
        if (primary == null || !primary.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente principal não encontrado"));
        }
        if (secondary == null || !secondary.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente duplicado não encontrado"));
        }

        if ((primary.getTipo() == null || primary.getTipo().isBlank()) && secondary.getTipo() != null) {
            primary.setTipo(secondary.getTipo());
        }
        if ((primary.getNif() == null || primary.getNif().isBlank()) && secondary.getNif() != null) {
            primary.setNif(secondary.getNif());
        }
        if ((primary.getEmail() == null || primary.getEmail().isBlank()) && secondary.getEmail() != null) {
            primary.setEmail(secondary.getEmail());
        }
        if ((primary.getTelefone() == null || primary.getTelefone().isBlank()) && secondary.getTelefone() != null) {
            primary.setTelefone(secondary.getTelefone());
        }
        if ((primary.getMorada() == null || primary.getMorada().isBlank()) && secondary.getMorada() != null) {
            primary.setMorada(secondary.getMorada());
        }
        if ((primary.getLocalidade() == null || primary.getLocalidade().isBlank()) && secondary.getLocalidade() != null) {
            primary.setLocalidade(secondary.getLocalidade());
        }
        if (primary.getAtivo() == null && secondary.getAtivo() != null) {
            primary.setAtivo(secondary.getAtivo());
        }

        final Cliente savedPrimary = clienteRepository.save(primary);

        List<Processo> processosToMove = processoRepository.findByClienteId(payload.secondaryId()).stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .toList();
        processosToMove.forEach(p -> p.setClienteId(savedPrimary.getId()));
        processoRepository.saveAll(processosToMove);

        List<ClienteContacto> contactosToMove =
                clienteContactoRepository.findByTenantIdAndClienteIdOrderByCreatedAtAsc(tenantId, payload.secondaryId());
        contactosToMove.forEach(c -> c.setClienteId(savedPrimary.getId()));
        clienteContactoRepository.saveAll(contactosToMove);

        List<ClienteNota> notasToMove =
                clienteNotaRepository.findByTenantIdAndClienteIdOrderByUpdatedAtDesc(tenantId, payload.secondaryId());
        notasToMove.forEach(n -> n.setClienteId(savedPrimary.getId()));
        clienteNotaRepository.saveAll(notasToMove);

        BigDecimal mergedSaldo = BigDecimal.ZERO;
        Optional<ContaCorrente> secondaryCcOpt = contaCorrenteRepository.findByClienteId(payload.secondaryId());
        if (secondaryCcOpt.isPresent()) {
            ContaCorrente secondaryCc = secondaryCcOpt.get();
            mergedSaldo = secondaryCc.getSaldo() != null ? secondaryCc.getSaldo() : BigDecimal.ZERO;
            if (mergedSaldo.compareTo(BigDecimal.ZERO) != 0) {
                ContaCorrente primaryCc = contaCorrenteRepository.findByClienteId(savedPrimary.getId())
                        .orElseGet(() -> contaCorrenteRepository.save(
                                ContaCorrente.builder().clienteId(savedPrimary.getId()).saldo(BigDecimal.ZERO).build()));
                BigDecimal primarySaldo = primaryCc.getSaldo() != null ? primaryCc.getSaldo() : BigDecimal.ZERO;
                primaryCc.setSaldo(primarySaldo.add(mergedSaldo));
                contaCorrenteRepository.save(primaryCc);
            }
            contaCorrenteRepository.delete(secondaryCc);
        }

        List<Documento> docsToMove = documentoRepository.findByTenantIdAndClienteId(tenantId, payload.secondaryId());
        docsToMove.forEach(d -> d.setClienteId(savedPrimary.getId()));
        documentoRepository.saveAll(docsToMove);

        for (ClienteAdvogado link : clienteAdvogadoRepository.findByClienteIdAndTenantId(payload.secondaryId(), tenantId)) {
            if (clienteAdvogadoRepository.findByClienteIdAndUserIdAndTenantId(savedPrimary.getId(), link.getUserId(), tenantId).isEmpty()) {
                link.setClienteId(savedPrimary.getId());
                clienteAdvogadoRepository.save(link);
            } else {
                clienteAdvogadoRepository.delete(link);
            }
        }
        for (ClienteAdministrativo link : clienteAdministrativoRepository.findByClienteIdAndTenantId(payload.secondaryId(), tenantId)) {
            if (clienteAdministrativoRepository.findByClienteIdAndUserIdAndTenantId(savedPrimary.getId(), link.getUserId(), tenantId).isEmpty()) {
                link.setClienteId(savedPrimary.getId());
                clienteAdministrativoRepository.save(link);
            } else {
                clienteAdministrativoRepository.delete(link);
            }
        }

        // WR-02 (Phase 90 code review, iteration 3): ParecerSolicitacao.clienteId is
        // nullable = false and enforced as a real FK-like reference by ParecerController; without
        // this migration a parecer still referencing secondaryId becomes permanently orphaned
        // once the secondary Cliente row is deleted below -- the same defect class CR-01 fixed
        // for Documento/ClienteAdvogado/ClienteAdministrativo.
        List<ParecerSolicitacao> pareceresToMove =
                parecerSolicitacaoRepository.findByTenantIdAndClienteId(tenantId, payload.secondaryId());
        pareceresToMove.forEach(ps -> ps.setClienteId(savedPrimary.getId()));
        parecerSolicitacaoRepository.saveAll(pareceresToMove);

        clienteRepository.delete(secondary);

        return ResponseEntity.ok(Map.of(
                "primary_id", savedPrimary.getId().toString(),
                "moved_processos", processosToMove.size(),
                "moved_contactos", contactosToMove.size(),
                "moved_notas", notasToMove.size(),
                "moved_documentos", docsToMove.size(),
                "merged_saldo", mergedSaldo.toString(),
                "moved_pareceres", pareceresToMove.size()
        ));
    }

    // ==========================================
    // PROCESSOS
    // ==========================================
    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos")
    public ResponseEntity<?> listProcessos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tribunal,
            @RequestParam(required = false) String area_juridica,
            @RequestParam(required = false) UUID cliente_id,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        UUID tenantId = getTenantId();
        List<Processo> processos = processoRepository.findByTenantId(tenantId);

        final String qNorm = q != null ? q.trim().toLowerCase() : null;
        final String estadoNorm = estado != null ? estado.trim().toLowerCase() : null;
        final String tribunalNorm = tribunal != null ? tribunal.trim().toLowerCase() : null;
        final String areaNorm = area_juridica != null ? area_juridica.trim().toLowerCase() : null;

        List<Processo> filtered = processos.stream()
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> {
                    if (cliente_id == null) return true;
                    return cliente_id.equals(p.getClienteId());
                })
                .filter(p -> {
                    if (estadoNorm == null || estadoNorm.isEmpty()) return true;
                    return (p.getEstado() != null ? p.getEstado() : "").trim().toLowerCase().equals(estadoNorm);
                })
                .filter(p -> {
                    if (tribunalNorm == null || tribunalNorm.isEmpty()) return true;
                    return contains(p.getTribunal(), tribunalNorm);
                })
                .filter(p -> {
                    if (areaNorm == null || areaNorm.isEmpty()) return true;
                    return contains(p.getAreaJuridica(), areaNorm);
                })
                .filter(p -> {
                    if (qNorm == null || qNorm.isEmpty()) return true;
                    return contains(p.getNumeroProcesso(), qNorm)
                            || contains(p.getTipoProcesso(), qNorm)
                            || contains(p.getDescricao(), qNorm)
                            || contains(p.getTribunal(), qNorm)
                            || contains(p.getAreaJuridica(), qNorm)
                            || contains(p.getEstado(), qNorm);
                })
                .toList();

        final String sortField = sortBy != null ? sortBy.trim().toLowerCase() : "created_at";
        final boolean asc = sortDir != null && sortDir.trim().equalsIgnoreCase("asc");

        Comparator<Processo> comparator;
        if ("numero".equals(sortField)) {
            comparator = Comparator.comparing(p -> p.getNumeroProcesso() != null ? p.getNumeroProcesso() : "");
        } else if ("estado".equals(sortField)) {
            comparator = Comparator.comparing(p -> p.getEstado() != null ? p.getEstado() : "");
        } else {
            comparator = Comparator.comparing(p -> p.getCreatedAt() != null ? p.getCreatedAt() : LocalDateTime.MIN);
        }

        if (!asc) comparator = comparator.reversed();

        List<Processo> sorted = filtered.stream().sorted(comparator).toList();

        // Enrich with responsavel_nome, risco_mais_critico, tem_prazo_escalonado
        // Fetch all prazos for tenant once to avoid N+1
        List<Prazo> allPrazos = prazoRepository.findByTenantId(tenantId);
        Map<UUID, List<Prazo>> prazosPorProcesso = new HashMap<>();
        for (Prazo prazo : allPrazos) {
            prazosPorProcesso.computeIfAbsent(prazo.getProcessoId(), k -> new ArrayList<>()).add(prazo);
        }

        // Batch-load responsáveis to avoid N+1 queries
        Set<UUID> responsavelIds = sorted.stream()
                .map(Processo::getResponsavelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> responsaveisMap = userRepository.findAllById(responsavelIds).stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .collect(Collectors.toMap(User::getId, u -> u));

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
            m.put("juizo", p.getJuizo());
            m.put("origem", p.getOrigem());
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

            String riscoMaisCritico = RiscoPrazoService.OK;
            boolean temEscalonado = false;
            for (Prazo pr : ativos) {
                String r = riscoPrazoService.computeRisco(pr.getDataLimite(), pr.getPrioridade());
                if (RiscoPrazoService.VENCIDO.equals(r)) {
                    riscoMaisCritico = RiscoPrazoService.VENCIDO;
                } else if (RiscoPrazoService.PROXIMO.equals(r) && !RiscoPrazoService.VENCIDO.equals(riscoMaisCritico)) {
                    riscoMaisCritico = RiscoPrazoService.PROXIMO;
                }
                if (Boolean.TRUE.equals(pr.getEscalonado())) {
                    temEscalonado = true;
                }
            }
            m.put("risco_mais_critico", riscoMaisCritico);
            m.put("tem_prazo_escalonado", temEscalonado);

            return m;
        }).toList();

        return ResponseEntity.ok(enriched);
    }

    @PreAuthorize("hasAuthority('processos:manage')")
    @PostMapping("/processos")
    public ResponseEntity<?> createProcesso(@RequestBody Processo processo) {
        UUID tenantId = getTenantId();
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        processo.setId(null);
        processo.setTenantId(tenantId);
        if (processo.getClienteId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "clienteId é obrigatório"));
        }
        Cliente cliente = clienteRepository.findById(processo.getClienteId()).orElse(null);
        if (cliente == null || !tenantId.equals(cliente.getTenantId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId não pertence a este tenant"));
        }
        if (processo.getResponsavelId() != null) {
            User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
            if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "responsavelId não pertence a este tenant"));
            }
        }
        Processo saved = processoRepository.save(processo);
        if (saved.getResponsavelId() != null) {
            notificacaoService.notificarProcessoAtribuido(tenantId, saved.getId(), saved.getResponsavelId(),
                    saved.getNumeroProcesso(), "/processos/" + saved.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('processos:manage')")
    @PutMapping("/processos/{id}/atribuir")
    @Transactional
    public ResponseEntity<?> atribuirResponsavel(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        UUID tenantId = getTenantId();

        String responsavelIdRaw = body.get("responsavelId");
        if (responsavelIdRaw == null || responsavelIdRaw.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "responsavelId é obrigatório"));
        }

        UUID responsavelId;
        try {
            responsavelId = UUID.fromString(responsavelIdRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "responsavelId inválido"));
        }

        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        User responsavel = userRepository.findById(responsavelId).orElse(null);
        // WR-02 (Phase 87 code review, iteration 3): the ativo invariant the prior WR-01 fix's
        // UserSummaryResponse.ativo field exists to support was only enforced client-side (the
        // "assign to" pickers filter it out) -- a direct call to this endpoint could still
        // assign a deactivated user. Enforced here too now.
        if (responsavel == null || !tenantId.equals(responsavel.getTenantId())
                || Boolean.FALSE.equals(responsavel.getAtivo())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "responsavelId não pertence a este tenant ou está inativo"));
        }

        // WR-01 (Phase 87 code review): no-op guard -- reassigning to the same
        // responsavelId must not re-fire "you were assigned" notifications.
        if (responsavelId.equals(processo.getResponsavelId())) {
            return ResponseEntity.ok(processo);
        }

        processo.setResponsavelId(responsavelId);
        Processo saved = processoRepository.save(processo);

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

        notificacaoService.notificarProcessoAtribuido(tenantId, saved.getId(), saved.getResponsavelId(),
                saved.getNumeroProcesso(), "/processos/" + saved.getId());

        return ResponseEntity.ok(saved);
    }

    // CR-02 (Phase 87 code review): tenant-scoped user listing for "assign to" pickers
    // (Reatribuir Responsável, Novo Prazo responsável) that any processos:manage holder
    // must be able to use. Deliberately NOT under /admin — GET /admin/users is gated
    // hasRole('ADMIN'), which left ADVOGADO (a processos:manage holder per
    // DatabaseSeeder.seedRbac()) unable to populate those pickers. Gated on
    // processos:view (the same permission that gates this whole processo-detail page),
    // so no one who couldn't already reach this page gains new access. Returns only
    // {id, nome} — see UserSummaryResponse — never roles/permissions/email/telefone.
    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/users")
    public ResponseEntity<?> listTenantUsers() {
        UUID tenantId = getTenantId();
        List<UserSummaryResponse> response = userRepository.findByTenantId(tenantId).stream()
                .map(u -> UserSummaryResponse.builder().id(u.getId()).nome(u.getNome()).ativo(u.getAtivo()).build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}")
    public ResponseEntity<?> getProcesso(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(processo);
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PutMapping("/processos/{id}")
    public ResponseEntity<?> updateProcesso(@PathVariable UUID id, @RequestBody Processo payload) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        if (payload.getClienteId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "clienteId é obrigatório"));
        }
        Cliente cliente = clienteRepository.findById(payload.getClienteId()).orElse(null);
        if (cliente == null || !tenantId.equals(cliente.getTenantId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId não pertence a este tenant"));
        }
        processo.setClienteId(payload.getClienteId());
        processo.setNumeroProcesso(payload.getNumeroProcesso());
        processo.setTipoProcesso(payload.getTipoProcesso());
        processo.setAreaJuridica(payload.getAreaJuridica());
        processo.setTribunal(payload.getTribunal());
        processo.setJuizo(payload.getJuizo());
        // estado is intentionally excluded: changes must go through /transicao or /formalizar
        // origem is intentionally excluded: immutable after intake, only writable via POST /processos/intake
        processo.setDescricao(payload.getDescricao());
        processo.setDataInicio(payload.getDataInicio());
        processo.setDataFim(payload.getDataFim());
        processo.setLegalHold(payload.getLegalHold() != null ? payload.getLegalHold() : false);
        processo.setDataRetencao(payload.getDataRetencao());

        return ResponseEntity.ok(processoRepository.save(processo));
    }

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

    // ==========================================
    // INTAKE / CONFLICT CHECK / FORMALIZAR
    // ==========================================

    @PreAuthorize("hasAuthority('processos:create')")
    @PostMapping("/processos/intake")
    public ResponseEntity<?> createProcessoIntake(@RequestBody Processo processo) {
        UUID tenantId = getTenantId();
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        processo.setId(null);
        processo.setTenantId(tenantId);
        if (processo.getClienteId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "clienteId é obrigatório"));
        }
        Cliente cliente = clienteRepository.findById(processo.getClienteId()).orElse(null);
        if (cliente == null || !tenantId.equals(cliente.getTenantId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId não pertence a este tenant"));
        }
        if (processo.getResponsavelId() != null) {
            User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
            if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "responsavelId não pertence a este tenant"));
            }
        }
        if (processo.getOrigem() == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "message", "Não é possível criar processo: campo 'origem' é obrigatório no intake",
                    "camposEmFalta", List.of("origem")
            ));
        }
        processo.setEstado("TRIAGEM");
        Processo saved = processoRepository.save(processo);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('processos:create')")
    @PostMapping("/processos/{id}/conflict-check")
    public ResponseEntity<?> runConflictCheck(@PathVariable UUID id) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        if (!"TRIAGEM".equalsIgnoreCase(processo.getEstado())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Conflict check só pode ser executado em processos em estado TRIAGEM"
            ));
        }

        List<ConflictMatchDto> matches = new ArrayList<>();

        // Exact NIF match against process's client
        Cliente clienteDoProcesso = processo.getClienteId() != null
                ? clienteRepository.findById(processo.getClienteId()).orElse(null)
                : null;
        String nifToSearch = clienteDoProcesso != null ? clienteDoProcesso.getNif() : null;
        String nomeToSearch = clienteDoProcesso != null ? clienteDoProcesso.getNome() : null;

        if (nifToSearch != null && !nifToSearch.isBlank()) {
            List<Cliente> nifMatches = clienteRepository.findByTenantIdAndNif(tenantId, nifToSearch);
            for (Cliente c : nifMatches) {
                // Exclude the process's own client from matches
                if (processo.getClienteId() != null && c.getId().equals(processo.getClienteId())) continue;
                matches.add(new ConflictMatchDto(
                        c.getId().toString(), "cliente", c.getNome(), c.getNif(),
                        "potencial", "NIF coincide com cliente existente"
                ));
            }
        }

        // Approximate name match against all tenant clients
        if (nomeToSearch != null && !nomeToSearch.isBlank()) {
            String nomeLower = nomeToSearch.toLowerCase();
            List<Cliente> allClientes = clienteRepository.findByTenantId(tenantId);
            for (Cliente c : allClientes) {
                if (processo.getClienteId() != null && c.getId().equals(processo.getClienteId())) continue;
                boolean alreadyMatched = matches.stream().anyMatch(m -> m.entidadeId().equals(c.getId().toString()));
                boolean nameMatches = contains(c.getNome(), nomeLower)
                        || (c.getNome() != null && contains(nomeToSearch, c.getNome().toLowerCase()));
                if (!alreadyMatched && nameMatches) {
                    matches.add(new ConflictMatchDto(
                            c.getId().toString(), "cliente", c.getNome(), c.getNif(),
                            "potencial", "Nome similar a cliente existente"
                    ));
                }
            }
        }

        // Name match against partes of other processes in the tenant
        List<Processo> allProcessos = processoRepository.findByTenantId(tenantId);
        for (Processo p : allProcessos) {
            if (p.getId().equals(id)) continue;
            List<Parte> partes = parteRepository.findByProcessoId(p.getId());
            for (Parte parte : partes) {
                if (nomeToSearch != null && !nomeToSearch.isBlank() && contains(parte.getNome(), nomeToSearch.toLowerCase())) {
                    boolean alreadyMatched = matches.stream().anyMatch(m -> m.entidadeId().equals(parte.getId().toString()) && "parte".equals(m.entidadeTipo()));
                    if (!alreadyMatched) {
                        matches.add(new ConflictMatchDto(
                                parte.getId().toString(), "parte", parte.getNome(), null,
                                "potencial", "Nome similar a parte de processo existente"
                        ));
                    }
                }
            }
        }

        String nivelSugerido = matches.isEmpty() ? "sem_conflito" : "potencial";
        return ResponseEntity.ok(new ConflictCheckResponse(matches, nivelSugerido));
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/conflict-check/decisao")
    public ResponseEntity<?> getDecisaoConflito(@PathVariable UUID id) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        ConflictCheckDecisao decisao = conflictCheckDecisaoRepository
                .findByTenantIdAndProcessoId(tenantId, id).orElse(null);
        return ResponseEntity.ok(decisao);
    }

    @Transactional
    @PreAuthorize("hasAuthority('processos:manage')")
    @PostMapping("/processos/{id}/conflict-check/decisao")
    public ResponseEntity<?> registarDecisaoConflito(@PathVariable UUID id,
            @RequestBody ConflictCheckDecisaoRequest payload) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        // Validate nivel
        String nivel = payload.nivel();
        Set<String> niveisValidos = Set.of("sem_conflito", "potencial", "sanavel", "impeditivo");
        if (nivel == null || !niveisValidos.contains(nivel)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Nível inválido. Valores aceites: sem_conflito, potencial, sanavel, impeditivo"
            ));
        }

        // Justificativa required for potencial and sanavel overrides
        if (("potencial".equals(nivel) || "sanavel".equals(nivel))
                && (payload.justificativa() == null || payload.justificativa().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Justificativa é obrigatória para o nível '" + nivel + "' (override registado)"
            ));
        }

        // Resolve decisorId from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        UUID decisorId = principal.getUserId();

        // Upsert decision
        ConflictCheckDecisao decisao = conflictCheckDecisaoRepository
                .findByTenantIdAndProcessoId(tenantId, id)
                .orElse(ConflictCheckDecisao.builder().tenantId(tenantId).processoId(id).build());

        decisao.setNivel(nivel);
        decisao.setJustificativa(payload.justificativa());
        decisao.setReferenciaEvidencia(payload.referenciaEvidencia());
        decisao.setDecisorId(decisorId);
        decisao.setDataDecisao(LocalDate.now());

        ConflictCheckDecisao saved = conflictCheckDecisaoRepository.save(decisao);

        // Audit record — T-34-03: record before response is returned
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .processoId(id)
                .acao("conflict_check_decisao")
                .entidadeTipo("conflict_check_decisao")
                .entidadeId(saved.getId().toString())
                .autorId(decisorId)
                .build());

        return ResponseEntity.ok(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('processos:manage')")
    @PostMapping("/processos/{id}/formalizar")
    public ResponseEntity<?> formalizarProcesso(@PathVariable UUID id) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        // (0) BLOQUEIO POR ESTADO — só processos em TRIAGEM podem ser formalizados
        if (!"TRIAGEM".equalsIgnoreCase(processo.getEstado())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Não é possível formalizar: o processo não está em estado de TRIAGEM"
            ));
        }

        // (a) BLOQUEIO POR CAMPOS MÍNIMOS
        String tipoProcesso = processo.getTipoProcesso() != null ? processo.getTipoProcesso().toLowerCase() : null;
        List<String> camposObrigatorios = CAMPOS_MINIMOS_POR_TIPO.getOrDefault(tipoProcesso,
                CAMPOS_MINIMOS_POR_TIPO.get("default"));
        List<String> camposEmFalta = new ArrayList<>();
        for (String campo : camposObrigatorios) {
            switch (campo) {
                case "clienteId" -> { if (processo.getClienteId() == null) camposEmFalta.add(campo); }
                case "numeroProcesso" -> { if (processo.getNumeroProcesso() == null || processo.getNumeroProcesso().isBlank()) camposEmFalta.add(campo); }
                case "tipoProcesso" -> { if (processo.getTipoProcesso() == null || processo.getTipoProcesso().isBlank()) camposEmFalta.add(campo); }
                case "areaJuridica" -> { if (processo.getAreaJuridica() == null || processo.getAreaJuridica().isBlank()) camposEmFalta.add(campo); }
                case "tribunal" -> { if (processo.getTribunal() == null || processo.getTribunal().isBlank()) camposEmFalta.add(campo); }
                case "dataInicio" -> { if (processo.getDataInicio() == null) camposEmFalta.add(campo); }
                case "descricao" -> { if (processo.getDescricao() == null || processo.getDescricao().isBlank()) camposEmFalta.add(campo); }
                case "origem" -> { if (processo.getOrigem() == null) camposEmFalta.add(campo); }
                default -> log.warn("FORMALIZAR: campo obrigatório desconhecido '{}' em CAMPOS_MINIMOS_POR_TIPO, ignorado na validação", campo);
            }
        }
        if (!camposEmFalta.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "message", "Não é possível formalizar: faltam campos mínimos obrigatórios para este tipo de processo",
                    "camposEmFalta", camposEmFalta
            ));
        }

        // (b) BLOQUEIO POR DECISÃO DE CONFLITO
        ConflictCheckDecisao decisao = conflictCheckDecisaoRepository
                .findByTenantIdAndProcessoId(tenantId, id).orElse(null);
        if (decisao == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Não é possível formalizar: o conflict check ainda não tem uma decisão registada"
            ));
        }
        if ("impeditivo".equals(decisao.getNivel())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Não é possível formalizar: existe um conflito impeditivo registado"
            ));
        }

        // Transition TRIAGEM -> ATIVO
        processo.setEstado("ATIVO");

        // Criação idempotente do Honorario (PROC-14): existence-check independente do guard de estado acima,
        // para proteger contra retries/replays da transação
        if (honorarioRepository.findByProcessoId(id).isEmpty()) {
            try {
                Honorario honorario = Honorario.builder()
                        .processoId(id)
                        .valorTotal(null)
                        .dataAcordo(LocalDate.now())
                        .build();
                honorarioRepository.save(honorario);
            } catch (DataIntegrityViolationException ex) {
                // outro formalizar concorrente já criou o honorário — seguro ignorar, o registo existe
            }
        }

        return ResponseEntity.ok(processoRepository.save(processo));
    }

    // ==========================================
    // WORKFLOW — estado atual, responsavel, transições disponíveis
    // ==========================================

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/workflow")
    public ResponseEntity<?> getWorkflow(@PathVariable UUID id) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Processo não encontrado"));
        }
        String estado = processo.getEstado() != null ? processo.getEstado().toUpperCase() : "";
        List<TransicaoInfo> transicoes = TRANSICOES_PERMITIDAS.getOrDefault(estado, List.of());

        // Resolve responsavel (must belong to same tenant)
        User responsavel = null;
        if (processo.getResponsavelId() != null) {
            User candidate = userRepository.findById(processo.getResponsavelId()).orElse(null);
            if (candidate != null && tenantId.equals(candidate.getTenantId())) {
                responsavel = candidate;
            }
        }

        return ResponseEntity.ok(new WorkflowResponse(
                estado,
                processo.getResponsavelId(),
                responsavel != null ? responsavel.getNome() : null,
                derivarProximoPasso(estado),
                transicoes
        ));
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('processos:edit', 'processos:manage')")
    @PostMapping("/processos/{id}/transicao/{acao}")
    public ResponseEntity<?> executarTransicao(
            @PathVariable UUID id,
            @PathVariable String acao,
            @RequestBody(required = false) TransicaoRequest payload) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Processo não encontrado"));
        }

        // (0) Estado gate — find allowed transition for this acao in the current estado
        String estadoAtual = processo.getEstado() != null ? processo.getEstado().toUpperCase() : "";
        List<TransicaoInfo> transicoes = TRANSICOES_PERMITIDAS.getOrDefault(estadoAtual, List.of());
        TransicaoInfo transicao = transicoes.stream()
                .filter(t -> t.acao().equals(acao))
                .findFirst().orElse(null);
        if (transicao == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Transição '" + acao + "' não é permitida no estado " + estadoAtual
            ));
        }

        // (1) Permission gate — manage-level transitions require processos:manage
        if ("processos:manage".equals(transicao.permissaoNecessaria())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean hasManage = auth.getAuthorities().stream()
                    .anyMatch(a -> "processos:manage".equals(a.getAuthority()));
            if (!hasManage) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "message", "Requer permissão processos:manage"
                ));
            }
        }

        // (2) Justificativa gate — must be present and at least 10 characters (matches frontend schema)
        if (transicao.requerJustificativa()) {
            String justificativa = payload != null ? payload.justificativa() : null;
            if (justificativa == null || justificativa.isBlank() || justificativa.trim().length() < 10) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Justificativa é obrigatória e deve ter pelo menos 10 caracteres"
                ));
            }
        }

        // Resolve target estado — explicit cases only; no silent fallback
        String novoEstado = switch (acao) {
            case "ativar"    -> "ATIVO";
            case "suspender" -> "SUSPENSO";
            case "encerrar"  -> "ENCERRADO";
            case "reabrir"   -> "ATIVO";
            default -> throw new IllegalStateException(
                    "Acao '" + acao + "' exists in TRANSICOES_PERMITIDAS but has no target estado mapping");
        };

        // Record Movimentacao — author from SecurityContext, never from payload
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        String descricaoMov = estadoAtual + " -> " + novoEstado;
        if (payload != null && payload.justificativa() != null && !payload.justificativa().isBlank()) {
            descricaoMov = descricaoMov + ": " + payload.justificativa();
        }

        Movimentacao mov = new Movimentacao();
        mov.setProcessoId(id);
        mov.setTipo("TRANSICAO_ESTADO");
        mov.setDescricao(descricaoMov);
        mov.setData(LocalDateTime.now());
        mov.setAutorId(principal.getUserId()); // Phase 34: populate author for timeline attribution
        movimentacaoRepository.save(mov);

        // Audit record — T-34-04: autor_id from SecurityContext, never from request payload
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .processoId(id)
                .acao("transicao_estado")
                .entidadeTipo("processo")
                .entidadeId(id.toString())
                .autorId(principal.getUserId())
                .build());

        // Transition
        processo.setEstado(novoEstado);
        return ResponseEntity.ok(processoRepository.save(processo));
    }

    // ==========================================
    // PRAZOS — risco derivado no backend
    // ==========================================

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/prazos")
    public ResponseEntity<?> listPrazos(@PathVariable UUID id) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Processo não encontrado"));
        }
        List<Prazo> prazos = prazoRepository.findByTenantIdAndProcessoIdOrderByDataLimiteAsc(tenantId, id);
        List<Map<String, Object>> result = prazos.stream().map(p -> {
            String risco = riscoPrazoService.computeRisco(p.getDataLimite(), p.getPrioridade());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("descricao", p.getDescricao());
            m.put("dataLimite", p.getDataLimite());
            m.put("prioridade", p.getPrioridade());
            m.put("responsavelId", p.getResponsavelId());
            m.put("concluido", p.getConcluido());
            m.put("escalonado", p.getEscalonado());
            m.put("risco", risco);
            m.put("createdAt", p.getCreatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/prazos")
    public ResponseEntity<?> listAllPrazos() {
        UUID tenantId = getTenantId();
        List<Prazo> prazos = prazoRepository.findByTenantId(tenantId);
        List<Map<String, Object>> result = prazos.stream().map(p -> {
            String risco = riscoPrazoService.computeRisco(p.getDataLimite(), p.getPrioridade());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("processoId", p.getProcessoId());
            m.put("descricao", p.getDescricao());
            m.put("dataLimite", p.getDataLimite());
            m.put("prioridade", p.getPrioridade());
            m.put("responsavelId", p.getResponsavelId());
            m.put("concluido", p.getConcluido());
            m.put("escalonado", p.getEscalonado());
            m.put("risco", risco);
            m.put("createdAt", p.getCreatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @Transactional
    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping("/processos/{id}/prazos")
    public ResponseEntity<?> createPrazo(
            @PathVariable UUID id,
            @Valid @RequestBody PrazoRequest payload) {
        UUID tenantId = getTenantId();
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Processo não encontrado"));
        }
        // Validate prioridade against allowed set (WR-03)
        Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
        if (payload.prioridade() != null && !prioridadesValidas.contains(payload.prioridade().toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
        }
        // Validate responsavelId belongs to same tenant
        // WR-02 (Phase 87 code review, iteration 3): also reject a deactivated responsavelId --
        // see the identical guard in atribuirResponsavel above.
        if (payload.responsavelId() != null) {
            User responsavel = userRepository.findById(payload.responsavelId()).orElse(null);
            if (responsavel == null || !tenantId.equals(responsavel.getTenantId())
                    || Boolean.FALSE.equals(responsavel.getAtivo())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "responsavelId não pertence a este tenant ou está inativo"));
            }
        }
        String prioridade = payload.prioridade() != null ? payload.prioridade().toUpperCase() : "MEDIA";
        String risco = riscoPrazoService.computeRisco(payload.dataLimite(), prioridade);
        boolean escalonado = RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco);
        Prazo prazo = Prazo.builder()
                .tenantId(tenantId)
                .processoId(id)
                .descricao(payload.descricao())
                .dataLimite(payload.dataLimite())
                .prioridade(prioridade)
                .responsavelId(payload.responsavelId())
                .escalonado(escalonado)
                .build();
        Prazo saved = prazoRepository.save(prazo);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("descricao", saved.getDescricao());
        response.put("dataLimite", saved.getDataLimite());
        response.put("prioridade", saved.getPrioridade());
        response.put("responsavelId", saved.getResponsavelId());
        response.put("concluido", saved.getConcluido());
        response.put("escalonado", saved.getEscalonado());
        response.put("risco", riscoPrazoService.computeRisco(saved.getDataLimite(), saved.getPrioridade()));
        response.put("createdAt", saved.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
        // Recompute escalonado: concluded prazos are never escalated
        boolean nowEscalonado = !nowConcluido &&
                (RiscoPrazoService.PROXIMO.equals(riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade()))
                        || RiscoPrazoService.VENCIDO.equals(riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade())));
        prazo.setEscalonado(nowEscalonado);
        Prazo saved = prazoRepository.save(prazo);
        // Return response map including recomputed risco (verifier requirement W1)
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("descricao", saved.getDescricao());
        response.put("dataLimite", saved.getDataLimite());
        response.put("prioridade", saved.getPrioridade());
        response.put("responsavelId", saved.getResponsavelId());
        response.put("concluido", saved.getConcluido());
        response.put("escalonado", saved.getEscalonado());
        response.put("risco", riscoPrazoService.computeRisco(saved.getDataLimite(), saved.getPrioridade()));
        response.put("createdAt", saved.getCreatedAt());
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // PROCESSOS SUB-RESOURCES
    // ==========================================
    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/partes")
    public ResponseEntity<?> listPartes(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(parteRepository.findByProcessoId(id));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping("/processos/{id}/partes")
    public ResponseEntity<?> createParte(@PathVariable UUID id, @RequestBody Parte parte) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        if (parte.getNome() == null || parte.getNome().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "nome é obrigatório"));
        }
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        parte.setId(null);
        parte.setProcessoId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(parteRepository.save(parte));
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/fases")
    public ResponseEntity<?> listFases(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        List<ProcessoFase> fases = processoFaseRepository.findByProcessoId(id);
        List<Map<String, Object>> response = new ArrayList<>();

        for (ProcessoFase pf : fases) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", pf.getId());
            map.put("processo_id", pf.getProcessoId());
            map.put("fase_id", pf.getFaseId());
            map.put("data_inicio", pf.getDataInicio());
            map.put("data_fim", pf.getDataFim());
            map.put("status", pf.getAtiva() ? "EM_ANDAMENTO" : "CONCLUIDA");

            faseProcessualRepository.findById(pf.getFaseId()).ifPresent(catalog -> {
                map.put("nome", catalog.getNome());
            });

            response.add(map);
        }

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping("/processos/{id}/fases")
    public ResponseEntity<?> createProcessoFase(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        String faseNome = (String) body.get("nome");
        if (faseNome == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nome da fase é obrigatório"));
        }

        FaseProcessual catalog = faseProcessualRepository.findByNome(faseNome)
                .orElseGet(() -> faseProcessualRepository.save(FaseProcessual.builder().nome(faseNome).build()));

        ProcessoFase pf = ProcessoFase.builder()
                .processoId(id)
                .faseId(catalog.getId())
                .dataInicio(LocalDate.now())
                .ativa(true)
                .build();

        ProcessoFase saved = processoFaseRepository.save(pf);
        try {
            notificacaoService.notificarFaseEntrada(processo.getTenantId(), id, processo.getResponsavelId(),
                    processo.getNumeroProcesso(), faseNome, "/processos/" + id + "?tab=fases");
        } catch (IllegalArgumentException ex) {
            log.warn("FASE_ENTRADA: falha ao notificar (responsavelId possivelmente órfão) processo={}", id, ex);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

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
            String status = (String) body.get("status");
            if ("CONCLUIDA".equals(status)) {
                pf.setAtiva(false);
                pf.setDataFim(LocalDate.now());
            } else {
                pf.setAtiva(true);
            }
        }

        return ResponseEntity.ok(processoFaseRepository.save(pf));
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/movimentacoes")
    public ResponseEntity<?> listMovimentacoes(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(movimentacaoRepository.findByProcessoId(id));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping("/processos/{id}/movimentacoes")
    public ResponseEntity<?> createMovimentacao(@PathVariable UUID id, @RequestBody Movimentacao mov) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        mov.setId(null);
        mov.setProcessoId(id);
        if (mov.getData() == null) mov.setData(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoRepository.save(mov));
    }

    // ==========================================
    // DECISÕES / FACTOS / TESTEMUNHAS
    // ==========================================

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/decisoes")
    public ResponseEntity<?> listDecisoes(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(decisaoRepository.findByProcessoId(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping(value = "/processos/{id}/decisoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDecisao(
            @PathVariable UUID id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("data") String data,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "resumo", required = false) String resumo) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        LocalDate parsedData;
        try {
            parsedData = LocalDate.parse(data);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Parâmetro 'data' inválido. Esperado YYYY-MM-DD"));
        }

        TipoDecisao parsedTipo;
        try {
            parsedTipo = TipoDecisao.valueOf(tipo);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Parâmetro 'tipo' inválido"));
        }

        Decisao decisao = Decisao.builder()
                .processoId(id)
                .data(parsedData)
                .tipo(parsedTipo)
                .resumo(resumo)
                .build();

        if (file != null && !file.isEmpty()) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Nome do ficheiro em falta"));
            }
            try {
                UUID documentoId = UUID.randomUUID();
                String objectKey;
                try (InputStream inputStream = file.getInputStream()) {
                    objectKey = storageService.upload(getTenantId(), documentoId,
                            originalName, inputStream, file.getContentType(), file.getSize());
                }

                Documento documento = Documento.builder()
                        .id(documentoId)
                        .tenantId(getTenantId())
                        .processoId(id)
                        .clienteId(null)
                        .nome(originalName)
                        .tipo("ANEXO")
                        .confidencialidade("PUBLICO")
                        .caminhoArquivo(objectKey)
                        .tamanho(file.getSize())
                        .mimeType(file.getContentType())
                        .versao(1)
                        .build();

                Documento savedDoc = documentoRepository.save(documento);
                decisao.setDocumentoId(savedDoc.getId());
            } catch (StorageUnavailableException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Storage service unavailable"));
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Erro ao ler ficheiro"));
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(decisaoRepository.save(decisao));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PutMapping("/processos/{id}/decisoes/{decisaoId}")
    public ResponseEntity<?> updateDecisao(
            @PathVariable UUID id,
            @PathVariable Integer decisaoId,
            @RequestBody Map<String, Object> payload) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        Decisao decisao = decisaoRepository.findById(decisaoId).orElse(null);
        if (decisao == null || !decisao.getProcessoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Decisão não encontrada"));
        }

        Object dataVal = payload.get("data");
        if (dataVal == null || dataVal.toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "data é obrigatória"));
        }
        LocalDate parsedData;
        try {
            parsedData = LocalDate.parse(dataVal.toString());
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Parâmetro 'data' inválido. Esperado YYYY-MM-DD"));
        }

        Object tipoVal = payload.get("tipo");
        if (tipoVal == null || tipoVal.toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "tipo é obrigatório"));
        }
        TipoDecisao parsedTipo;
        try {
            parsedTipo = TipoDecisao.valueOf(tipoVal.toString());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Parâmetro 'tipo' inválido"));
        }

        Object resumoVal = payload.get("resumo");

        decisao.setData(parsedData);
        decisao.setTipo(parsedTipo);
        decisao.setResumo(resumoVal == null ? null : resumoVal.toString());

        return ResponseEntity.ok(decisaoRepository.save(decisao));
    }

    @Transactional
    @PreAuthorize("hasAuthority('processos:edit')")
    @DeleteMapping("/processos/{id}/decisoes/{decisaoId}")
    public ResponseEntity<?> deleteDecisao(@PathVariable UUID id, @PathVariable Integer decisaoId) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        Decisao decisao = decisaoRepository.findById(decisaoId).orElse(null);
        if (decisao == null || !decisao.getProcessoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Decisão não encontrada"));
        }

        if (decisao.getDocumentoId() != null) {
            documentoRepository.findById(decisao.getDocumentoId())
                    .filter(d -> d.getTenantId().equals(getTenantId()))
                    .ifPresent(d -> {
                        try {
                            storageService.delete(d.getCaminhoArquivo());
                        } catch (StorageUnavailableException ignored) {
                            // Storage outage should not block decisão deletion; the object
                            // becomes unreferenced but the DB row is still cleaned up below.
                        }
                        documentoRepository.delete(d);
                    });
        }

        decisaoRepository.delete(decisao);
        return ResponseEntity.ok(Map.of("message", "Decisão removida com sucesso!"));
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/testemunhas")
    public ResponseEntity<?> listTestemunhas(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(testemunhaRepository.findByProcessoId(id));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping("/processos/{id}/testemunhas")
    public ResponseEntity<?> createTestemunha(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        Object nomeVal = payload.get("nome");
        if (nomeVal == null || nomeVal.toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "nome é obrigatório"));
        }

        TipoTestemunha parsedTipo = null;
        Object tipoVal = payload.get("tipo");
        if (tipoVal != null && !tipoVal.toString().isBlank()) {
            try {
                parsedTipo = TipoTestemunha.valueOf(tipoVal.toString());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Parâmetro 'tipo' inválido"));
            }
        }

        Object contactoVal = payload.get("contacto");
        Object notasVal = payload.get("notas");

        Testemunha testemunha = Testemunha.builder()
                .processoId(id)
                .nome(nomeVal.toString())
                .contacto(contactoVal == null ? null : contactoVal.toString())
                .tipo(parsedTipo)
                .notas(notasVal == null ? null : notasVal.toString())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(testemunhaRepository.save(testemunha));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PutMapping("/processos/{id}/testemunhas/{testemunhaId}")
    public ResponseEntity<?> updateTestemunha(
            @PathVariable UUID id,
            @PathVariable Integer testemunhaId,
            @RequestBody Map<String, Object> payload) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        Testemunha testemunha = testemunhaRepository.findById(testemunhaId).orElse(null);
        if (testemunha == null || !testemunha.getProcessoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Testemunha não encontrada"));
        }

        Object nomeVal = payload.get("nome");
        if (nomeVal == null || nomeVal.toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "nome é obrigatório"));
        }

        TipoTestemunha parsedTipo = null;
        Object tipoVal = payload.get("tipo");
        if (tipoVal != null && !tipoVal.toString().isBlank()) {
            try {
                parsedTipo = TipoTestemunha.valueOf(tipoVal.toString());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Parâmetro 'tipo' inválido"));
            }
        }

        Object contactoVal = payload.get("contacto");
        Object notasVal = payload.get("notas");

        testemunha.setNome(nomeVal.toString());
        testemunha.setContacto(contactoVal == null ? null : contactoVal.toString());
        testemunha.setTipo(parsedTipo);
        testemunha.setNotas(notasVal == null ? null : notasVal.toString());

        return ResponseEntity.ok(testemunhaRepository.save(testemunha));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @DeleteMapping("/processos/{id}/testemunhas/{testemunhaId}")
    public ResponseEntity<?> deleteTestemunha(@PathVariable UUID id, @PathVariable Integer testemunhaId) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        Testemunha testemunha = testemunhaRepository.findById(testemunhaId).orElse(null);
        if (testemunha == null || !testemunha.getProcessoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Testemunha não encontrada"));
        }

        testemunhaRepository.delete(testemunha);
        return ResponseEntity.ok(Map.of("message", "Testemunha removida com sucesso!"));
    }

    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}/factos")
    public ResponseEntity<?> listFactos(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(factoRepository.findByProcessoIdOrderByOrdemAsc(id));
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PostMapping("/processos/{id}/factos")
    public ResponseEntity<?> createFacto(@PathVariable UUID id, @RequestBody Facto facto) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        if (facto.getDescricao() == null || facto.getDescricao().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "descricao é obrigatória"));
        }
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        facto.setId(null);
        facto.setProcessoId(id);
        try {
            synchronized (FactoRepository.class) {
                int nextOrdem = factoRepository.findMaxOrdemByProcessoId(id).orElse(0) + 1;
                facto.setOrdem(nextOrdem);
                facto = factoRepository.save(facto);
            }
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Conflito ao atribuir ordem ao facto, tente novamente"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(facto);
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @PutMapping("/processos/{id}/factos/{factoId}")
    public ResponseEntity<?> updateFacto(
            @PathVariable UUID id,
            @PathVariable Integer factoId,
            @RequestBody Facto payload) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        Facto facto = factoRepository.findById(factoId).orElse(null);
        if (facto == null || !facto.getProcessoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Facto não encontrado"));
        }

        if (payload.getDescricao() == null || payload.getDescricao().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "descricao é obrigatória"));
        }
        if (payload.getOrdem() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "ordem é obrigatória"));
        }

        facto.setDescricao(payload.getDescricao());
        facto.setData(payload.getData());
        facto.setOrdem(payload.getOrdem());

        try {
            return ResponseEntity.ok(factoRepository.save(facto));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Conflito ao atribuir ordem ao facto, tente novamente"));
        }
    }

    @PreAuthorize("hasAuthority('processos:edit')")
    @DeleteMapping("/processos/{id}/factos/{factoId}")
    public ResponseEntity<?> deleteFacto(@PathVariable UUID id, @PathVariable Integer factoId) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        Facto facto = factoRepository.findById(factoId).orElse(null);
        if (facto == null || !facto.getProcessoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Facto não encontrado"));
        }

        factoRepository.delete(facto);
        return ResponseEntity.ok(Map.of("message", "Facto removido com sucesso!"));
    }

    // ==========================================
    // TIMELINE — unified chronological feed for a processo
    // ==========================================

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

        // 1. Movimentacoes (includes TRANSICAO_ESTADO type from Phase 33)
        List<Movimentacao> movs = movimentacaoRepository.findByProcessoId(id);
        for (Movimentacao m : movs) {
            String autorNome = resolveAutorNome(m.getAutorId(), tenantId);
            String tipo = "TRANSICAO_ESTADO".equals(m.getTipo()) ? "transicao" : "movimentacao";
            String titulo = m.getTipo() != null ? m.getTipo() : "Movimentação";
            items.add(new TimelineItemDto(tipo, String.valueOf(m.getId()), m.getData(), titulo,
                    m.getDescricao(), autorNome));
        }

        // 2. Eventos linked to this processo
        List<Evento> eventos = eventoRepository.findByTenantIdAndProcessoId(tenantId, id);
        for (Evento e : eventos) {
            items.add(new TimelineItemDto("evento", String.valueOf(e.getId()), e.getDataInicio(),
                    e.getTitulo(), e.getDescricao(), null));
        }

        // 3. Documentos linked to this processo
        List<Documento> docs = documentoRepository.findByTenantIdAndProcessoId(tenantId, id);
        for (Documento d : docs) {
            items.add(new TimelineItemDto("documento", d.getId().toString(), d.getCreatedAt(),
                    d.getNome(), d.getTipo(), null));
        }

        // 4. ConflictCheckDecisao (optional — only if a decision was registered)
        conflictCheckDecisaoRepository.findByTenantIdAndProcessoId(tenantId, id)
                .ifPresent(dec -> {
                    // Use createdAt (LocalDateTime), NOT dataDecisao (LocalDate) — Pitfall #3
                    String autorNome = resolveAutorNome(dec.getDecisorId(), tenantId);
                    items.add(new TimelineItemDto("decisao", dec.getId().toString(), dec.getCreatedAt(),
                            "Decisão de Conflict Check", "Nível: " + dec.getNivel(), autorNome));
                });

        // Sort chronologically descending — nullsLast so null timestamps don't throw NPE
        items.sort(Comparator.comparing(TimelineItemDto::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return ResponseEntity.ok(items);
    }

    // ==========================================
    // AUDIT LOG — compliance trail restricted to processos:manage
    // ==========================================

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

    // ==========================================
    // AGENDA / EVENTOS
    // ==========================================
    @PreAuthorize("hasAuthority('agenda:view')")
    @GetMapping("/eventos")
    public ResponseEntity<?> listEventos(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) UUID processoId,
            @RequestParam(required = false) Boolean concluido) {
        UUID tenantId = getTenantId();
        List<Evento> eventos = eventoRepository.findByTenantId(tenantId);

        // In-memory filter for complex parameters to remain simple
        if (processoId != null) {
            eventos.removeIf(e -> e.getProcessoId() == null || !e.getProcessoId().equals(processoId));
        }

        if (concluido != null) {
            eventos.removeIf(e -> !e.getConcluido().equals(concluido));
        }

        LocalDateTime start = null;
        LocalDateTime end = null;

        if (dataInicio != null) {
            try {
                start = LocalDateTime.parse(dataInicio, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Parâmetro 'dataInicio' com formato inválido. Deve ser ISO-8601 (ex: YYYY-MM-DDTHH:mm:ss)"));
            }
        }

        if (dataFim != null) {
            try {
                end = LocalDateTime.parse(dataFim, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Parâmetro 'dataFim' com formato inválido. Deve ser ISO-8601 (ex: YYYY-MM-DDTHH:mm:ss)"));
            }
        }

        if (start != null && end != null && end.isBefore(start)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "O parâmetro 'dataFim' não pode ser anterior a 'dataInicio'"));
        }

        if (start != null) {
            LocalDateTime finalStart = start;
            eventos.removeIf(e -> e.getDataInicio() != null && e.getDataInicio().isBefore(finalStart));
        }

        if (end != null) {
            LocalDateTime finalEnd = end;
            eventos.removeIf(e -> e.getDataInicio() != null && e.getDataInicio().isAfter(finalEnd));
        }

        // Expand recurring instances
        LocalDateTime effectiveStart = start != null ? start : LocalDateTime.now().minusYears(1);
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now().plusYears(1);

        List<Evento> expanded = new ArrayList<>();
        for (Evento master : eventos) {
            if (master.getRecurrenceRule() == null) {
                expanded.add(master);
                continue;
            }
            // Build exception set
            Set<String> exceptions = new LinkedHashSet<>();
            if (master.getRecurrenceExceptions() != null) {
                for (String token : master.getRecurrenceExceptions().split(",")) {
                    String t = token.trim();
                    if (!t.isEmpty()) exceptions.add(t);
                }
            }
            // Determine duration between dataInicio and dataFim
            Duration eventDuration = (master.getDataInicio() != null && master.getDataFim() != null)
                    ? Duration.between(master.getDataInicio(), master.getDataFim()) : null;

            LocalDateTime cursor = master.getDataInicio();
            if (cursor == null) continue;

            java.time.LocalDate endDate = master.getRecurrenceEndDate();
            if (endDate == null) endDate = effectiveEnd.toLocalDate();

            while (!cursor.toLocalDate().isAfter(endDate)) {
                // Check window
                if (!cursor.isBefore(effectiveStart) && !cursor.isAfter(effectiveEnd)) {
                    String dateStr = cursor.toLocalDate().toString();
                    if (!exceptions.contains(dateStr)) {
                        LocalDateTime instanceFim = eventDuration != null ? cursor.plus(eventDuration) : null;
                        Evento instance = Evento.builder()
                                .id(master.getId())
                                .tenantId(master.getTenantId())
                                .processoId(master.getProcessoId())
                                .tipo(master.getTipo())
                                .titulo(master.getTitulo())
                                .descricao(master.getDescricao())
                                .prioridade(master.getPrioridade())
                                .concluido(master.getConcluido())
                                .dataInicio(cursor)
                                .dataFim(instanceFim)
                                .recurrenceRule(master.getRecurrenceRule())
                                .recurrenceEndDate(master.getRecurrenceEndDate())
                                .recurrenceExceptions(master.getRecurrenceExceptions())
                                .build();
                        instance.setIsRecurrenceInstance(true);
                        instance.setRecurrenceInstanceDate(dateStr);
                        expanded.add(instance);
                    }
                }
                // Advance cursor
                switch (master.getRecurrenceRule()) {
                    case "DAILY" -> cursor = cursor.plusDays(1);
                    case "WEEKLY" -> cursor = cursor.plusWeeks(1);
                    case "MONTHLY" -> cursor = cursor.plusMonths(1);
                    default -> { break; }
                }
                // Safety: if rule unknown, avoid infinite loop
                if (!master.getRecurrenceRule().equals("DAILY") &&
                    !master.getRecurrenceRule().equals("WEEKLY") &&
                    !master.getRecurrenceRule().equals("MONTHLY")) break;
            }
        }

        for (Evento e : expanded) {
            e.setRisco(riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade()));
        }

        return ResponseEntity.ok(expanded);
    }

    @PreAuthorize("hasAuthority('agenda:view')")
    @GetMapping("/eventos/{id}")
    public ResponseEntity<?> getEvento(@PathVariable Integer id) {
        Evento evento = eventoRepository.findById(id).orElse(null);
        if (evento == null || !evento.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Evento não encontrado"));
        }
        return ResponseEntity.ok(evento);
    }

    @PreAuthorize("hasAuthority('agenda:edit')")
    @PostMapping("/eventos")
    public ResponseEntity<?> createEvento(@RequestBody Evento evento) {
        if (evento.getDataInicio() != null && evento.getDataFim() != null && evento.getDataFim().isBefore(evento.getDataInicio())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "A data de fim não pode ser anterior à data de início"));
        }
        // CR-02 (92-REVIEW.md): processoId is client-supplied and must be verified as belonging
        // to the caller's own tenant — mirrors the same check already established in
        // uploadDocumento and listHonorarios for this exact relationship.
        if (evento.getProcessoId() != null) {
            Processo processo = processoRepository.findById(evento.getProcessoId()).orElse(null);
            if (processo == null || !processo.getTenantId().equals(getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "processoId não pertence a este tenant"));
            }
        }
        // Validate prioridade against allowed set (WR-02, 85-REVIEW.md) — mirrors createPrazo's
        // allow-list so Evento.prioridade can't silently fall back to the strict 3-day threshold
        // in RiscoPrazoService via a typo or free-text value. Normalizes on write (WR-01,
        // 85-REVIEW.md iteration 3) to match createPrazo's persisted-value casing — the frontend
        // agenda page's "urgentes" counter compares prioridade === "ALTA" case-sensitively, so an
        // unnormalized "alta" would silently be excluded from that count despite passing this check.
        Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
        if (evento.getPrioridade() != null) {
            String prioridadeUpper = evento.getPrioridade().toUpperCase();
            if (!prioridadesValidas.contains(prioridadeUpper)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
            }
            evento.setPrioridade(prioridadeUpper);
        }
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        evento.setId(null);
        evento.setTenantId(getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoRepository.save(evento));
    }

    @PreAuthorize("hasAuthority('agenda:edit')")
    @PutMapping("/eventos/{id}")
    public ResponseEntity<?> updateEvento(@PathVariable Integer id, @RequestBody Evento payload) {
        Evento evento = eventoRepository.findById(id).orElse(null);
        if (evento == null || !evento.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Evento não encontrado"));
        }

        LocalDateTime start = payload.getDataInicio() != null ? payload.getDataInicio() : evento.getDataInicio();
        LocalDateTime end = payload.getDataFim() != null ? payload.getDataFim() : evento.getDataFim();
        if (start != null && end != null && end.isBefore(start)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "A data de fim não pode ser anterior à data de início"));
        }

        // Validate prioridade against allowed set (WR-02, 85-REVIEW.md) — mirrors createPrazo's
        // allow-list; only checked when the payload actually supplies a new value, consistent
        // with the partial-update semantics below. Normalizes on write (WR-01, 85-REVIEW.md
        // iteration 3) to match createPrazo's persisted-value casing — the frontend agenda page's
        // "urgentes" counter compares prioridade === "ALTA" case-sensitively, so an unnormalized
        // "alta" would silently be excluded from that count despite passing this validation.
        if (payload.getPrioridade() != null) {
            Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
            String prioridadeUpper = payload.getPrioridade().toUpperCase();
            if (!prioridadesValidas.contains(prioridadeUpper)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
            }
            evento.setPrioridade(prioridadeUpper);
        }

        // Partial update: only overwrite fields explicitly provided in the payload.
        // Drag & drop sends only dataInicio/dataFim and toggle-concluido sends only
        // concluido, so a full replace would null out titulo/prioridade/recorrência.
        if (payload.getTitulo() != null) evento.setTitulo(payload.getTitulo());
        if (payload.getDescricao() != null) evento.setDescricao(payload.getDescricao());
        if (payload.getTipo() != null) evento.setTipo(payload.getTipo());
        // CR-01 (92-REVIEW.md): processoId was previously absent from this whitelist, so
        // "change processo" edits from the frontend were silently dropped. Verifies tenant
        // ownership the same way createEvento (CR-02) does. Note: under the current
        // `if (x != null)` partial-update convention this still cannot express "clear/unlink
        // to null" — that would need an explicit sentinel or a dedicated PATCH DTO (see WR-05).
        if (payload.getProcessoId() != null) {
            Processo linkedProcesso = processoRepository.findById(payload.getProcessoId()).orElse(null);
            if (linkedProcesso == null || !linkedProcesso.getTenantId().equals(getTenantId())) {
                return ResponseEntity.badRequest().body(Map.of("message", "processoId não pertence a este tenant"));
            }
            evento.setProcessoId(payload.getProcessoId());
        }
        if (payload.getDataInicio() != null) evento.setDataInicio(payload.getDataInicio());
        if (payload.getDataFim() != null) evento.setDataFim(payload.getDataFim());
        // prioridade is validated, normalized to uppercase, and set above (WR-01, 85-REVIEW.md
        // iteration 3) — not repeated here to avoid persisting the raw, unnormalized value.
        if (payload.getConcluido() != null) evento.setConcluido(payload.getConcluido());
        if (payload.getRecurrenceRule() != null) evento.setRecurrenceRule(payload.getRecurrenceRule());
        if (payload.getRecurrenceEndDate() != null) evento.setRecurrenceEndDate(payload.getRecurrenceEndDate());
        if (payload.getRecurrenceExceptions() != null) evento.setRecurrenceExceptions(payload.getRecurrenceExceptions());

        return ResponseEntity.ok(eventoRepository.save(evento));
    }

    @PreAuthorize("hasAuthority('agenda:edit')")
    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<?> deleteEvento(@PathVariable Integer id) {
        Evento evento = eventoRepository.findById(id).orElse(null);
        if (evento == null || !evento.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Evento não encontrado"));
        }

        eventoRepository.delete(evento);
        return ResponseEntity.ok(Map.of("message", "Evento removido com sucesso!"));
    }

    @PreAuthorize("hasAuthority('agenda:edit')")
    @DeleteMapping("/eventos/{id}/instances")
    public ResponseEntity<?> deleteEventoInstance(
            @PathVariable Integer id,
            @RequestParam String date) {
        Evento master = eventoRepository.findById(id).orElse(null);
        if (master == null || !master.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Evento não encontrado"));
        }
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Parâmetro 'date' inválido. Esperado YYYY-MM-DD"));
        }
        LinkedHashSet<String> exceptions = new LinkedHashSet<>();
        if (master.getRecurrenceExceptions() != null) {
            for (String token : master.getRecurrenceExceptions().split(",")) {
                String t = token.trim();
                if (!t.isEmpty()) exceptions.add(t);
            }
        }
        exceptions.add(parsedDate.toString());
        master.setRecurrenceExceptions(String.join(",", exceptions));
        eventoRepository.save(master);
        return ResponseEntity.ok(Map.of("message", "Instância removida com sucesso!"));
    }

    // ==========================================
    // DOCUMENTOS
    // ==========================================
    @PreAuthorize("hasAuthority('documentos:edit')")
    @PostMapping(value = "/documentos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocumento(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "processoId", required = false) UUID processoId,
            @RequestParam(value = "clienteId", required = false) UUID clienteId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "confidencialidade", required = false) String confidencialidade,
            @RequestParam(value = "replace_id", required = false) UUID replaceId) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ficheiro em falta"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nome do ficheiro em falta"));
        }

        if (clienteId != null) {
            Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
            if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "clienteId não pertence a este tenant"));
            }
        }
        if (processoId != null) {
            Processo processo = processoRepository.findById(processoId).orElse(null);
            if (processo == null || !processo.getTenantId().equals(getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "processoId não pertence a este tenant"));
            }
        }

        try {
            String fileId = UUID.randomUUID().toString();

            Documento documento;
            String oldKeyToDeleteAfterSave = null;
            if (replaceId != null) {
                documento = documentoRepository.findById(replaceId).orElse(null);
                if (documento == null || !documento.getTenantId().equals(getTenantId())) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento a substituir não encontrado"));
                }
                // Upload the new object BEFORE deleting the old one.
                // If the upload fails, the old object remains intact (WR-01).
                String oldKey = documento.getCaminhoArquivo();
                String objectKey;
                try (InputStream inputStream = file.getInputStream()) {
                    objectKey = storageService.upload(getTenantId(), documento.getId(),
                            originalName, inputStream, file.getContentType(), file.getSize());
                }
                // Deletion is deferred until after the DB save commits (WR-02): if the save
                // below fails, the DB still points at oldKey, which must still exist.
                oldKeyToDeleteAfterSave = oldKey;

                documento.setNome(originalName);
                if (tipo != null) documento.setTipo(tipo);
                if (confidencialidade != null) documento.setConfidencialidade(confidencialidade);
                documento.setCaminhoArquivo(objectKey);
                documento.setTamanho(file.getSize());
                documento.setMimeType(file.getContentType());
                documento.setVersao((documento.getVersao() != null ? documento.getVersao() : 1) + 1);
            } else {
                UUID documentoId = UUID.fromString(fileId);
                String objectKey;
                try (InputStream inputStream = file.getInputStream()) {
                    objectKey = storageService.upload(getTenantId(), documentoId,
                            originalName, inputStream, file.getContentType(), file.getSize());
                }

                documento = Documento.builder()
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
            }

            Documento saved = documentoRepository.save(documento);

            if (oldKeyToDeleteAfterSave != null) {
                storageService.delete(oldKeyToDeleteAfterSave);
            }

            if (replaceId == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
                UUID atorId = principal.getUserId();
                UUID tenantId = getTenantId();
                if (saved.getProcessoId() != null) {
                    Processo proc = processoRepository.findById(saved.getProcessoId()).orElse(null);
                    UUID resp = proc != null ? proc.getResponsavelId() : null;
                    List<UUID> dests = resp != null ? List.of(resp) : List.of();
                    try {
                        notificacaoService.notificarDocumentoNovo(tenantId, saved.getId().toString(), dests,
                                saved.getNome(), "/processos/" + saved.getProcessoId(), atorId);
                    } catch (IllegalArgumentException ex) {
                        log.warn("DOCUMENTO_NOVO: falha ao notificar (responsavelId possivelmente órfão) documento={}",
                                saved.getId(), ex);
                    }
                } else if (saved.getClienteId() != null) {
                    List<UUID> dests = new ArrayList<>();
                    clienteAdvogadoRepository.findByClienteIdAndTenantId(saved.getClienteId(), tenantId)
                            .forEach(a -> dests.add(a.getUserId()));
                    clienteAdministrativoRepository.findByClienteIdAndTenantId(saved.getClienteId(), tenantId)
                            .forEach(a -> dests.add(a.getUserId()));
                    try {
                        notificacaoService.notificarDocumentoNovo(tenantId, saved.getId().toString(), dests,
                                saved.getNome(), "/clientes/" + saved.getClienteId(), atorId);
                    } catch (IllegalArgumentException ex) {
                        log.warn("DOCUMENTO_NOVO: falha ao notificar (destinatário possivelmente órfão) documento={}",
                                saved.getId(), ex);
                    }
                }
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao ler ficheiro"));
        }
    }

    @PreAuthorize("hasAuthority('documentos:view')")
    @GetMapping("/documentos")
    public ResponseEntity<?> listDocumentos() {
        return ResponseEntity.ok(documentoRepository.findByTenantId(getTenantId()));
    }

    @PreAuthorize("hasAuthority('documentos:view')")
    @GetMapping("/processos/{id}/documentos")
    public ResponseEntity<?> listProcessoDocumentos(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(documentoRepository.findByTenantIdAndProcessoId(getTenantId(), id));
    }

    @PreAuthorize("hasAuthority('documentos:view')")
    @GetMapping("/clientes/{id}/documentos")
    public ResponseEntity<?> listClienteDocumentos(@PathVariable UUID id) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
        }
        return ResponseEntity.ok(documentoRepository.findByTenantIdAndClienteId(getTenantId(), id));
    }

    @PreAuthorize("hasAuthority('documentos:view')")
    @GetMapping("/documentos/{id}/download")
    public ResponseEntity<?> downloadDocumento(@PathVariable UUID id) {
        Documento doc = documentoRepository.findById(id).orElse(null);
        if (doc == null || !doc.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento não encontrado"));
        }

        // Audit record — T-34-03: placed before response so record is written even if downstream error occurs
        Authentication dlAuth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal dlPrincipal = (UserPrincipal) dlAuth.getPrincipal();
        auditLogRepository.save(AuditLog.builder()
                .tenantId(dlPrincipal.getTenantId())
                .processoId(doc.getProcessoId()) // nullable — documento may not be linked to a processo
                .acao("documento_download")
                .entidadeTipo("documento")
                .entidadeId(id.toString())
                .autorId(dlPrincipal.getUserId())
                .build());

        try {
            String url = storageService.presignedDownloadUrl(doc.getCaminhoArquivo());
            return ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600));
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        }
    }

    @PreAuthorize("hasAuthority('documentos:edit')")
    @DeleteMapping("/documentos/{id}")
    public ResponseEntity<?> deleteDocumento(@PathVariable UUID id) {
        Documento doc = documentoRepository.findById(id).orElse(null);
        if (doc == null || !doc.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento não encontrado"));
        }

        if (doc.getProcessoId() != null) {
            Processo processo = processoRepository.findById(doc.getProcessoId()).orElse(null);
            if (processo != null && Boolean.TRUE.equals(processo.getLegalHold())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Processo sob Legal Hold. Documento protegido."));
            }
        }

        Authentication delAuth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal delPrincipal = (UserPrincipal) delAuth.getPrincipal();

        try {
            storageService.delete(doc.getCaminhoArquivo());
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        }

        // Audit record — T-34-03: written after storage delete succeeds, before DB entity removal
        // so that the FK (entidadeId → documento.id) is still valid at save time.
        auditLogRepository.save(AuditLog.builder()
                .tenantId(delPrincipal.getTenantId())
                .processoId(doc.getProcessoId()) // nullable — documento may not be linked to a processo
                .acao("documento_eliminacao")
                .entidadeTipo("documento")
                .entidadeId(id.toString())
                .autorId(delPrincipal.getUserId())
                .build());

        documentoRepository.delete(doc);
        return ResponseEntity.ok(Map.of("message", "Documento removido com sucesso!"));
    }

    // ==========================================
    // FINANCEIRO
    // ==========================================
    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/honorarios")
    public ResponseEntity<?> listHonorarios(@RequestParam(name = "processo_id", required = false) UUID processoId) {
        UUID tenantId = getTenantId();
        if (processoId != null) {
            Processo processo = processoRepository.findById(processoId).orElse(null);
            if (processo == null || !processo.getTenantId().equals(tenantId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
            }
            return ResponseEntity.ok(honorarioRepository.findByProcessoId(processoId));
        }
        // Return honorarios associated with processes under current tenant
        List<Processo> tenantProcs = processoRepository.findByTenantId(tenantId);
        List<Honorario> response = new ArrayList<>();

        for (Processo p : tenantProcs) {
            response.addAll(honorarioRepository.findByProcessoId(p.getId()));
        }

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('financeiro:edit')")
    @PostMapping("/honorarios")
    public ResponseEntity<?> createHonorario(@RequestBody Honorario hon) {
        if (hon.getProcessoId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "processoId é obrigatório"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        hon.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(honorarioRepository.save(hon));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/honorarios/{id}/pagamentos")
    public ResponseEntity<?> listHonorarioPagamentos(@PathVariable Integer id) {
        Honorario hon = honorarioRepository.findById(id).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        return ResponseEntity.ok(pagamentoRepository.findByHonorarioId(id));
    }

    @PreAuthorize("hasAuthority('financeiro:edit')")
    @PostMapping("/pagamentos")
    public ResponseEntity<?> createPagamento(@RequestBody Pagamento pag) {
        if (pag.getHonorarioId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "honorarioId é obrigatório"));
        }
        Honorario hon = honorarioRepository.findById(pag.getHonorarioId()).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo associado não encontrado"));
        }
        if (pag.getValorPago() == null || pag.getValorPago().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "valorPago é obrigatório e deve ser positivo"));
        }
        // ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.
        pag.setId(null);
        Pagamento saved = pagamentoRepository.save(pag);

        // Lógica de Negócio: Atualizar Saldo da Conta Corrente do Cliente
        try {
            UUID clienteId = processo.getClienteId();
                    ContaCorrente cc = contaCorrenteRepository.findByClienteId(clienteId)
                            .orElseGet(() -> contaCorrenteRepository.save(
                                    ContaCorrente.builder().clienteId(clienteId).saldo(BigDecimal.ZERO).build()
                            ));

                    // A payment increases account balance (positive inflow)
                    cc.setSaldo(cc.getSaldo().add(pag.getValorPago()));
                    contaCorrenteRepository.save(cc);
        } catch (DataAccessException ex) {
            // WR-05: narrowed from Exception -- only persistence-layer failures (transient DB
            // issues) are treated as recoverable here. valorPago is already validated non-null
            // above (CR-04), so a NullPointerException from bad arithmetic input can no longer
            // reach this block; if one does, or any other programming error occurs, it now
            // propagates instead of being silently logged as a warning.
            log.warn("PAGAMENTO_CREATE: falha ao atualizar saldo da conta corrente, pagamento={}", saved.getId(), ex);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/honorarios/{id}")
    public ResponseEntity<?> getHonorario(@PathVariable Integer id) {
        Honorario hon = honorarioRepository.findById(id).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        return ResponseEntity.ok(hon);
    }

    @PreAuthorize("hasAuthority('financeiro:edit')")
    @PutMapping("/honorarios/{id}")
    public ResponseEntity<?> updateHonorario(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Honorario hon = honorarioRepository.findById(id).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        if (body.containsKey("valorTotal")) {
            // WR-03 (Phase 90 code review, iteration 3): guard the parse the same way the
            // dataAcordo handling below already does, and reject a negative value, so a
            // malformed/negative valorTotal returns a clean 400 instead of an uncaught
            // NumberFormatException surfacing as a generic 500.
            BigDecimal valorTotal;
            try {
                valorTotal = new BigDecimal(body.get("valorTotal").toString());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "valorTotal inválido"));
            }
            if (valorTotal.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "valorTotal não pode ser negativo"));
            }
            hon.setValorTotal(valorTotal);
        }
        if (body.containsKey("descricao")) {
            hon.setDescricao(body.get("descricao") == null ? null : body.get("descricao").toString());
        }
        if (body.containsKey("dataAcordo")) {
            try {
                hon.setDataAcordo(body.get("dataAcordo") == null ? null : java.time.LocalDate.parse(body.get("dataAcordo").toString()));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Data de acordo inválida"));
            }
        }
        return ResponseEntity.ok(honorarioRepository.save(hon));
    }

    @PreAuthorize("hasAuthority('financeiro:manage')")
    @DeleteMapping("/honorarios/{id}")
    public ResponseEntity<?> deleteHonorario(@PathVariable Integer id) {
        Honorario hon = honorarioRepository.findById(id).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        List<Pagamento> pagamentos = pagamentoRepository.findByHonorarioId(id);
        if (!pagamentos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Não é possível eliminar um honorário com pagamentos registados"));
        }
        honorarioRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('financeiro:manage')")
    @DeleteMapping("/pagamentos/{id}")
    public ResponseEntity<?> deletePagamento(@PathVariable Integer id) {
        Pagamento pag = pagamentoRepository.findById(id).orElse(null);
        if (pag == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Pagamento não encontrado"));
        }
        Honorario hon = honorarioRepository.findById(pag.getHonorarioId()).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo associado não encontrado"));
        }
        try {
            UUID clienteId = processo.getClienteId();
            ContaCorrente cc = contaCorrenteRepository.findByClienteId(clienteId).orElse(null);
            // WR-05: guard against null valorPago on legacy rows persisted before CR-04's
            // validation existed, so BigDecimal#subtract can't NPE here.
            if (cc != null && pag.getValorPago() != null) {
                cc.setSaldo(cc.getSaldo().subtract(pag.getValorPago()));
                contaCorrenteRepository.save(cc);
            }
        } catch (DataAccessException ex) {
            // WR-05: narrowed from Exception -- see createPagamento's equivalent catch clause.
            log.warn("PAGAMENTO_DELETE: falha ao atualizar saldo da conta corrente, pagamento={}", id, ex);
        }
        pagamentoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // DASHBOARD
    // ==========================================
    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        UUID tenantId = getTenantId();

        long totalClientes = clienteRepository.findByTenantId(tenantId).size();
        
        List<Processo> procs = processoRepository.findByTenantId(tenantId);
        long processosAtivos = procs.stream()
                .filter(p -> !"ENCERRADO".equalsIgnoreCase(p.getEstado()))
                .count();

        long prazosVencer = agendaUrgentesCount(tenantId);

        BigDecimal valoresRecebidos = calculateMensalReceived(tenantId);

        DashboardKpiResponse kpis = DashboardKpiResponse.builder()
                .total_clientes(totalClientes)
                .processos_ativos(processosAtivos)
                .prazos_vencer(prazosVencer)
                .valores_recebidos_mes(valoresRecebidos)
                .build();

        return ResponseEntity.ok(kpis);
    }

    // NOTA (WR-01, 85-REVIEW.md): eventos com prioridade ALTA e dataFim nula resultam em
    // risco="ok" (computeRiscoEvento trata data nula como "ok") e por isso NÃO são contados
    // como urgentes/críticos aqui — comportamento aceite explicitamente para este corner case
    // (lembrete de prioridade ALTA sem data de fim definida). Isto é uma mudança face à
    // lógica anterior, que contava qualquer evento ALTA independentemente da data. Se o
    // produto decidir que este corner case deve contar como urgente/crítico por definição,
    // tratar "ALTA" + dataFim nula como urgente explicitamente antes de delegar em
    // computeRiscoEvento. Helper único partilhado por agendaUrgentesCount (KPI /dashboard
    // prazos_vencer) e prazosCriticosCount (KPI /processos/dashboard prazos_criticos_count) —
    // WR-01 (85-REVIEW.md) identificou que os dois KPIs tinham o mesmo padrão duplicado sem
    // cross-reference entre si; consolidado aqui para que este comentário cubra ambos.
    // Cobertura de teste de regressão está a cargo do follow-up de testes de controller
    // (WR-03, 85-REVIEW.md).
    private boolean isEventoCritico(Evento e) {
        String risco = riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade());
        return RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco);
    }

    private long agendaUrgentesCount(UUID tenantId) {
        return eventoRepository.findByTenantIdAndConcluido(tenantId, false)
                .stream()
                .filter(this::isEventoCritico)
                .count();
    }

    private BigDecimal calculateMensalReceived(UUID tenantId) {
        List<Processo> procs = processoRepository.findByTenantId(tenantId);
        BigDecimal total = BigDecimal.ZERO;

        for (Processo p : procs) {
            List<Honorario> hList = honorarioRepository.findByProcessoId(p.getId());
            for (Honorario h : hList) {
                List<Pagamento> pagList = pagamentoRepository.findByHonorarioId(h.getId());
                for (Pagamento pag : pagList) {
                    // Check if payment was made in current month
                    if (pag.getDataPagamento() != null && pag.getDataPagamento().getMonthValue() == LocalDate.now().getMonthValue()) {
                        total = total.add(pag.getValorPago());
                    }
                }
            }
        }

        return total;
    }

    @GetMapping("/processos/dashboard")
    @PreAuthorize("hasAuthority('processos:view')")
    public ResponseEntity<com.lexcv.dtos.ProcessosDashboardData> getProcessosDashboard() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();
        UUID tenantId = userPrincipal.getTenantId();

        List<Processo> tenantProcessos = processoRepository.findByTenantId(tenantId);
        
        // 1. Operational Metrics
        Map<String, com.lexcv.dtos.ProcessosDashboardData.DashboardBacklogItem> backlogMap = new HashMap<>();
        long processosInativosCount = 0;
        int activeProcessCount = 0;
        Map<String, Long> carteiraMap = new HashMap<>();
        long processesWithDocs = 0;
        long totalDays = 0;
        long closedCount = 0;

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        for (Processo p : tenantProcessos) {
            String estado = p.getEstado() != null ? p.getEstado().toUpperCase() : "";
            if (!"ENCERRADO".equals(estado)) {
                activeProcessCount++;
                
                // Backlog por responsável
                String responsavelId = p.getResponsavelId() != null ? p.getResponsavelId().toString() : "unassigned";
                String responsavelNome = "Não atribuído";
                if (p.getResponsavelId() != null) {
                    User rUser = userRepository.findById(p.getResponsavelId()).orElse(null);
                    if (rUser != null) {
                        responsavelNome = rUser.getNome();
                    }
                }
                com.lexcv.dtos.ProcessosDashboardData.DashboardBacklogItem bItem = backlogMap.get(responsavelId);
                if (bItem == null) {
                    bItem = com.lexcv.dtos.ProcessosDashboardData.DashboardBacklogItem.builder()
                            .responsavel_id(responsavelId)
                            .responsavel_nome(responsavelNome)
                            .count(1)
                            .build();
                    backlogMap.put(responsavelId, bItem);
                } else {
                    bItem.setCount(bItem.getCount() + 1);
                }

                // Inativos
                List<Movimentacao> movs = movimentacaoRepository.findByProcessoId(p.getId());
                LocalDateTime lastActivity = p.getCreatedAt();
                if (movs != null && !movs.isEmpty()) {
                    movs.sort((m1, m2) -> m2.getData().compareTo(m1.getData()));
                    lastActivity = movs.get(0).getData();
                }
                if (lastActivity.isBefore(thirtyDaysAgo)) {
                    processosInativosCount++;
                }

                // Conformidade Documental
                List<Documento> docs = documentoRepository.findByTenantIdAndProcessoId(tenantId, p.getId());
                if (docs != null && !docs.isEmpty()) {
                    processesWithDocs++;
                }

                // Exposicao Carteira
                String area = p.getAreaJuridica() != null ? p.getAreaJuridica() : "Não classificada";
                carteiraMap.put(area, carteiraMap.getOrDefault(area, 0L) + 1L);
            } else {
                // Encerrado
                if (p.getDataFim() != null && p.getCreatedAt() != null) {
                    long diff = ChronoUnit.DAYS.between(p.getCreatedAt().toLocalDate(), p.getDataFim());
                    totalDays += diff;
                    closedCount++;
                }
            }
        }

        List<com.lexcv.dtos.ProcessosDashboardData.DashboardBacklogItem> backlogList = backlogMap.values().stream()
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        // Prazos Críticos — via RiscoPrazoService (tabela de limiares partilhada 7d-ALTA/3d-outros).
        // Corner case (ALTA + dataFim nula não conta como crítico) documentado em isEventoCritico()
        // — WR-01 (85-REVIEW.md).
        long prazosCriticosCount = eventoRepository.findByTenantIdAndConcluido(tenantId, false)
                .stream()
                .filter(this::isEventoCritico)
                .count();

        // Exposicao por Carteira list
        long finalActiveProcessCount = activeProcessCount;
        List<com.lexcv.dtos.ProcessosDashboardData.ExposicaoCarteiraItem> carteiraList = carteiraMap.entrySet().stream()
                .map(entry -> com.lexcv.dtos.ProcessosDashboardData.ExposicaoCarteiraItem.builder()
                        .area_juridica(entry.getKey())
                        .count(entry.getValue())
                        .percentage(finalActiveProcessCount > 0 ? Math.round((entry.getValue() * 100.0) / finalActiveProcessCount) : 0L)
                        .build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        long conformidade = activeProcessCount > 0 ? Math.round((processesWithDocs * 100.0) / activeProcessCount) : 0L;
        long tempoMedio = closedCount > 0 ? Math.round((double) totalDays / closedCount) : 0L;

        com.lexcv.dtos.ProcessosDashboardData data = com.lexcv.dtos.ProcessosDashboardData.builder()
                .operacional(com.lexcv.dtos.ProcessosDashboardData.OperacionalData.builder()
                        .backlog_por_responsavel(backlogList)
                        .prazos_criticos_count(prazosCriticosCount)
                        .processos_inativos_count(processosInativosCount)
                        .build())
                .executivo(com.lexcv.dtos.ProcessosDashboardData.ExecutivoData.builder()
                        .conformidade_documental(conformidade)
                        .exposicao_por_carteira(carteiraList)
                        .tempo_medio_resolucao_dias(tempoMedio)
                        .build())
                .build();

        return ResponseEntity.ok(data);
    }
}
