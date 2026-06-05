package com.geofields.dto.fieldwork;

import java.time.LocalDateTime;

public record FieldWorkStatusHistoryItem(
        Long id,
        String status,
        String statusTitleRu,
        LocalDateTime changedAt,
        Long userId,
        String userDisplayName,
        String note
) {
}
