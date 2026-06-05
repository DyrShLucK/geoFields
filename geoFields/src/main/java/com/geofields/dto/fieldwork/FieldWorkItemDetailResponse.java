package com.geofields.dto.fieldwork;

import java.util.List;

public record FieldWorkItemDetailResponse(
        FieldWorkItemResponse item,
        List<FieldWorkStatusHistoryItem> statusHistory
) {
}
