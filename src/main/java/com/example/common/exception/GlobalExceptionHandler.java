package com.example.common.exception;

import com.example.common.dto.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러 계층에서 던져진 예외를 ApiResponse 포맷으로 일괄 변환한다 (CLAUDE.md 4장).
 * ⚠️ 인증 필터 단계의 401(AUTH_003~005)은 DispatcherServlet 바깥에서 발생해 여기서 잡히지 않는다.
 * 그 경로는 JwtAuthenticationEntryPoint가 별도로 처리한다 (Task 012, API_SPEC 2.2).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
				.body(ApiResponse.fail(e.getMessage(), errorCode.name()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
			MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ResponseEntity.status(ErrorCode.COMMON_001.getHttpStatus())
				.body(ApiResponse.fail(fieldErrors, ErrorCode.COMMON_001.getDefaultMessage(), ErrorCode.COMMON_001.name()));
	}

	/**
	 * NoResourceFoundException(매핑 안 된 경로 → 정상 404), ErrorResponseException 계열
	 * (HttpRequestMethodNotSupportedException 등)처럼 Spring MVC가 이미 올바른 HTTP 상태코드를
	 * 정해준 예외는 그 상태코드를 그대로 살린다. 이 핸들러가 없으면 아래 catch-all이
	 * 이런 정상적인 4xx까지 전부 500으로 뭉개버린다 — 실측(2026-08-31)으로 드러난 문제.
	 */
	@ExceptionHandler({NoResourceFoundException.class, ErrorResponseException.class})
	public ResponseEntity<ApiResponse<Void>> handleErrorResponse(ErrorResponse e) {
		return ResponseEntity.status(e.getStatusCode())
				.body(ApiResponse.fail(e.getBody().getDetail(), ErrorCode.COMMON_002.name()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		return ResponseEntity.status(ErrorCode.COMMON_500.getHttpStatus())
				.body(ApiResponse.fail(ErrorCode.COMMON_500.getDefaultMessage(), ErrorCode.COMMON_500.name()));
	}
}
