package com.geofields.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcNdviAnalyticsRepository implements NdviAnalyticsRepository {

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
    public List<Long> findDistinctFieldIdsForOrganization(long organizationId) {
        return jdbcTemplate.query(
                DISTINCT_FIELD_IDS_FOR_ORG,
                (rs, rowNum) -> rs.getLong(1),
                organizationId);
    }

    @Override
    public Optional<Long> findFieldIdCoveringPoint(long organizationId, double latitude, double longitude) {
        return jdbcTemplate.query(
                        FIELD_CONTAINING_POINT,
                        (rs, rowNum) -> rs.getLong(1),
                        organizationId,
                        longitude,
                        latitude)
                .stream()
                .findFirst();
    }
}
