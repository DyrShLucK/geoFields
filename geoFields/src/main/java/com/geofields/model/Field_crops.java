package com.geofields.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Field_crops {
    Long id;

    Fields field;

    Crops crop;

    Date sowing_date;

    Date harvest_date;

    @NotNull
    @Digits(integer=10, fraction=2)
    BigDecimal sown_area_ha;

    @NotNull
    @Digits(integer=10, fraction=2)
    BigDecimal harvest_area_ha;

    @NotNull
    @Digits(integer=8, fraction=2)
    BigDecimal actual_yield;

    @NotNull
    @Digits(integer=8, fraction=2)
    BigDecimal total_yield;

    @NotNull
    @Digits(integer=8, fraction=2)
    BigDecimal planned_yield;

    @NotNull
    @Digits(integer=8, fraction=2)
    BigDecimal forecasted_yield;

    @Size(min=1, max=50)
    String source_data;

    @Size(min=1, max=100)
    String sowing_details;

    @NotNull
    Integer crop_year;
}
