package com.geofields.dto.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportFieldFeature(
        String type,
        @NotNull @Valid ImportFieldProperties properties,
        @NotNull @Valid ImportGeoJsonGeometry geometry
) {
}
