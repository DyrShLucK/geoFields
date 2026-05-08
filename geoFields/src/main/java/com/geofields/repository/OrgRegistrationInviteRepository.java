package com.geofields.repository;

import java.util.List;
import java.util.Optional;

public interface OrgRegistrationInviteRepository {

    /**
     * Блокирует строку приглашения (FOR UPDATE) в текущей транзакции — одноразовая ссылка без гонок.
     */
    Optional<Long> lockActiveOrganizationIdByToken(String token);

    int markConsumed(String token);

    void insertInvite(long organizationId, String token, Long createdByUserId);

    void revokeToken(String token, long organizationId);

    List<String> findActiveTokensByOrganization(long organizationId);
}
