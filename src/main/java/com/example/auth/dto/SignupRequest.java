package com.example.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API_SPEC 3.1. 불변 규칙 1·2: username 필드 없음, 비밀번호는 6자 이상이면 충분(추가 복잡도 규칙 없음). */
public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 6) String password,
		@Size(max = 100) String name) {
}
