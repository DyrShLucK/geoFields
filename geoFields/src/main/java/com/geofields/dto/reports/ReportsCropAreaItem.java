package com.geofields.dto.reports;

public record ReportsCropAreaItem(
        String cropName,
        Double totalAreaHa,
        Double avgYield
) {
}
