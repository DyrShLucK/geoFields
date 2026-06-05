package com.geofields.dto.fieldwork;

import java.time.LocalDateTime;

public record FieldWorkItemResponse(
        Long id,
        Long fieldId,
        String name,
        String category,
        String categoryTitleRu,
        String status,
        String statusTitleRu,
        LocalDateTime operationAt,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
