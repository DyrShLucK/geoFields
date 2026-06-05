package com.geofields.service;

import com.geofields.dto.reports.ReportsCountItem;
import com.geofields.dto.reports.ReportsCropAreaItem;
import com.geofields.dto.reports.ReportsDashboardResponse;
import com.geofields.dto.reports.ReportsFieldSummaryItem;
import com.geofields.dto.reports.ReportsOverviewDto;
import com.geofields.dto.reports.ReportsRecentOperationItem;
import com.geofields.dto.reports.ReportsTimelineItem;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.repository.ReportsRepository;
import com.geofields.repository.row.ReportsCountRow;
import com.geofields.repository.row.ReportsCropAreaRow;
import com.geofields.repository.row.ReportsFieldSummaryRow;
import com.geofields.repository.row.ReportsOverviewRow;
import com.geofields.repository.row.ReportsRecentOperationRow;
import com.geofields.repository.row.ReportsTimelineRow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReportsService {

    private static final int RECENT_OPERATIONS_LIMIT = 20;

    private final ReportsRepository reportsRepository;
    private final FieldRepository fieldRepository;
    private final OrganizationRepository organizationRepository;

    public ReportsService(
            ReportsRepository reportsRepository,
            FieldRepository fieldRepository,
            OrganizationRepository organizationRepository) {
        this.reportsRepository = reportsRepository;
        this.fieldRepository = fieldRepository;
        this.organizationRepository = organizationRepository;
    }

    public ReportsDashboardResponse buildDashboard(
            long organizationId,
            String fieldIdsParam,
            LocalDate dateFrom,
            LocalDate dateTo) {
        DateRange period = resolveDateRange(dateFrom, dateTo);

        List<Long> fieldIds = parseAndValidateFieldIds(fieldIdsParam, organizationId);
        LocalDateTime fromDt = period.from().atStartOfDay();
        LocalDateTime toDt = period.to().atTime(LocalTime.MAX);

        String orgName = organizationRepository.findNameById(organizationId)
                .orElse("Организация #" + organizationId);

        ReportsOverviewRow overview = reportsRepository.loadOverview(organizationId, fieldIds, fromDt, toDt);

        return new ReportsDashboardResponse(
                organizationId,
                orgName,
                period.from(),
                period.to(),
                fieldIds,
                toOverview(overview),
                mapCounts(reportsRepository.countOperationsByCategory(organizationId, fieldIds, fromDt, toDt)),
                mapCounts(reportsRepository.countOperationsByStatus(organizationId, fieldIds, fromDt, toDt)),
                mapTimeline(reportsRepository.operationsTimeline(organizationId, fieldIds, fromDt, toDt)),
                mapCropAreas(reportsRepository.cropAreasByCrop(organizationId, fieldIds, fromDt, toDt)),
                mapFieldSummaries(reportsRepository.fieldSummaries(organizationId, fieldIds, fromDt, toDt)),
                mapRecent(reportsRepository.recentOperations(
                        organizationId, fieldIds, fromDt, toDt, RECENT_OPERATIONS_LIMIT)));
    }

    private List<Long> parseAndValidateFieldIds(String fieldIdsParam, long organizationId) {
        if (fieldIdsParam == null || fieldIdsParam.isBlank()) {
            return List.of();
        }
        List<Long> ids;
        try {
            ids = FieldIdRequestParser.parseFieldIds(List.of(fieldIdsParam));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex.getMessage().replace("field_id", "id поля"));
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        for (Long id : uniqueIds) {
            if (!fieldRepository.fieldBelongsToOrganization(id, organizationId)) {
                throw new IllegalArgumentException("Поле не найдено в организации: " + id);
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    private static DateRange resolveDateRange(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate from = dateFrom != null ? dateFrom : LocalDate.now().minusMonths(12);
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        return to.isBefore(from) ? new DateRange(to, from) : new DateRange(from, to);
    }

    private static ReportsOverviewDto toOverview(ReportsOverviewRow row) {
        return new ReportsOverviewDto(
                row.fieldsInScope(),
                row.totalAreaHa(),
                row.operationsTotal(),
                row.operationsCompleted(),
                row.operationsPlanned(),
                row.operationsInProgress(),
                row.overduePlanned(),
                row.cropSeasonsCount(),
                row.avgActualYield());
    }

    private static List<ReportsCountItem> mapCounts(List<ReportsCountRow> rows) {
        return rows.stream()
                .map(r -> new ReportsCountItem(r.code(), r.label(), r.count()))
                .toList();
    }

    private static List<ReportsTimelineItem> mapTimeline(List<ReportsTimelineRow> rows) {
        return rows.stream()
                .map(r -> new ReportsTimelineItem(r.period(), r.count()))
                .toList();
    }

    private static List<ReportsCropAreaItem> mapCropAreas(List<ReportsCropAreaRow> rows) {
        return rows.stream()
                .map(r -> new ReportsCropAreaItem(r.cropName(), r.totalAreaHa(), r.avgYield()))
                .toList();
    }

    private static List<ReportsFieldSummaryItem> mapFieldSummaries(List<ReportsFieldSummaryRow> rows) {
        return rows.stream()
                .map(r -> new ReportsFieldSummaryItem(
                        r.fieldId(),
                        r.fieldName(),
                        r.areaHa(),
                        r.operationsCount(),
                        r.completedOperations(),
                        r.latestCropName(),
                        r.latestCropYear(),
                        r.latestYield()))
                .toList();
    }

    private static List<ReportsRecentOperationItem> mapRecent(List<ReportsRecentOperationRow> rows) {
        return rows.stream()
                .map(r -> new ReportsRecentOperationItem(
                        r.id(),
                        r.fieldId(),
                        r.fieldName(),
                        r.name(),
                        r.categoryTitleRu(),
                        r.statusTitleRu(),
                        r.operationAt()))
                .toList();
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
