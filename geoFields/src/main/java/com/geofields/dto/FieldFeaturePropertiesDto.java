package com.geofields.dto;

import java.math.BigDecimal;

// Бизнес-поля внутри блока properties у Feature.
public record FieldFeaturePropertiesDto(
        Long id,
        String name,
        BigDecimal area
) {
}
