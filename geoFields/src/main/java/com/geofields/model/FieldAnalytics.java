package com.geofields.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldAnalytics {
    private Long id;

    @NotNull
    private FieldСrops fieldCrop;

    private NdviData ndvi;

    @NotNull
    private LocalDate recordDate;

    private LocalDateTime createdAt;
}