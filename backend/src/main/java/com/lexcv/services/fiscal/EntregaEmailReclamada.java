package com.lexcv.services.fiscal;

import java.util.UUID;

/**
 * Phase 137 (ENTR-03, ENTR-04): uma linha da fila de entrega por email reclamada por um worker.
 *
 * @param destinatario endereço de entrega (dado pessoal): nunca em logs nem em auditoria
 * @param tentativas   valor depois do incremento feito na reclamação
 * @param versao       versão depois do incremento da reclamação; o resultado só é gravado se a
 *                     linha ainda tiver esta versão
 * @param reenvios     contador de reenvios manuais (episódio de falha para a notificação)
 */
public record EntregaEmailReclamada(UUID id, UUID tenantId, UUID documentoFiscalId, String destinatario,
                                    int tentativas, long versao, int reenvios) {

    /** Sem o destinatário: o endereço é dado pessoal e nunca vai para logs (toString de records). */
    @Override
    public String toString() {
        return "EntregaEmailReclamada[id=" + id + ", tenantId=" + tenantId + ", documentoFiscalId="
                + documentoFiscalId + ", tentativas=" + tentativas + ", versao=" + versao + ", reenvios="
                + reenvios + "]";
    }
}
