package com.example.attachment.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 본문 하나에 이미지가 여러 개 있어도 한 번에 조회한다 (가이드 §6 — 단건 API였다면 N번 왕복했을 것). */
public record ViewUrlsRequest(@NotEmpty List<Long> ids) {
}
