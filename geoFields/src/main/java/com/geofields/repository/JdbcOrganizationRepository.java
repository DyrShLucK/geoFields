package com.geofields.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findIdByName(String name) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM organizations WHERE name = ?",
                (rs, rowNum) -> rs.getLong("id"),
                name
        );
        return ids.stream().findFirst();
    }

    @Override
    public Optional<String> findNameById(long organizationId) {
        List<String> names = jdbcTemplate.query(
                "SELECT name FROM organizations WHERE id = ?",
                (rs, rowNum) -> rs.getString("name"),
                organizationId
        );
        return names.stream().findFirst();
    }
}
