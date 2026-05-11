package com.geofields.repository;

import com.geofields.repository.row.CropOptionRow;
import com.geofields.repository.row.FieldCropHistoryRow;
import com.geofields.repository.row.FieldOptionRow;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFieldCropHistoryRepository implements FieldCropHistoryRepository {

    private static final String LIST_FIELDS_BASE = """
            SELECT DISTINCT f.id,
                   COALESCE(NULLIF(TRIM(f.field_name), ''), 'Поле #' || f.id::text) AS field_name
            FROM fields f
            INNER JOIN field_crops fc ON fc.field_id = f.id AND fc.organization_id = ?
            WHERE %s
            ORDER BY f.id
            """;

    private static final String LIST_FIELDS = String.format(LIST_FIELDS_BASE, "COALESCE(f.is_active, TRUE)");

    private static final String LIST_OBSOLETE_FIELDS = String.format(LIST_FIELDS_BASE, "NOT COALESCE(f.is_active, TRUE)");

    private static final String LIST_CROPS = """
            SELECT c.crop_id, c.crop_name
            FROM crops c
            ORDER BY c.crop_name
            """;

    private static final String LIST_HISTORY = """
            SELECT fc.history_id,
                   fc.crop_id,
                   c.crop_name,
                   fc.sowing_date,
                   fc.harvest_date,
                   fc.sown_area_ha,
                   fc.harvested_area_ha,
                   fc.actual_yield,
                   fc.total_yield,
                   fc.planned_yield,
                   fc.forecasted_yield,
                   fc.source_data,
                   fc.sowing_details,
                   fc.crop_year
            FROM field_crops fc
            INNER JOIN crops c ON c.crop_id = fc.crop_id
            WHERE fc.field_id = ? AND fc.organization_id = ?
            ORDER BY fc.crop_year NULLS LAST, fc.history_id
            """;

    private static final String BELONGS = """
            SELECT EXISTS (
                SELECT 1 FROM field_crops fc
                WHERE fc.history_id = ? AND fc.organization_id = ?
            )
            """;

    private static final String COUNT_HISTORY_BY_FIELD_SQL = """
            SELECT COUNT(*)
            FROM field_crops fc
            WHERE fc.field_id = ? AND fc.organization_id = ?
            """;

    private static final String FIND_FIELD_ID_BY_HISTORY_SQL = """
            SELECT fc.field_id
            FROM field_crops fc
            WHERE fc.history_id = ? AND fc.organization_id = ?
            LIMIT 1
            """;

    private static final String CROP_EXISTS = """
            SELECT crop_id FROM crops WHERE crop_id = ? LIMIT 1
            """;

    private static final String INSERT_HISTORY = """
            INSERT INTO field_crops (
                crop_id, field_id, organization_id,
                sowing_date, harvest_date,
                sown_area_ha, harvested_area_ha,
                actual_yield, total_yield, planned_yield, forecasted_yield,
                source_data, sowing_details, crop_year
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String UPDATE_HISTORY = """
            UPDATE field_crops fc SET
                crop_id = ?,
                sowing_date = ?,
                harvest_date = ?,
                sown_area_ha = ?,
                harvested_area_ha = ?,
                actual_yield = ?,
                total_yield = ?,
                planned_yield = ?,
                forecasted_yield = ?,
                source_data = ?,
                sowing_details = ?,
                crop_year = ?
            WHERE fc.history_id = ? AND fc.organization_id = ?
            """;

    private static final String DELETE_HISTORY = """
            DELETE FROM field_crops fc WHERE fc.history_id = ? AND fc.organization_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcFieldCropHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FieldOptionRow> listFieldsForOrganization(long organizationId) {
        return jdbcTemplate.query(
                LIST_FIELDS,
                this::mapFieldOptionRow,
                organizationId);
    }

    @Override
    public List<FieldOptionRow> listObsoleteFieldsForOrganization(long organizationId) {
        return jdbcTemplate.query(
                LIST_OBSOLETE_FIELDS,
                this::mapFieldOptionRow,
                organizationId);
    }

    @Override
    public List<CropOptionRow> listAllCrops() {
        return jdbcTemplate.query(
                LIST_CROPS,
                this::mapCropOptionRow);
    }

    @Override
    public List<FieldCropHistoryRow> findHistoryByFieldAndOrganization(long fieldId, long organizationId) {
        return jdbcTemplate.query(
                LIST_HISTORY,
                this::mapHistoryRow,
                fieldId,
                organizationId);
    }

    @Override
    public boolean historyBelongsToOrganization(long historyId, long organizationId) {
        Boolean ok = jdbcTemplate.queryForObject(BELONGS, Boolean.class, historyId, organizationId);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public long countHistoryByFieldAndOrganization(long fieldId, long organizationId) {
        Long count = jdbcTemplate.queryForObject(
                COUNT_HISTORY_BY_FIELD_SQL,
                Long.class,
                fieldId,
                organizationId);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<Long> findFieldIdByHistoryId(long historyId, long organizationId) {
        try {
            Long fieldId = jdbcTemplate.queryForObject(
                    FIND_FIELD_ID_BY_HISTORY_SQL,
                    Long.class,
                    historyId,
                    organizationId);
            return Optional.ofNullable(fieldId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public long insertHistory(
            long fieldId,
            long organizationId,
            long cropId,
            LocalDate sowingDate,
            LocalDate harvestDate,
            BigDecimal sownAreaHa,
            BigDecimal harvestAreaHa,
            BigDecimal actualYield,
            BigDecimal totalYield,
            BigDecimal plannedYield,
            BigDecimal forecastedYield,
            String sourceData,
            String sowingDetails,
            Integer cropYear) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_HISTORY, new String[]{"history_id"});
            ps.setLong(1, cropId);
            ps.setLong(2, fieldId);
            ps.setLong(3, organizationId);
            ps.setObject(4, sowingDate);
            ps.setObject(5, harvestDate);
            ps.setObject(6, sownAreaHa);
            ps.setObject(7, harvestAreaHa);
            ps.setObject(8, actualYield);
            ps.setObject(9, totalYield);
            ps.setObject(10, plannedYield);
            ps.setObject(11, forecastedYield);
            ps.setString(12, emptyToNull(sourceData));
            ps.setString(13, emptyToNull(sowingDetails));
            ps.setObject(14, cropYear);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Не удалось получить history_id после INSERT");
        }
        return key.longValue();
    }

    @Override
    public int updateHistory(
            long historyId,
            long organizationId,
            long cropId,
            LocalDate sowingDate,
            LocalDate harvestDate,
            BigDecimal sownAreaHa,
            BigDecimal harvestAreaHa,
            BigDecimal actualYield,
            BigDecimal totalYield,
            BigDecimal plannedYield,
            BigDecimal forecastedYield,
            String sourceData,
            String sowingDetails,
            Integer cropYear) {
        return jdbcTemplate.update(
                UPDATE_HISTORY,
                cropId,
                sowingDate,
                harvestDate,
                sownAreaHa,
                harvestAreaHa,
                actualYield,
                totalYield,
                plannedYield,
                forecastedYield,
                emptyToNull(sourceData),
                emptyToNull(sowingDetails),
                cropYear,
                historyId,
                organizationId);
    }

    @Override
    public int deleteHistory(long historyId, long organizationId) {
        return jdbcTemplate.update(
                DELETE_HISTORY,
                historyId,
                organizationId);
    }

    @Override
    public Optional<Long> findCropIdIfExists(long cropId) {
        try {
            Long id = jdbcTemplate.queryForObject(CROP_EXISTS, Long.class, cropId);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private FieldCropHistoryRow mapHistoryRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FieldCropHistoryRow(
                rs.getLong("history_id"),
                rs.getLong("crop_id"),
                rs.getString("crop_name"),
                rs.getObject("sowing_date", LocalDate.class),
                rs.getObject("harvest_date", LocalDate.class),
                rs.getBigDecimal("sown_area_ha"),
                rs.getBigDecimal("harvested_area_ha"),
                rs.getBigDecimal("actual_yield"),
                rs.getBigDecimal("total_yield"),
                rs.getBigDecimal("planned_yield"),
                rs.getBigDecimal("forecasted_yield"),
                rs.getString("source_data"),
                rs.getString("sowing_details"),
                rs.getObject("crop_year", Integer.class));
    }

    private FieldOptionRow mapFieldOptionRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FieldOptionRow(rs.getLong("id"), rs.getString("field_name"));
    }

    private CropOptionRow mapCropOptionRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CropOptionRow(rs.getLong("crop_id"), rs.getString("crop_name"));
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
