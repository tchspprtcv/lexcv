package com.lexcv.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Phase 135: grava {@link MotivoNotaCredito} como {@code name()} numa coluna varchar simples
 * ({@code t_documento_fiscal.motivo_codigo}). Usado em vez de {@code @Enumerated(EnumType.STRING)}
 * porque o Hibernate 6.6 gera um {@code CHECK (... IN (...))} para colunas {@code @Enumerated} -- e
 * esse CHECK obrigaria a um {@code DROP CONSTRAINT} sempre que um motivo novo fosse acrescentado
 * (PITFALLS P-15).
 */
@Converter
public class MotivoNotaCreditoConverter implements AttributeConverter<MotivoNotaCredito, String> {

    @Override
    public String convertToDatabaseColumn(MotivoNotaCredito valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public MotivoNotaCredito convertToEntityAttribute(String coluna) {
        return coluna == null ? null : MotivoNotaCredito.valueOf(coluna);
    }
}
