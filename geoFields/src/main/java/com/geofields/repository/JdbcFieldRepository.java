package com.geofields.repository;

import com.geofields.model.Fields;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcFieldRepository implements FieldRepository {

    // Берем геометрию сразу как GeoJSON, чтобы не парсить WKT вручную.
    private static final String GET_FIELDS_SQL = """
            SELECT id,
                   field_name,
                   field_area,
                   ST_AsGeoJSON(geom) AS geometry
            FROM fields
            ORDER BY id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Простая выборка всех полей для дальнейшего маппинга в FeatureCollection.
    @Override
    public List<Fields> findAllFields() {
        return jdbcTemplate.query(
                GET_FIELDS_SQL,
                (rs, rowNum) -> new Fields(
                        rs.getLong("id"),
                        rs.getString("field_name"),
                        rs.getBigDecimal("field_area"),
                        rs.getString("geometry")
                )
        );
    }
}
