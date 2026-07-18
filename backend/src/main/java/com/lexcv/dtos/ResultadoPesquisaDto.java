package com.lexcv.dtos;

/**
 * Unified cross-entity search-result DTO for GET /api/v1/pesquisa.
 * tipo discriminates the source entity: "cliente" | "processo" | "documento" | "parecer".
 * These four values are the only ones this record's tipo will ever carry — search is
 * structurally limited to them by design: PesquisaController has no fifth query branch,
 * and no billing/fee-related field exists anywhere on this record.
 */
public record ResultadoPesquisaDto(
        String tipo,
        String id,
        String titulo,
        String subtitulo,
        String rota
) {}
