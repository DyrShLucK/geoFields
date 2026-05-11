package com.geofields.repository.row;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FieldCropHistoryRow(
        long historyId,
        long cropId,
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
