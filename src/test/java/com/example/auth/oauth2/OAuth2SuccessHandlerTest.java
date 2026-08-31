package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.jwt.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Task 015 검증: 콜백 URL이 쿼리스트링이 아니라 프래그먼트로 토큰을 전달하는지 확인한다 (불변 규칙 12). */
class OAuth2SuccessHandlerTest {

	private final JwtTokenProvider jwtTokenProvider =
			new JwtTokenProvider("test-oauth2-success-handler-secret-32bytes-min", 86_400_000L);

	@Test
	void redirectsWithTokenInFragmentNotQueryString() throws Exception {
		OAuth2SuccessHandler handler = new OAuth2SuccessHandler(jwtTokenProvider, "http://localhost:3000");
		CustomOAuth2User principal = new CustomOAuth2User(Map.of(), 1L, "user@example.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(request, response, authentication);

		String location = response.getRedirectedUrl();
		assertThat(location).startsWith("http://localhost:3000/oauth2/callback#token=");
		assertThat(location).doesNotContain("?token=");

		String token = location.substring(location.indexOf("#token=") + "#token=".length());
		assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);
		assertThat(jwtTokenProvider.getEmail(token)).isEqualTo("user@example.com");
	}

	@Test
	void trailingSlashInFrontendUrlDoesNotProduceDoubleSlash() throws Exception {
		OAuth2SuccessHandler handler = new OAuth2SuccessHandler(jwtTokenProvider, "http://localhost:3000/");
		CustomOAuth2User principal = new CustomOAuth2User(Map.of(), 1L, "user@example.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(request, response, authentication);

		assertThat(response.getRedirectedUrl()).startsWith("http://localhost:3000/oauth2/callback#token=");
	}
}
