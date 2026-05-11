package com.geofields.dto.agronomist;

import jakarta.validation.constraints.NotBlank;

public record FieldStatusUpdateRequest(@NotBlank String status) {
}
