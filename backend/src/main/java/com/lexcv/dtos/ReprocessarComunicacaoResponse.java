package com.lexcv.dtos;

/**
 * Phase 136 (DFE-05): resposta de {@code POST /documentos-fiscais/{id}/comunicacao/reprocessar}.
 * Depois de um reprocessamento a comunicação está sempre {@code PENDENTE} com 0 tentativas; o
 * job do outbox apanha-a na próxima execução.
 */
public record ReprocessarComunicacaoResponse(String estado, int tentativas) {
}
