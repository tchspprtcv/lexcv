package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 137 (ENTR-02, T-137-61, T-137-62): as transações CURTAS da descarga de um documento fiscal
 * -- autorização pelo tenant do chamador e o evento de auditoria, na mesma transação.
 *
 * <p>Num bean separado de {@link DescargaDocumentoFiscalService} para que a geração do PDF e o
 * MinIO corram fora de qualquer transação (chamada pelo proxy do Spring).
 *
 * <p>Um id de outro escritório responde o mesmo 404 {@code DOCUMENTO_FISCAL_NAO_ENCONTRADO} de um id
 * inexistente (sem oráculo) e não deixa evento.
 */
@Service
@RequiredArgsConstructor
public class DescargaDocumentoFiscalTransacoes {

    static final String FORMATO_PDF = "PDF";
    static final String FORMATO_XML = "XML";

    private final DocumentoFiscalRepository documentoRepository;
    private final DocumentoFiscalXmlRepository xmlRepository;
    private final AuditoriaFiscalService auditoria;

    /**
     * Autoriza e regista o pedido de descarga do PDF. O evento faz commit ANTES da geração e do
     * presign (fora desta transação): um pedido que depois responde 503 fica registado como
     * tentativa.
     *
     * @return o número formatado do documento
     */
    @Transactional
    public String autorizarERegistarPdf(UUID tenantId, UserPrincipal autor, UUID documentoId) {
        DocumentoFiscal documento = documento(tenantId, documentoId);
        auditoria.registarDescarga(tenantId, autor, documentoId, documento.getNumeroFormatado(), FORMATO_PDF);
        return documento.getNumeroFormatado();
    }

    /**
     * Autoriza, lê o XML guardado e regista a descarga. Sem linha XML responde 503
     * {@code FICHEIRO_INDISPONIVEL} ANTES do evento (sem registo).
     */
    @Transactional
    public XmlDescarregavel autorizarERegistarXml(UUID tenantId, UserPrincipal autor, UUID documentoId) {
        DocumentoFiscal documento = documento(tenantId, documentoId);
        DocumentoFiscalXml xml = xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE,
                        FicheiroFiscalIndisponivelException.CODIGO, FicheiroFiscalIndisponivelException.MENSAGEM));
        auditoria.registarDescarga(tenantId, autor, documentoId, documento.getNumeroFormatado(), FORMATO_XML);
        return new XmlDescarregavel(xml.getXml().getBytes(StandardCharsets.UTF_8),
                NomesFicheiroFiscal.xml(documento.getNumeroFormatado()));
    }

    private DocumentoFiscal documento(UUID tenantId, UUID documentoId) {
        return documentoRepository.findByIdAndTenantId(documentoId, tenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND,
                        ReenvioEmailFiscalService.CODIGO_NAO_ENCONTRADO, ReenvioEmailFiscalService.MSG_NAO_ENCONTRADO));
    }

    /** Os bytes UTF-8 exatos do XML guardado e o nome do anexo; os bytes são copiados. */
    public record XmlDescarregavel(byte[] conteudo, String nomeFicheiro) {

        public XmlDescarregavel {
            Objects.requireNonNull(conteudo, "conteudo");
            conteudo = conteudo.clone();
        }

        @Override
        public byte[] conteudo() {
            return conteudo.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof XmlDescarregavel x && Arrays.equals(conteudo, x.conteudo)
                    && Objects.equals(nomeFicheiro, x.nomeFicheiro);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(conteudo), nomeFicheiro);
        }

        @Override
        public String toString() {
            return "XmlDescarregavel[nomeFicheiro=" + nomeFicheiro + ", bytes=" + conteudo.length + "]";
        }
    }
}
