package com.geofields.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;

    @NotBlank
    @Size(min = 3, max = 100)
    private String login;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 60, max = 255) // bcrypt/argon2 хэш ~60 символов
    private String passwordHash;

    private Boolean isActive;

    private LocalDateTime createdAt;

    @NotNull
    private Organization organization;
}