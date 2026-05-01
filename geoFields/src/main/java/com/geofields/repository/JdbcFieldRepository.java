package com.geofields.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Repository
public class JdbcFieldRepository implements FieldRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcFieldRepository.class);

    // Основной запрос под текущую схему (crops + history_id + harvested_area_ha).
    private static final String GET_FIELDS_WITH_HISTORY_SQL = """
            SELECT f.id                                      AS field_id,
                   f.field_name                              AS field_name,
                   f.field_area                              AS field_area,
                   ST_AsGeoJSON(f.geom)                      AS geometry,
                   fc.history_id                             AS field_crop_id,
                   c.crop_id                                 AS crop_id,
                   c.crop_name                               AS crop_name,
                   fc.sowing_date                            AS sowing_date,
                   fc.harvest_date                           AS harvest_date,
                   fc.sown_area_ha                           AS sown_area_ha,
                   fc.harvested_area_ha                      AS harvest_area_ha,
                   fc.actual_yield                           AS actual_yield,
                   fc.total_yield                            AS total_yield,
                   fc.planned_yield                          AS planned_yield,
                   fc.forecasted_yield                       AS forecasted_yield,
                   fc.source_data                            AS source_data,
                   fc.sowing_details                         AS sowing_details,
                   fc.crop_year                              AS crop_year,
                   fa_latest.analytics_date                  AS analytics_date,
                   fa_latest.ndvi_url                        AS ndvi_url,
                   fa_latest.ndvi_created_at                 AS ndvi_created_at
            FROM fields f
                     INNER JOIN field_crops fc ON fc.field_id = f.id
                     LEFT JOIN crops c ON c.crop_id = fc.crop_id
                     LEFT JOIN LATERAL (
                        SELECT fa.record_date AS analytics_date,
                               nd.url         AS ndvi_url,
                               nd.created_at  AS ndvi_created_at
                        FROM field_analytics fa
                        LEFT JOIN ndvi_data nd ON nd.id = fa.ndvi_id
                        WHERE fa.field_crop_id = fc.history_id
                        ORDER BY fa.record_date DESC, fa.id DESC
                        LIMIT 1
                     ) fa_latest ON TRUE
            WHERE fc.organization_id = ?
            ORDER BY f.id, fc.crop_year NULLS LAST, fc.history_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Отдаем плоский список строк, сервис сгруппирует их по полю.
    @Override
    public List<FieldHistoryRow> findAllFieldsWithHistory(Long organizationId) {
        try {
            List<FieldHistoryRow> rows = queryWithHistory(GET_FIELDS_WITH_HISTORY_SQL, organizationId);
            log.info("Loaded {} field history rows for organization {}", rows.size(), organizationId);
            return rows;
        } catch (DataAccessException mainEx) {
            log.error("Field history SQL failed. Please check DB schema names and column types.", mainEx);
            throw mainEx;
        }
    }

    private List<FieldHistoryRow> queryWithHistory(String sql, Long organizationId) {
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, organizationId), (rs, rowNum) -> new FieldHistoryRow(
                rs.getLong("field_id"),
                rs.getString("field_name"),
                rs.getBigDecimal("field_area"),
                rs.getString("geometry"),
                toLong(rs.getObject("field_crop_id")),
                toLong(rs.getObject("crop_id")),
                rs.getString("crop_name"),
                rs.getObject("sowing_date", java.time.LocalDate.class),
                rs.getObject("harvest_date", java.time.LocalDate.class),
                rs.getBigDecimal("sown_area_ha"),
                rs.getBigDecimal("harvest_area_ha"),
                rs.getBigDecimal("actual_yield"),
                rs.getBigDecimal("total_yield"),
                rs.getBigDecimal("planned_yield"),
                rs.getBigDecimal("forecasted_yield"),
                rs.getString("source_data"),
                rs.getString("sowing_details"),
                rs.getObject("crop_year", Integer.class),
                rs.getObject("analytics_date", java.time.LocalDate.class),
                rs.getString("ndvi_url"),
                rs.getObject("ndvi_created_at", java.time.LocalDateTime.class)
        ));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
