package com.geofields.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    /** Id организации по точному совпадению названия. */
    private static final String FIND_ID_BY_NAME_SQL = "SELECT id FROM organizations WHERE name = ?";

    /** Название организации по id. */
    private static final String FIND_NAME_BY_ID_SQL = "SELECT name FROM organizations WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findIdByName(String name) {
        List<Long> ids = jdbcTemplate.query(
                FIND_ID_BY_NAME_SQL,
                (rs, rowNum) -> rs.getLong("id"),
                name
        );
        return ids.stream().findFirst();
    }

    @Override
    public Optional<String> findNameById(long organizationId) {
        List<String> names = jdbcTemplate.query(
                FIND_NAME_BY_ID_SQL,
                (rs, rowNum) -> rs.getString("name"),
                organizationId
        );
        return names.stream().findFirst();
    }
}
