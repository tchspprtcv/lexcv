package com.lexcv.services.fiscal;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalPdf;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalPdfRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-01): as transações CURTAS do PDF fiscal, num bean separado para que
 * {@link PdfDocumentoFiscalService} as chame através do proxy do Spring e não tenha ele próprio
 * {@code @Transactional}. Nenhum método daqui renderiza nem fala com o MinIO: ler (tx só de
 * leitura) -> [render + upload fora de transação] -> registar (tx curta).
 *
 * <p>Todas as leituras e a escrita são filtradas pelo {@code tenantId} recebido; o documento de
 * origem de uma NC também é procurado no mesmo tenant (T-137-37).
 */
@Service
public class PdfDocumentoFiscalTransacoes {

    /**
     * Fotografia necessária para o PDF.
     *
     * @param iud           o IUD do XML do próprio documento; vazio enquanto não houver XML
     * @param origem        NC: a FR de origem (mesmo tenant); nulo numa FR
     * @param pdfExistente  a linha do PDF já guardado, se existir
     */
    public record DadosParaPdf(DocumentoFiscal documento, List<DocumentoFiscalLinha> linhas, Optional<String> iud,
                               DocumentoFiscal origem, Optional<DocumentoFiscalPdf> pdfExistente) {
        public DadosParaPdf {
            linhas = linhas == null ? List.of() : List.copyOf(linhas);
        }
    }

    private final DocumentoFiscalRepository documentoRepository;
    private final DocumentoFiscalLinhaRepository linhaRepository;
    private final DocumentoFiscalXmlRepository xmlRepository;
    private final DocumentoFiscalPdfRepository pdfRepository;

    public PdfDocumentoFiscalTransacoes(DocumentoFiscalRepository documentoRepository,
                                        DocumentoFiscalLinhaRepository linhaRepository,
                                        DocumentoFiscalXmlRepository xmlRepository,
                                        DocumentoFiscalPdfRepository pdfRepository) {
        this.documentoRepository = documentoRepository;
        this.linhaRepository = linhaRepository;
        this.xmlRepository = xmlRepository;
        this.pdfRepository = pdfRepository;
    }

    @Transactional(readOnly = true)
    public Optional<DocumentoFiscalPdf> pdfExistente(UUID tenantId, UUID documentoId) {
        return pdfRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId);
    }

    /** Vazio se o documento não existe neste tenant. */
    @Transactional(readOnly = true)
    public Optional<DadosParaPdf> carregarDados(UUID tenantId, UUID documentoId) {
        Optional<DocumentoFiscal> encontrado = documentoRepository.findByIdAndTenantId(documentoId, tenantId);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        DocumentoFiscal documento = encontrado.get();
        List<DocumentoFiscalLinha> linhas =
                linhaRepository.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(tenantId, documentoId);
        Optional<String> iud = xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)
                .map(DocumentoFiscalXml::getIud);
        DocumentoFiscal origem = documento.getDocumentoOrigemId() == null ? null
                : documentoRepository.findByIdAndTenantId(documento.getDocumentoOrigemId(), tenantId).orElse(null);
        Optional<DocumentoFiscalPdf> pdf = pdfRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId);
        return Optional.of(new DadosParaPdf(documento, linhas, iud, origem, pdf));
    }

    /**
     * Insere a linha do PDF se ainda não existir e devolve a linha registada (a nossa, ou a de um
     * concorrente que chegou primeiro -- quem chama compara o {@code id}).
     */
    @Transactional
    public Optional<DocumentoFiscalPdf> registar(UUID id, UUID tenantId, UUID documentoId, String objectKey,
                                                 String sha256, long tamanhoBytes, String versaoModelo,
                                                 Instant geradoEm) {
        pdfRepository.inserirSeAusente(id, tenantId, documentoId, objectKey, sha256, tamanhoBytes, versaoModelo,
                geradoEm);
        return pdfRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId);
    }
}
