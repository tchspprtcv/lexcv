package com.lexcv.dtos;

import java.util.List;

public record ConflictCheckResponse(
        List<ConflictMatchDto> matches,
        String nivelSugerido
) {
    public record ConflictMatchDto(
            String entidadeId,
            String entidadeTipo,   // "cliente" | "parte"
            String nome,
            String nif,
            String nivelConflito,
            String motivo
    ) {}
}
