package com.example.config;

import com.example.auth.jwt.JwtAuthenticationEntryPoint;
import com.example.auth.jwt.JwtAuthenticationFilter;
import com.example.auth.jwt.JwtTokenProvider;
import com.example.auth.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.example.auth.oauth2.CustomOAuth2UserService;
import com.example.auth.oauth2.OAuth2FailureHandler;
import com.example.auth.oauth2.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 무상태 JWT API 필터체인 (PRD 10장).
 * ⚠️ Spring Security 7(Boot 4 동봉)은 람다 DSL만 지원한다 — authorizeRequests()/antMatchers()는 제거됨.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtTokenProvider jwtTokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final OAuth2SuccessHandler oAuth2SuccessHandler;
	private final OAuth2FailureHandler oAuth2FailureHandler;

	public SecurityConfig(
			JwtTokenProvider jwtTokenProvider,
			JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
			CustomOAuth2UserService customOAuth2UserService,
			OAuth2SuccessHandler oAuth2SuccessHandler,
			OAuth2FailureHandler oAuth2FailureHandler) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
		this.customOAuth2UserService = customOAuth2UserService;
		this.oAuth2SuccessHandler = oAuth2SuccessHandler;
		this.oAuth2FailureHandler = oAuth2FailureHandler;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(); // 불변 규칙 3
	}

	@Bean
	public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
		return new CookieOAuth2AuthorizationRequestRepository(); // STATELESS 세션 정책 아래에서 인가 요청을 쿠키로 보관 (Task 015)
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable()) // 무상태 JWT API라 CSRF 토큰이 필요 없음
				.cors(Customizer.withDefaults()) // CorsConfig의 CorsConfigurationSource 빈을 그대로 사용
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/auth/signup",
								"/api/auth/login",
								"/oauth2/**",
								"/login/oauth2/**",
								// <img> 태그가 직접 호출해 Authorization 헤더를 실을 수 없다 — 쿼리의 서명
								// 토큰(AttachmentUrlTokenProvider)으로 컨트롤러가 직접 인가한다 (가이드 §4)
								"/api/attachments/*/raw")
						.permitAll()
						.anyRequest()
						.authenticated())
				.exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
				.oauth2Login(oauth2 -> oauth2
						.authorizationEndpoint(
								endpoint -> endpoint.authorizationRequestRepository(authorizationRequestRepository()))
						.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
						.successHandler(oAuth2SuccessHandler)
						.failureHandler(oAuth2FailureHandler))
				.addFilterBefore(
						new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
