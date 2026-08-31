package com.example.auth.jwt;

import com.example.common.exception.CustomException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {token} 헤더를 파싱해 SecurityContext에 인증 정보를 채운다.
 * 이 필터는 DispatcherServlet 바깥에서 실행되어 GlobalExceptionHandler가 예외를 잡지 못하므로,
 * 검증 실패 시 요청 속성(JWT_EXCEPTION_ATTRIBUTE)에 CustomException을 담아두기만 하고 체인은 계속 진행한다.
 * 실제 401 응답 직렬화는 SecurityConfig에 등록될 JwtAuthenticationEntryPoint(Task 012)가 담당한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String JWT_EXCEPTION_ATTRIBUTE = "jwtException";

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);

		if (StringUtils.hasText(token)) {
			try {
				Claims claims = jwtTokenProvider.parseClaims(token);
				Long userId = Long.valueOf(claims.getSubject());
				var authentication = new UsernamePasswordAuthenticationToken(
						userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (CustomException e) {
				request.setAttribute(JWT_EXCEPTION_ATTRIBUTE, e);
			}
		}

		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(AUTHORIZATION_HEADER);
		if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
			return header.substring(BEARER_PREFIX.length());
		}
		return null;
	}
}
