package com.geofields.repository.row;

import java.time.LocalDateTime;

public record FieldWorkItemRow(
        Long id,
        Long fieldId,
        Long organizationId,
        String name,
        String category,
        String categoryTitleRu,
        String status,
        String statusTitleRu,
        LocalDateTime operationAt,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
