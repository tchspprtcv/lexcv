package com.lexcv.dtos;

import jakarta.validation.constraints.NotNull;

/**
 * Phase 133 (CFG-06): corpo de {@code PUT /api/v1/faturacao/configuracao/envio-email} (Plan 05).
 * Ligar exige {@code aceiteDeclaracao = true}; desligar não exige aceitação.
 */
public record EmailAutomaticoRequest(
        @NotNull Boolean ligado,
        Boolean aceiteDeclaracao
) {
}
