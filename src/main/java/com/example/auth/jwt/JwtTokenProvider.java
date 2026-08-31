package com.example.auth.jwt;

import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JWT 발급·검증 (불변 규칙 4: Access Token 24시간 고정, Refresh Token 없음).
 * 서명 키는 ${JWT_SECRET} 환경변수에서만 읽는다 (불변 규칙 9).
 */
@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long expirationMillis;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration}") long expirationMillis) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMillis;
	}

	public String createToken(Long userId, String email) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMillis);

		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("email", email)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}

	public long getExpirationMillis() {
		return expirationMillis;
	}

	public Long getUserId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	public String getEmail(String token) {
		return parseClaims(token).get("email", String.class);
	}

	/**
	 * 토큰 상태별로 서로 다른 ErrorCode를 던진다 (API_SPEC 2.2).
	 * 필터 단계에서 발생하므로 GlobalExceptionHandler가 못 잡는다 — 호출부(Task 012의
	 * JwtAuthenticationFilter/EntryPoint)가 이 CustomException을 직접 처리해야 한다.
	 */
	public Claims parseClaims(String token) {
		if (!StringUtils.hasText(token)) {
			throw new CustomException(ErrorCode.AUTH_003, "토큰이 없습니다.");
		}
		try {
			return Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (ExpiredJwtException e) {
			throw new CustomException(ErrorCode.AUTH_004);
		} catch (SignatureException e) {
			throw new CustomException(ErrorCode.AUTH_005);
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.AUTH_003);
		}
	}
}
