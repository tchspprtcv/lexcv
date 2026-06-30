package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.User;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ParecerSolicitacaoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pareceres/solicitacoes")
@RequiredArgsConstructor
public class ParecerController {

    private final ParecerSolicitacaoRepository parecerSolicitacaoRepository;
    private final UserRepository userRepository;
    private final ClienteRepository clienteRepository;
    private final ProcessoRepository processoRepository;

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
        if (user == null || !tenantId.equals(user.getTenantId())) {
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

    @PreAuthorize("hasAuthority('pareceres:create')")
    @PostMapping("")
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
                        .body(Map.of("message", "advogadoId não pertence a este tenant ou não tem papel ADVOGADO"));
            }
            solicitacao.setAdvogadoId(body.getAdvogadoId());
            solicitacao.setStatus("EM_ELABORACAO");
        }

        ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);
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

        solicitacao.setPrazo(payload.getPrazo());
        solicitacao.setPrioridade(payload.getPrioridade());
        solicitacao.setClienteId(payload.getClienteId());
        solicitacao.setProcessoId(payload.getProcessoId());
        // status and advogadoId are intentionally excluded: status is a state-machine field
        // and advogadoId is changed only via /atribuir

        return ResponseEntity.ok(parecerSolicitacaoRepository.save(solicitacao));
    }

    @PreAuthorize("hasAuthority('pareceres:edit')")
    @PutMapping("/{id}/atribuir")
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
                    .body(Map.of("message", "advogadoId não pertence a este tenant ou não tem papel ADVOGADO"));
        }

        solicitacao.setAdvogadoId(advogadoId);
        solicitacao.setStatus("EM_ELABORACAO");

        return ResponseEntity.ok(parecerSolicitacaoRepository.save(solicitacao));
    }
}
