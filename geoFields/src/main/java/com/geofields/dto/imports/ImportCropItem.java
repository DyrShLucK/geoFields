package com.geofields.dto.imports;

import jakarta.validation.constraints.NotBlank;

public record ImportCropItem(
        @NotBlank String cropName
) {
}
