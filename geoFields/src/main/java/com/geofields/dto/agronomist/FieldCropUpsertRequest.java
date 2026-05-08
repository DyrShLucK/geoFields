package com.geofields.dto.agronomist;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Создание / обновление строки field_crops. */
public record FieldCropUpsertRequest(
        @NotNull Long cropId,
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
        @NotNull Integer cropYear
) {
}
