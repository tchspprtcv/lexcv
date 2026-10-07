package com.lexcv.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Phase 137: grava {@link EstadoEntregaEmail} como {@code name()} numa coluna varchar simples, pelo
 * mesmo motivo de {@link EstadoComunicacaoFiscalConverter}: {@code @Enumerated(EnumType.STRING)}
 * faria o Hibernate 6.6 gerar um {@code CHECK (... IN (...))} que obrigaria a um
 * {@code DROP CONSTRAINT} sempre que um estado fosse acrescentado.
 *
 * <p>Um valor desconhecido na base de dados é um erro de integridade: falha com uma mensagem fixa
 * (sem ecoar o valor lido).
 */
@Converter
public class EstadoEntregaEmailConverter implements AttributeConverter<EstadoEntregaEmail, String> {

    static final String MENSAGEM_VALOR_DESCONHECIDO = "Estado de entrega de email desconhecido na base de dados";

    @Override
    public String convertToDatabaseColumn(EstadoEntregaEmail valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public EstadoEntregaEmail convertToEntityAttribute(String coluna) {
        if (coluna == null) {
            return null;
        }
        try {
            return EstadoEntregaEmail.valueOf(coluna);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(MENSAGEM_VALOR_DESCONHECIDO, e);
        }
    }
}
