package com.example.storage;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GET /api/attachments/{id}/raw 는 &lt;img&gt; 태그가 직접 호출해 Authorization 헤더를 실을 수
 * 없으므로 SecurityConfig에서 permitAll로 열어두고, 대신 이 단기 유효 서명 토큰으로 인가한다
 * (가이드 §4). 사용자 인증(JwtTokenProvider)과는 클레임 구조가 다른 별개의 발급 체계이지만,
 * 새 시크릿을 추가로 요구하지 않도록 같은 jwt.secret을 재사용한다.
 */
@Component
public class AttachmentUrlTokenProvider {

	private static final String ATTACHMENT_ID_CLAIM = "attachmentId";

	private final SecretKey key;
	private final long expiryMinutes;

	public AttachmentUrlTokenProvider(
			@Value("${jwt.secret}") String secret, @Value("${app.storage.url-expiry-minutes}") long expiryMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiryMinutes = expiryMinutes;
	}

	public String issue(Long attachmentId) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + Duration.ofMinutes(expiryMinutes).toMillis());
		return Jwts.builder()
				.claim(ATTACHMENT_ID_CLAIM, attachmentId)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}

	/** 만료·위조·attachmentId 불일치는 전부 무효로 취급한다 — 실패 원인을 구분해 노출하지 않는다. */
	public boolean isValid(String token, Long attachmentId) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			// JSON 숫자가 Integer/Long 중 무엇으로 역직렬화될지 라이브러리 내부에 맡기지 않고 문자열로 비교한다
			Object claim = claims.get(ATTACHMENT_ID_CLAIM);
			return claim != null && attachmentId.toString().equals(claim.toString());
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}
}
