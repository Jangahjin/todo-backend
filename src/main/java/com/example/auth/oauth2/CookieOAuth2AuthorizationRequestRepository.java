package com.example.auth.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;

/**
 * JWT API라 SecurityConfig가 SessionCreationPolicy.STATELESS를 쓰는데, Spring Security의
 * OAuth2 로그인은 기본적으로 HttpSession에 인가 요청(state/PKCE)을 저장한다. 세션이 없으니
 * 콜백에서 authorization_request_not_found가 난다(PRD_VALIDATION Major #6) — 쿠키에
 * 직렬화해 저장하는 방식으로 대체한다. OAuth2 왕복은 톱레벨 내비게이션이라 SameSite=Lax로 충분하다.
 */
public class CookieOAuth2AuthorizationRequestRepository
		implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	static final String COOKIE_NAME = "oauth2_auth_request";
	private static final int COOKIE_EXPIRE_SECONDS = 180;

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return getCookie(request).map(CookieOAuth2AuthorizationRequestRepository::deserialize).orElse(null);
	}

	@Override
	public void saveAuthorizationRequest(
			OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
		if (authorizationRequest == null) {
			clearCookie(response);
			return;
		}
		addCookie(response, serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(
			HttpServletRequest request, HttpServletResponse response) {
		OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
		clearCookie(response);
		return authorizationRequest;
	}

	private Optional<Cookie> getCookie(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return Optional.empty();
		}
		return Arrays.stream(request.getCookies()).filter(c -> COOKIE_NAME.equals(c.getName())).findFirst();
	}

	private void clearCookie(HttpServletResponse response) {
		addCookie(response, "", 0);
	}

	private void addCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
				.path("/")
				.httpOnly(true)
				.sameSite("Lax")
				.maxAge(maxAgeSeconds)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private static String serialize(OAuth2AuthorizationRequest authorizationRequest) {
		return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(authorizationRequest));
	}

	private static OAuth2AuthorizationRequest deserialize(Cookie cookie) {
		return (OAuth2AuthorizationRequest)
				SerializationUtils.deserialize(Base64.getUrlDecoder().decode(cookie.getValue()));
	}
}
