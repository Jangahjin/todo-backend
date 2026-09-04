package com.example.attachment.dto;

/**
 * requiresAuthHeader — 프론트는 스토리지 종류를 직접 판단하지 않고 이 플래그만 본다.
 * 로컬은 true(우리 서버의 인증 엔드포인트), S3는 false(Authorization 헤더를 붙이면 서명 충돌).
 */
public record PresignResponse(Long attachmentId, String uploadUrl, boolean requiresAuthHeader) {
}
