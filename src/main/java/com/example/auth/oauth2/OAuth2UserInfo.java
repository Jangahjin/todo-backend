package com.example.auth.oauth2;

/** Google/Kakao 등 제공자마다 다른 응답 구조를 공통 형태로 뽑아낸다 (PRD 12.2). */
public interface OAuth2UserInfo {

	String getProviderId();

	String getEmail();

	String getName();
}
