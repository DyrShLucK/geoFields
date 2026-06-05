package com.geofields.repository;

import com.geofields.repository.row.FieldGeometryConflictRow;
import com.geofields.repository.row.FieldHistoryRow;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

@Repository
public class JdbcFieldRepository implements FieldRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcFieldRepository.class);

    /** Общий SELECT: поле, GeoJSON-контур и история посева (без NDVI — тайлы через Python). */
    private static final String HISTORY_SELECT_COLUMNS = """
            SELECT f.id                                      AS field_id,
                   f.field_name                              AS field_name,
                   f.field_area                              AS field_area,
                   COALESCE(f.is_active, TRUE)               AS field_active,
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
                   fc.crop_year                              AS crop_year
            """;

    /** Подтягивает название культуры из справочника crops. */
    private static final String JOIN_CROPS_ON_CROP_ID = """
            LEFT JOIN crops c ON c.crop_id = fc.crop_id
            """;

    /** Сортировка: поле, год культуры, id записи истории. */
    private static final String HISTORY_ORDER_BY = """
            ORDER BY f.id, fc.crop_year NULLS LAST, fc.history_id
            """;

    /** Все активные поля организации с историей посевов (карта /get_fields). */
    private static final String GET_FIELDS_WITH_HISTORY_SQL = HISTORY_SELECT_COLUMNS + """
            FROM fields f
            INNER JOIN field_crops fc ON fc.field_id = f.id
            """ + JOIN_CROPS_ON_CROP_ID + """
            WHERE fc.organization_id = ?
              AND COALESCE(f.is_active, TRUE)
            """ + HISTORY_ORDER_BY;

    /**
     * Поля, уже записанные как пересекающиеся с заданным fieldId в таблице field_intersections
     * (без повторного ST_Intersects; для API /api/fields/{id}/intersections).
     */
    private static final String GET_INTERSECTING_FIELDS_WITH_HISTORY_SQL = HISTORY_SELECT_COLUMNS + """
            FROM field_intersections fi
            JOIN fields f ON (
               (fi.field_id_left = ? AND f.id = fi.field_id_right)
               OR
               (fi.field_id_right = ? AND f.id = fi.field_id_left)
            )
            LEFT JOIN field_crops fc ON fc.field_id = f.id AND fc.organization_id = fi.organization_id
            """ + JOIN_CROPS_ON_CROP_ID + """
            WHERE fi.organization_id = ?
            """ + HISTORY_ORDER_BY;

    private static final RowMapper<FieldHistoryRow> FIELD_HISTORY_ROW_MAPPER = (rs, rowNum) -> new FieldHistoryRow(
            rs.getLong("field_id"),
            rs.getString("field_name"),
            rs.getBigDecimal("field_area"),
            Boolean.TRUE.equals(rs.getObject("field_active", Boolean.class)),
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
            rs.getObject("crop_year", Integer.class)
    );

    private static final RowMapper<FieldGeometryConflictRow> FIELD_GEOMETRY_CONFLICT_ROW_MAPPER =
            (rs, rowNum) -> new FieldGeometryConflictRow(rs.getLong("id"), rs.getString("field_name"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Проверяет, что поле привязано к организации через field_crops. */
    private static final String FIELD_BELONGS_TO_ORG_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM field_crops fc
                WHERE fc.field_id = ? AND fc.organization_id = ?
                LIMIT 1
            )
            """;

    /** Переводит поле в активное/неактивное (OBSOLETE), только внутри своей организации. */
    private static final String UPDATE_FIELD_ACTIVE_STATUS_SQL = """
            UPDATE fields f
            SET is_active = ?
            WHERE f.id = ?
              AND EXISTS (
                    SELECT 1
                    FROM field_crops fc
                    WHERE fc.field_id = f.id AND fc.organization_id = ?
                )
            """;

    /** Удаляет строку поля из fields (после очистки связанных данных). */
    private static final String DELETE_FIELD_SQL = """
            DELETE FROM fields
            WHERE id = ?
            """;

    /** Удаляет историю посевов поля в рамках организации. */
    private static final String DELETE_FIELD_CROPS_SQL = """
            DELETE FROM field_crops
            WHERE field_id = ?
              AND organization_id = ?
            """;

    /** Удаляет кэш NDVI Python (field_analytic) для поля. */
    private static final String DELETE_FIELD_ANALYTIC_SQL = """
            DELETE FROM field_analytic
            WHERE field_id = ?
            """;

    /**
     * Создаёт поле: контур из GeoJSON (SRID 4326), имя и площадь в га
     * (ST_Area по geography / 10000).
     */
    private static final String INSERT_FIELD_SQL = """
            INSERT INTO fields (geom, field_name, field_area, is_active)
            VALUES (
                ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)),
                ?,
                ST_Area(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)::geography) / 10000.0,
                TRUE
            )
            """;

    /**
     * Сохраняет пару пересекающихся полей (меньший id — field_id_left, больший — field_id_right).
     * Дубликаты пар игнорируются.
     */
    private static final String INSERT_FIELD_INTERSECTION_SQL = """
            INSERT INTO field_intersections (organization_id, field_id_left, field_id_right)
            VALUES (?, LEAST(?, ?), GREATEST(?, ?))
            ON CONFLICT (field_id_left, field_id_right) DO NOTHING
            """;

    /** Удаляет все записи о пересечениях, где участвует данное поле. */
    private static final String DELETE_FIELD_INTERSECTIONS_BY_FIELD_SQL = """
            DELETE FROM field_intersections
            WHERE field_id_left = ? OR field_id_right = ?
            """;

    /**
     * Ищет поля организации, чей контур пересекается с переданной GeoJSON-геометрией
     * (PostGIS ST_Intersects). Используется при создании/проверке нового поля.
     */
    private static final String INTERSECTING_FIELDS_SQL = """
            SELECT DISTINCT f.id, f.field_name
            FROM fields f
            INNER JOIN field_crops fc ON fc.field_id = f.id AND fc.organization_id = ?
            WHERE f.geom IS NOT NULL
              AND ST_Intersects(
                    f.geom,
                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)
                  )
            """;

    /** То же, что INTERSECTING_FIELDS_SQL, но без сравнения с самим собой (при редактировании поля). */
    private static final String INTERSECTING_FIELDS_EXCLUDE_SQL = INTERSECTING_FIELDS_SQL + """
              AND f.id <> ?
            """;

    @Override
    public List<FieldHistoryRow> findAllFieldsWithHistory(Long organizationId) {
        try {
            List<FieldHistoryRow> rows = queryWithHistory(organizationId);
            return rows;
        } catch (DataAccessException mainEx) {
            log.error("Field history SQL failed. Please check DB schema names and column types.", mainEx);
            throw mainEx;
        }
    }

    @Override
    public boolean fieldBelongsToOrganization(Long fieldId, Long organizationId) {
        Boolean exists = jdbcTemplate.queryForObject(
                FIELD_BELONGS_TO_ORG_SQL,
                Boolean.class,
                fieldId,
                organizationId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public int updateFieldActiveStatus(long fieldId, long organizationId, boolean active) {
        return jdbcTemplate.update(
                UPDATE_FIELD_ACTIVE_STATUS_SQL,
                active,
                fieldId,
                organizationId);
    }

    @Override
    @Transactional
    public int deleteField(long fieldId, long organizationId) {
        if (!fieldBelongsToOrganization(fieldId, organizationId)) {
            return 0;
        }
        jdbcTemplate.update(DELETE_FIELD_INTERSECTIONS_BY_FIELD_SQL, fieldId, fieldId);
        jdbcTemplate.update(DELETE_FIELD_ANALYTIC_SQL, fieldId);
        jdbcTemplate.update(DELETE_FIELD_CROPS_SQL, fieldId, organizationId);
        return jdbcTemplate.update(DELETE_FIELD_SQL, fieldId);
    }

    @Override
    public List<FieldHistoryRow> findIntersectingFieldsWithHistory(long organizationId, long fieldId) {
        return jdbcTemplate.query(
                GET_INTERSECTING_FIELDS_WITH_HISTORY_SQL,
                FIELD_HISTORY_ROW_MAPPER,
                fieldId,
                fieldId,
                organizationId);
    }

    @Override
    public long insertField(String fieldName, String geometryGeoJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(INSERT_FIELD_SQL, new String[]{"id"});
            ps.setString(1, geometryGeoJson);
            ps.setString(2, fieldName);
            ps.setString(3, geometryGeoJson);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Не удалось получить id поля после INSERT");
        }
        return key.longValue();
    }

    @Override
    public void saveFieldIntersections(long organizationId, long fieldId, Collection<Long> intersectingFieldIds) {
        if (intersectingFieldIds == null || intersectingFieldIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                DELETE_FIELD_INTERSECTIONS_BY_FIELD_SQL,
                fieldId,
                fieldId);
        jdbcTemplate.batchUpdate(
                INSERT_FIELD_INTERSECTION_SQL,
                intersectingFieldIds,
                intersectingFieldIds.size(),
                (ps, otherFieldId) -> {
                    ps.setLong(1, organizationId);
                    ps.setLong(2, fieldId);
                    ps.setLong(3, otherFieldId);
                    ps.setLong(4, fieldId);
                    ps.setLong(5, otherFieldId);
                });
    }

    @Override
    public List<FieldGeometryConflictRow> findFieldsIntersectingGeometry(
            long organizationId,
            String geometryGeoJson,
            Long excludeFieldId) {
        if (excludeFieldId == null) {
            return jdbcTemplate.query(
                    INTERSECTING_FIELDS_SQL,
                    FIELD_GEOMETRY_CONFLICT_ROW_MAPPER,
                    organizationId,
                    geometryGeoJson);
        }
        return jdbcTemplate.query(
                INTERSECTING_FIELDS_EXCLUDE_SQL,
                FIELD_GEOMETRY_CONFLICT_ROW_MAPPER,
                organizationId,
                geometryGeoJson,
                excludeFieldId);
    }

    private List<FieldHistoryRow> queryWithHistory(Long organizationId) {
        return jdbcTemplate.query(
                GET_FIELDS_WITH_HISTORY_SQL,
                ps -> ps.setLong(1, organizationId),
                FIELD_HISTORY_ROW_MAPPER);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
