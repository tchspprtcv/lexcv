package com.lexcv.services;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalService;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalTransacoes;
import com.lexcv.services.fiscal.FaturacaoSubscricaoEscritorioService;
import com.lexcv.services.fiscal.PlatformFaturacaoConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaturacaoSubscricaoEscritorioServiceTest {

    @Mock
    private PlatformFaturacaoConfigService platformConfigService;

    @Mock
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Mock
    private DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;

    @Mock
    private ComunicacaoFiscalRepository comunicacaoFiscalRepository;

    @Mock
    private EntregaEmailFiscalRepository entregaEmailFiscalRepository;

    @Mock
    private DocumentoFiscalXmlRepository documentoFiscalXmlRepository;

    @Mock
    private DescargaDocumentoFiscalService descargaDocumentoFiscalService;

    @Mock
    private EmailProperties emailProperties;

    private FaturacaoSubscricaoEscritorioService service;

    private UUID platformTenantId;
    private UUID officeTenantId;
    private Tenant platformTenant;

    @BeforeEach
    void setUp() {
        platformTenantId = UUID.randomUUID();
        officeTenantId = UUID.randomUUID();
        platformTenant = Tenant.builder().id(platformTenantId).nome("LexCV").build();

        lenient().when(platformConfigService.obterTenantPlataforma()).thenReturn(platformTenant);
        lenient().when(emailProperties.configurado()).thenReturn(false);

        service = new FaturacaoSubscricaoEscritorioService(
                platformConfigService,
                documentoFiscalRepository,
                documentoFiscalLinhaRepository,
                comunicacaoFiscalRepository,
                entregaEmailFiscalRepository,
                documentoFiscalXmlRepository,
                descargaDocumentoFiscalService,
                emailProperties
        );
    }

    @Test
    void listarSubscricoesFiltraPorAdquirenteEPlataforma() {
        DocumentoFiscal doc = DocumentoFiscal.builder()
                .id(UUID.randomUUID())
                .tenantId(platformTenantId)
                .adquirenteTenantId(officeTenantId)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO)
                .numeroFormatado("FR-2026-SIM/1")
                .dataEmissao(LocalDate.of(2026, 10, 7))
                .totalDocumento(new BigDecimal("50000.00"))
                .build();

        Page<DocumentoFiscal> page = new PageImpl<>(List.of(doc));
        when(documentoFiscalRepository.findByAdquirenteTenantIdAndTenantIdOrderByDataEmissaoDescNumeroDesc(
                eq(officeTenantId), eq(platformTenantId), any(PageRequest.class))).thenReturn(page);
        when(comunicacaoFiscalRepository.findByTenantIdAndDocumentoFiscalIdIn(eq(platformTenantId), any())).thenReturn(Collections.emptyList());
        when(entregaEmailFiscalRepository.findByTenantIdAndDocumentoFiscalIdIn(eq(platformTenantId), any())).thenReturn(Collections.emptyList());

        Page<DocumentoFiscalResumoResponse> result = service.listar(officeTenantId, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("FR-2026-SIM/1", result.getContent().get(0).numeroFormatado());
    }

    @Test
    void detalheSubscricaoSucesso() {
        UUID docId = UUID.randomUUID();
        DocumentoFiscal doc = DocumentoFiscal.builder()
                .id(docId)
                .tenantId(platformTenantId)
                .adquirenteTenantId(officeTenantId)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO)
                .numeroFormatado("FR-2026-SIM/1")
                .dataEmissao(LocalDate.of(2026, 10, 7))
                .totalDocumento(new BigDecimal("50000.00"))
                .build();

        when(documentoFiscalRepository.findByIdAndAdquirenteTenantIdAndTenantId(docId, officeTenantId, platformTenantId))
                .thenReturn(Optional.of(doc));
        when(documentoFiscalLinhaRepository.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(platformTenantId, docId))
                .thenReturn(Collections.emptyList());
        when(comunicacaoFiscalRepository.findByTenantIdAndDocumentoFiscalId(platformTenantId, docId))
                .thenReturn(Optional.empty());
        when(documentoFiscalRepository.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(platformTenantId, docId))
                .thenReturn(Collections.emptyList());
        when(entregaEmailFiscalRepository.findByTenantIdAndDocumentoFiscalId(platformTenantId, docId))
                .thenReturn(Optional.empty());

        DocumentoFiscalDetalheResponse detalhe = service.detalhe(officeTenantId, docId);

        assertNotNull(detalhe);
        assertEquals("FR-2026-SIM/1", detalhe.numeroFormatado());
    }

    @Test
    void detalheSubscricaoDeOutroEscritorioLanca404() {
        UUID docId = UUID.randomUUID();
        when(documentoFiscalRepository.findByIdAndAdquirenteTenantIdAndTenantId(docId, officeTenantId, platformTenantId))
                .thenReturn(Optional.empty());

        assertThrows(RecusaFiscalException.class, () -> service.detalhe(officeTenantId, docId));
    }

    @Test
    void descarregarPdfValidaAdquirenteAntesDeChamarServico() {
        UUID docId = UUID.randomUUID();
        DocumentoFiscal doc = DocumentoFiscal.builder().id(docId).build();
        when(documentoFiscalRepository.findByIdAndAdquirenteTenantIdAndTenantId(docId, officeTenantId, platformTenantId))
                .thenReturn(Optional.of(doc));

        UserPrincipal autor = mock(UserPrincipal.class);
        DescargaDocumentoFiscalService.DescargaPdf pdfMock = new DescargaDocumentoFiscalService.DescargaPdf("https://url", "FR-1.pdf", 300L);
        when(descargaDocumentoFiscalService.descarregarPdf(platformTenantId, autor, docId)).thenReturn(pdfMock);

        DescargaDocumentoFiscalService.DescargaPdf result = service.descarregarPdf(officeTenantId, autor, docId);

        assertNotNull(result);
        assertEquals("FR-1.pdf", result.nomeFicheiro());
        verify(descargaDocumentoFiscalService).descarregarPdf(platformTenantId, autor, docId);
    }
}
