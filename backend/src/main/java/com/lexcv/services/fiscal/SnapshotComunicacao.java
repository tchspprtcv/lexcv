package com.lexcv.services.fiscal;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;

import java.util.Optional;

/**
 * Phase 136 (DFE-01): tudo o que o processador precisa para construir e enviar o DFE de um
 * documento, lido numa transação curta só de leitura e presa ao tenant da linha reclamada.
 *
 * @param documento              o documento fiscal imutável
 * @param linha                  a sua linha única (numero_linha 1)
 * @param xmlExistente           a linha XML já gravada, se existir (reutilizada para sempre)
 * @param iudOrigem              numa NC, o IUD do XML da FR corrigida (vazio se a FR ainda não o
 *                               tem); sempre vazio numa FR
 * @param numeroFormatadoOrigem  numa NC, o número formatado da FR (mesmo tenant); vazio numa FR
 * @param estadoOrigem           numa NC, o estado da comunicação da FR (mesmo tenant; WR-05); vazio
 *                               numa FR
 */
public record SnapshotComunicacao(DocumentoFiscal documento, DocumentoFiscalLinha linha,
                                  Optional<DocumentoFiscalXml> xmlExistente, Optional<String> iudOrigem,
                                  Optional<String> numeroFormatadoOrigem,
                                  Optional<EstadoComunicacaoFiscal> estadoOrigem) {

    /** Snapshot sem o estado da comunicação da origem (FR, ou NC cuja FR ainda não o tem). */
    public SnapshotComunicacao(DocumentoFiscal documento, DocumentoFiscalLinha linha,
                               Optional<DocumentoFiscalXml> xmlExistente, Optional<String> iudOrigem,
                               Optional<String> numeroFormatadoOrigem) {
        this(documento, linha, xmlExistente, iudOrigem, numeroFormatadoOrigem, Optional.empty());
    }
}
