package com.lexcv.dtos;

import java.util.List;

public record ConflictCheckResponse(
        List<ConflictMatchDto> matches,
        String nivelSugerido
) {
    public ConflictCheckResponse {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public record ConflictMatchDto(
            String entidadeId,
            String entidadeTipo,   // "cliente" | "parte"
            String nome,
            String nif,
            String nivelConflito,
            String motivo
    ) {}
}
