package com.example.domain.user;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.SignupRequest;
import com.example.auth.dto.TokenResponse;
import com.example.auth.dto.UserResponse;
import com.example.auth.jwt.JwtTokenProvider;
import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controller에 비즈니스 로직을 두지 않는다 (CLAUDE.md 4장). */
@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new CustomException(ErrorCode.AUTH_002);
		}

		User user = new User(
				request.email(), passwordEncoder.encode(request.password()), request.name(), AuthProvider.LOCAL, null);
		return UserResponse.from(userRepository.save(user));
	}

	public TokenResponse login(LoginRequest request) {
		// "이메일 없음"과 "비밀번호 불일치"를 구분하지 않고 모두 AUTH_001 — 가입 이메일 열거 방지 (API_SPEC 2.2)
		User user = userRepository.findByEmail(request.email())
				.filter(candidate -> candidate.getPassword() != null
						&& passwordEncoder.matches(request.password(), candidate.getPassword()))
				.orElseThrow(() -> new CustomException(ErrorCode.AUTH_001));

		String accessToken = jwtTokenProvider.createToken(user.getId(), user.getEmail());
		return new TokenResponse(accessToken, "Bearer", jwtTokenProvider.getExpirationMillis(), UserResponse.from(user));
	}

	public UserResponse getMe(Long userId) {
		User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.AUTH_006));
		return UserResponse.from(user);
	}
}
