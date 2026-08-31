package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * Task 014 검증: 소셜 계정 연동 정책(PRD 13.1)이 정확히 지켜지는지 확인한다.
 * resolveUser()는 실제 OAuth2 핸드셰이크(super.loadUser) 없이 계정 연동 로직만 테스트할 수 있게 분리했다.
 */
class CustomOAuth2UserServiceTest extends IntegrationTestSupport {

	@Autowired
	private CustomOAuth2UserService customOAuth2UserService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void newGoogleUserIsAutoSignedUpWithNullPassword() {
		Map<String, Object> attributes =
				Map.of("sub", "google-sub-1", "email", "google-new@example.com", "name", "구글사용자");

		CustomOAuth2User result = customOAuth2UserService.resolveUser("google", attributes);

		User saved = userRepository.findByEmail("google-new@example.com").orElseThrow();
		assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(saved.getPassword()).isNull();
		assertThat(result.getUserId()).isEqualTo(saved.getId());
	}

	@Test
	void kakaoNestedResponseParsesEmailAndNickname() {
		Map<String, Object> attributes = Map.of(
				"id",
				987654321L,
				"kakao_account",
				Map.of("email", "kakao-new@example.com", "profile", Map.of("nickname", "카카오사용자")));

		customOAuth2UserService.resolveUser("kakao", attributes);

		User saved = userRepository.findByEmail("kakao-new@example.com").orElseThrow();
		assertThat(saved.getProvider()).isEqualTo(AuthProvider.KAKAO);
		assertThat(saved.getName()).isEqualTo("카카오사용자");
	}

	@Test
	void kakaoResponseWithoutEmailThrowsAuth007() {
		Map<String, Object> attributes =
				Map.of("id", 111L, "kakao_account", Map.of("profile", Map.of("nickname", "이메일없음")));

		assertThatThrownBy(() -> customOAuth2UserService.resolveUser("kakao", attributes))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
						.isEqualTo("AUTH_007"));
	}

	@Test
	void existingLocalAccountKeepsLocalProviderWhenLinkingSocialLogin() {
		userRepository.saveAndFlush(
				new User("existing@example.com", "encoded-password", "기존유저", AuthProvider.LOCAL, null));

		Map<String, Object> attributes = Map.of("sub", "google-sub-2", "email", "existing@example.com", "name", "구글이름");

		customOAuth2UserService.resolveUser("google", attributes);

		User saved = userRepository.findByEmail("existing@example.com").orElseThrow();
		assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);
		assertThat(saved.getPassword()).isNotNull();
	}
}
