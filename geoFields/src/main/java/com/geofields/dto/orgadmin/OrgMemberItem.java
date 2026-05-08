package com.geofields.dto.orgadmin;

public record OrgMemberItem(
        long id,
        String lastName,
        String firstName,
        String middleName,
        String login,
        String email,
        String role,
        String roleLabel,
        String registrationStatus,
        boolean active,
        boolean currentUser) {
}
