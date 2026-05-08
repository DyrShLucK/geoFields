package com.geofields.dto;

import java.math.BigDecimal;
import java.util.List;

// Бизнес-поля внутри блока properties у Feature.
public record FieldFeaturePropertiesDto(
        Long id,
        String name,
        BigDecimal area,
        List<FieldHistoryItemDto> history
) {
}
