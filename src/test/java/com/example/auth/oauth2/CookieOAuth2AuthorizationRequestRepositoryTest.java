package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Task 015 검증: STATELESS 세션 정책 아래에서도 인가 요청이 쿠키를 거쳐 왕복되는지 확인한다
 * (PRD_VALIDATION Major #6 — authorization_request_not_found 방지).
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

	private final CookieOAuth2AuthorizationRequestRepository repository =
			new CookieOAuth2AuthorizationRequestRepository();

	@Test
	void savedRequestRoundTripsThroughHttpOnlySameSiteCookie() {
		OAuth2AuthorizationRequest original = OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
				.clientId("client-id")
				.redirectUri("http://localhost:8080/login/oauth2/code/google")
				.state("state-value")
				.build();

		MockHttpServletRequest saveRequest = new MockHttpServletRequest();
		MockHttpServletResponse saveResponse = new MockHttpServletResponse();
		repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

		String setCookieHeader = saveResponse.getHeader("Set-Cookie");
		assertThat(setCookieHeader).contains("HttpOnly").contains("SameSite=Lax").contains("Secure");

		MockHttpServletRequest loadRequest = new MockHttpServletRequest();
		loadRequest.setCookies(extractCookie(saveResponse));

		OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

		assertThat(loaded).isNotNull();
		assertThat(loaded.getState()).isEqualTo("state-value");
		assertThat(loaded.getClientId()).isEqualTo("client-id");
	}

	@Test
	void loadWithoutCookieReturnsNull() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		assertThat(repository.loadAuthorizationRequest(request)).isNull();
	}

	@Test
	void removeClearsTheCookie() {
		OAuth2AuthorizationRequest original = OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri("https://kauth.kakao.com/oauth/authorize")
				.clientId("kakao-client")
				.redirectUri("http://localhost:8080/login/oauth2/code/kakao")
				.state("kakao-state")
				.build();

		MockHttpServletRequest saveRequest = new MockHttpServletRequest();
		MockHttpServletResponse saveResponse = new MockHttpServletResponse();
		repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

		MockHttpServletRequest removeRequest = new MockHttpServletRequest();
		removeRequest.setCookies(extractCookie(saveResponse));
		MockHttpServletResponse removeResponse = new MockHttpServletResponse();

		OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(removeRequest, removeResponse);

		assertThat(removed).isNotNull();
		assertThat(removed.getState()).isEqualTo("kakao-state");
		// addCookie()가 아니라 Set-Cookie 헤더로 직접 쓰므로(SameSite 지원을 위해 ResponseCookie 사용),
		// MockHttpServletResponse.getCookie()가 아니라 헤더 문자열로 만료(Max-Age=0)를 확인한다
		assertThat(removeResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
	}

	private Cookie[] extractCookie(MockHttpServletResponse response) {
		String header = response.getHeader("Set-Cookie");
		String nameValue = header.split(";")[0];
		String[] parts = nameValue.split("=", 2);
		return new Cookie[] {new Cookie(parts[0], parts[1])};
	}
}
