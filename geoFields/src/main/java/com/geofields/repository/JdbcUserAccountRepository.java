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

    /** Базовый SELECT полей пользователя для списка участников организации. */
    private static final String SELECT_ORG_MEMBER_BASE = """
            SELECT id, login, email, last_name, first_name, middle_name, role, registration_status, is_active
            FROM users
            """;

    /** Участники организации, отсортированные по id. */
    private static final String FIND_MEMBERS_BY_ORG_SQL = SELECT_ORG_MEMBER_BASE + """
            WHERE organization_id = ?
            ORDER BY id
            """;

    /** Один участник организации по user id. */
    private static final String FIND_MEMBER_SQL = SELECT_ORG_MEMBER_BASE + """
            WHERE organization_id = ? AND id = ?
            """;

    /** Проверка занятости логина. */
    private static final String COUNT_BY_LOGIN_SQL = "SELECT COUNT(*) FROM users WHERE login = ?";

    /** Проверка занятости email. */
    private static final String COUNT_BY_EMAIL_SQL = "SELECT COUNT(*) FROM users WHERE email = ?";

    /**
     * Регистрация пользователя сразу в статусе APPROVED и is_active = TRUE
     * (например, по инвайт-ссылке).
     */
    private static final String INSERT_APPROVED_USER_SQL = """
            INSERT INTO users (login, email, password_hash, organization_id, role, is_active, registration_status,
                               last_name, first_name, middle_name)
            VALUES (?, ?, ?, ?, ?, TRUE, 'APPROVED', ?, ?, ?)
            """;

    /**
     * Заявка на регистрацию: PENDING, is_active = FALSE
     * (ожидает одобрения менеджером).
     */
    private static final String INSERT_PENDING_USER_SQL = """
            INSERT INTO users (login, email, password_hash, organization_id, role, is_active, registration_status,
                               last_name, first_name, middle_name)
            VALUES (?, ?, ?, ?, ?, FALSE, 'PENDING', ?, ?, ?)
            """;

    /** Заявки на регистрацию в организации (статус PENDING). */
    private static final String FIND_PENDING_REGISTRATIONS_SQL = """
            SELECT id, login, email, last_name, first_name, middle_name, created_at FROM users
            WHERE organization_id = ? AND registration_status = 'PENDING'
            ORDER BY created_at DESC
            """;

    /** Одобрение заявки: APPROVED и активация учётной записи. */
    private static final String APPROVE_USER_SQL = """
            UPDATE users SET registration_status = 'APPROVED', is_active = TRUE
            WHERE id = ? AND organization_id = ? AND registration_status = 'PENDING'
            """;

    /** Отклонение заявки: REJECTED и деактивация. */
    private static final String REJECT_USER_SQL = """
            UPDATE users SET registration_status = 'REJECTED', is_active = FALSE
            WHERE id = ? AND organization_id = ? AND registration_status = 'PENDING'
            """;

    /** Смена роли пользователя внутри организации. */
    private static final String UPDATE_USER_ROLE_SQL = "UPDATE users SET role = ? WHERE id = ? AND organization_id = ?";

    /** Удаление пользователя из организации. */
    private static final String DELETE_USER_SQL = "DELETE FROM users WHERE id = ? AND organization_id = ?";

    /** Число одобренных администраторов организации (для проверки «не удалить последнего»). */
    private static final String COUNT_APPROVED_ADMINS_SQL = """
            SELECT COUNT(*) FROM users
            WHERE organization_id = ? AND role = 'ORG_ADMIN' AND registration_status = 'APPROVED'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByLogin(String login) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_LOGIN_SQL, Long.class, login);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_EMAIL_SQL, Long.class, email);
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
                INSERT_APPROVED_USER_SQL,
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
                INSERT_PENDING_USER_SQL,
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
                FIND_PENDING_REGISTRATIONS_SQL,
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
        return jdbcTemplate.update(APPROVE_USER_SQL, userId, organizationId);
    }

    @Override
    public int rejectUser(long userId, long organizationId) {
        return jdbcTemplate.update(REJECT_USER_SQL, userId, organizationId);
    }

    @Override
    public List<OrganizationMemberRow> findMembersByOrganization(long organizationId) {
        return jdbcTemplate.query(
                FIND_MEMBERS_BY_ORG_SQL,
                this::mapOrganizationMemberRow,
                organizationId);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    @Override
    public Optional<OrganizationMemberRow> findMember(long organizationId, long userId) {
        List<OrganizationMemberRow> rows = jdbcTemplate.query(
                FIND_MEMBER_SQL,
                this::mapOrganizationMemberRow,
                organizationId,
                userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public int updateUserRole(long userId, long organizationId, UserRole role) {
        return jdbcTemplate.update(UPDATE_USER_ROLE_SQL, role.name(), userId, organizationId);
    }

    @Override
    public int deleteUser(long userId, long organizationId) {
        return jdbcTemplate.update(DELETE_USER_SQL, userId, organizationId);
    }

    @Override
    public long countApprovedAdmins(long organizationId) {
        Long n = jdbcTemplate.queryForObject(COUNT_APPROVED_ADMINS_SQL, Long.class, organizationId);
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
