package com.lexcv.services;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.CriarNotaCreditoSubscricaoRequest;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscricaoNotaCreditoServiceTest {

    @Mock
    private PlatformFaturacaoConfigService platformConfigService;

    @Mock
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Mock
    private DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;

    @Mock
    private PagamentoSubscricaoRepository pagamentoSubscricaoRepository;

    @Mock
    private ComunicacaoFiscalRepository comunicacaoFiscalRepository;

    @Mock
    private NumeracaoService numeracaoService;

    @Mock
    private AuditoriaFiscalService auditoriaFiscalService;

    @Mock
    private Clock clock;

    @InjectMocks
    private SubscricaoNotaCreditoService subscricaoNotaCreditoService;

    private UUID platformTenantId;
    private UUID officeTenantId;
    private UUID frOrigemId;
    private Tenant platformTenant;
    private DocumentoFiscal frOrigem;

    @BeforeEach
    void setUp() {
        platformTenantId = UUID.randomUUID();
        officeTenantId = UUID.randomUUID();
        frOrigemId = UUID.randomUUID();

        platformTenant = Tenant.builder()
                .id(platformTenantId)
                .nome("LexCV")
                .build();

        frOrigem = DocumentoFiscal.builder()
                .id(frOrigemId)
                .tenantId(platformTenantId)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID())
                .serieCodigo("FR-2026-SIM")
                .ano(2026)
                .numero(1L)
                .numeroFormatado("FR-2026-SIM/1")
                .dataEmissao(LocalDate.of(2026, 10, 7))
                .emitenteNif("200000001")
                .emitenteFirma("LexCV Platform Lda")
                .emitenteMorada("Praia")
                .emitenteLocalidade("Praia")
                .emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("211111111")
                .adquirenteNome("Escritório Silva")
                .adquirenteMorada("Praia")
                .adquirenteLocalidade("Praia")
                .adquirenteTenantId(officeTenantId)
                .metodoPagamento("TRANSFERENCIA_BANCARIA")
                .meioPagamentoCodigo("TB")
                .moeda("CVE")
                .taxaIva(new BigDecimal("15.0000"))
                .totalBase(new BigDecimal("43478.26"))
                .totalIva(new BigDecimal("6521.74"))
                .totalRetencao(BigDecimal.ZERO)
                .totalDocumento(new BigDecimal("50000.00"))
                .valorLiquido(new BigDecimal("50000.00"))
                .build();

        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-10-07T14:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void emitirNotaCreditoSucesso() {
        when(platformConfigService.obterTenantPlataforma()).thenReturn(platformTenant);
        when(documentoFiscalRepository.findByIdAndTenantId(frOrigemId, platformTenantId)).thenReturn(Optional.of(frOrigem));
        when(documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(eq(platformTenantId), any())).thenReturn(Optional.empty());
        when(documentoFiscalRepository.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(platformTenantId, frOrigemId))
                .thenReturn(Collections.emptyList());

        UUID ncSerieId = UUID.randomUUID();
        NumeroFiscalAtribuido numAtribuido = new NumeroFiscalAtribuido(ncSerieId, "NC-2026-SIM", 2026, 1L, LocalDate.of(2026, 10, 7));
        when(numeracaoService.proximoNumero(platformTenantId, TipoDocumentoFiscal.NC, AmbienteFiscal.SIMULADO)).thenReturn(numAtribuido);

        when(pagamentoSubscricaoRepository.save(any(PagamentoSubscricao.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentoFiscalRepository.save(any(DocumentoFiscal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentoFiscalLinhaRepository.save(any(DocumentoFiscalLinha.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPrincipal autor = mock(UserPrincipal.class);
        when(autor.getUserId()).thenReturn(UUID.randomUUID());
        when(autor.getNome()).thenReturn("Admin Plataforma");

        CriarNotaCreditoSubscricaoRequest req = new CriarNotaCreditoSubscricaoRequest(
                MotivoNotaCredito.ANULACAO_TOTAL,
                "Cancelamento de subscrição por lapso",
                new BigDecimal("50000.00"),
                UUID.randomUUID()
        );

        SubscricaoFaturaResponse response = subscricaoNotaCreditoService.emitirNotaCredito(frOrigemId, autor, req);

        assertNotNull(response);
        assertEquals(TipoDocumentoFiscal.NC, response.tipo());
        assertEquals("NC-2026-SIM/1", response.numeroFormatado());
        assertEquals(officeTenantId, response.adquirenteTenantId());
        assertEquals(new BigDecimal("50000.00"), response.totalDocumento());
        assertEquals(EstadoComunicacaoFiscal.PENDENTE, response.estadoComunicacao());

        verify(pagamentoSubscricaoRepository).save(any(PagamentoSubscricao.class));
        verify(documentoFiscalRepository).save(any(DocumentoFiscal.class));
        verify(documentoFiscalLinhaRepository).save(any(DocumentoFiscalLinha.class));
        verify(comunicacaoFiscalRepository).save(any(ComunicacaoFiscal.class));
        verify(auditoriaFiscalService).registarEmissaoNotaCredito(eq(platformTenantId), eq(autor), any(), eq("NC-2026-SIM/1"), eq("FR-2026-SIM/1"));
    }

    @Test
    void recusaSeValorCreditoExcederSaldo() {
        when(platformConfigService.obterTenantPlataforma()).thenReturn(platformTenant);
        when(documentoFiscalRepository.findByIdAndTenantId(frOrigemId, platformTenantId)).thenReturn(Optional.of(frOrigem));
        when(documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(eq(platformTenantId), any())).thenReturn(Optional.empty());
        when(documentoFiscalRepository.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(platformTenantId, frOrigemId))
                .thenReturn(Collections.emptyList());

        UserPrincipal autor = mock(UserPrincipal.class);
        CriarNotaCreditoSubscricaoRequest req = new CriarNotaCreditoSubscricaoRequest(
                MotivoNotaCredito.CORRECAO_VALOR,
                "Valor excessivo",
                new BigDecimal("60000.00"),
                UUID.randomUUID()
        );

        assertThrows(RecusaFiscalException.class, () -> subscricaoNotaCreditoService.emitirNotaCredito(frOrigemId, autor, req));
    }
}
