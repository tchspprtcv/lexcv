package com.lexcv.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * Phase 135 (NCRD-01): motivo obrigatório da Nota de Crédito, escolhido de uma lista fechada e
 * acompanhado de um texto livre obrigatório ({@code t_documento_fiscal.motivo_texto}).
 *
 * <p>O mapeamento destas constantes para o {@code IssueReasonCode} do eFatura é da
 * responsabilidade da Phase 136 (CONTEXT). Guardado como {@code name()} numa coluna varchar via
 * {@link MotivoNotaCreditoConverter} -- nunca com {@code @Enumerated}, para que o Hibernate não gere
 * um {@code CHECK} que obrigaria a {@code DROP CONSTRAINT} ao acrescentar um motivo (PITFALLS P-15).
 */
public enum MotivoNotaCredito {
    ANULACAO_TOTAL("Anulação total"),
    CORRECAO_VALOR("Correção de valor"),
    ERRO_DADOS_CLIENTE("Erro nos dados do cliente"),
    OUTRO("Outro");

    private final String rotulo;

    MotivoNotaCredito(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Procura pelo {@code name()}, ignorando espaços à volta e maiúsculas/minúsculas. */
    public static Optional<MotivoNotaCredito> porNome(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String limpo = valor.trim();
        return Arrays.stream(values()).filter(m -> m.name().equalsIgnoreCase(limpo)).findFirst();
    }
}
