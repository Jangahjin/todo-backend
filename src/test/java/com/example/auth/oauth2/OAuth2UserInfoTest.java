package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Task 014 검증: Google/Kakao 응답 파싱이 실제 응답 구조대로 동작하는지 확인한다. */
class OAuth2UserInfoTest {

	@Test
	void googleUserInfoExtractsStandardFields() {
		GoogleOAuth2UserInfo userInfo =
				new GoogleOAuth2UserInfo(Map.of("sub", "google-123", "email", "user@example.com", "name", "홍길동"));

		assertThat(userInfo.getProviderId()).isEqualTo("google-123");
		assertThat(userInfo.getEmail()).isEqualTo("user@example.com");
		assertThat(userInfo.getName()).isEqualTo("홍길동");
	}

	@Test
	void kakaoUserInfoExtractsNestedFields() {
		KakaoOAuth2UserInfo userInfo = new KakaoOAuth2UserInfo(Map.of(
				"id", 123456789L,
				"kakao_account", Map.of("email", "user@example.com", "profile", Map.of("nickname", "홍길동"))));

		assertThat(userInfo.getProviderId()).isEqualTo("123456789");
		assertThat(userInfo.getEmail()).isEqualTo("user@example.com");
		assertThat(userInfo.getName()).isEqualTo("홍길동");
	}

	@Test
	void kakaoUserInfoWithoutEmailReturnsNullButStillParsesNickname() {
		KakaoOAuth2UserInfo userInfo =
				new KakaoOAuth2UserInfo(Map.of("id", 1L, "kakao_account", Map.of("profile", Map.of("nickname", "이름만"))));

		assertThat(userInfo.getEmail()).isNull();
		assertThat(userInfo.getName()).isEqualTo("이름만");
	}

	@Test
	void kakaoUserInfoWithoutKakaoAccountAtAllReturnsNullForEmailAndName() {
		KakaoOAuth2UserInfo userInfo = new KakaoOAuth2UserInfo(Map.of("id", 1L));

		assertThat(userInfo.getEmail()).isNull();
		assertThat(userInfo.getName()).isNull();
	}
}
