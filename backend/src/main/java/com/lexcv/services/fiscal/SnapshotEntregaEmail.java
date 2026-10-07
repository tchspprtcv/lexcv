package com.lexcv.services.fiscal;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;

import java.util.Optional;

/**
 * Phase 137 (ENTR-03): o que o processador de envio precisa de um documento, lido numa transação só
 * de leitura e sempre pelo tenant da linha reclamada.
 *
 * @param xml          o XML guardado do documento (IUD e anexo), vazio enquanto não existir
 * @param numeroOrigem NC: {@code numero_formatado} da FR de origem, do mesmo tenant
 * @param replyTo      {@code ConfiguracaoFiscal.emailContacto} em bruto (vazio quando em branco); a
 *                     validação do endereço é feita pelo compositor do email (137-14)
 */
public record SnapshotEntregaEmail(DocumentoFiscal documento, Optional<DocumentoFiscalXml> xml,
                                   Optional<String> numeroOrigem, Optional<String> replyTo) {
}
