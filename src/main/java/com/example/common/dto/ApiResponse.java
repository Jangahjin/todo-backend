package com.example.common.dto;

/**
 * 모든 API 응답을 감싸는 공통 래퍼 (API_SPEC 1.1).
 * data는 성공 시 T, 실패 시 null이 원칙이나, 검증 실패(COMMON_001)에 한해
 * 필드명→메시지 맵(T=Map<String,String>)을 담을 수 있다 (API_SPEC 1.3).
 */
public record ApiResponse<T>(boolean success, T data, String message, String errorCode) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null, null);
	}

	public static <T> ApiResponse<T> fail(String message, String errorCode) {
		return fail(null, message, errorCode);
	}

	public static <T> ApiResponse<T> fail(T data, String message, String errorCode) {
		return new ApiResponse<>(false, data, message, errorCode);
	}
}
