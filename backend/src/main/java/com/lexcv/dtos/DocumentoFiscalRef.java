package com.lexcv.dtos;

import com.lexcv.models.DocumentoFiscal;

import java.util.UUID;

/**
 * Phase 134 (D-19, EMIS-12): referência curta ao documento fiscal de um pagamento, mostrada na
 * coluna "Documento fiscal" da lista de pagamentos.
 */
public record DocumentoFiscalRef(UUID id, String numeroFormatado) {

    public static DocumentoFiscalRef de(DocumentoFiscal documento) {
        return new DocumentoFiscalRef(documento.getId(), documento.getNumeroFormatado());
    }
}
