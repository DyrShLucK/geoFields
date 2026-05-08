package com.geofields.model;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NdviData {
    private Long id;

    @Size(max = 1000)
    private String url;

    private LocalDateTime createdAt;
}