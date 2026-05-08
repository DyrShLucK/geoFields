package com.geofields.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrgRegistrationInviteRepository implements OrgRegistrationInviteRepository {

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
                    """
                            SELECT organization_id FROM org_registration_invites
                            WHERE token = ? AND NOT revoked AND consumed_at IS NULL
                            FOR UPDATE
                            """,
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
        return jdbcTemplate.update(
                """
                        UPDATE org_registration_invites SET consumed_at = CURRENT_TIMESTAMP
                        WHERE token = ? AND consumed_at IS NULL
                        """,
                token.trim());
    }

    @Override
    public void insertInvite(long organizationId, String token, Long createdByUserId) {
        jdbcTemplate.update(
                """
                        INSERT INTO org_registration_invites (organization_id, token, created_by_user_id)
                        VALUES (?, ?, ?)
                        """,
                organizationId,
                token,
                createdByUserId);
    }

    @Override
    public void revokeToken(String token, long organizationId) {
        jdbcTemplate.update(
                """
                        UPDATE org_registration_invites SET revoked = TRUE
                        WHERE token = ? AND organization_id = ?
                        """,
                token,
                organizationId);
    }

    @Override
    public List<String> findActiveTokensByOrganization(long organizationId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT token FROM org_registration_invites
                        WHERE organization_id = ? AND NOT revoked AND consumed_at IS NULL
                        ORDER BY created_at DESC
                        """,
                String.class,
                organizationId);
    }
}
