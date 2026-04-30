package com.geofields.model;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Crops {
    Long id;
    @Size(min = 1, max = 100)
    String crop_name;
}
