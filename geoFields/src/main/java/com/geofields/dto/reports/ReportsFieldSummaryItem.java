package com.geofields.dto.reports;

public record ReportsFieldSummaryItem(
        long fieldId,
        String fieldName,
        Double areaHa,
        long operationsCount,
        long completedOperations,
        String latestCropName,
        Integer latestCropYear,
        Double latestYield
) {
}
