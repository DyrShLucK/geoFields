package com.geofields.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrgRegistrationInviteRepository implements OrgRegistrationInviteRepository {

    /**
     * Блокирует строку приглашения и возвращает id организации, если токен активен
     * (не отозван и не использован). FOR UPDATE — для транзакции регистрации.
     */
    private static final String LOCK_ACTIVE_ORG_BY_TOKEN_SQL = """
            SELECT organization_id FROM org_registration_invites
            WHERE token = ? AND NOT revoked AND consumed_at IS NULL
            FOR UPDATE
            """;

    /** Отмечает приглашение использованным (время consumed_at). */
    private static final String MARK_CONSUMED_SQL = """
            UPDATE org_registration_invites SET consumed_at = CURRENT_TIMESTAMP
            WHERE token = ? AND consumed_at IS NULL
            """;

    /** Создаёт новое приглашение на регистрацию в организацию. */
    private static final String INSERT_INVITE_SQL = """
            INSERT INTO org_registration_invites (organization_id, token, created_by_user_id)
            VALUES (?, ?, ?)
            """;

    /** Отзывает (revoked) инвайт-токен в рамках организации. */
    private static final String REVOKE_TOKEN_SQL = """
            UPDATE org_registration_invites SET revoked = TRUE
            WHERE token = ? AND organization_id = ?
            """;

    /** Список активных токенов организации (новые сверху). */
    private static final String FIND_ACTIVE_TOKENS_SQL = """
            SELECT token FROM org_registration_invites
            WHERE organization_id = ? AND NOT revoked AND consumed_at IS NULL
            ORDER BY created_at DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrgRegistrationInviteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> lockActiveOrganizationIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Long orgId = jdbcTemplate.queryForObject(
                    LOCK_ACTIVE_ORG_BY_TOKEN_SQL,
                    Long.class,
                    token.trim());
            return Optional.ofNullable(orgId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public int markConsumed(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        return jdbcTemplate.update(MARK_CONSUMED_SQL, token.trim());
    }

    @Override
    public void insertInvite(long organizationId, String token, Long createdByUserId) {
        jdbcTemplate.update(INSERT_INVITE_SQL, organizationId, token, createdByUserId);
    }

    @Override
    public void revokeToken(String token, long organizationId) {
        jdbcTemplate.update(REVOKE_TOKEN_SQL, token, organizationId);
    }

    @Override
    public List<String> findActiveTokensByOrganization(long organizationId) {
        return jdbcTemplate.queryForList(
                FIND_ACTIVE_TOKENS_SQL,
                String.class,
                organizationId);
    }
}
