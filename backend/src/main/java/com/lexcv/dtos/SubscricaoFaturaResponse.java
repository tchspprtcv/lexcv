package com.lexcv.dtos;

import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 138 (SUBS-02, SUBS-03, SUBS-04): Resposta de Fatura-Recibo de subscrição emitida pela plataforma.
 */
public record SubscricaoFaturaResponse(
        UUID documentoId,
        UUID pagamentoSubscricaoId,
        TipoDocumentoFiscal tipo,
        String serieCodigo,
        Long numero,
        String numeroFormatado,
        LocalDate dataEmissao,
        Instant emitidoEm,
        UUID adquirenteTenantId,
        String adquirenteNome,
        String adquirenteNif,
        BigDecimal totalBase,
        BigDecimal totalIva,
        BigDecimal totalDocumento,
        String metodoPagamento,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        String plano,
        EstadoComunicacaoFiscal estadoComunicacao
) {
}
