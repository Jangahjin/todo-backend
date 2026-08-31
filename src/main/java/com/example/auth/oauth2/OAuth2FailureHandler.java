package com.example.auth.oauth2;

import com.example.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** 실패 리다이렉트: {APP_FRONTEND_URL}/oauth2/callback#error=AUTH_007 (API_SPEC 3.5). */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

	private final String frontendUrl;

	public OAuth2FailureHandler(@Value("${app.frontend-url}") String frontendUrl) {
		this.frontendUrl = frontendUrl;
	}

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		response.sendRedirect(baseUrl() + "/oauth2/callback#error=" + resolveErrorCode(exception));
	}

	private String resolveErrorCode(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException oauth2Exception
				&& oauth2Exception.getError().getErrorCode() != null) {
			return oauth2Exception.getError().getErrorCode();
		}
		return ErrorCode.AUTH_007.name();
	}

	private String baseUrl() {
		return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
	}
}
