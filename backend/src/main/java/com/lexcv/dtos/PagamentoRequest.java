package com.lexcv.dtos;

import com.lexcv.models.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 134 (D-01, D-03, D-04): corpo de {@code POST /pagamentos} e de
 * {@code POST /faturacao/pre-visualizacao}.
 *
 * <p>Só traz o que o utilizador escolhe. Não tem id, tenant nem dados de quem emite: o tenant vem
 * sempre do principal autenticado e quem emite vem da configuração fiscal, por isso não há
 * atribuição em massa possível por construção. {@code retencaoPercentagem} é opcional (sem
 * retenção quando nulo); {@code chaveIdempotencia} só é exigida quando a faturação está ativa.
 *
 * <p>Propriedades JSON desconhecidas são ignoradas pelo ObjectMapper do Spring Boot, e as que
 * faltam ficam nulas, por isso os payloads antigos ({@code honorarioId, valorPago,
 * dataPagamento, metodo}) continuam a ligar (A2, provado no plano 08).
 */
public record PagamentoRequest(
        Integer honorarioId,
        BigDecimal valorPago,
        LocalDate dataPagamento,
        String metodo,
        BigDecimal retencaoPercentagem,
        UUID chaveIdempotencia
) {

    /**
     * Pagamento do caminho com a faturação desligada, exatamente como era antes: só os quatro
     * campos legados. O id nunca é copiado (o INSERT gera-o).
     */
    public Pagamento paraPagamentoLegado() {
        return Pagamento.builder()
                .honorarioId(honorarioId)
                .valorPago(valorPago)
                .dataPagamento(dataPagamento)
                .metodo(metodo)
                .build();
    }
}
