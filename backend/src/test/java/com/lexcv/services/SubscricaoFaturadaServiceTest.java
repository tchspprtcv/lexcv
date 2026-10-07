package com.lexcv.services;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RegistarPagamentoSubscricaoRequest;
import com.lexcv.dtos.SubscricaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import com.lexcv.services.fiscal.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscricaoFaturadaServiceTest {

    @Mock
    private PlatformFaturacaoConfigService platformConfigService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ConfiguracaoFiscalRepository configuracaoFiscalRepository;

    @Mock
    private PagamentoSubscricaoRepository pagamentoSubscricaoRepository;

    @Mock
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Mock
    private DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;

    @Mock
    private ComunicacaoFiscalRepository comunicacaoFiscalRepository;

    @Mock
    private EntregaEmailFiscalRepository entregaEmailFiscalRepository;

    @Mock
    private NumeracaoService numeracaoService;

    @Mock
    private ParametroFiscalService parametroFiscalService;

    @Mock
    private AuditoriaFiscalService auditoriaFiscalService;

    @Mock
    private Clock clock;

    @InjectMocks
    private SubscricaoFaturadaService subscricaoFaturadaService;

    private UUID platformTenantId;
    private UUID officeTenantId;
    private Tenant platformTenant;
    private Tenant officeTenant;
    private ConfiguracaoFiscal platformConfig;

    @BeforeEach
    void setUp() {
        platformTenantId = UUID.randomUUID();
        officeTenantId = UUID.randomUUID();

        platformTenant = Tenant.builder()
                .id(platformTenantId)
                .nome("LexCV")
                .build();

        officeTenant = Tenant.builder()
                .id(officeTenantId)
                .nome("Escritório Silva & Associados")
                .nif("211111111")
                .plano(TenantPlano.STANDARD)
                .build();

        platformConfig = ConfiguracaoFiscal.builder()
                .tenantId(platformTenantId)
                .nif("200000001")
                .firma("LexCV Platform Lda")
                .morada("Praia")
                .localidade("Praia")
                .regimeIva(RegimeIva.NORMAL)
                .emailContacto("faturacao@lexcv.cv")
                .telefoneContacto("2610000")
                .ativa(true)
                .build();

        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-10-07T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void emitirFaturaReciboSubscricaoSucesso() {
        when(platformConfigService.obterTenantPlataforma()).thenReturn(platformTenant);
        doNothing().when(platformConfigService).validarProntaParaEmitir();
        when(configuracaoFiscalRepository.findByTenantId(platformTenantId)).thenReturn(Optional.of(platformConfig));
        when(tenantRepository.findById(officeTenantId)).thenReturn(Optional.of(officeTenant));
        when(documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(eq(platformTenantId), any())).thenReturn(Optional.empty());

        UUID serieId = UUID.randomUUID();
        NumeroFiscalAtribuido numAtribuido = new NumeroFiscalAtribuido(serieId, "FR-2026-SIM", 2026, 1L, LocalDate.of(2026, 10, 7));
        when(numeracaoService.proximoNumero(eq(platformTenantId), eq(TipoDocumentoFiscal.FR), eq(AmbienteFiscal.SIMULADO))).thenReturn(numAtribuido);
        when(parametroFiscalService.valorVigenteHoje(CodigoParametroFiscal.IVA_TAXA_NORMAL)).thenReturn(new BigDecimal("15.0000"));

        when(pagamentoSubscricaoRepository.save(any(PagamentoSubscricao.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentoFiscalRepository.save(any(DocumentoFiscal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentoFiscalLinhaRepository.save(any(DocumentoFiscalLinha.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPrincipal autor = mock(UserPrincipal.class);
        when(autor.getUserId()).thenReturn(UUID.randomUUID());
        when(autor.getNome()).thenReturn("Admin Plataforma");

        RegistarPagamentoSubscricaoRequest req = new RegistarPagamentoSubscricaoRequest(
                officeTenantId,
                new BigDecimal("50000.00"),
                LocalDate.of(2026, 10, 7),
                "TRANSFERENCIA_BANCARIA",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 9, 30),
                "PROFESSIONAL",
                UUID.randomUUID()
        );

        SubscricaoFaturaResponse response = subscricaoFaturadaService.registarPagamentoFaturado(autor, req);

        assertNotNull(response);
        assertEquals(TipoDocumentoFiscal.FR, response.tipo());
        assertEquals("FR-2026-SIM/1", response.numeroFormatado());
        assertEquals(officeTenantId, response.adquirenteTenantId());
        assertEquals(new BigDecimal("50000.00"), response.totalDocumento());
        assertEquals(EstadoComunicacaoFiscal.PENDENTE, response.estadoComunicacao());

        verify(pagamentoSubscricaoRepository).save(any(PagamentoSubscricao.class));
        verify(documentoFiscalRepository).save(any(DocumentoFiscal.class));
        verify(documentoFiscalLinhaRepository).save(any(DocumentoFiscalLinha.class));
        verify(comunicacaoFiscalRepository).save(any(ComunicacaoFiscal.class));
        verify(auditoriaFiscalService).registarEmissao(eq(platformTenantId), eq(autor), any(), eq("FR-2026-SIM/1"));
    }

    @Test
    void recusaEmissaoSeDestinatarioForLexCV() {
        when(platformConfigService.obterTenantPlataforma()).thenReturn(platformTenant);
        doNothing().when(platformConfigService).validarProntaParaEmitir();
        when(configuracaoFiscalRepository.findByTenantId(platformTenantId)).thenReturn(Optional.of(platformConfig));

        UserPrincipal autor = mock(UserPrincipal.class);
        RegistarPagamentoSubscricaoRequest req = new RegistarPagamentoSubscricaoRequest(
                platformTenantId,
                new BigDecimal("10000.00"),
                LocalDate.of(2026, 10, 7),
                "TRANSFERENCIA_BANCARIA",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 9, 30),
                "ENTERPRISE",
                UUID.randomUUID()
        );

        assertThrows(RecusaFiscalException.class, () -> subscricaoFaturadaService.registarPagamentoFaturado(autor, req));
    }
}
