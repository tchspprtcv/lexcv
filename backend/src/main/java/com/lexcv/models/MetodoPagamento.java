package com.lexcv.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * Phase 134 (EMIS-10, D-04): método de pagamento, obrigatório quando a faturação está ativa, com
 * o código de meio de pagamento eFatura correspondente (tabela fixa no backend).
 *
 * <p>Os códigos UNCL4461 são [ASSUMED]: confirmados contra o {@code PaymentMeansCode_D19B} de
 * Cabo Verde na Phase 136. O código fica fotografado em
 * {@code t_documento_fiscal.meio_pagamento_codigo}, por isso uma correção futura não altera
 * documentos já emitidos. {@code t_pagamento.metodo} guarda {@code name()} apenas no caminho com
 * faturação ativa; o texto livre dos pagamentos antigos não é tocado.
 */
public enum MetodoPagamento {
    DINHEIRO("Dinheiro", "10"),
    TRANSFERENCIA("Transferência bancária", "30"),
    CHEQUE("Cheque", "20"),
    CARTAO("Cartão / Multibanco", "48"),
    OUTRO("Outro", "ZZZ");

    private final String rotulo;
    private final String codigoMeioPagamento;

    MetodoPagamento(String rotulo, String codigoMeioPagamento) {
        this.rotulo = rotulo;
        this.codigoMeioPagamento = codigoMeioPagamento;
    }

    public String rotulo() {
        return rotulo;
    }

    public String codigoMeioPagamento() {
        return codigoMeioPagamento;
    }

    /** Procura pelo {@code name()}, ignorando espaços à volta e maiúsculas/minúsculas. */
    public static Optional<MetodoPagamento> porNome(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String limpo = valor.trim();
        return Arrays.stream(values()).filter(m -> m.name().equalsIgnoreCase(limpo)).findFirst();
    }
}
