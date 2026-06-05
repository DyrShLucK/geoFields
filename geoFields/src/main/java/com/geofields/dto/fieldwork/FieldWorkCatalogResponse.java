package com.geofields.dto.fieldwork;

import java.util.List;

public record FieldWorkCatalogResponse(
        List<FieldWorkCodeItem> statuses,
        List<FieldWorkCodeItem> categories
) {
}
