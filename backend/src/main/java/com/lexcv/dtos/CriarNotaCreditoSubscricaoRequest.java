package com.lexcv.dtos;

import com.lexcv.models.MotivoNotaCredito;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Phase 138 (SUBS-03): Pedido de emissão de Nota de Crédito sobre uma Fatura-Recibo de subscrição.
 */
public record CriarNotaCreditoSubscricaoRequest(
        @NotNull(message = "O motivo da Nota de Crédito é obrigatório.")
        MotivoNotaCredito motivoCodigo,

        @NotBlank(message = "A justificação da Nota de Crédito é obrigatória.")
        @Size(max = 200, message = "A justificação não pode ter mais de 200 caracteres.")
        String motivoTexto,

        @DecimalMin(value = "0.01", message = "O valor deve ser superior a zero.")
        BigDecimal valorTotal,

        @NotNull(message = "A chave de idempotência é obrigatória.")
        UUID chaveIdempotencia
) {
}
