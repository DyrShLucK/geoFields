package com.geofields.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Плоская строка результата JOIN по полю и его истории.
public record FieldHistoryRow(
        Long fieldId,
        String fieldName,
        BigDecimal fieldArea,
        String geometryJson,
        Long fieldCropId,
        Long cropId,
        String cropName,
        LocalDate sowingDate,
        LocalDate harvestDate,
        BigDecimal sownAreaHa,
        BigDecimal harvestAreaHa,
        BigDecimal actualYield,
        BigDecimal totalYield,
        BigDecimal plannedYield,
        BigDecimal forecastedYield,
        String sourceData,
        String sowingDetails,
        Integer cropYear,
        LocalDate analyticsDate,
        String ndviUrl,
        LocalDateTime ndviCreatedAt
) {
}
