package com.lexcv.services.fiscal;

import com.lexcv.fiscal.pdf.DadosPdfDocumentoFiscal;
import com.lexcv.fiscal.pdf.PdfDocumentoFiscalRenderer;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalPdf;
import com.lexcv.services.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-01, ENTR-02): o PDF de cada FR/NC é gerado uma única vez, a partir da fotografia
 * guardada e do IUD, guardado no MinIO sob o prefixo do tenant e depois sempre reutilizado.
 *
 * <ul>
 *   <li>Já existe linha: devolve-a, sem renderizar nem carregar nada.</li>
 *   <li>Ainda não há XML/IUD: {@link FicheiroFiscalIndisponivelException} ("ainda não disponível"),
 *       sem renderizar.</li>
 *   <li>Caso contrário: renderiza, carrega para
 *       {@code <tenantId>/documentos-fiscais/<documentoId>/pdf-<idLinha>.pdf} e regista a linha.
 *       Se um concorrente registou primeiro, devolve a linha dele e apaga (best-effort) o objeto
 *       que acabou de carregar -- o id da linha no nome garante que só apaga o seu.</li>
 * </ul>
 *
 * <p>Sem {@code @Transactional} (T-137-40): as leituras e o registo correm em
 * {@link PdfDocumentoFiscalTransacoes}; o render e o MinIO correm sempre fora de transação.
 * Os logs só levam ids e nomes de classes de exceção (nunca texto, NIF ou nomes do documento).
 */
@Service
@RequiredArgsConstructor
public class PdfDocumentoFiscalService {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentoFiscalService.class);

    /** Versão do template gravada em {@code versao_modelo}. */
    public static final String VERSAO_MODELO = "137.1";
    static final String TIPO_PDF = "application/pdf";

    /**
     * PDF guardado.
     *
     * @param bytesFrescos os bytes, só quando acabaram de ser gerados nesta chamada
     */
    public record PdfArmazenado(String objectKey, String sha256, long tamanhoBytes, String nomeFicheiro,
                                Optional<byte[]> bytesFrescos) {

        @Override
        public boolean equals(Object o) {
            return o instanceof PdfArmazenado p && objectKey.equals(p.objectKey) && sha256.equals(p.sha256)
                    && tamanhoBytes == p.tamanhoBytes && nomeFicheiro.equals(p.nomeFicheiro)
                    && bytesFrescos.isPresent() == p.bytesFrescos.isPresent()
                    && (bytesFrescos.isEmpty() || Arrays.equals(bytesFrescos.get(), p.bytesFrescos.get()));
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectKey, sha256, tamanhoBytes, nomeFicheiro);
        }

        @Override
        public String toString() {
            return "PdfArmazenado[objectKey=" + objectKey + ", tamanhoBytes=" + tamanhoBytes
                    + ", nomeFicheiro=" + nomeFicheiro + ", fresco=" + bytesFrescos.isPresent() + "]";
        }
    }

    private final PdfDocumentoFiscalTransacoes transacoes;
    private final PdfDocumentoFiscalRenderer renderer;
    private final StorageService storage;
    private final Clock clock;

    /**
     * Garante que o PDF existe e devolve-o.
     *
     * @return vazio se o documento não existe neste tenant
     * @throws FicheiroFiscalIndisponivelException o documento ainda não tem XML/IUD
     * @throws com.lexcv.exceptions.StorageUnavailableException o MinIO falhou (nada é registado)
     * @throws com.lexcv.fiscal.pdf.FalhaGeracaoPdf o renderer falhou
     */
    public Optional<PdfArmazenado> garantirPdf(UUID tenantId, UUID documentoId) {
        Optional<PdfDocumentoFiscalTransacoes.DadosParaPdf> carregado = transacoes.carregarDados(tenantId, documentoId);
        if (carregado.isEmpty()) {
            return Optional.empty();
        }
        PdfDocumentoFiscalTransacoes.DadosParaPdf dados = carregado.get();
        String nome = NomesFicheiroFiscal.pdf(dados.documento().getNumeroFormatado());
        if (dados.pdfExistente().isPresent()) {
            return Optional.of(armazenado(dados.pdfExistente().get(), nome, null));
        }
        if (dados.iud().isEmpty()) {
            throw new FicheiroFiscalIndisponivelException();
        }

        DocumentoFiscal documento = dados.documento();
        byte[] bytes = renderer.renderizar(
                DadosPdfDocumentoFiscal.de(documento, dados.linhas(), dados.iud().get(), dados.origem()));
        UUID idLinha = UUID.randomUUID();
        String chave = tenantId + "/documentos-fiscais/" + documentoId + "/pdf-" + idLinha + ".pdf";
        String sha256 = ProcessadorComunicacaoFiscal.sha256Hex(bytes);
        storage.uploadBytes(chave, bytes, TIPO_PDF);

        Optional<DocumentoFiscalPdf> registado = transacoes.registar(idLinha, tenantId, documentoId, chave, sha256,
                bytes.length, VERSAO_MODELO, clock.instant());
        if (registado.isPresent() && idLinha.equals(registado.get().getId())) {
            return Optional.of(armazenado(registado.get(), nome, bytes));
        }
        // Um concorrente registou primeiro: o PDF dele é o que fica; o nosso objeto é apagado.
        apagarObjetoPerdedor(documentoId, chave);
        if (registado.isEmpty()) {
            throw new FicheiroFiscalIndisponivelException();
        }
        return Optional.of(armazenado(registado.get(), nome, null));
    }

    /** Para o gancho em segundo plano depois da emissão (137-17): nunca lança. */
    public void garantirPdfSilencioso(UUID tenantId, UUID documentoId) {
        try {
            garantirPdf(tenantId, documentoId);
        } catch (FicheiroFiscalIndisponivelException e) {
            log.debug("PDF do documento fiscal {} adiado: ainda sem IUD", documentoId);
        } catch (RuntimeException e) {
            log.warn("PDF do documento fiscal {} não gerado ({})", documentoId, e.getClass().getSimpleName());
        }
    }

    /**
     * Bytes do PDF (anexo do email, 137-14): os frescos se acabou de o gerar, senão lidos do MinIO.
     *
     * @throws IllegalArgumentException o documento não existe neste tenant
     */
    public byte[] lerPdf(UUID tenantId, UUID documentoId) {
        PdfArmazenado pdf = garantirPdf(tenantId, documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento fiscal inexistente"));
        if (pdf.bytesFrescos().isPresent()) {
            return pdf.bytesFrescos().get();
        }
        return storage.lerBytes(pdf.objectKey());
    }

    private void apagarObjetoPerdedor(UUID documentoId, String chave) {
        try {
            storage.delete(chave);
        } catch (RuntimeException e) {
            log.warn("Objeto PDF duplicado do documento fiscal {} não apagado ({})", documentoId,
                    e.getClass().getSimpleName());
        }
    }

    private static PdfArmazenado armazenado(DocumentoFiscalPdf linha, String nome, byte[] frescos) {
        long tamanho = linha.getTamanhoBytes() == null ? 0L : linha.getTamanhoBytes();
        return new PdfArmazenado(linha.getObjectKey(), linha.getSha256(), tamanho, nome,
                Optional.ofNullable(frescos));
    }
}
