package com.geofields.repository;

import com.geofields.repository.row.ReportsCountRow;
import com.geofields.repository.row.ReportsCropAreaRow;
import com.geofields.repository.row.ReportsFieldSummaryRow;
import com.geofields.repository.row.ReportsOverviewRow;
import com.geofields.repository.row.ReportsRecentOperationRow;
import com.geofields.repository.row.ReportsTimelineRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcReportsRepository implements ReportsRepository {

    private static final String FIELD_IN_ORG = """
            EXISTS (
                SELECT 1 FROM field_crops fc
                WHERE fc.field_id = f.id AND fc.organization_id = ?
            )
            """;
    private static final List<String> IN_PROGRESS_STATUSES = List.of("STARTED", "IN_PROGRESS");

    private final JdbcTemplate jdbcTemplate;

    public JdbcReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReportsOverviewRow loadOverview(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        int fieldsInScope = countFieldsInScope(organizationId, fieldIds);
        double totalAreaHa = sumFieldArea(organizationId, fieldIds);
        long operationsTotal = countOperations(organizationId, fieldIds, from, to, null);
        long completed = countOperations(organizationId, fieldIds, from, to, "COMPLETED");
        long planned = countOperations(organizationId, fieldIds, from, to, "PLANNED");
        long inProgressExact = countOperationsInStatuses(organizationId, fieldIds, from, to, IN_PROGRESS_STATUSES);
        long overdue = countOverduePlanned(organizationId, fieldIds, from, to);
        int cropSeasons = countCropSeasons(organizationId, fieldIds, from, to);
        Double avgYield = averageYield(organizationId, fieldIds, from, to);
        return new ReportsOverviewRow(
                fieldsInScope,
                totalAreaHa,
                operationsTotal,
                completed,
                planned,
                inProgressExact,
                overdue,
                cropSeasons,
                avgYield);
    }

    @Override
    public List<ReportsCountRow> countOperationsByCategory(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String sql = """
                SELECT fo.category AS code,
                       cat.title_ru AS label,
                       COUNT(*) AS cnt
                FROM field_operations fo
                JOIN field_operation_category cat ON cat.code = fo.category
                WHERE fo.organization_id = ?
                """ + filter.clause() + """
                \sGROUP BY fo.category, cat.title_ru
                ORDER BY cnt DESC, cat.title_ru
                """;
        return jdbcTemplate.query(sql, (rs, n) -> new ReportsCountRow(
                rs.getString("code"),
                rs.getString("label"),
                rs.getLong("cnt")), concatArgs(organizationId, filter.args()));
    }

    @Override
    public List<ReportsCountRow> countOperationsByStatus(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String sql = """
                SELECT fo.status AS code,
                       st.title_ru AS label,
                       COUNT(*) AS cnt
                FROM field_operations fo
                JOIN field_operation_status st ON st.code = fo.status
                WHERE fo.organization_id = ?
                """ + filter.clause() + """
                \sGROUP BY fo.status, st.title_ru, st.sort_order
                ORDER BY st.sort_order, cnt DESC
                """;
        return jdbcTemplate.query(sql, (rs, n) -> new ReportsCountRow(
                rs.getString("code"),
                rs.getString("label"),
                rs.getLong("cnt")), concatArgs(organizationId, filter.args()));
    }

    @Override
    public List<ReportsTimelineRow> operationsTimeline(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String sql = """
                SELECT TO_CHAR(DATE_TRUNC('month', fo.operation_at), 'YYYY-MM') AS period,
                       COUNT(*) AS cnt
                FROM field_operations fo
                WHERE fo.organization_id = ?
                """ + filter.clause() + """
                \sGROUP BY DATE_TRUNC('month', fo.operation_at)
                ORDER BY period
                """;
        return jdbcTemplate.query(sql, (rs, n) -> new ReportsTimelineRow(
                rs.getString("period"),
                rs.getLong("cnt")), concatArgs(organizationId, filter.args()));
    }

    @Override
    public List<ReportsCropAreaRow> cropAreasByCrop(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildCropFilter(fieldIds, from, to);
        String sql = """
                SELECT c.crop_name AS crop_name,
                       SUM(fc.sown_area_ha) AS total_area,
                       AVG(fc.actual_yield) AS avg_yield
                FROM field_crops fc
                JOIN crops c ON c.crop_id = fc.crop_id
                WHERE fc.organization_id = ?
                """ + filter.clause() + """
                \sGROUP BY c.crop_name
                ORDER BY total_area DESC NULLS LAST, c.crop_name
                """;
        return jdbcTemplate.query(sql, (rs, n) -> new ReportsCropAreaRow(
                rs.getString("crop_name"),
                toDouble(rs.getObject("total_area")),
                toDouble(rs.getObject("avg_yield"))), concatArgs(organizationId, filter.args()));
    }

    @Override
    public List<ReportsFieldSummaryRow> fieldSummaries(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts fieldFilter = buildFieldIdFilter(fieldIds);
        FilterParts opFilter = buildOperationFilter(fieldIds, from, to);
        String sql = """
                SELECT f.id AS field_id,
                       f.field_name AS field_name,
                       f.field_area AS area_ha,
                       COALESCE(op_stats.total_ops, 0) AS operations_count,
                       COALESCE(op_stats.completed_ops, 0) AS completed_ops,
                       crop_latest.crop_name AS latest_crop_name,
                       crop_latest.crop_year AS latest_crop_year,
                       crop_latest.actual_yield AS latest_yield
                FROM fields f
                LEFT JOIN (
                    SELECT fo.field_id,
                           COUNT(*) AS total_ops,
                           COUNT(*) FILTER (WHERE fo.status = 'COMPLETED') AS completed_ops
                    FROM field_operations fo
                    WHERE fo.organization_id = ?
                    """ + opFilter.clause() + """
                    \sGROUP BY fo.field_id
                ) op_stats ON op_stats.field_id = f.id
                LEFT JOIN LATERAL (
                    SELECT c.crop_name, fc.crop_year, fc.actual_yield
                    FROM field_crops fc
                    JOIN crops c ON c.crop_id = fc.crop_id
                    WHERE fc.field_id = f.id AND fc.organization_id = ?
                    ORDER BY fc.crop_year DESC NULLS LAST, fc.history_id DESC
                    LIMIT 1
                ) crop_latest ON TRUE
                WHERE COALESCE(f.is_active, TRUE)
                  AND \s""" + FIELD_IN_ORG + fieldFilter.clause() + """
                \sORDER BY f.field_name
                """;
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        args.addAll(opFilter.args());
        args.add(organizationId);
        args.add(organizationId);
        args.addAll(fieldFilter.args());
        return jdbcTemplate.query(sql, (rs, n) -> new ReportsFieldSummaryRow(
                rs.getLong("field_id"),
                rs.getString("field_name"),
                toDouble(rs.getObject("area_ha")),
                rs.getLong("operations_count"),
                rs.getLong("completed_ops"),
                rs.getString("latest_crop_name"),
                toInteger(rs.getObject("latest_crop_year")),
                toDouble(rs.getObject("latest_yield"))), args.toArray());
    }

    @Override
    public List<ReportsRecentOperationRow> recentOperations(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to,
            int limit) {
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String sql = """
                SELECT fo.id,
                       fo.field_id,
                       f.field_name,
                       fo.name,
                       cat.title_ru AS category_title_ru,
                       st.title_ru AS status_title_ru,
                       fo.operation_at
                FROM field_operations fo
                JOIN fields f ON f.id = fo.field_id
                JOIN field_operation_category cat ON cat.code = fo.category
                JOIN field_operation_status st ON st.code = fo.status
                WHERE fo.organization_id = ?
                """ + filter.clause() + """
                \sORDER BY fo.operation_at DESC, fo.id DESC
                LIMIT ?
                """;
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        args.addAll(filter.args());
        args.add(limit);
        return jdbcTemplate.query(sql, (rs, n) -> new ReportsRecentOperationRow(
                rs.getLong("id"),
                rs.getLong("field_id"),
                rs.getString("field_name"),
                rs.getString("name"),
                rs.getString("category_title_ru"),
                rs.getString("status_title_ru"),
                rs.getTimestamp("operation_at").toLocalDateTime()), args.toArray());
    }

    private int countFieldsInScope(long organizationId, List<Long> fieldIds) {
        FilterParts filter = buildFieldIdFilter(fieldIds);
        String sql = """
                SELECT COUNT(DISTINCT f.id)
                FROM fields f
                WHERE COALESCE(f.is_active, TRUE)
                  AND \s""" + FIELD_IN_ORG + filter.clause();
        Integer n = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                concatArgs(organizationId, filter.args()));
        return n != null ? n : 0;
    }

    private double sumFieldArea(long organizationId, List<Long> fieldIds) {
        FilterParts filter = buildFieldIdFilter(fieldIds);
        String sql = """
                SELECT COALESCE(SUM(sub.area), 0)
                FROM (
                    SELECT DISTINCT f.id, f.field_area AS area
                    FROM fields f
                    WHERE COALESCE(f.is_active, TRUE)
                      AND \s""" + FIELD_IN_ORG + filter.clause() + """
                ) sub
                """;
        // org id + field filter args only once inside subquery
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        args.addAll(filter.args());
        return queryDoubleOrDefault(sql, 0.0, args.toArray());
    }

    private long countOperations(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to,
            String status) {
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String statusClause = status != null ? " AND fo.status = ?" : "";
        String sql = """
                SELECT COUNT(*)
                FROM field_operations fo
                WHERE fo.organization_id = ?
                """ + filter.clause() + statusClause;
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        args.addAll(filter.args());
        if (status != null) {
            args.add(status);
        }
        return queryLongOrDefault(sql, 0L, args.toArray());
    }

    private long countOperationsInStatuses(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to,
            List<String> statuses) {
        if (statuses.isEmpty()) {
            return 0L;
        }
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String in = String.join(",", statuses.stream().map(s -> "?").toList());
        String sql = """
                SELECT COUNT(*)
                FROM field_operations fo
                WHERE fo.organization_id = ?
                """ + filter.clause() + " AND fo.status IN (" + in + ")";
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        args.addAll(filter.args());
        args.addAll(statuses);
        return queryLongOrDefault(sql, 0L, args.toArray());
    }

    private long countOverduePlanned(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildOperationFilter(fieldIds, from, to);
        String sql = """
                SELECT COUNT(*)
                FROM field_operations fo
                WHERE fo.organization_id = ?
                  AND fo.status = 'PLANNED'
                  AND fo.operation_at < CURRENT_TIMESTAMP
                """ + filter.clause();
        return queryLongOrDefault(sql, 0L, concatArgs(organizationId, filter.args()));
    }

    private int countCropSeasons(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildCropFilter(fieldIds, from, to);
        String sql = """
                SELECT COUNT(DISTINCT fc.history_id)
                FROM field_crops fc
                WHERE fc.organization_id = ?
                """ + filter.clause();
        return queryIntOrDefault(sql, 0, concatArgs(organizationId, filter.args()));
    }

    private Double averageYield(
            long organizationId,
            List<Long> fieldIds,
            LocalDateTime from,
            LocalDateTime to) {
        FilterParts filter = buildCropFilter(fieldIds, from, to);
        String sql = """
                SELECT AVG(fc.actual_yield)
                FROM field_crops fc
                WHERE fc.organization_id = ?
                  AND fc.actual_yield IS NOT NULL
                """ + filter.clause();
        return jdbcTemplate.queryForObject(
                sql,
                Double.class,
                concatArgs(organizationId, filter.args()));
    }

    private FilterParts buildFieldIdFilter(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return new FilterParts("", List.of());
        }
        return new FilterParts(" AND f.id IN (" + placeholders(fieldIds.size()) + ")", new ArrayList<>(fieldIds));
    }

    private FilterParts buildOperationFilter(List<Long> fieldIds, LocalDateTime from, LocalDateTime to) {
        StringBuilder clause = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendFieldIdsFilter(clause, args, fieldIds, "fo.field_id");
        if (from != null) {
            clause.append(" AND fo.operation_at >= ?");
            args.add(from);
        }
        if (to != null) {
            clause.append(" AND fo.operation_at <= ?");
            args.add(to);
        }
        return new FilterParts(clause.toString(), args);
    }

    private FilterParts buildCropFilter(List<Long> fieldIds, LocalDateTime from, LocalDateTime to) {
        StringBuilder clause = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendFieldIdsFilter(clause, args, fieldIds, "fc.field_id");
        if (from != null) {
            clause.append(" AND (fc.sowing_date >= ? OR fc.harvest_date >= ? OR fc.crop_year >= ?)");
            int year = from.getYear();
            args.add(from.toLocalDate());
            args.add(from.toLocalDate());
            args.add(year);
        }
        if (to != null) {
            clause.append(" AND (fc.sowing_date <= ? OR fc.harvest_date <= ? OR fc.crop_year <= ?)");
            int year = to.getYear();
            args.add(to.toLocalDate());
            args.add(to.toLocalDate());
            args.add(year);
        }
        return new FilterParts(clause.toString(), args);
    }

    private static Object[] concatArgs(Object first, List<Object> rest) {
        List<Object> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all.toArray();
    }

    private static void appendFieldIdsFilter(StringBuilder clause, List<Object> args, List<Long> fieldIds, String column) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return;
        }
        clause.append(" AND ").append(column).append(" IN (").append(placeholders(fieldIds.size())).append(")");
        args.addAll(fieldIds);
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private record FilterParts(String clause, List<Object> args) {
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private long queryLongOrDefault(String sql, long fallback, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value != null ? value : fallback;
    }

    private int queryIntOrDefault(String sql, int fallback, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value != null ? value : fallback;
    }

    private double queryDoubleOrDefault(String sql, double fallback, Object... args) {
        Double value = jdbcTemplate.queryForObject(sql, Double.class, args);
        return value != null ? value : fallback;
    }
}
