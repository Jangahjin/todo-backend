package com.example.attachment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 클라이언트가 선언한 값이다 — 크기·형식의 실질적 강제는 complete 단계에서 이뤄진다 (가이드 §3). */
public record PresignRequest(@NotBlank String filename, @NotBlank String contentType, @Positive long fileSize) {
}
