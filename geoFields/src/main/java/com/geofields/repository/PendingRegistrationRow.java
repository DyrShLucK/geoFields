package com.geofields.repository;

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
