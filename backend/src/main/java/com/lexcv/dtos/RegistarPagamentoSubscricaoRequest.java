package com.lexcv.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 138 (SUBS-02): Pedido de registo de pagamento de subscrição de um escritório cliente e
 * emissão atómica da respetiva Fatura-Recibo pela plataforma LexCV.
 */
public record RegistarPagamentoSubscricaoRequest(
        @NotNull(message = "O ID do escritório é obrigatório.")
        UUID tenantId,

        @NotNull(message = "O valor pago é obrigatório.")
        @DecimalMin(value = "0.01", message = "O valor deve ser superior a zero.")
        BigDecimal valorPago,

        @NotNull(message = "A data de pagamento é obrigatória.")
        LocalDate dataPagamento,

        @NotBlank(message = "O método de pagamento é obrigatório.")
        String metodo,

        @NotNull(message = "A data de início do período é obrigatória.")
        LocalDate periodoInicio,

        @NotNull(message = "A data de fim do período é obrigatória.")
        LocalDate periodoFim,

        String plano,

        @NotNull(message = "A chave de idempotência é obrigatória.")
        UUID chaveIdempotencia
) {
}
