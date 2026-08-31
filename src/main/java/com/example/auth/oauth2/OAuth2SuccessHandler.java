package com.example.auth.oauth2;

import com.example.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * JWT 발급 후 프론트로 리다이렉트한다. 토큰은 URL 프래그먼트로 전달한다(불변 규칙 12,
 * API_SPEC 3.5) — 쿼리스트링은 Referer 헤더·브라우저 히스토리·프록시 로그에 남는다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

	private final JwtTokenProvider jwtTokenProvider;
	private final String frontendUrl;

	public OAuth2SuccessHandler(
			JwtTokenProvider jwtTokenProvider, @Value("${app.frontend-url}") String frontendUrl) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.frontendUrl = frontendUrl;
	}

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException {
		CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
		String accessToken = jwtTokenProvider.createToken(principal.getUserId(), principal.getEmail());

		response.sendRedirect(baseUrl() + "/oauth2/callback#token=" + accessToken);
	}

	private String baseUrl() {
		return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
	}
}
