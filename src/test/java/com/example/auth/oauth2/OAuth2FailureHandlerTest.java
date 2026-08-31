package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** Task 015 검증: 실패 리다이렉트가 {frontendUrl}/oauth2/callback#error={code} 형식인지 확인한다. */
class OAuth2FailureHandlerTest {

	private final OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:3000");

	@Test
	void redirectsWithErrorCodeFromOAuth2Exception() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationFailure(
				request, response, new OAuth2AuthenticationException(new OAuth2Error("AUTH_007"), "이메일 없음"));

		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth2/callback#error=AUTH_007");
	}

	@Test
	void fallsBackToAuth007ForNonOAuth2Exceptions() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationFailure(request, response, new BadCredentialsException("무관한 예외"));

		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth2/callback#error=AUTH_007");
	}
}
