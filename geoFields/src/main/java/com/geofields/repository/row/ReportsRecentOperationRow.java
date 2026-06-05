package com.geofields.repository.row;

import java.time.LocalDateTime;

public record ReportsRecentOperationRow(
        long id,
        long fieldId,
        String fieldName,
        String name,
        String categoryTitleRu,
        String statusTitleRu,
        LocalDateTime operationAt
) {
}
