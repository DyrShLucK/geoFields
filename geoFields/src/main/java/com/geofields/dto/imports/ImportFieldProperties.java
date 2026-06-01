package com.geofields.dto.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportFieldProperties(
        @NotBlank String name,
        Double area,
        Boolean active,
        @Valid List<ImportFieldHistoryItem> history
) {
}
