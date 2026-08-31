package com.example.auth.oauth2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 우리 서비스의 userId를 함께 들고 다니는 principal — OAuth2SuccessHandler가 JWT 발급에 그대로 쓴다. */
public class CustomOAuth2User implements OAuth2User {

	private final Map<String, Object> attributes;
	private final Long userId;
	private final String email;

	public CustomOAuth2User(Map<String, Object> attributes, Long userId, String email) {
		this.attributes = attributes;
		this.userId = userId;
		this.email = email;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEmail() {
		return email;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getName() {
		return String.valueOf(userId);
	}
}
