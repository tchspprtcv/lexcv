package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ComunicacaoFiscalResumo;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.dtos.EntregaEmailResumo;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.Tenant;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.repositories.EntregaEmailFiscalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 138 (SUBS-04): Serviço de consulta e descarga de faturas de subscrição emitidas pela plataforma LexCV
 * para o escritório adquirente autenticado.
 *
 * <p><b>Isolamento multi-tenant estrito:</b>
 * Cada consulta filtra obrigatoriamente {@code adquirente_tenant_id = adquirenteTenantId} E {@code tenant_id = lexcvTenantId}.
 * Um escritório nunca tem acesso a documentos de outros escritórios nem a documentos emitidos pelo próprio escritório
 * através deste endpoint.
 */
@Service
@Slf4j
public class FaturacaoSubscricaoEscritorioService {

    private static final String MSG_NAO_ENCONTRADO = "Documento fiscal não encontrado.";
    private static final String CODIGO_NAO_ENCONTRADO = "DOCUMENTO_FISCAL_NAO_ENCONTRADO";

    private final PlatformFaturacaoConfigService platformConfigService;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;
    private final ComunicacaoFiscalRepository comunicacaoFiscalRepository;
    private final EntregaEmailFiscalRepository entregaEmailFiscalRepository;
    private final DocumentoFiscalXmlRepository documentoFiscalXmlRepository;
    private final DescargaDocumentoFiscalService descargaDocumentoFiscalService;
    private final boolean smtpConfigurado;

    public FaturacaoSubscricaoEscritorioService(PlatformFaturacaoConfigService platformConfigService,
                                               DocumentoFiscalRepository documentoFiscalRepository,
                                               DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository,
                                               ComunicacaoFiscalRepository comunicacaoFiscalRepository,
                                               EntregaEmailFiscalRepository entregaEmailFiscalRepository,
                                               DocumentoFiscalXmlRepository documentoFiscalXmlRepository,
                                               DescargaDocumentoFiscalService descargaDocumentoFiscalService,
                                               EmailProperties emailProperties) {
        this.platformConfigService = platformConfigService;
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.documentoFiscalLinhaRepository = documentoFiscalLinhaRepository;
        this.comunicacaoFiscalRepository = comunicacaoFiscalRepository;
        this.entregaEmailFiscalRepository = entregaEmailFiscalRepository;
        this.documentoFiscalXmlRepository = documentoFiscalXmlRepository;
        this.descargaDocumentoFiscalService = descargaDocumentoFiscalService;
        this.smtpConfigurado = emailProperties.configurado();
    }

