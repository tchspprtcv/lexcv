package com.lexcv.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Phase 134: grava {@link EstadoComunicacaoFiscal} como {@code name()} numa coluna varchar
 * simples. Usado em vez de {@code @Enumerated(EnumType.STRING)} porque o Hibernate 6.6 gera um
 * {@code CHECK (... IN (...))} para colunas {@code @Enumerated} -- e esse CHECK obrigaria a um
 * {@code DROP CONSTRAINT} quando a Phase 136 acrescentar estados (PITFALLS P-15). Provado por
 * {@code MigracaoFiscal134IT.hibernateNaoGeraCheckNasColunasDeEnum}.
 */
@Converter
public class EstadoComunicacaoFiscalConverter implements AttributeConverter<EstadoComunicacaoFiscal, String> {

    @Override
    public String convertToDatabaseColumn(EstadoComunicacaoFiscal valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public EstadoComunicacaoFiscal convertToEntityAttribute(String coluna) {
        return coluna == null ? null : EstadoComunicacaoFiscal.valueOf(coluna);
    }
}
