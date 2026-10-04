package com.lexcv.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Phase 133: grava {@link RegimeIva} como {@code name()} numa coluna varchar simples. Usado em vez de
 * {@code @Enumerated(EnumType.STRING)} porque o Hibernate 6.6 gera um {@code CHECK (... IN (...))}
 * para colunas {@code @Enumerated} -- e esse CHECK obrigaria a um {@code DROP CONSTRAINT} sempre
 * que um valor novo fosse acrescentado ao enum (PITFALLS P-15). Provado por
 * {@code MigracaoFiscal133IT.hibernateNaoGeraCheckNasColunasDeEnum}.
 */
@Converter
public class RegimeIvaConverter implements AttributeConverter<RegimeIva, String> {

    @Override
    public String convertToDatabaseColumn(RegimeIva valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public RegimeIva convertToEntityAttribute(String coluna) {
        return coluna == null ? null : RegimeIva.valueOf(coluna);
    }
}
