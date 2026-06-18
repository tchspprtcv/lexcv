package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ClienteMergeRequest;
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
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
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

    private static final String UPLOAD_DIR = "uploads/";

    // ==========================================
    // INTAKE & CONFLICT CHECK — campos mínimos por tipo_processo
    // ==========================================
    private static final Map<String, List<String>> CAMPOS_MINIMOS_POR_TIPO = Map.of(
            "civel", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
            "penal", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
            "laboral", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
            "administrativo", List.of("clienteId", "numeroProcesso", "areaJuridica", "dataInicio"),
            "familia", List.of("clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"),
            "comercial", List.of("clienteId", "numeroProcesso", "areaJuridica", "dataInicio"),
            "default", List.of("clienteId", "tipoProcesso", "areaJuridica", "dataInicio")
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
    public ResponseEntity<?> createCliente(@RequestBody Cliente cliente) {
        cliente.setTenantId(getTenantId());
        if (cliente.getAtivo() == null) {
            cliente.setAtivo(true);
        }
        if (cliente.getDocumentoTipo() == DocumentoTipo.NIF) {
            cliente.setNif(cliente.getDocumentoNumero());
        }
        Cliente saved = clienteRepository.save(cliente);

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

        if (payload.getDocumentoTipo() == DocumentoTipo.NIF) {
            cliente.setNif(payload.getDocumentoNumero());
        } else {
            cliente.setNif(payload.getNif());
        }

        Cliente saved = clienteRepository.save(cliente);
        return ResponseEntity.ok(saved);
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

        contaCorrenteRepository.findByClienteId(payload.secondaryId()).ifPresent(contaCorrenteRepository::delete);
        clienteRepository.delete(secondary);

        return ResponseEntity.ok(Map.of(
                "primary_id", savedPrimary.getId().toString(),
                "moved_processos", processosToMove.size(),
                "moved_contactos", contactosToMove.size(),
                "moved_notas", notasToMove.size()
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

        return ResponseEntity.ok(enriched);
    }

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
        processo.setTenantId(getTenantId());
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

    private String computeRisco(LocalDate dataLimite, String prioridade) {
        if (dataLimite == null) return "ok";
        LocalDate hoje = LocalDate.now();
        if (dataLimite.isBefore(hoje)) return "vencido";
        long diasRestantes = ChronoUnit.DAYS.between(hoje, dataLimite);
        int limiarProximo = "ALTA".equalsIgnoreCase(prioridade) ? 7 : 3;
        return diasRestantes <= limiarProximo ? "proximo" : "ok";
    }

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
            String risco = computeRisco(p.getDataLimite(), p.getPrioridade());
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
            String risco = computeRisco(p.getDataLimite(), p.getPrioridade());
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
        if (payload.responsavelId() != null) {
            User responsavel = userRepository.findById(payload.responsavelId()).orElse(null);
            if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "responsavelId não pertence a este tenant"));
            }
        }
        String prioridade = payload.prioridade() != null ? payload.prioridade().toUpperCase() : "MEDIA";
        String risco = computeRisco(payload.dataLimite(), prioridade);
        boolean escalonado = "proximo".equals(risco) || "vencido".equals(risco);
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
        response.put("risco", computeRisco(saved.getDataLimite(), saved.getPrioridade()));
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
                ("proximo".equals(computeRisco(prazo.getDataLimite(), prazo.getPrioridade()))
                        || "vencido".equals(computeRisco(prazo.getDataLimite(), prazo.getPrioridade())));
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
        response.put("risco", computeRisco(saved.getDataLimite(), saved.getPrioridade()));
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

        return ResponseEntity.status(HttpStatus.CREATED).body(processoFaseRepository.save(pf));
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
        mov.setProcessoId(id);
        if (mov.getData() == null) mov.setData(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoRepository.save(mov));
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

        return ResponseEntity.ok(eventos);
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

        evento.setTitulo(payload.getTitulo());
        evento.setDescricao(payload.getDescricao());
        evento.setTipo(payload.getTipo());
        evento.setDataInicio(payload.getDataInicio());
        evento.setDataFim(payload.getDataFim());
        evento.setPrioridade(payload.getPrioridade());
        evento.setConcluido(payload.getConcluido());

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

        try {
            File uploadFolder = new File(UPLOAD_DIR);
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            String fileId = UUID.randomUUID().toString();
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String savedName = fileId + extension;
            Path path = Paths.get(UPLOAD_DIR + savedName);
            Files.write(path, file.getBytes());

            Documento documento;
            if (replaceId != null) {
                documento = documentoRepository.findById(replaceId).orElse(null);
                if (documento == null || !documento.getTenantId().equals(getTenantId())) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento a substituir não encontrado"));
                }
                try {
                    Files.deleteIfExists(Paths.get(documento.getCaminhoArquivo()));
                } catch (IOException ignored) {}
                
                documento.setNome(originalName);
                if (tipo != null) documento.setTipo(tipo);
                if (confidencialidade != null) documento.setConfidencialidade(confidencialidade);
                documento.setCaminhoArquivo(path.toString());
                documento.setTamanho(file.getSize());
                documento.setMimeType(file.getContentType());
                documento.setVersao((documento.getVersao() != null ? documento.getVersao() : 1) + 1);
            } else {
                documento = Documento.builder()
                        .id(UUID.fromString(fileId))
                        .tenantId(getTenantId())
                        .processoId(processoId)
                        .clienteId(clienteId)
                        .nome(originalName)
                        .tipo(tipo != null ? tipo : "ANEXO")
                        .confidencialidade(confidencialidade != null ? confidencialidade : "PUBLICO")
                        .caminhoArquivo(path.toString())
                        .tamanho(file.getSize())
                        .mimeType(file.getContentType())
                        .versao(1)
                        .build();
            }

            Documento saved = documentoRepository.save(documento);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao gravar o arquivo localmente."));
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
    @GetMapping("/documentos/{id}/download")
    public ResponseEntity<?> downloadDocumento(@PathVariable UUID id) {
        Documento doc = documentoRepository.findById(id).orElse(null);
        if (doc == null || !doc.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento não encontrado"));
        }

        File file = new File(doc.getCaminhoArquivo());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Arquivo físico não encontrado"));
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

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getNome() + "\"")
                .body(resource);
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

        // Audit record — T-34-03: placed before delete so record is written before the entity is removed
        Authentication delAuth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal delPrincipal = (UserPrincipal) delAuth.getPrincipal();
        auditLogRepository.save(AuditLog.builder()
                .tenantId(delPrincipal.getTenantId())
                .processoId(doc.getProcessoId()) // nullable — documento may not be linked to a processo
                .acao("documento_eliminacao")
                .entidadeTipo("documento")
                .entidadeId(id.toString())
                .autorId(delPrincipal.getUserId())
                .build());

        try {
            Files.deleteIfExists(Paths.get(doc.getCaminhoArquivo()));
        } catch (IOException ignored) {}

        documentoRepository.delete(doc);
        return ResponseEntity.ok(Map.of("message", "Documento removido com sucesso!"));
    }

    // ==========================================
    // FINANCEIRO
    // ==========================================
    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/honorarios")
    public ResponseEntity<?> listHonorarios() {
        UUID tenantId = getTenantId();
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
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
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
        Honorario hon = honorarioRepository.findById(pag.getHonorarioId()).orElse(null);
        if (hon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
        }
        Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo associado não encontrado"));
        }
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
        } catch (Exception ignored) {}

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
            hon.setValorTotal(new BigDecimal(body.get("valorTotal").toString()));
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
            if (cc != null) {
                cc.setSaldo(cc.getSaldo().subtract(pag.getValorPago()));
                contaCorrenteRepository.save(cc);
            }
        } catch (Exception ignored) {}
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

    private long agendaUrgentesCount(UUID tenantId) {
        return eventoRepository.findByTenantIdAndConcluido(tenantId, false)
                .stream()
                .filter(e -> "ALTA".equalsIgnoreCase(e.getPrioridade()))
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

        // Prazos Críticos (próximos 7 dias)
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime sevenDays = today.plusDays(7);
        long prazosCriticosCount = 0;

        List<Evento> eventos = eventoRepository.findByTenantIdAndConcluido(tenantId, false);
        for (Evento e : eventos) {
            if (e.getDataFim() != null) {
                if (!e.getDataFim().isBefore(today) && !e.getDataFim().isAfter(sevenDays)) {
                    prazosCriticosCount++;
                }
            }
        }

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
