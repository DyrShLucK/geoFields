package com.geofields.repository.row;

import com.geofields.security.UserRole;

/** Строка списка пользователей организации (админка). */
public record OrganizationMemberRow(
        long id,
        String login,
        String email,
        String lastName,
        String firstName,
        String middleName,
        UserRole role,
        String registrationStatus,
        boolean active) {
}
