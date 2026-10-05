package com.lexcv.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Phase 135 (NCRD-01, NCRD-02): corpo de {@code POST /documentos-fiscais/{id}/notas-credito} e da
 * respetiva pré-visualização.
 *
 * <p>Só traz o que o utilizador escolhe: {@code tipo} ({@code TOTAL} ou {@code PARCIAL}),
 * {@code valor} (só no crédito parcial, IVA incluído), o motivo fechado e o texto livre
 * obrigatório, e a chave de idempotência. O id da Fatura-Recibo de origem vem do caminho do URL e
 * o tenant do principal autenticado; não há campos de tenant, origem ou estorno, por isso não há
 * atribuição em massa possível por construção.
 *
 * <p>WR-01 da revisão: {@code totalEsperado} e {@code valorCreditavelEsperado} são o "Total a
 * creditar" e o valor creditável que o utilizador viu e confirmou na pré-visualização. Na emissão,
 * se a composição sob o lock der outro valor (outra NC foi emitida entretanto), o pedido é recusado
 * com 409 {@code NC_VALORES_ALTERADOS}. Ficam FORA da comparação de idempotência (uma repetição da
 * mesma chave devolve sempre a NC guardada) e são ignorados pela pré-visualização. São opcionais
 * por compatibilidade; o frontend envia sempre os dois.
 */
public record NotaCreditoRequest(String tipo, BigDecimal valor, String motivoCodigo, String motivoTexto,
                                 UUID chaveIdempotencia, BigDecimal totalEsperado,
                                 BigDecimal valorCreditavelEsperado) {

    @JsonCreator
    public NotaCreditoRequest {
    }

    /** Pedido sem os valores confirmados (pré-visualização e chamadores internos). */
    public NotaCreditoRequest(String tipo, BigDecimal valor, String motivoCodigo, String motivoTexto,
                              UUID chaveIdempotencia) {
        this(tipo, valor, motivoCodigo, motivoTexto, chaveIdempotencia, null, null);
    }
}
