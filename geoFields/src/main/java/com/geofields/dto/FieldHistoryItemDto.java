package com.geofields.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Одна запись истории по полю: культура и агро-метрики (NDVI — через Python-прокси, не в GeoJSON).
public record FieldHistoryItemDto(
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
        Integer cropYear
) {
}
