package com.geofields.security;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GeoFieldsUserDetailsService implements UserDetailsService {

    private static final String LOAD_USER_SQL = """
            SELECT id, login, password_hash, organization_id, role, is_active,
                   COALESCE(NULLIF(TRIM(registration_status), ''), 'APPROVED') AS registration_status,
                   COALESCE(last_name, '') AS last_name,
                   COALESCE(first_name, '') AS first_name,
                   COALESCE(middle_name, '') AS middle_name
            FROM users
            WHERE login = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public GeoFieldsUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return jdbcTemplate.queryForObject(
                    LOAD_USER_SQL,
                    (rs, rowNum) -> {
                        Boolean activeCol = rs.getObject("is_active", Boolean.class);
                        boolean activeFlag = activeCol == null || activeCol;
                        String reg = rs.getString("registration_status");
                        boolean approved = reg == null || reg.isBlank() || "APPROVED".equalsIgnoreCase(reg);
                        boolean enabled = activeFlag && approved;
                        return new GeoFieldsUserDetails(
                                rs.getLong("id"),
                                rs.getLong("organization_id"),
                                rs.getString("login"),
                                rs.getString("password_hash"),
                                UserRole.fromDatabase(rs.getString("role")),
                                enabled,
                                rs.getString("last_name"),
                                rs.getString("first_name"),
                                rs.getString("middle_name"));
                    },
                    username
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new UsernameNotFoundException("Пользователь не найден");
        }
    }
}
