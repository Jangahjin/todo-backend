package com.example.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

/**
 * Task 011 검증: 만료 24시간 고정(불변 규칙 4)과 토큰 상태별 ErrorCode 분류(AUTH_003/004/005)를 확인한다.
 * Spring 컨텍스트 없이도 순수 객체이므로 직접 new로 생성해 테스트한다.
 */
class JwtTokenProviderTest {

	private static final String SECRET = "test-jwt-secret-key-for-unit-tests-0123456789";
	private static final long EXPIRATION_MILLIS = 86_400_000L; // 불변 규칙 4: 24시간 고정

	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MILLIS);

	@Test
	void issuedTokenExpiresExactly24HoursLater() {
		String token = jwtTokenProvider.createToken(1L, "user@example.com");

		Claims claims = jwtTokenProvider.parseClaims(token);
		long diffMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

		assertThat(diffMillis).isEqualTo(EXPIRATION_MILLIS);
	}

	@Test
	void resolvesUserIdAndEmailFromToken() {
		String token = jwtTokenProvider.createToken(42L, "claims@example.com");

		assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(42L);
		assertThat(jwtTokenProvider.getEmail(token)).isEqualTo("claims@example.com");
	}

	@Test
	void expiredTokenIsClassifiedAsAuth004() {
		JwtTokenProvider alreadyExpiredProvider = new JwtTokenProvider(SECRET, -1_000L);
		String token = alreadyExpiredProvider.createToken(1L, "user@example.com");

		assertThatThrownBy(() -> jwtTokenProvider.parseClaims(token))
				.isInstanceOf(CustomException.class)
				.satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_004));
	}

	@Test
	void tamperedSignatureIsClassifiedAsAuth005() {
		String token = jwtTokenProvider.createToken(1L, "user@example.com");
		JwtTokenProvider otherKeyProvider =
				new JwtTokenProvider("different-jwt-secret-key-for-unit-tests-987654321", EXPIRATION_MILLIS);

		assertThatThrownBy(() -> otherKeyProvider.parseClaims(token))
				.isInstanceOf(CustomException.class)
				.satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_005));
	}

	@Test
	void blankTokenIsClassifiedAsAuth003() {
		assertThatThrownBy(() -> jwtTokenProvider.parseClaims(" "))
				.isInstanceOf(CustomException.class)
				.satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_003));
	}

	@Test
	void malformedTokenIsClassifiedAsAuth003() {
		assertThatThrownBy(() -> jwtTokenProvider.parseClaims("not-a-valid-jwt"))
				.isInstanceOf(CustomException.class)
				.satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_003));
	}
}
