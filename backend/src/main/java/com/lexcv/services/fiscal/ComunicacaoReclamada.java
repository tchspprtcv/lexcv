package com.lexcv.services.fiscal;

import com.lexcv.models.AmbienteFiscal;

import java.util.UUID;

/**
 * Phase 136 (DFE-04): uma linha da fila de comunicação reclamada por um worker.
 *
 * @param tentativas       valor depois do incremento feito na reclamação
 * @param versao           versão depois do incremento da reclamação; o resultado só é gravado se
 *                         a linha ainda tiver esta versão
 * @param reprocessamentos contador de reprocessamentos manuais (episódio de falha)
 */
public record ComunicacaoReclamada(UUID id, UUID tenantId, UUID documentoFiscalId, AmbienteFiscal ambiente,
                                   int tentativas, long versao, int reprocessamentos) {
}
