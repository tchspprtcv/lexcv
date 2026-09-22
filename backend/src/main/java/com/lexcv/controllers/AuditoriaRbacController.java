package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.AuditoriaRbacEntradaDto;
import com.lexcv.services.AuditoriaRbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /api/v1/admin/rbac/auditoria} (Phase 128, Decisao 5, Plano 07): consulta paginada e
 * com ambito de tenant sobre os eventos de auditoria RBAC (papeis criados/renomeados/apagados,
 * atribuicoes concedidas/retiradas) gravados por {@link AuditoriaRbacService}.
 *
 * <p><b>Porque e gateada pela MESMA autoridade que governa a escrita
 * ({@code hasAuthority('rbac:manage')}):</b> Decisao 5 (128-CONTEXT.md) e explicita -- quem pode
 * mudar papeis e atribuicoes num escritorio e quem pode consultar o historico dessas mudancas,
 * sem introduzir uma autoridade nova so para leitura ({@code audit:view} nao existe neste
 * milestone).
 *
 * <p><b>Porque e uma classe propria, e nao mais um handler em {@link AdminController} ou em
 * {@link OfficeRolesController}:</b> a mesma razao ja documentada em
 * {@link OfficeRolesController} -- um gate de CLASSE nao pode ser esquecido num handler novo,
 * enquanto uma anotacao de metodo pode. {@link AdminController} tem gate de classe
 * {@code users:manage}; um handler de auditoria ali herdaria silenciosamente essa autoridade
 * errada se alguem se esquecesse de anotar o metodo. Uma classe dedicada com gate de classe
 * {@code hasAuthority('rbac:manage')} torna esse esquecimento impossivel: qualquer handler
 * acrescentado aqui fica automaticamente coberto.
 *
 * <p><b>A garantia de so-leitura (AUDT-04):</b> esta classe tem, e so pode ter, handlers
 * {@code @GetMapping}. Isto e pinado estruturalmente por
 * {@code AuditLogImutabilidadeTest.nenhumControladorTemHandlerMutanteSobreAudit} (que falha se
 * qualquer {@code @RestController} sob {@code com.lexcv.controllers} ganhar um handler
 * POST/PUT/PATCH/DELETE cujo caminho contenha "audit") e pelo proprio teste desta classe
 * ({@code AuditoriaRbacControllerTest}), que verifica por reflexao que nenhuma das quatro
 * anotacoes mutantes esta presente em nenhum metodo declarado.
 *
 * <p><b>O tenant vem sempre do principal autenticado (AUDT-03):</b> nunca de um parametro do
 * pedido -- o handler {@link #listar} nao declara nenhum parametro de tenant, por construcao (nao
 * ha nada a esquecer de filtrar). {@code utilizadorAlvoId} e {@code papelId} sao passados tal e
 * qual ao servico; e {@link AuditoriaRbacService#listar} e {@link
 * com.lexcv.repositories.AuditLogRepository#buscarEventosRbac} que garantem, contra a base de
 * dados real, que o {@code tenantId} do principal e sempre a clausula WHERE dominante.
 *
 * <p><b>So eventos de RBAC:</b> eventos de processos/pareceres tem a sua propria vista
 * ({@code GET /processos/{id}/audit}, {@link ResourceController#getAuditLog}) e nunca aparecem
 * aqui -- {@code buscarEventosRbac} filtra por {@code entidade_tipo IN ('papel_escritorio',
 * 'atribuicao_papel')}.
 *
 * <p><b>Filtros:</b> {@code papelId} filtra por ID do papel, nunca por nome -- um papel renomeado
 * continua a corresponder aos seus proprios eventos antigos (ver o comentario de
 * {@code AuditLogRepository.buscarEventosRbac}). {@code utilizadorAlvoId} filtra pelo
 * {@code entidade_id} dos eventos {@code atribuicao_papel} (o utilizador alvo da
 * atribuicao/retirada).
 *
 * <p><b>Nota de reconciliacao com 128-UI-SPEC.md:</b> a secao "Data Contract Assumptions" do
 * UI-SPEC propos um filtro de papel por NOME ({@code papel}, correspondencia exata). Esta classe
 * segue em vez disso a interface do Plano 02/01 ({@code papelId}, um UUID) -- ver 128-07-SUMMARY.md
 * para o registo desta divergencia; o backend e a fonte da verdade e o Plano 08 (frontend) deve
 * construir os seus tipos contra esta realidade, nao contra a suposicao do UI-SPEC.
 */
@RestController
@RequestMapping("/api/v1/admin/rbac/auditoria")
@PreAuthorize("hasAuthority('rbac:manage')")
@RequiredArgsConstructor
public class AuditoriaRbacController {

    private final AuditoriaRbacService auditoriaRbacService;

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    /**
     * {@code GET /api/v1/admin/rbac/auditoria} (AUDT-03, Decisao 5): pagina o historico de
     * eventos RBAC do escritorio do principal autenticado. Bounds copiados verbatim de
     * {@link NotificacaoController#listar} -- mesma mensagem, mesmo limite de {@code size}
     * (1..100), para que um chamador nao consiga pedir uma pagina sem limite (T-128-35).
     */
    @GetMapping("")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) UUID utilizadorAlvoId,
            @RequestParam(required = false) UUID papelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().body(Map.of("message", "page deve ser >= 0 e size deve estar entre 1 e 100"));
        }
        Pageable pageable = PageRequest.of(page, size);
        UserPrincipal principal = getPrincipal();
        Page<AuditoriaRbacEntradaDto> pageResult = auditoriaRbacService.listar(
                principal.getTenantId(), utilizadorAlvoId, papelId, pageable);
        return ResponseEntity.ok(Map.of(
                "content", pageResult.getContent(),
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages(),
                "page", pageResult.getNumber(),
                "size", pageResult.getSize()
        ));
    }
}
