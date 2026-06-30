package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.User;
import com.lexcv.repositories.ParecerSolicitacaoRepository;
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

    @PreAuthorize("hasAuthority('pareceres:create')")
    @PostMapping("")
    public ResponseEntity<?> createSolicitacao(@RequestBody ParecerSolicitacao body) {
        UUID tenantId = getTenantId();
        body.setTenantId(tenantId);

        if (body.getClienteId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "clienteId é obrigatório"));
        }
        if (body.getDescricao() == null || body.getDescricao().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "descricao é obrigatória"));
        }

        if (body.getAdvogadoId() != null) {
            User advogado = validateAdvogado(body.getAdvogadoId(), tenantId);
            if (advogado == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "advogadoId não pertence a este tenant ou não tem papel ADVOGADO"));
            }
            body.setStatus("EM_ELABORACAO");
        }

        ParecerSolicitacao saved = parecerSolicitacaoRepository.save(body);
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
                .filter(p -> status == null || status.isBlank() || status.equalsIgnoreCase(p.getStatus()))
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
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
        if (solicitacao == null || !solicitacao.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
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
                    .body(Map.of("message", "advogadoId é obrigatório"));
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
