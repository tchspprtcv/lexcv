package com.lexcv.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Converter
public class DocumentosATratarConverter implements AttributeConverter<List<DocumentoATratar>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<DocumentoATratar> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("Failed to serialize DocumentosATratar for persistence", e);
            return null;
        }
    }

    @Override
    public List<DocumentoATratar> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<DocumentoATratar>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize DocumentosATratar from database value: {}", dbData, e);
            return null;
        }
    }
}
