package com.geofields.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
// Геометрия в формате GeoJSON (тип + координаты как есть).
public record GeoJsonGeometryDto(
        String type,
        Object coordinates
) {
}
