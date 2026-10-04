package com.lexcv.dtos;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 134 (D-16, D-17, EMIS-11): linha da listagem de documentos fiscais. {@code tipo},
 * {@code ambiente} e {@code estadoComunicacao} são o {@code name()} do enum
 * ({@code estadoComunicacao} nulo quando o documento ainda não tem linha de comunicação).
 * A chave de idempotência e o id de quem emitiu nunca são expostos.
 */
public record DocumentoFiscalResumoResponse(
        UUID id,
        String numeroFormatado,
        String tipo,
        String tipoRotulo,
        String ambiente,
        LocalDate dataEmissao,
        UUID clienteId,
        String adquirenteNome,
        String adquirenteNif,
        BigDecimal totalDocumento,
        String estadoComunicacao
) {

    public static DocumentoFiscalResumoResponse de(DocumentoFiscal d, EstadoComunicacaoFiscal estadoOuNulo) {
        return new DocumentoFiscalResumoResponse(
                d.getId(),
                d.getNumeroFormatado(),
                d.getTipo().name(),
                d.getTipo().rotulo(),
                d.getAmbiente().name(),
                d.getDataEmissao(),
                d.getClienteId(),
                d.getAdquirenteNome(),
                d.getAdquirenteNif(),
                d.getTotalDocumento(),
                estadoOuNulo == null ? null : estadoOuNulo.name());
    }
}
