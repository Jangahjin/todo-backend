package com.example.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Task 009 검증: users.email UNIQUE 제약이 실제로 예외를 던지는지 확인한다 (PRD 8.2).
 */
class UserRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private UserRepository userRepository;

	@Test
	void duplicateEmailViolatesUniqueConstraint() {
		userRepository.saveAndFlush(new User("dup@example.com", "encoded", "first", AuthProvider.LOCAL, null));

		assertThatThrownBy(() ->
				userRepository.saveAndFlush(new User("dup@example.com", "encoded", "second", AuthProvider.LOCAL, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findByEmailReturnsSavedUser() {
		userRepository.saveAndFlush(new User("find@example.com", "encoded", "찾기", AuthProvider.LOCAL, null));

		assertThat(userRepository.findByEmail("find@example.com")).isPresent();
	}

	@Test
	void socialOnlyUserAllowsNullPassword() {
		User saved = userRepository.saveAndFlush(
				new User("social@example.com", null, "소셜사용자", AuthProvider.GOOGLE, "google-sub-1"));

		assertThat(saved.getPassword()).isNull();
	}
}
