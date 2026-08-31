package com.example.auth.dto;

import com.example.domain.user.User;
import java.time.LocalDateTime;

/** API_SPEC 3.1/3.3. provider는 최초 가입 수단을 뜻한다 (PRD 13.1). */
public record UserResponse(Long id, String email, String name, String provider, LocalDateTime createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(), user.getEmail(), user.getName(), user.getProvider().name(), user.getCreatedAt());
	}
}
