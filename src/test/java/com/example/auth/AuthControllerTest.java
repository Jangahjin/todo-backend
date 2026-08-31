package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Task 013 검증: 회원가입·로그인·내 정보 조회가 API_SPEC 3.1~3.3 계약대로 동작하는지 확인한다. */
class AuthControllerTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void signupSucceedsAndStoresBcryptHashedPassword() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"signup@example.com\",\"password\":\"abc123\",\"name\":\"홍길동\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.email").value("signup@example.com"))
				.andExpect(jsonPath("$.data.provider").value("LOCAL"))
				.andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));

		User saved = userRepository.findByEmail("signup@example.com").orElseThrow();
		assertThat(saved.getPassword()).isNotEqualTo("abc123");
		assertThat(passwordEncoder.matches("abc123", saved.getPassword())).isTrue();
	}

	@Test
	void signupWithInvalidEmailAndShortPasswordReturns400WithFieldErrorMap() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\",\"password\":\"abc12\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMON_001"))
				.andExpect(jsonPath("$.data.email").exists())
				.andExpect(jsonPath("$.data.password").exists());
	}

	@Test
	void signupWithDuplicateEmailReturns409WithAuth002() throws Exception {
		userRepository.saveAndFlush(
				new User("dup@example.com", passwordEncoder.encode("abc123"), "먼저", AuthProvider.LOCAL, null));

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"dup@example.com\",\"password\":\"abc123\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("AUTH_002"));
	}

	@Test
	void loginSucceedsAndTokenGrantsAccessToMe() throws Exception {
		userRepository.saveAndFlush(
				new User("login@example.com", passwordEncoder.encode("abc123"), "로그인유저", AuthProvider.LOCAL, null));

		String loginResponse = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"login@example.com\",\"password\":\"abc123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(86_400_000))
				.andReturn()
				.getResponse()
				.getContentAsString();

		String accessToken = JsonPath.read(loginResponse, "$.data.accessToken");

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("login@example.com"));
	}

	@Test
	void loginWithUnknownEmailAndWrongPasswordBothReturn401WithAuth001() throws Exception {
		userRepository.saveAndFlush(
				new User("known@example.com", passwordEncoder.encode("correct-password"), "유저", AuthProvider.LOCAL, null));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"unknown@example.com\",\"password\":\"whatever\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("AUTH_001"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"known@example.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("AUTH_001"));
	}
}
