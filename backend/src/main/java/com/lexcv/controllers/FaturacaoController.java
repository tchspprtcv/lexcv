package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.dtos.EmailAutomaticoRequest;
import com.lexcv.dtos.MotivoIsencaoResponse;
import com.lexcv.dtos.SerieFiscalResponse;
import com.lexcv.services.fiscal.ConfiguracaoFiscalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/faturacao} (Phase 133, Plano 05, CFG-01/CFG-02/CFG-06): leitura e alteracao da
 * configuracao fiscal do escritorio do chamador -- dados fiscais, ativacao/desativacao, envio
 * automatico por email, series e lista oficial de motivos de isencao de IVA.
 *
 * <p><b>Controller dedicado de proposito:</b> nao se faz crescer {@link ResourceController}; a
 * superficie fiscal vive isolada aqui.
 *
 * <p><b>Gate apenas de CLASSE</b> ({@code hasAuthority('financeiro:manage')}, alinhado com
 * {@code hasScopedPermission(perms, "financeiro", "manage")} no frontend -- correspondencia exata,
 * sem alargamento por fallback): uma anotacao de autorizacao de metodo SUBSTITUIRIA -- nunca somaria --
 * o de classe (ver {@link OfficeRolesController}), por isso nenhum handler aqui tem anotacao
 * propria; qualquer handler acrescentado no futuro fica coberto automaticamente. Esta fase nao
 * cria permissao nova: reutiliza {@code financeiro:manage}. O {@code PLATAFORMA_ADMIN} do tenant
 * reservado nao detem permissoes de ambito, por isso e recusado por construcao.
 *
 * <p><b>Tenant exclusivamente do principal autenticado</b>, nunca do path, query ou corpo -- os
 * handlers nao recebem {@code UUID} nem variaveis de path ou parametros de query.
 *
 * <p><b>Sem {@code @Transactional} aqui:</b> o {@link ConfiguracaoFiscalService} detem a fronteira
 * transacional; as recusas sao excecoes ({@code RecusaFiscalException}) -- rollback e nenhum
 * evento de auditoria -- mapeadas globalmente para {@code {message, code, campo?}}.
 */
@RestController
@RequestMapping("/api/v1/faturacao")
@PreAuthorize("hasAuthority('financeiro:manage')")
@RequiredArgsConstructor
@Slf4j
public class FaturacaoController {

    private final ConfiguracaoFiscalService configuracaoFiscalService;

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    private UUID getTenantId() {
        return getPrincipal().getTenantId();
    }

    @GetMapping("/configuracao")
    public ResponseEntity<ConfiguracaoFiscalResponse> getConfiguracao() {
        return ResponseEntity.ok(configuracaoFiscalService.obter(getTenantId()));
    }

    @PutMapping("/configuracao")
    public ResponseEntity<ConfiguracaoFiscalResponse> putConfiguracao(@Valid @RequestBody ConfiguracaoFiscalRequest req) {
        return ResponseEntity.ok(configuracaoFiscalService.guardar(getTenantId(), getPrincipal(), req));
    }

    @PostMapping("/ativar")
    public ResponseEntity<ConfiguracaoFiscalResponse> postAtivar() {
        return ResponseEntity.ok(configuracaoFiscalService.ativar(getTenantId(), getPrincipal()));
    }

    @PostMapping("/desativar")
    public ResponseEntity<ConfiguracaoFiscalResponse> postDesativar() {
        return ResponseEntity.ok(configuracaoFiscalService.desativar(getTenantId(), getPrincipal()));
    }

    @PutMapping("/email-automatico")
    public ResponseEntity<ConfiguracaoFiscalResponse> putEmailAutomatico(@Valid @RequestBody EmailAutomaticoRequest req) {
        return ResponseEntity.ok(configuracaoFiscalService.definirEmailAutomatico(getTenantId(), getPrincipal(), req));
    }

    @GetMapping("/series")
    public ResponseEntity<List<SerieFiscalResponse>> getSeries() {
        return ResponseEntity.ok(configuracaoFiscalService.listarSeries(getTenantId()));
    }

    @GetMapping("/motivos-isencao")
    public ResponseEntity<List<MotivoIsencaoResponse>> getMotivosIsencao() {
        return ResponseEntity.ok(MotivoIsencaoResponse.todos());
    }
}
