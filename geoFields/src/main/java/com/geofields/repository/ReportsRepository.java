package com.geofields.repository;

import com.geofields.repository.row.ReportsCountRow;
import com.geofields.repository.row.ReportsCropAreaRow;
import com.geofields.repository.row.ReportsFieldSummaryRow;
import com.geofields.repository.row.ReportsOverviewRow;
import com.geofields.repository.row.ReportsRecentOperationRow;
import com.geofields.repository.row.ReportsTimelineRow;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportsRepository {

    ReportsOverviewRow loadOverview(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to);

    List<ReportsCountRow> countOperationsByCategory(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to);

    List<ReportsCountRow> countOperationsByStatus(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to);

    List<ReportsTimelineRow> operationsTimeline(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to);

    List<ReportsCropAreaRow> cropAreasByCrop(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to);

    List<ReportsFieldSummaryRow> fieldSummaries(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to);

    List<ReportsRecentOperationRow> recentOperations(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to,
            int limit);
}
