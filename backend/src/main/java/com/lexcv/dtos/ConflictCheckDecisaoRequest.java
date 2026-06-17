package com.lexcv.dtos;

public record ConflictCheckDecisaoRequest(
        String nivel,
        String justificativa,
        String referenciaEvidencia
) {}
