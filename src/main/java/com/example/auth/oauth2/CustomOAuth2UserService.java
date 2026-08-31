package com.example.auth.oauth2;

import com.example.common.exception.ErrorCode;
import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 이메일은 자동 가입(password=NULL), 기존 이메일은 그 계정으로 로그인시키되
 * provider는 덮어쓰지 않는다 — 최초 가입 수단을 유지한다 (PRD 13.1).
 * loadUser()가 실제 HTTP 왕복(super.loadUser)을 하고, 계정 연동 로직 자체는
 * resolveUser()로 분리해 OAuth2 핸드셰이크 없이 단위 테스트할 수 있게 했다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

	private final UserRepository userRepository;

	public CustomOAuth2UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oAuth2User = super.loadUser(userRequest);
		String registrationId = userRequest.getClientRegistration().getRegistrationId(); // "google" | "kakao"
		return resolveUser(registrationId, oAuth2User.getAttributes());
	}

	@Transactional
	public CustomOAuth2User resolveUser(String registrationId, Map<String, Object> attributes) {
		OAuth2UserInfo userInfo = parseUserInfo(registrationId, attributes);
		String email = userInfo.getEmail();
		if (email == null || email.isBlank()) {
			// 카카오 account_email이 선택 동의라 이메일이 안 내려올 수 있다 — AUTH_007로 실패 처리
			throw new OAuth2AuthenticationException(
					new OAuth2Error(ErrorCode.AUTH_007.name()), "이메일 정보를 가져올 수 없습니다.");
		}

		AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
		User user = userRepository
				.findByEmail(email)
				.orElseGet(() -> userRepository.save(
						new User(email, null, userInfo.getName(), provider, userInfo.getProviderId())));
		// 기존 이메일이면 여기서 끝 — provider를 덮어쓰지 않는다 (최초 가입 수단 유지)

		return new CustomOAuth2User(attributes, user.getId(), user.getEmail());
	}

	private OAuth2UserInfo parseUserInfo(String registrationId, Map<String, Object> attributes) {
		return switch (registrationId) {
			case "google" -> new GoogleOAuth2UserInfo(attributes);
			case "kakao" -> new KakaoOAuth2UserInfo(attributes);
			default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인 제공자입니다: " + registrationId);
		};
	}
}
