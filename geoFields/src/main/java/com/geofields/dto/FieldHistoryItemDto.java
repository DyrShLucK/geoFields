package com.geofields.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Одна запись истории по полю: культура, агро-метрики и NDVI-ссылка.
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
        Integer cropYear,
        LocalDate analyticsDate,
        String ndviUrl,
        LocalDateTime ndviCreatedAt
) {
}
