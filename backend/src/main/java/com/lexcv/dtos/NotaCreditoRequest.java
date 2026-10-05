package com.lexcv.dtos;

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
 */
public record NotaCreditoRequest(String tipo, BigDecimal valor, String motivoCodigo, String motivoTexto, UUID chaveIdempotencia) {
}
