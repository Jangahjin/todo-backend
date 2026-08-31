package com.example.auth.dto;

/** API_SPEC 3.2. expiresIn 단위는 밀리초(불변 규칙 4: 86400000 = 24시간). */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
