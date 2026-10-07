package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.Tenant;
import com.lexcv.models.TipoDocumentoFiscal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 138 (SUBS-03): Serviço de consulta e descarga de documentos fiscais emitidos pela plataforma LexCV.
 */
@Service
@RequiredArgsConstructor
public class PlatformDocumentoFiscalService {

    private final PlatformFaturacaoConfigService platformConfigService;
    private final DocumentoFiscalService documentoFiscalService;
    private final DescargaDocumentoFiscalService descargaDocumentoFiscalService;

    public Page<DocumentoFiscalResumoResponse> listarDocumentos(
            TipoDocumentoFiscal tipo,
            EstadoComunicacaoFiscal estado,
            LocalDate de,
            LocalDate ate,
            int page,
            int size) {
        Tenant lexcv = platformConfigService.obterTenantPlataforma();
        return documentoFiscalService.listar(lexcv.getId(), null, tipo, estado, de, ate, page, size);
    }

    public DocumentoFiscalDetalheResponse obterDocumento(UUID id) {
        Tenant lexcv = platformConfigService.obterTenantPlataforma();
        return documentoFiscalService.detalhe(lexcv.getId(), id);
    }

    public DescargaDocumentoFiscalService.DescargaPdf descarregarPdf(UserPrincipal autor, UUID id) {
        Tenant lexcv = platformConfigService.obterTenantPlataforma();
        return descargaDocumentoFiscalService.descarregarPdf(lexcv.getId(), autor, id);
    }

    public DescargaDocumentoFiscalTransacoes.XmlDescarregavel descarregarXml(UserPrincipal autor, UUID id) {
        Tenant lexcv = platformConfigService.obterTenantPlataforma();
        return descargaDocumentoFiscalService.descarregarXml(lexcv.getId(), autor, id);
    }
}
