package com.lexcv.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Converter
public class DeslocacoesConverter implements AttributeConverter<List<Deslocacao>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Deslocacao> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("Failed to serialize Deslocacoes for persistence", e);
            return null;
        }
    }

    @Override
    public List<Deslocacao> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<Deslocacao>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize Deslocacoes from database value: {}", dbData, e);
            return null;
        }
    }
}
