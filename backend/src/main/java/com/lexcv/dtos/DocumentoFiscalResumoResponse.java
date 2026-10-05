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
 *
 * <p>Phase 135 (NCRD-01): numa Nota de Crédito, {@code documentoOrigemId} e
 * {@code documentoOrigemNumero} identificam a Fatura-Recibo corrigida (nulos numa FR). O total de
 * uma NC é uma magnitude positiva.
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
        String estadoComunicacao,
        UUID documentoOrigemId,
        String documentoOrigemNumero
) {

    public static DocumentoFiscalResumoResponse de(DocumentoFiscal d, EstadoComunicacaoFiscal estadoOuNulo) {
        return de(d, estadoOuNulo, null);
    }

    /** Phase 135: {@code origemNumeroOuNulo} é o número da FR de origem de uma NC. */
    public static DocumentoFiscalResumoResponse de(DocumentoFiscal d, EstadoComunicacaoFiscal estadoOuNulo,
                                                   String origemNumeroOuNulo) {
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
                estadoOuNulo == null ? null : estadoOuNulo.name(),
                d.getDocumentoOrigemId(),
                origemNumeroOuNulo);
    }
}
