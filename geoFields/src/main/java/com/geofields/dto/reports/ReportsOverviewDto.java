package com.geofields.dto.reports;

public record ReportsOverviewDto(
        int fieldsInScope,
        double totalAreaHa,
        long operationsTotal,
        long operationsCompleted,
        long operationsPlanned,
        long operationsInProgress,
        long overduePlanned,
        int cropSeasonsCount,
        Double avgActualYield
) {
}
