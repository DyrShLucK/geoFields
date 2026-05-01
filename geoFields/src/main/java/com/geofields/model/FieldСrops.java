package com.geofields.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldСrops {
    Long id;

    @NotNull
    @Valid
    Fields field;

    @NotNull
    @Valid
    Crops crop;

    LocalDate sowing_date;

    LocalDate harvest_date;

    @Digits(integer=10, fraction=2)
    BigDecimal sown_area_ha;

    @Digits(integer=10, fraction=2)
    BigDecimal harvest_area_ha;

    @Digits(integer=8, fraction=2)
    BigDecimal actual_yield;

    @Digits(integer=8, fraction=2)
    BigDecimal total_yield;

    @Digits(integer=8, fraction=2)
    BigDecimal planned_yield;

    @Digits(integer=8, fraction=2)
    BigDecimal forecasted_yield;

    @Size(min=1, max=50)
    String source_data;

    @Size(min=1, max=100)
    String sowing_details;

    @NotNull
    Integer crop_year;

    private Organization organization;
}
