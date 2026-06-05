package com.geofields.repository.row;

public record ReportsOverviewRow(
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
