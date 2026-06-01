package com.geofields.dto.imports;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShapefileImportResponse(
        @NotNull @Valid ImportFieldFeatureCollection fields,
        @NotNull @Valid List<ImportCropItem> crops
) {
}
