package com.geofields.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Organization {
    private Long id;

    @NotBlank
    @Size(min = 2, max = 255)
    private String name;

    private LocalDateTime createdAt;
}