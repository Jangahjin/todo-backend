package com.example.common.exception;

import com.example.common.dto.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 컨트롤러 계층에서 던져진 예외를 ApiResponse 포맷으로 일괄 변환한다 (CLAUDE.md 4장).
 * ⚠️ 인증 필터 단계의 401(AUTH_003~005)은 DispatcherServlet 바깥에서 발생해 여기서 잡히지 않는다.
 * 그 경로는 JwtAuthenticationEntryPoint가 별도로 처리한다 (Task 012, API_SPEC 2.2).
 *
 * ResponseEntityExceptionHandler를 상속한다 — HttpRequestMethodNotSupportedException(405),
 * NoResourceFoundException(404), HttpMessageNotReadableException(400) 등 Spring MVC가
 * 자체적으로 던지는 모든 표준 예외가 결국 handleExceptionInternal 한 곳으로 모이므로,
 * 이런 예외들을 개별적으로 나열하지 않아도 원래 상태코드를 보존하면서 ApiResponse로 감쌀 수 있다.
 * (실측 2026-08-31: 개별 @ExceptionHandler를 나열하는 방식은 매번 새 프레임워크 예외가
 * 나타날 때마다 빠뜨려 catch-all이 500으로 뭉개는 문제를 반복했다.)
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
				.body(ApiResponse.fail(e.getMessage(), errorCode.name()));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ResponseEntity.status(ErrorCode.COMMON_001.getHttpStatus())
				.body(ApiResponse.fail(fieldErrors, ErrorCode.COMMON_001.getDefaultMessage(), ErrorCode.COMMON_001.name()));
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		return ResponseEntity.status(statusCode)
				.body(ApiResponse.fail(ex.getMessage(), ErrorCode.COMMON_002.name()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		return ResponseEntity.status(ErrorCode.COMMON_500.getHttpStatus())
				.body(ApiResponse.fail(ErrorCode.COMMON_500.getDefaultMessage(), ErrorCode.COMMON_500.name()));
	}
}
