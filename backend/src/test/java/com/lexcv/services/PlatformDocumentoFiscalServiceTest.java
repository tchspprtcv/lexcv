package com.lexcv.services;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.Tenant;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformDocumentoFiscalServiceTest {

    @Mock
    private PlatformFaturacaoConfigService platformConfigService;

    @Mock
    private DocumentoFiscalService documentoFiscalService;

    @Mock
    private DescargaDocumentoFiscalService descargaDocumentoFiscalService;

    @InjectMocks
    private PlatformDocumentoFiscalService platformDocumentoFiscalService;

    private UUID platformTenantId;
    private Tenant platformTenant;

    @BeforeEach
    void setUp() {
        platformTenantId = UUID.randomUUID();
        platformTenant = Tenant.builder()
                .id(platformTenantId)
                .nome("LexCV")
                .build();
        when(platformConfigService.obterTenantPlataforma()).thenReturn(platformTenant);
    }

    @Test
    void listarDocumentosDelegaComTenantPlataforma() {
        Page<DocumentoFiscalResumoResponse> pageResult = new PageImpl<>(List.of());
        when(documentoFiscalService.listar(eq(platformTenantId), isNull(), eq(TipoDocumentoFiscal.FR), eq(EstadoComunicacaoFiscal.ACEITE_SIMULADO), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(pageResult);

        Page<DocumentoFiscalResumoResponse> result = platformDocumentoFiscalService.listarDocumentos(
                TipoDocumentoFiscal.FR, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, 0, 20);

        assertNotNull(result);
        verify(documentoFiscalService).listar(platformTenantId, null, TipoDocumentoFiscal.FR, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, 0, 20);
    }

    @Test
    void obterDocumentoDelegaComTenantPlataforma() {
        UUID docId = UUID.randomUUID();
        DocumentoFiscalDetalheResponse detalhe = mock(DocumentoFiscalDetalheResponse.class);
        when(documentoFiscalService.detalhe(platformTenantId, docId)).thenReturn(detalhe);

        DocumentoFiscalDetalheResponse result = platformDocumentoFiscalService.obterDocumento(docId);

        assertNotNull(result);
        assertEquals(detalhe, result);
        verify(documentoFiscalService).detalhe(platformTenantId, docId);
    }

    @Test
    void descarregarPdfDelegaComTenantPlataformaEAutor() {
        UUID docId = UUID.randomUUID();
        UserPrincipal autor = mock(UserPrincipal.class);
        DescargaDocumentoFiscalService.DescargaPdf pdf = new DescargaDocumentoFiscalService.DescargaPdf("https://minio/pdf", "FR-1.pdf", 300L);
        when(descargaDocumentoFiscalService.descarregarPdf(platformTenantId, autor, docId)).thenReturn(pdf);

        DescargaDocumentoFiscalService.DescargaPdf result = platformDocumentoFiscalService.descarregarPdf(autor, docId);

        assertNotNull(result);
        assertEquals("FR-1.pdf", result.nomeFicheiro());
        verify(descargaDocumentoFiscalService).descarregarPdf(platformTenantId, autor, docId);
    }

    @Test
    void descarregarXmlDelegaComTenantPlataformaEAutor() {
        UUID docId = UUID.randomUUID();
        UserPrincipal autor = mock(UserPrincipal.class);
        DescargaDocumentoFiscalTransacoes.XmlDescarregavel xml = new DescargaDocumentoFiscalTransacoes.XmlDescarregavel("<xml/>".getBytes(), "FR-1.xml");
        when(descargaDocumentoFiscalService.descarregarXml(platformTenantId, autor, docId)).thenReturn(xml);

        DescargaDocumentoFiscalTransacoes.XmlDescarregavel result = platformDocumentoFiscalService.descarregarXml(autor, docId);

        assertNotNull(result);
        assertEquals("FR-1.xml", result.nomeFicheiro());
        verify(descargaDocumentoFiscalService).descarregarXml(platformTenantId, autor, docId);
    }
}
