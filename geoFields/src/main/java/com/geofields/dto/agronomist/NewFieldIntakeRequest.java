package com.geofields.dto.agronomist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Черновой payload для приема нового поля:
 * геометрия GeoJSON + первая запись истории посева.
 */
public record NewFieldIntakeRequest(
        @NotBlank String fieldName,
        @NotNull @Valid GeoJsonGeometryInput geometry,
        @NotNull @Valid FieldCropUpsertRequest history
) {
    public record GeoJsonGeometryInput(
            @NotBlank String type,
            @NotNull Object coordinates
    ) {
    }
}
