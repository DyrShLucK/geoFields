package com.geofields.dto.imports;

/**
 * Итог сохранения импортированных полей в БД (ручное подтверждение пользователем).
 */
public record ShapefileImportCommitResponse(
        int savedFields,
        int savedHistories,
        int createdCrops,
        String message
) {
}