    @Transactional(readOnly = true)
    public Page<DocumentoFiscalResumoResponse> listar(UUID adquirenteTenantId, int page, int size) {
        Tenant lexcvTenant = platformConfigService.obterTenantPlataforma();
        UUID lexcvTenantId = lexcvTenant.getId();

        Page<DocumentoFiscal> documentos = documentoFiscalRepository
                .findByAdquirenteTenantIdAndTenantIdOrderByDataEmissaoDescNumeroDesc(
                        adquirenteTenantId, lexcvTenantId, PageRequest.of(page, size));

        if (documentos.isEmpty()) {
            return documentos.map(d -> DocumentoFiscalResumoResponse.de(d, null));
        }

        List<UUID> ids = documentos.getContent().stream().map(DocumentoFiscal::getId).toList();

        Map<UUID, EstadoComunicacaoFiscal> estados = comunicacaoFiscalRepository
                .findByTenantIdAndDocumentoFiscalIdIn(lexcvTenantId, ids).stream()
                .collect(Collectors.toMap(ComunicacaoFiscal::getDocumentoFiscalId, ComunicacaoFiscal::getEstado,
                        (a, b) -> a));

        Set<UUID> origens = documentos.getContent().stream()
                .map(DocumentoFiscal::getDocumentoOrigemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> numerosOrigem = origens.isEmpty()
                ? Map.of()
                : documentoFiscalRepository.findByTenantIdAndIdIn(lexcvTenantId, origens).stream()
                .collect(Collectors.toMap(DocumentoFiscal::getId, DocumentoFiscal::getNumeroFormatado,
                        (a, b) -> a));

        Map<UUID, String> entregas = entregaEmailFiscalRepository.findByTenantIdAndDocumentoFiscalIdIn(lexcvTenantId, ids)
                .stream()
                .filter(e -> e.getEstado() != null)
                .collect(Collectors.toMap(EntregaEmailFiscal::getDocumentoFiscalId,
                        e -> RegrasEntregaEmail.estadoApresentado(e.getEstado(), smtpConfigurado), (a, b) -> a));

        return documentos.map(d -> DocumentoFiscalResumoResponse.de(d, estados.get(d.getId()),
                d.getDocumentoOrigemId() == null ? null : numerosOrigem.get(d.getDocumentoOrigemId()),
                entregas.get(d.getId())));
    }

    @Transactional(readOnly = true)
    public DocumentoFiscalDetalheResponse detalhe(UUID adquirenteTenantId, UUID documentoId) {
        Tenant lexcvTenant = platformConfigService.obterTenantPlataforma();
        UUID lexcvTenantId = lexcvTenant.getId();

        DocumentoFiscal doc = documentoFiscalRepository
                .findByIdAndAdquirenteTenantIdAndTenantId(documentoId, adquirenteTenantId, lexcvTenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, CODIGO_NAO_ENCONTRADO, MSG_NAO_ENCONTRADO));

        List<DocumentoFiscalLinha> linhas = documentoFiscalLinhaRepository
                .findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(lexcvTenantId, doc.getId());

        ComunicacaoFiscal comunicacao = comunicacaoFiscalRepository
                .findByTenantIdAndDocumentoFiscalId(lexcvTenantId, doc.getId())
                .orElse(null);
        EstadoComunicacaoFiscal estado = comunicacao == null ? null : comunicacao.getEstado();

        ComunicacaoFiscalResumo resumo = comunicacao == null ? null : ComunicacaoFiscalResumo.de(comunicacao,
                documentoFiscalXmlRepository.findByTenantIdAndDocumentoFiscalId(lexcvTenantId, doc.getId())
                        .map(DocumentoFiscalXml::getIud)
                        .orElse(null));

        DocumentoFiscalRef origem = null;
        List<DocumentoFiscal> notasCredito = List.of();
        if (doc.getTipo() == TipoDocumentoFiscal.FR) {
            notasCredito = documentoFiscalRepository
                    .findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(lexcvTenantId, doc.getId());
        } else if (doc.getDocumentoOrigemId() != null) {
            origem = documentoFiscalRepository.findByIdAndTenantId(doc.getDocumentoOrigemId(), lexcvTenantId)
                    .map(DocumentoFiscalRef::de)
                    .orElse(null);
        }

        EntregaEmailResumo entrega = entregaEmailFiscalRepository
                .findByTenantIdAndDocumentoFiscalId(lexcvTenantId, doc.getId())
                .map(linha -> EntregaEmailResumo.de(linha, smtpConfigurado, true, estado, doc.getAdquirenteNif()))
                .orElse(null);

        return DocumentoFiscalDetalheResponse.de(doc, linhas, estado, origem, notasCredito, resumo, entrega);
    }

    public DescargaDocumentoFiscalService.DescargaPdf descarregarPdf(UUID adquirenteTenantId, UserPrincipal autor, UUID documentoId) {
        Tenant lexcvTenant = platformConfigService.obterTenantPlataforma();
        UUID lexcvTenantId = lexcvTenant.getId();

        // Valida que o documento pertence ao adquirente sob a plataforma
        documentoFiscalRepository.findByIdAndAdquirenteTenantIdAndTenantId(documentoId, adquirenteTenantId, lexcvTenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, CODIGO_NAO_ENCONTRADO, MSG_NAO_ENCONTRADO));

        return descargaDocumentoFiscalService.descarregarPdf(lexcvTenantId, autor, documentoId);
    }

    public DescargaDocumentoFiscalTransacoes.XmlDescarregavel descarregarXml(UUID adquirenteTenantId, UserPrincipal autor, UUID documentoId) {
        Tenant lexcvTenant = platformConfigService.obterTenantPlataforma();
        UUID lexcvTenantId = lexcvTenant.getId();

        // Valida que o documento pertence ao adquirente sob a plataforma
        documentoFiscalRepository.findByIdAndAdquirenteTenantIdAndTenantId(documentoId, adquirenteTenantId, lexcvTenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, CODIGO_NAO_ENCONTRADO, MSG_NAO_ENCONTRADO));

        return descargaDocumentoFiscalService.descarregarXml(lexcvTenantId, autor, documentoId);
    }
}
