package com.lexcv.services.fiscal;

import com.lexcv.config.MinioProperties;
import com.lexcv.config.UserPrincipal;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.fiscal.pdf.FalhaGeracaoPdf;
import com.lexcv.services.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Phase 137 (ENTR-02): descarga do PDF e do XML de um documento fiscal ({@code financeiro:view}
 * exato, verificado no controlador).
 *
 * <ul>
 *   <li><b>PDF:</b> autoriza e audita ({@link DescargaDocumentoFiscalTransacoes}, tx curta) ->
 *       {@link PdfDocumentoFiscalService#garantirPdf} (gera a pedido se faltar) -> URL pré-assinado
 *       com {@code Content-Disposition: attachment} e o nome série/número. A geração e o MinIO
 *       correm FORA de transação; por isso o evento da tentativa fica mesmo quando a resposta é
 *       503.</li>
 *   <li><b>XML:</b> autoriza, lê o XML guardado e audita na mesma tx curta; sem XML, 503 sem
 *       evento.</li>
 * </ul>
 *
 * <p>Erros com código fixo (T-137-63), nunca o texto da exceção: {@code FICHEIRO_INDISPONIVEL}
 * (sem XML/IUD ainda), {@code STORAGE_INDISPONIVEL} (MinIO), {@code FALHA_PDF} (renderer), todos
 * 503. Sem anotações de transação nesta classe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescargaDocumentoFiscalService {

    static final String CODIGO_STORAGE_INDISPONIVEL = "STORAGE_INDISPONIVEL";
    static final String CODIGO_FALHA_PDF = "FALHA_PDF";
    static final String MSG_PREPARAR =
            "Não foi possível preparar o ficheiro neste momento. Aguarde um momento e tente novamente.";

    private final DescargaDocumentoFiscalTransacoes transacoes;
    private final PdfDocumentoFiscalService pdfService;
    private final StorageService storageService;
    private final MinioProperties minioProperties;

    /** URL pré-assinado do PDF, o nome do anexo e a validade em segundos. */
    public record DescargaPdf(String url, String nomeFicheiro, long expiresIn) {
    }

    public DescargaPdf descarregarPdf(UUID tenantId, UserPrincipal autor, UUID documentoId) {
        transacoes.autorizarERegistarPdf(tenantId, autor, documentoId);
        try {
            PdfDocumentoFiscalService.PdfArmazenado pdf = pdfService.garantirPdf(tenantId, documentoId)
                    .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND,
                            ReenvioEmailFiscalService.CODIGO_NAO_ENCONTRADO, ReenvioEmailFiscalService.MSG_NAO_ENCONTRADO));
            String url = storageService.presignedDownloadUrl(pdf.objectKey(), pdf.nomeFicheiro());
            return new DescargaPdf(url, pdf.nomeFicheiro(), minioProperties.getPresignedUrlExpiry());
        } catch (FicheiroFiscalIndisponivelException e) {
            throw new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE, FicheiroFiscalIndisponivelException.CODIGO,
                    FicheiroFiscalIndisponivelException.MENSAGEM);
        } catch (StorageUnavailableException e) {
            log.warn("PDF do documento fiscal não disponibilizado: armazenamento indisponível");
            throw new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE, CODIGO_STORAGE_INDISPONIVEL, MSG_PREPARAR);
        } catch (FalhaGeracaoPdf e) {
            log.warn("PDF do documento fiscal não disponibilizado: falha na geração");
            throw new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE, CODIGO_FALHA_PDF, MSG_PREPARAR);
        }
    }

    public DescargaDocumentoFiscalTransacoes.XmlDescarregavel descarregarXml(UUID tenantId, UserPrincipal autor,
                                                                             UUID documentoId) {
        return transacoes.autorizarERegistarXml(tenantId, autor, documentoId);
    }
}
