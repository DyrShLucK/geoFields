package com.geofields.dto.imports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImportGeoJsonGeometry(
        @NotBlank String type,
        @NotNull Object coordinates
) {
}
