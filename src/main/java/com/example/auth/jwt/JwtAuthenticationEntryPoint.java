package com.example.auth.jwt;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 필터 단계 401을 ApiResponse JSON으로 직접 직렬화한다 (API_SPEC 2.2).
 * GlobalExceptionHandler는 DispatcherServlet 안쪽에서만 동작해 이 경로를 잡지 못하므로,
 * JwtAuthenticationFilter가 요청 속성에 남긴 CustomException을 여기서 직접 읽어 응답한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ErrorCode errorCode = resolveErrorCode(request);

		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
				.write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode.getDefaultMessage(), errorCode.name())));
	}

	private ErrorCode resolveErrorCode(HttpServletRequest request) {
		Object attribute = request.getAttribute(JwtAuthenticationFilter.JWT_EXCEPTION_ATTRIBUTE);
		if (attribute instanceof CustomException customException) {
			return customException.getErrorCode();
		}
		return ErrorCode.AUTH_003; // 토큰 자체가 없어 필터가 아무 것도 기록하지 않은 기본 경우
	}
}
