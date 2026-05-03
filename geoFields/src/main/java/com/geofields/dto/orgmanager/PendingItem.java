package com.geofields.dto.orgmanager;

import java.time.LocalDateTime;

public record PendingItem(
        long id,
        String lastName,
        String firstName,
        String middleName,
        String login,
        String email,
        LocalDateTime createdAt) {
}
