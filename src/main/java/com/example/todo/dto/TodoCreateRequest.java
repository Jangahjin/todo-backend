package com.example.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import tools.jackson.databind.JsonNode;

/**
 * API_SPEC 4.4. content는 JSON 객체로 오므로 String이 아니라 JsonNode로 받는다 —
 * String으로 받으면 JSON 객체가 바인딩될 때 타입 불일치 예외가 난다.
 */
public record TodoCreateRequest(
		@NotBlank @Size(max = 255) String title, JsonNode content, LocalDate dueDate) {
}
