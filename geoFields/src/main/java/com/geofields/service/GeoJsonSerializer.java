package com.geofields.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Сериализация GeoJSON-геометрии в строку для PostGIS. */
@Component
public class GeoJsonSerializer {

    private final ObjectMapper objectMapper;

    public GeoJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeGeometry(Object geometry) {
        return writeGeometry(geometry, null);
    }

    public String writeGeometry(Object geometry, String fieldLabel) {
        if (geometry == null) {
            String suffix = fieldLabel != null && !fieldLabel.isBlank()
                    ? " поля '" + fieldLabel.trim() + "'"
                    : "";
            throw new IllegalArgumentException("Отсутствует геометрия" + suffix);
        }
        try {
            return objectMapper.writeValueAsString(geometry);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Некорректная геометрия поля", ex);
        }
    }
}
