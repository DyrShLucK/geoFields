package com.geofields.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcNdviAnalyticsRepository implements NdviAnalyticsRepository {

    private static final String NDVI_URL_FOR_FIELD_ON_DATE = """
            SELECT nd.url
            FROM field_analytics fa
            INNER JOIN field_crops fc ON fc.history_id = fa.field_crop_id
            LEFT JOIN ndvi_data nd ON nd.id = fa.ndvi_id
            WHERE fc.field_id = ?
              AND fc.organization_id = ?
              AND fa.record_date = ?
              AND fa.ndvi_id IS NOT NULL
              AND nd.url IS NOT NULL
              AND TRIM(nd.url) <> ''
            ORDER BY fa.id DESC
            LIMIT 1
            """;

    private static final String DISTINCT_FIELD_IDS_FOR_ORG = """
            SELECT DISTINCT fc.field_id
            FROM field_crops fc
            WHERE fc.organization_id = ?
            ORDER BY fc.field_id
            """;

    private static final String FIELD_CONTAINING_POINT = """
            SELECT f.id
            FROM fields f
            INNER JOIN field_crops fc ON fc.field_id = f.id AND fc.organization_id = ?
            WHERE f.geom IS NOT NULL
              AND ST_Contains(f.geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcNdviAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findNdviTileUrlForFieldOnDate(long fieldId, long organizationId, LocalDate recordDate) {
        List<String> rows = jdbcTemplate.query(
                NDVI_URL_FOR_FIELD_ON_DATE,
                (rs, rowNum) -> rs.getString(1),
                fieldId,
                organizationId,
                recordDate);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String url = rows.getFirst();
        return url == null || url.isBlank() ? Optional.empty() : Optional.of(url);
    }

    @Override
    public List<Long> findDistinctFieldIdsForOrganization(long organizationId) {
        return jdbcTemplate.query(
                DISTINCT_FIELD_IDS_FOR_ORG,
                (rs, rowNum) -> rs.getLong(1),
                organizationId);
    }

    @Override
    public Optional<Long> findFieldIdCoveringPoint(long organizationId, double latitude, double longitude) {
        // PostGIS: ST_MakePoint(lon, lat)
        List<Long> rows = jdbcTemplate.query(
                FIELD_CONTAINING_POINT,
                (rs, rowNum) -> rs.getLong(1),
                organizationId,
                longitude,
                latitude);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }
}
