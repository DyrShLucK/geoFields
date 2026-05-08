package com.geofields.repository.row;

import java.time.LocalDateTime;

public record PendingRegistrationRow(
        long id,
        String login,
        String email,
        String lastName,
        String firstName,
        String middleName,
        LocalDateTime createdAt) {
}
