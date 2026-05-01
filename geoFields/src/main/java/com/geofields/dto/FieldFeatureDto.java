package com.geofields.dto;

// Один объект Feature в коллекции полей.
public record FieldFeatureDto(
        String type,
        Long id,
        FieldFeaturePropertiesDto properties,
        GeoJsonGeometryDto geometry
) {
}
