package com.geofields.dto.reports;

import java.time.LocalDateTime;

public record ReportsRecentOperationItem(
        long id,
        long fieldId,
        String fieldName,
        String name,
        String categoryTitleRu,
        String statusTitleRu,
        LocalDateTime operationAt
) {
}
