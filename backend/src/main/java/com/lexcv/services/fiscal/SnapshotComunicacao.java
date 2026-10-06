package com.lexcv.services.fiscal;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;

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
 */
public record SnapshotComunicacao(DocumentoFiscal documento, DocumentoFiscalLinha linha,
                                  Optional<DocumentoFiscalXml> xmlExistente, Optional<String> iudOrigem,
                                  Optional<String> numeroFormatadoOrigem) {
}
