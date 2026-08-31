package com.example.auth.oauth2;

import java.util.Map;

/** Kakao 응답은 kakao_account.email / kakao_account.profile.nickname처럼 중첩돼 있다 (PRD 12.2). */
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

	private final Map<String, Object> attributes;

	public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String getProviderId() {
		// String.valueOf(null)은 실제 null이 아니라 문자열 "null"을 반환한다 — id가 없을 때
		// providerId에 그 네 글자가 그대로 저장되는 걸 막는다 (실측 2026-08-31 재검토로 발견)
		Object id = attributes.get("id");
		return id == null ? null : String.valueOf(id);
	}

	@Override
	public String getEmail() {
		Map<String, Object> kakaoAccount = getKakaoAccount();
		return kakaoAccount == null ? null : (String) kakaoAccount.get("email");
	}

	@Override
	public String getName() {
		Map<String, Object> kakaoAccount = getKakaoAccount();
		if (kakaoAccount == null) {
			return null;
		}
		Object profile = kakaoAccount.get("profile");
		if (!(profile instanceof Map<?, ?> profileMap)) {
			return null;
		}
		return (String) profileMap.get("nickname");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getKakaoAccount() {
		Object account = attributes.get("kakao_account");
		return account instanceof Map ? (Map<String, Object>) account : null;
	}
}
