package com.example.todo.dto;

import com.example.domain.todo.Todo;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API_SPEC 4.1. content는 저장된 JSON 문자열을 이스케이프 없이 그대로 응답에 박아 넣어야
 * "JSON 객체 그대로"라는 계약이 성립한다 — @JsonRawValue(jackson-annotations는 Jackson 3에서도
 * com.fasterxml.jackson.annotation 패키지를 그대로 쓴다).
 */
public record TodoResponse(
		Long id,
		String title,
		@JsonRawValue String content,
		String status,
		LocalDate dueDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static TodoResponse from(Todo todo) {
		return new TodoResponse(
				todo.getId(),
				todo.getTitle(),
				todo.getContent(),
				todo.getStatus().name(),
				todo.getDueDate(),
				todo.getCreatedAt(),
				todo.getUpdatedAt());
	}
}
