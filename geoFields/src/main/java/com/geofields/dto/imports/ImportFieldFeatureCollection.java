package com.geofields.dto.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportFieldFeatureCollection(
        String type,
        @NotNull @Valid List<ImportFieldFeature> features
) {
}
