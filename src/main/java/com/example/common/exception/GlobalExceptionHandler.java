package com.example.common.exception;

import com.example.common.dto.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		return ResponseEntity.status(ErrorCode.COMMON_500.getHttpStatus())
				.body(ApiResponse.fail(ErrorCode.COMMON_500.getDefaultMessage(), ErrorCode.COMMON_500.name()));
	}
}
