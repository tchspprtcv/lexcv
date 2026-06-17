package com.lexcv.dtos;

import java.time.LocalDateTime;

/**
 * Unified timeline entry DTO for GET /processos/{id}/timeline.
 * tipo discriminates the source entity:
 *   "movimentacao" — regular process movement
 *   "transicao"    — state transition (Movimentacao with tipo=TRANSICAO_ESTADO)
 *   "evento"       — agenda event linked to processo
 *   "documento"    — document uploaded/linked to processo
 *   "decisao"      — conflict check decision
 */
public record TimelineItemDto(
        String tipo,
        String id,
        LocalDateTime timestamp,
        String titulo,
        String descricao,
        String autorNome
) {}
