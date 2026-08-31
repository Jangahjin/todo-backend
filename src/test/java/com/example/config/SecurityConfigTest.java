package com.example.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.jwt.JwtTokenProvider;
import com.example.support.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import java.lang.reflect.Field;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 012 검증: SecurityConfig 필터체인·JwtAuthenticationEntryPoint·CorsConfig가
 * API_SPEC 2.2 계약대로 동작하는지 확인한다. 아직 실제 도메인 컨트롤러가 없어(M4 예정)이지만,
 * Spring Security의 인가 검사는 DispatcherServlet 진입 전에 동작하므로 컨트롤러 부재와 무관하게 검증할 수 있다.
 *
 * ⚠️ jwt.secret을 테스트에서 문자열로 다시 하드코딩하지 않는다 — Spring Boot는 OS 환경변수를
 * application.properties보다 우선하므로(relaxed binding으로 JWT_SECRET → jwt.secret 매핑),
 * 로컬 셸에 JWT_SECRET이 설정돼 있으면 실제 실행 중인 키가 테스트 properties의 더미 값과 달라진다.
 * 그래서 만료 토큰은 실행 중인 빈의 진짜 키를 리플렉션으로 꺼내 서명한다.
 */
class SecurityConfigTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void protectedEndpointWithoutTokenReturns401WithAuth003() throws Exception {
		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("AUTH_003"));
	}

	@Test
	void expiredTokenReturns401WithAuth004() throws Exception {
		String expiredToken = Jwts.builder()
				.subject("1")
				.claim("email", "user@example.com")
				.issuedAt(new Date(System.currentTimeMillis() - 2_000))
				.expiration(new Date(System.currentTimeMillis() - 1_000))
				.signWith(runningSecretKey())
				.compact();

		mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("AUTH_004"));
	}

	@Test
	void tamperedTokenReturns401WithAuth005() throws Exception {
		JwtTokenProvider otherKeyProvider =
				new JwtTokenProvider("different-jwt-secret-key-for-security-test-987654321", 86_400_000L);
		String tamperedToken = otherKeyProvider.createToken(1L, "user@example.com");

		mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + tamperedToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("AUTH_005"));
	}

	@Test
	void signupAndLoginBypassAuthentication() throws Exception {
		// signup/login은 POST로만 매핑돼 있어(Task 013) GET은 405가 나지만, 401이 아니라는 사실 자체가
		// 인증 필터를 permitAll로 통과했다는 증거다. permitAll이 아니었다면 405보다 401이 먼저 응답됐을 것이다.
		mockMvc.perform(get("/api/auth/signup")).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void preflightFromFrontendOriginAllowsPatch() throws Exception {
		mockMvc.perform(options("/api/todos/1")
						.header("Origin", "http://localhost:3000")
						.header("Access-Control-Request-Method", "PATCH")
						.header("Access-Control-Request-Headers", "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
	}

	private SecretKey runningSecretKey() throws Exception {
		Field keyField = JwtTokenProvider.class.getDeclaredField("key");
		keyField.setAccessible(true);
		return (SecretKey) keyField.get(jwtTokenProvider);
	}
}
