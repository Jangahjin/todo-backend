package com.example.common.exception;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 010 검증: 아직 실제 도메인 컨트롤러가 없어(M2~M4 예정) MockMvc standalone 모드로
 * 테스트 전용 컨트롤러에 GlobalExceptionHandler만 붙여 API_SPEC 1~2장 계약을 검증한다.
 * SecurityConfig도 아직 없으므로(Task 012) 이 방식이 시큐리티 자동설정을 우회할 수 있어 더 적합하다.
 */
class GlobalExceptionHandlerTest {

	@RestController
	static class TestController {

		@GetMapping("/test/success")
		public ApiResponse<String> success() {
			return ApiResponse.success("ok");
		}

		@GetMapping("/test/custom-exception")
		public ApiResponse<Void> customException() {
			throw new CustomException(ErrorCode.AUTH_002);
		}

		@PostMapping("/test/validate")
		public ApiResponse<Void> validate(@Valid @RequestBody TestRequest request) {
			return ApiResponse.success(null);
		}
	}

	record TestRequest(@NotBlank @Email String email) {
	}

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void successResponseMatchesContract() throws Exception {
		mockMvc.perform(get("/test/success"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value("ok"))
				.andExpect(jsonPath("$.message").value(nullValue()))
				.andExpect(jsonPath("$.errorCode").value(nullValue()));
	}

	@Test
	void customExceptionMapsToDeclaredHttpStatusAndErrorCode() throws Exception {
		mockMvc.perform(get("/test/custom-exception"))
				.andExpect(status().isConflict()) // AUTH_002 = 409
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.errorCode").value("AUTH_002"));
	}

	@Test
	void validationFailureReturns400WithFieldErrorMap() throws Exception {
		mockMvc.perform(post("/test/validate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("COMMON_001"))
				.andExpect(jsonPath("$.data.email").exists());
	}
}
