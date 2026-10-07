package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.dtos.SerieFiscalResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.SerieFiscalRepository;
import com.lexcv.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Phase 138 (SUBS-01, SUBS-05): Serviço de configuração fiscal e séries da plataforma LexCV.
 * Opera sobre a tenant reservada "LexCV", permitindo a PLATAFORMA_ADMIN configurar os dados
 * de emitente da plataforma e gerir as séries isoladas de subscrição.
 */
@Service
@RequiredArgsConstructor
public class PlatformFaturacaoConfigService {

    public static final String NOME_TENANT_PLATAFORMA = "LexCV";

    private final TenantRepository tenantRepository;
    private final ConfiguracaoFiscalService configuracaoFiscalService;
    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final SerieFiscalRepository serieFiscalRepository;

    public Tenant obterTenantPlataforma() {
        return tenantRepository.findFirstByNome(NOME_TENANT_PLATAFORMA)
                .orElseThrow(() -> new IllegalStateException("Tenant reservada '" + NOME_TENANT_PLATAFORMA + "' não encontrada"));
    }

    public UUID obterTenantPlataformaId() {
        return obterTenantPlataforma().getId();
    }

    @Transactional(readOnly = true)
    public ConfiguracaoFiscalResponse obterConfiguracao() {
        UUID platformTenantId = obterTenantPlataformaId();
        return configuracaoFiscalService.obter(platformTenantId);
    }

    @Transactional(readOnly = true)
    public List<SerieFiscalResponse> listarSeries() {
        UUID platformTenantId = obterTenantPlataformaId();
        return configuracaoFiscalService.listarSeries(platformTenantId);
    }

    @Transactional
    public ConfiguracaoFiscalResponse guardarConfiguracao(UserPrincipal autor, ConfiguracaoFiscalRequest request) {
        UUID platformTenantId = obterTenantPlataformaId();
        ConfiguracaoFiscalResponse response = configuracaoFiscalService.guardar(platformTenantId, autor, request);
        // Ativa automaticamente a faturação da plataforma se completa
        if (response.completa() && !response.ativa()) {
            response = configuracaoFiscalService.ativar(platformTenantId, autor);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public boolean prontaParaEmitir() {
        UUID platformTenantId = obterTenantPlataformaId();
        ConfiguracaoFiscal config = configuracaoFiscalRepository.findByTenantId(platformTenantId).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getAtiva())) {
            return false;
        }
        return config.completa();
    }

    public void validarProntaParaEmitir() {
        if (!prontaParaEmitir()) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_INCOMPLETA",
                    "A configuração fiscal da plataforma LexCV está incompleta ou inativa. Configure os dados fiscais em /plataforma/faturacao.");
        }
    }
}
