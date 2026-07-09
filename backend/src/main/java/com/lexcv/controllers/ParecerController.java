package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.models.AuditLog;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.ParecerVersao;
import com.lexcv.models.User;
import com.lexcv.repositories.AuditLogRepository;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ParecerSolicitacaoRepository;
import com.lexcv.repositories.ParecerVersaoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pareceres/solicitacoes")
@RequiredArgsConstructor
@Slf4j
public class ParecerController {

    private final ParecerSolicitacaoRepository parecerSolicitacaoRepository;
    private final UserRepository userRepository;
    private final ClienteRepository clienteRepository;
    private final ProcessoRepository processoRepository;
    private final ParecerVersaoRepository parecerVersaoRepository;
    private final StorageService storageService;
    private final AuditLogRepository auditLogRepository;
    private final NotificacaoService notificacaoService;

    private UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getTenantId();
    }

    /**
     * Validates that advogadoId references a User belonging to this tenant with role ADVOGADO.
     * Returns the validated User on success, or null if validation fails.
     */
    private User validateAdvogado(UUID advogadoId, UUID tenantId) {
        User user = userRepository.findById(advogadoId).orElse(null);
        // WR-02 (Phase 87 code review, iteration 3): also reject a deactivated advogado --
        // mirrors the identical ativo guard added to ResourceController.atribuirResponsavel/
        // createPrazo. Previously only the frontend pickers filtered inactive users out.
        if (user == null || !tenantId.equals(user.getTenantId()) || Boolean.FALSE.equals(user.getAtivo())) {
            return null;
        }
        boolean isAdvogado = user.getRoles().stream()
                .anyMatch(r -> "ADVOGADO".equals(r.getNome()));
        if (!isAdvogado) {
            return null;
        }
        return user;
    }

    /**
     * Validates that clienteId references a Cliente belonging to this tenant.
     */
    private boolean clienteBelongsToTenant(UUID clienteId, UUID tenantId) {
        return clienteRepository.findById(clienteId)
                .map(c -> tenantId.equals(c.getTenantId()))
                .orElse(false);
    }

    /**
     * Validates that processoId references a Processo belonging to this tenant.
     */
    private boolean processoBelongsToTenant(UUID processoId, UUID tenantId) {
        return processoRepository.findById(processoId)
                .map(p -> tenantId.equals(p.getTenantId()))
                .orElse(false);
    }

    /**
     * Validates that processoId references a Processo belonging to the given clienteId.
     * Assumes the processo has already been confirmed to belong to the current tenant.
     */
    private boolean processoBelongsToCliente(UUID processoId, UUID clienteId) {
        return processoRepository.findById(processoId)
                .map(p -> clienteId.equals(p.getClienteId()))
                .orElse(false);
    }

    @PreAuthorize("hasAuthority('pareceres:create')")
    @PostMapping("")
    @Transactional
    public ResponseEntity<?> createSolicitacao(@RequestBody ParecerSolicitacao body) {
        UUID tenantId = getTenantId();

        if (body.getClienteId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId é obrigatório"));
        }
        if (body.getDescricao() == null || body.getDescricao().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "descricao é obrigatória"));
        }
        if (!clienteBelongsToTenant(body.getClienteId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId inválido ou não pertence a este tenant"));
        }
        if (body.getProcessoId() != null && !processoBelongsToTenant(body.getProcessoId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "processoId inválido ou não pertence a este tenant"));
        }
        if (body.getProcessoId() != null && !processoBelongsToCliente(body.getProcessoId(), body.getClienteId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "processoId não pertence ao cliente indicado"));
        }

        // Construct the persisted entity from an explicit allowlist of creatable
        // fields only — id/createdAt are server-generated, status defaults to
        // PENDENTE (or EM_ELABORACAO when an advogado is assigned at creation
        // time) and is never taken directly from the request body.
        ParecerSolicitacao solicitacao = new ParecerSolicitacao();
        solicitacao.setTenantId(tenantId);
        solicitacao.setClienteId(body.getClienteId());
        solicitacao.setProcessoId(body.getProcessoId());
        solicitacao.setDescricao(body.getDescricao());
        solicitacao.setPrazo(body.getPrazo());
        if (body.getPrioridade() != null) {
            solicitacao.setPrioridade(body.getPrioridade());
        }
        solicitacao.setStatus("PENDENTE");

        if (body.getAdvogadoId() != null) {
            User advogado = validateAdvogado(body.getAdvogadoId(), tenantId);
            if (advogado == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "advogadoId não pertence a este tenant, não tem papel ADVOGADO ou está inativo"));
            }
            solicitacao.setAdvogadoId(body.getAdvogadoId());
            solicitacao.setStatus("EM_ELABORACAO");
        }

        ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);

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

        if (saved.getAdvogadoId() != null) {
            notificacaoService.notificarParecerAtribuido(tenantId, saved.getId().toString(),
                    saved.getAdvogadoId(), "/pareceres/" + saved.getId(), principal.getUserId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping("")
    public ResponseEntity<?> listSolicitacoes(
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID advogadoId,
            @RequestParam(required = false) String status
    ) {
        UUID tenantId = getTenantId();
        List<ParecerSolicitacao> result = parecerSolicitacaoRepository.findByTenantId(tenantId).stream()
                .filter(p -> clienteId == null || clienteId.equals(p.getClienteId()))
                .filter(p -> advogadoId == null || advogadoId.equals(p.getAdvogadoId()))
                .filter(p -> status == null || status.isBlank() || status.equals(p.getStatus()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getSolicitacao(@PathVariable UUID id) {
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }
        return ResponseEntity.ok(solicitacao);
    }

    @PreAuthorize("hasAuthority('pareceres:edit')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSolicitacao(@PathVariable UUID id, @RequestBody ParecerSolicitacao payload) {
        UUID tenantId = getTenantId();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }

        if (payload.getClienteId() != null && !clienteBelongsToTenant(payload.getClienteId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId inválido ou não pertence a este tenant"));
        }
        if (payload.getProcessoId() != null && !processoBelongsToTenant(payload.getProcessoId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "processoId inválido ou não pertence a este tenant"));
        }
        if (payload.getProcessoId() != null) {
            UUID effectiveClienteId = payload.getClienteId() != null
                    ? payload.getClienteId()
                    : solicitacao.getClienteId();
            if (!processoBelongsToCliente(payload.getProcessoId(), effectiveClienteId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "processoId não pertence ao cliente indicado"));
            }
        }

        // CR-01 (Phase 87 code review, iteration 3): prazo/prioridade/descricao are now
        // null-guarded exactly like clienteId/processoId below -- previously prazo and
        // prioridade were applied unconditionally, silently wiping an existing prazo (legal
        // deadline) on any partial update that omitted it, and risking an uncaught 500 on
        // flush if prioridade (NOT NULL) was omitted (its @Builder.Default only applies
        // through the Lombok builder, not the @NoArgsConstructor+setters path Jackson uses
        // to deserialize this @RequestBody). descricao was previously not editable through
        // this endpoint at all; it is now updatable like every other field here.
        if (payload.getPrazo() != null) {
            solicitacao.setPrazo(payload.getPrazo());
        }
        if (payload.getPrioridade() != null) {
            solicitacao.setPrioridade(payload.getPrioridade());
        }
        if (payload.getDescricao() != null && !payload.getDescricao().isBlank()) {
            solicitacao.setDescricao(payload.getDescricao());
        }
        if (payload.getClienteId() != null) {
            solicitacao.setClienteId(payload.getClienteId());
        }
        if (payload.getProcessoId() != null) {
            solicitacao.setProcessoId(payload.getProcessoId());
        }
        // status and advogadoId are intentionally excluded: status is a state-machine field
        // and advogadoId is changed only via /atribuir

        return ResponseEntity.ok(parecerSolicitacaoRepository.save(solicitacao));
    }

    @PreAuthorize("hasAuthority('pareceres:edit')")
    @PutMapping("/{id}/atribuir")
    @Transactional
    public ResponseEntity<?> atribuirAdvogado(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        UUID tenantId = getTenantId();

        String advogadoIdRaw = body.get("advogadoId");
        if (advogadoIdRaw == null || advogadoIdRaw.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "advogadoId é obrigatório"));
        }

        UUID advogadoId;
        try {
            advogadoId = UUID.fromString(advogadoIdRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "advogadoId inválido"));
        }

        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }

        if ("CONCLUIDO".equals(solicitacao.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Não é possível reatribuir um parecer concluído"));
        }

        User advogado = validateAdvogado(advogadoId, tenantId);
        if (advogado == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "advogadoId não pertence a este tenant, não tem papel ADVOGADO ou está inativo"));
        }

        // WR-01 (Phase 87 code review): no-op guard -- re-"assigning" the same advogado
        // while already EM_ELABORACAO must not re-fire "you were assigned" notifications.
        // If the status differs (e.g. EM_REVISAO), this is a real transition (send back
        // for rework) and must proceed to notify.
        if (advogadoId.equals(solicitacao.getAdvogadoId()) && "EM_ELABORACAO".equals(solicitacao.getStatus())) {
            return ResponseEntity.ok(solicitacao);
        }

        solicitacao.setAdvogadoId(advogadoId);
        solicitacao.setStatus("EM_ELABORACAO");

        ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);

        // Audit record — PARA-01: autor_id from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .processoId(saved.getProcessoId())
                .acao("parecer_atribuir")
                .entidadeTipo("parecer_solicitacao")
                .entidadeId(saved.getId().toString())
                .autorId(principal.getUserId())
                .build());

        notificacaoService.notificarParecerAtribuido(tenantId, saved.getId().toString(),
                saved.getAdvogadoId(), "/pareceres/" + saved.getId(), principal.getUserId());

        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasAuthority('pareceres:manage')")
    @PutMapping("/{id}/versoes/{versaoId}/aprovar")
    @Transactional
    public ResponseEntity<?> aprovarVersao(@PathVariable UUID id, @PathVariable UUID versaoId) {
        UUID tenantId = getTenantId();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }

        if ("CONCLUIDO".equals(solicitacao.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Não é possível aprovar uma versão de um parecer concluído"));
        }

        ParecerVersao versao = parecerVersaoRepository.findById(versaoId).orElse(null);
        if (versao == null || !versao.getSolicitacaoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Versão não encontrada"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        versao.setAprovado(true);
        versao.setAprovadoPorId(principal.getUserId());
        versao.setAprovadoEm(LocalDateTime.now());
        parecerVersaoRepository.save(versao);

        if ("PENDENTE".equals(solicitacao.getStatus()) || "EM_ELABORACAO".equals(solicitacao.getStatus())) {
            solicitacao.setStatus("EM_REVISAO");
            parecerSolicitacaoRepository.save(solicitacao);
        }

        // Audit record — PARA-01: autor_id from SecurityContext
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .processoId(solicitacao.getProcessoId())
                .acao("parecer_aprovar")
                .entidadeTipo("parecer_versao")
                .entidadeId(versao.getId().toString())
                .autorId(principal.getUserId())
                .build());

        return ResponseEntity.ok(versao);
    }

    @PreAuthorize("hasAuthority('pareceres:edit')")
    @PutMapping("/{id}/entregar")
    @Transactional
    public ResponseEntity<?> entregarSolicitacao(@PathVariable UUID id, @RequestParam UUID versaoFinalId) {
        UUID tenantId = getTenantId();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }

        if ("CONCLUIDO".equals(solicitacao.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Parecer já foi entregue"));
        }

        ParecerVersao versao = parecerVersaoRepository.findById(versaoFinalId).orElse(null);
        if (versao == null || !versao.getSolicitacaoId().equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Versão não encontrada"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        boolean isAdmin = principal.getRoles().contains("ADMIN");
        boolean isResponsavel = solicitacao.getAdvogadoId() != null
                && solicitacao.getAdvogadoId().equals(principal.getUserId());
        if (!isAdmin && !isResponsavel) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Apenas o advogado responsável ou ADMIN pode entregar o parecer"));
        }

        solicitacao.setVersaoFinalId(versaoFinalId);
        solicitacao.setStatus("CONCLUIDO");

        ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);

        // Audit record — PARA-01: autor_id from SecurityContext
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .processoId(saved.getProcessoId())
                .acao("parecer_entregar")
                .entidadeTipo("parecer_solicitacao")
                .entidadeId(saved.getId().toString())
                .autorId(principal.getUserId())
                .build());

        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping("/{solicitacaoId}/versoes")
    public ResponseEntity<?> listVersoes(@PathVariable UUID solicitacaoId) {
        UUID tenantId = getTenantId();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }
        return ResponseEntity.ok(parecerVersaoRepository.findBySolicitacaoId(solicitacaoId));
    }

    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping("/{solicitacaoId}/versoes/{versaoId}")
    public ResponseEntity<?> getVersao(@PathVariable UUID solicitacaoId, @PathVariable UUID versaoId) {
        UUID tenantId = getTenantId();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }
        ParecerVersao versao = parecerVersaoRepository.findById(versaoId).orElse(null);
        if (versao == null || !versao.getSolicitacaoId().equals(solicitacaoId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Versão não encontrada"));
        }
        return ResponseEntity.ok(versao);
    }

    @PreAuthorize("hasAuthority('pareceres:edit')")
    @PostMapping(value = "/{solicitacaoId}/versoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> createVersao(
            @PathVariable UUID solicitacaoId,
            @RequestParam(value = "conteudo", required = false) String conteudo,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        UUID tenantId = getTenantId();
        // WR-04 (Phase 87 code review, iteration 3): PESSIMISTIC_WRITE row lock, held for the
        // rest of this @Transactional method, makes the numeroVersao increment-and-insert
        // below atomic across concurrent requests for the same solicitacaoId (see note there).
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findByIdForUpdate(solicitacaoId).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        boolean isAdmin = principal.getRoles().contains("ADMIN");
        boolean isResponsavel = solicitacao.getAdvogadoId() != null
                && solicitacao.getAdvogadoId().equals(principal.getUserId());
        if (!isAdmin && !isResponsavel) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Apenas o advogado responsável ou ADMIN pode criar uma versão"));
        }

        if ((conteudo == null || conteudo.isBlank()) && (file == null || file.isEmpty())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "É necessário fornecer conteúdo ou anexo"));
        }

        // Insert the row first so Hibernate assigns the id (GenerationType.UUID) — do not
        // pre-generate the id in application code and set it on a new entity before persist():
        // Spring Data's isNew() check then sees a non-null id and routes save() through
        // merge() instead of persist(), which fails at runtime (either an
        // ObjectOptimisticLockingFailureException trying to UPDATE a row that doesn't exist yet,
        // or "detached entity passed to persist" once isNew() is corrected without also removing
        // the pre-assignment). The file upload needs the row's real id as the storage-key prefix,
        // so it happens after this first save(), inside the same @Transactional method — if the
        // upload fails, the method returns via setRollbackOnly() below and the insert rolls back.
        // WR-04 (Phase 87 code review, iteration 3): the increment-and-insert below is made
        // atomic by the PESSIMISTIC_WRITE row lock acquired on the parent ParecerSolicitacao
        // above (findByIdForUpdate), held until this transaction commits/rolls back -- not by
        // a JVM monitor, which only serializes threads within a single instance and provides
        // no protection once two concurrent transactions can each compute "next" from a
        // snapshot that doesn't yet include the other's uncommitted insert. A second
        // concurrent request for the same solicitacaoId now blocks at findByIdForUpdate until
        // this transaction ends, at which point its own findMaxNumeroVersaoBySolicitacaoId(...)
        // correctly observes this insert.
        int next = parecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId(solicitacaoId).orElse(0) + 1;

        ParecerVersao versao = ParecerVersao.builder()
                .tenantId(tenantId)
                .solicitacaoId(solicitacaoId)
                .conteudo(conteudo)
                .numeroVersao(next)
                .criadoPorId(principal.getUserId())
                .build();

        ParecerVersao saved = parecerVersaoRepository.save(versao);

        if (file != null && !file.isEmpty()) {
            try {
                String caminhoAnexo = storageService.upload(tenantId, saved.getId(), file.getOriginalFilename(),
                        file.getInputStream(), file.getContentType(), file.getSize());
                saved.setCaminhoAnexo(caminhoAnexo);
                saved = parecerVersaoRepository.save(saved);
            } catch (IOException e) {
                org.springframework.transaction.interceptor.TransactionAspectSupport
                        .currentTransactionStatus().setRollbackOnly();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Erro ao ler ficheiro"));
            } catch (StorageUnavailableException e) {
                org.springframework.transaction.interceptor.TransactionAspectSupport
                        .currentTransactionStatus().setRollbackOnly();
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Storage service unavailable"));
            }
        }

        // Audit record — PARA-01: autor_id from SecurityContext
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .processoId(solicitacao.getProcessoId())
                .acao("parecer_versao_criar")
                .entidadeTipo("parecer_versao")
                .entidadeId(saved.getId().toString())
                .autorId(principal.getUserId())
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping("/{solicitacaoId}/versoes/{versaoId}/anexo")
    public ResponseEntity<?> downloadAnexo(@PathVariable UUID solicitacaoId, @PathVariable UUID versaoId) {
        UUID tenantId = getTenantId();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(solicitacaoId).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
        }

        ParecerVersao versao = parecerVersaoRepository.findById(versaoId).orElse(null);
        if (versao == null || !versao.getSolicitacaoId().equals(solicitacaoId) || versao.getCaminhoAnexo() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Anexo não encontrado"));
        }

        try {
            String url = storageService.presignedDownloadUrl(versao.getCaminhoAnexo());
            return ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600));
        } catch (StorageUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Storage service unavailable"));
        }
    }
}
