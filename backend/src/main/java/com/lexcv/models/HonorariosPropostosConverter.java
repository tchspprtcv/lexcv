package com.lexcv.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class HonorariosPropostosConverter implements AttributeConverter<HonorariosPropostos, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(HonorariosPropostos attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("Failed to serialize HonorariosPropostos for persistence", e);
            return null;
        }
    }

    @Override
    public HonorariosPropostos convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, HonorariosPropostos.class);
        } catch (Exception e) {
            log.error("Failed to deserialize HonorariosPropostos from database value: {}", dbData, e);
            return null;
        }
    }
}
