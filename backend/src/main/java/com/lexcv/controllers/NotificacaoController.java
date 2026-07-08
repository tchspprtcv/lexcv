package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.Notificacao;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.services.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Dedicated controller for the /api/v1/notificacoes REST surface — extracted as its own
 * controller rather than growing the ~2900-line ResourceController, mirroring the exact
 * precedent set by ParecerPesquisaController (see 86-CONTEXT.md / ARCHITECTURE.md for the
 * rationale behind this extraction pattern).
 *
 * This is also the first controller in the codebase that scopes every query by BOTH the
 * caller's tenant AND their own userId: Notificacao is this project's first per-recipient
 * -private resource (every other resource in this codebase is tenant-shared, not
 * recipient-shared), so a dual-scoping mistake here would leak one user's notifications to
 * another user in the same tenant — the IDOR-adjacent risk this phase introduces.
 *
 * Read endpoints (listar, contarNaoLidas) query NotificacaoRepository directly. The two
 * mutating endpoints (marcarLida, marcarTodasLidas) delegate entirely to NotificacaoService
 * (Plan 86-02) instead of persisting changes directly from this class — this keeps
 * NotificacaoService the sole write path for the whole notification subsystem.
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoService notificacaoService;

    private UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getTenantId();
    }

    private UUID getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }

    @PreAuthorize("hasAuthority('notificacoes:view')")
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean lida,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notificacao> pageResult = notificacaoRepository.buscarPorFiltros(
                getTenantId(), getUserId(), categoria, lida, pageable);
        return ResponseEntity.ok(Map.of(
                "content", pageResult.getContent(),
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages(),
                "page", pageResult.getNumber(),
                "size", pageResult.getSize()
        ));
    }

    @PreAuthorize("hasAuthority('notificacoes:view')")
    @GetMapping("/unread-count")
    public ResponseEntity<?> contarNaoLidas() {
        long count = notificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse(getTenantId(), getUserId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PreAuthorize("hasAuthority('notificacoes:view')")
    @PatchMapping("/{id}/lida")
    public ResponseEntity<?> marcarLida(@PathVariable UUID id) {
        Notificacao n = notificacaoService.marcarLida(getTenantId(), getUserId(), id).orElse(null);
        if (n == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Notificação não encontrada"));
        }
        return ResponseEntity.ok(n);
    }

    @PreAuthorize("hasAuthority('notificacoes:view')")
    @PostMapping("/ler-todas")
    public ResponseEntity<?> marcarTodasLidas() {
        int marcadas = notificacaoService.marcarTodasLidas(getTenantId(), getUserId());
        return ResponseEntity.ok(Map.of("marcadas", marcadas));
    }
}
