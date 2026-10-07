package com.lexcv.services;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.SerieFiscalRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.services.fiscal.ConfiguracaoFiscalService;
import com.lexcv.services.fiscal.PlatformFaturacaoConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformFaturacaoConfigServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ConfiguracaoFiscalService configuracaoFiscalService;

    @Mock
    private ConfiguracaoFiscalRepository configuracaoFiscalRepository;

    @Mock
    private SerieFiscalRepository serieFiscalRepository;

    @InjectMocks
    private PlatformFaturacaoConfigService platformFaturacaoConfigService;

    private Tenant lexcvTenant;
    private UUID platformTenantId;

    @BeforeEach
    void setUp() {
        platformTenantId = UUID.randomUUID();
        lexcvTenant = Tenant.builder()
                .id(platformTenantId)
                .nome("LexCV")
                .build();
    }

    @Test
    void obterTenantPlataformaSucesso() {
        when(tenantRepository.findByNome("LexCV")).thenReturn(Optional.of(lexcvTenant));

        Tenant t = platformFaturacaoConfigService.obterTenantPlataforma();
        assertNotNull(t);
        assertEquals(platformTenantId, t.getId());
    }

    @Test
    void obterTenantPlataformaNaoEncontradoLancaExcecao() {
        when(tenantRepository.findByNome("LexCV")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> platformFaturacaoConfigService.obterTenantPlataforma());
    }

    @Test
    void guardarConfiguracaoDelegaEAtivaSeCompleta() {
        when(tenantRepository.findByNome("LexCV")).thenReturn(Optional.of(lexcvTenant));

        UserPrincipal autor = mock(UserPrincipal.class);
        ConfiguracaoFiscalRequest req = new ConfiguracaoFiscalRequest(
                "200000001", "LexCV Platform Lda", "Praia", "Praia",
                "faturacao@lexcv.cv", "2610000", RegimeIva.GERAL, null);

        ConfiguracaoFiscalResponse resInativa = new ConfiguracaoFiscalResponse(
                true, "200000001", "LexCV Platform Lda", "Praia", "Praia", "Cabo Verde",
                "faturacao@lexcv.cv", "2610000", RegimeIva.GERAL, null,
                true, false, false, false, false, false, null, null, false);

        ConfiguracaoFiscalResponse resAtiva = new ConfiguracaoFiscalResponse(
                true, "200000001", "LexCV Platform Lda", "Praia", "Praia", "Cabo Verde",
                "faturacao@lexcv.cv", "2610000", RegimeIva.GERAL, null,
                true, true, false, true, true, false, null, null, false);

        when(configuracaoFiscalService.guardar(eq(platformTenantId), eq(autor), any())).thenReturn(resInativa);
        when(configuracaoFiscalService.ativar(eq(platformTenantId), eq(autor))).thenReturn(resAtiva);

        ConfiguracaoFiscalResponse result = platformFaturacaoConfigService.guardarConfiguracao(autor, req);
        assertNotNull(result);
        assertTrue(result.ativa());
        verify(configuracaoFiscalService).ativar(platformTenantId, autor);
    }

    @Test
    void validarProntaParaEmitirLancaRecusaSeIncompleta() {
        when(tenantRepository.findByNome("LexCV")).thenReturn(Optional.of(lexcvTenant));
        when(configuracaoFiscalRepository.findByTenantId(platformTenantId)).thenReturn(Optional.empty());

        assertThrows(RecusaFiscalException.class, () -> platformFaturacaoConfigService.validarProntaParaEmitir());
    }

    @Test
    void validarProntaParaEmitirPassaSeAtivaECompleta() {
        when(tenantRepository.findByNome("LexCV")).thenReturn(Optional.of(lexcvTenant));

        ConfiguracaoFiscal config = ConfiguracaoFiscal.builder()
                .tenantId(platformTenantId)
                .nif("200000001")
                .firma("LexCV Platform Lda")
                .morada("Praia")
                .localidade("Praia")
                .regimeIva(RegimeIva.GERAL)
                .emailContacto("faturacao@lexcv.cv")
                .telefoneContacto("2610000")
                .ativa(true)
                .build();

        when(configuracaoFiscalRepository.findByTenantId(platformTenantId)).thenReturn(Optional.of(config));

        assertDoesNotThrow(() -> platformFaturacaoConfigService.validarProntaParaEmitir());
    }
}
