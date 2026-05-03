package com.geofields.dto.session;

import com.geofields.dto.orgmanager.CsrfInfo;

/** Контекст сессии для главной страницы (загрузка с клиента, как API). */
public record SessionContextResponse(
        long userId,
        long organizationId,
        String organizationName,
        String login,
        String fullName,
        String lastName,
        String firstName,
        String middleName,
        boolean admin,
        boolean orgManager,
        boolean agronomist,
        String role,
        String roleLabel,
        CsrfInfo csrf) {
}
