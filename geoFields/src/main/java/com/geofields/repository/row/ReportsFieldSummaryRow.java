package com.geofields.repository.row;

public record ReportsFieldSummaryRow(
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
