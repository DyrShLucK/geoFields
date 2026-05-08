package com.geofields.dto;

import java.util.List;

// Верхний уровень GeoJSON-ответа: type + список features.
public record FieldFeatureCollectionDto(
        String type,
        List<FieldFeatureDto> features
) {
}
