package com.example.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import tools.jackson.databind.JsonNode;

/**
 * API_SPEC 4.5. PUT은 전체 교체다 — title·status는 DB NOT NULL이라 생략 불가(@NotBlank),
 * content·dueDate는 생략 시 null로 갱신된다(검증 없음).
 * status는 String @NotBlank로 받는다 — enum으로 받으면 잘못된 값이 GlobalExceptionHandler가
 * 못 잡는 역직렬화 예외(500)로 새므로, 서비스 계층에서 TodoStatus.valueOf로 직접 변환해 TODO_002로 매핑한다.
 */
public record TodoUpdateRequest(
		@NotBlank @Size(max = 255) String title,
		JsonNode content,
		LocalDate dueDate,
		@NotBlank String status) {
}
