package com.geofields.dto.reports;

import java.time.LocalDate;
import java.util.List;

public record ReportsDashboardResponse(
        long organizationId,
        String organizationName,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<Long> fieldIds,
        ReportsOverviewDto overview,
        List<ReportsCountItem> operationsByCategory,
        List<ReportsCountItem> operationsByStatus,
        List<ReportsTimelineItem> operationsTimeline,
        List<ReportsCropAreaItem> cropAreas,
        List<ReportsFieldSummaryItem> fieldSummaries,
        List<ReportsRecentOperationItem> recentOperations
) {
}
