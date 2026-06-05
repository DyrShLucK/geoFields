package com.geofields.repository.row;

import java.time.LocalDateTime;

public record FieldWorkStatusHistoryRow(
        Long id,
        Long operationId,
        String status,
        String statusTitleRu,
        LocalDateTime changedAt,
        Long userId,
        String userLastName,
        String userFirstName,
        String note
) {
}
