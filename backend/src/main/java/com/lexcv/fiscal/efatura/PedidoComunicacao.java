package com.lexcv.fiscal.efatura;

import com.lexcv.models.AmbienteFiscal;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 136 (DFE-03): o pedido entregue à {@link EfaturaGateway}. O XML segue como
 * {@code String} (UTF-8) para não expor um {@code byte[]} mutável.
 */
public record PedidoComunicacao(UUID tenantId, UUID documentoFiscalId, AmbienteFiscal ambiente, String iud,
                                String xml) {

    public PedidoComunicacao {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(documentoFiscalId, "documentoFiscalId");
        Objects.requireNonNull(ambiente, "ambiente");
        Objects.requireNonNull(iud, "iud");
        Objects.requireNonNull(xml, "xml");
        if (iud.isBlank()) {
            throw new IllegalArgumentException("O IUD é obrigatório.");
        }
        if (xml.isBlank()) {
            throw new IllegalArgumentException("O XML é obrigatório.");
        }
    }
}
