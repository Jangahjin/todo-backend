package com.example.todo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * API_SPEC 4.6. 클라이언트가 목표 상태를 지정한다 — 서버가 값을 반전시키는 토글은 쓰지 않는다(불변 규칙 13).
 * status는 enum이 아니라 String @NotBlank로 받는다(TodoUpdateRequest와 동일한 이유:
 * enum으로 받으면 잘못된 값의 역직렬화 실패가 GlobalExceptionHandler가 못 잡는 500으로 샌다).
 */
public record TodoStatusUpdateRequest(@NotBlank String status) {
}
