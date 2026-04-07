package com.geofields.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.awt.Polygon;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Fields {
    Long id;
    @Size(min=1, max=255)
    String field_name;
    @NotNull
    @Digits(integer=10, fraction=2)
    BigDecimal field_area;
    Polygon field_geometry;
}
