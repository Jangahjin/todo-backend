package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** API_SPEC 3.2. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
