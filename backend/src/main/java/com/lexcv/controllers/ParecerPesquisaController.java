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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Separate controller for the top-level /api/v1/pareceres/pesquisa route.
 * ParecerController is mapped at /api/v1/pareceres/solicitacoes (class-level
 * @RequestMapping), so a sibling top-level route cannot live there — Spring
 * concatenates class-level and method-level mappings regardless of a leading
 * "/", producing /api/v1/pareceres/solicitacoes/api/v1/pareceres/pesquisa
 * instead of the intended path. This was a routing bug present since v2.5
 * (Phase 64) that made pesquisar() unreachable at its documented path;
 * fixed during v2.6 milestone integration audit (Phase 69).
 */
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
    public ResponseEntity<?> pesquisarSolicitacoes(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID advogadoId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime dataInicio,
            @RequestParam(required = false) LocalDateTime dataFim
    ) {
        UUID tenantId = getTenantId();
        List<ParecerSolicitacao> result = parecerSolicitacaoRepository.pesquisar(
                tenantId, texto, clienteId, advogadoId, status, dataInicio, dataFim);
        return ResponseEntity.ok(result);
    }
}
