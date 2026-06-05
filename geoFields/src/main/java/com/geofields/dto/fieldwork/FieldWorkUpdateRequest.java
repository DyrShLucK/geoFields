package com.geofields.dto.fieldwork;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record FieldWorkUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 50) String category,
        @NotNull LocalDateTime operationAt,
        @Size(max = 32) String status,
        @Size(max = 500) String statusNote
) {
}
