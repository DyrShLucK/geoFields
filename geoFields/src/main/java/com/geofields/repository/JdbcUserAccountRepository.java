package com.geofields.repository;

import com.geofields.repository.row.OrganizationMemberRow;
import com.geofields.repository.row.PendingRegistrationRow;
import com.geofields.security.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

    private static final String SELECT_ORG_MEMBER_BASE = """
            SELECT id, login, email, last_name, first_name, middle_name, role, registration_status, is_active
            FROM users
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByLogin(String login) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login = ?",
                Long.class,
                login
        );
        return count != null && count > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Long.class,
                email
        );
        return count != null && count > 0;
    }

    @Override
    public void insertUser(
            String login,
            String email,
            String passwordHash,
            long organizationId,
            UserRole role,
            String lastName,
            String firstName,
            String middleName) {
        jdbcTemplate.update(
                """
                        INSERT INTO users (login, email, password_hash, organization_id, role, is_active, registration_status,
                                           last_name, first_name, middle_name)
                        VALUES (?, ?, ?, ?, ?, TRUE, 'APPROVED', ?, ?, ?)
                        """,
                login,
                email,
                passwordHash,
                organizationId,
                role.name(),
                lastName,
                firstName,
                middleName);
    }

    @Override
    public void insertPendingUser(
            String login,
            String email,
            String passwordHash,
            long organizationId,
            UserRole role,
            String lastName,
            String firstName,
            String middleName) {
        jdbcTemplate.update(
                """
                        INSERT INTO users (login, email, password_hash, organization_id, role, is_active, registration_status,
                                           last_name, first_name, middle_name)
                        VALUES (?, ?, ?, ?, ?, FALSE, 'PENDING', ?, ?, ?)
                        """,
                login,
                email,
                passwordHash,
                organizationId,
                role.name(),
                lastName,
                firstName,
                middleName);
    }

    @Override
    public List<PendingRegistrationRow> findPendingRegistrations(long organizationId) {
        return jdbcTemplate.query(
                """
                        SELECT id, login, email, last_name, first_name, middle_name, created_at FROM users
                        WHERE organization_id = ? AND registration_status = 'PENDING'
                        ORDER BY created_at DESC
                        """,
                (rs, rowNum) -> new PendingRegistrationRow(
                        rs.getLong("id"),
                        rs.getString("login"),
                        rs.getString("email"),
                        rs.getString("last_name"),
                        rs.getString("first_name"),
                        rs.getString("middle_name"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                organizationId);
    }

    @Override
    public int approveUser(long userId, long organizationId) {
        return jdbcTemplate.update(
                """
                        UPDATE users SET registration_status = 'APPROVED', is_active = TRUE
                        WHERE id = ? AND organization_id = ? AND registration_status = 'PENDING'
                        """,
                userId,
                organizationId);
    }

    @Override
    public int rejectUser(long userId, long organizationId) {
        return jdbcTemplate.update(
                """
                        UPDATE users SET registration_status = 'REJECTED', is_active = FALSE
                        WHERE id = ? AND organization_id = ? AND registration_status = 'PENDING'
                        """,
                userId,
                organizationId);
    }

    @Override
    public List<OrganizationMemberRow> findMembersByOrganization(long organizationId) {
        return jdbcTemplate.query(
                SELECT_ORG_MEMBER_BASE + """
                        WHERE organization_id = ?
                        ORDER BY id
                        """,
                this::mapOrganizationMemberRow,
                organizationId);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    @Override
    public Optional<OrganizationMemberRow> findMember(long organizationId, long userId) {
        List<OrganizationMemberRow> rows = jdbcTemplate.query(
                SELECT_ORG_MEMBER_BASE + """
                        WHERE organization_id = ? AND id = ?
                        """,
                this::mapOrganizationMemberRow,
                organizationId,
                userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public int updateUserRole(long userId, long organizationId, UserRole role) {
        return jdbcTemplate.update(
                "UPDATE users SET role = ? WHERE id = ? AND organization_id = ?",
                role.name(),
                userId,
                organizationId);
    }

    @Override
    public int deleteUser(long userId, long organizationId) {
        return jdbcTemplate.update("DELETE FROM users WHERE id = ? AND organization_id = ?", userId, organizationId);
    }

    @Override
    public long countApprovedAdmins(long organizationId) {
        Long n = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM users
                        WHERE organization_id = ? AND role = 'ORG_ADMIN' AND registration_status = 'APPROVED'
                        """,
                Long.class,
                organizationId);
        return n != null ? n : 0L;
    }

    private OrganizationMemberRow mapOrganizationMemberRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrganizationMemberRow(
                rs.getLong("id"),
                rs.getString("login"),
                rs.getString("email"),
                nullToEmpty(rs.getString("last_name")),
                nullToEmpty(rs.getString("first_name")),
                nullToEmpty(rs.getString("middle_name")),
                UserRole.fromDatabase(rs.getString("role")),
                rs.getString("registration_status"),
                rs.getBoolean("is_active"));
    }
}
