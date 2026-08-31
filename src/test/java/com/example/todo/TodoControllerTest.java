package com.example.todo;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.jwt.JwtTokenProvider;
import com.example.domain.todo.Todo;
import com.example.domain.todo.TodoRepository;
import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Task 016 검증: Todo 생성·상세·수정이 API_SPEC 4.1~4.5 계약대로 동작하는지 확인한다. */
class TodoControllerTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TodoRepository todoRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private String tokenFor(User user) {
		return jwtTokenProvider.createToken(user.getId(), user.getEmail());
	}

	@Test
	void createReturns201WithContentSerializedAsJsonObject() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("owner-create@example.com", null, "생성자", AuthProvider.LOCAL, null));

		mockMvc.perform(post("/api/todos")
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"장보기\",\"content\":{\"type\":\"doc\",\"content\":[]},\"dueDate\":\"2026-09-01\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(notNullValue()))
				.andExpect(jsonPath("$.data.title").value("장보기"))
				.andExpect(jsonPath("$.data.status").value("TODO"))
				.andExpect(jsonPath("$.data.content.type").value("doc"))
				.andExpect(jsonPath("$.data.dueDate").value("2026-09-01"));
	}

	@Test
	void createWithBlankOrTooLongTitleReturns400WithCommon001() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("owner-invalid@example.com", null, "생성자", AuthProvider.LOCAL, null));
		String token = tokenFor(owner);

		mockMvc.perform(post("/api/todos")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMON_001"));

		String tooLongTitle = "a".repeat(256);
		mockMvc.perform(post("/api/todos")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"" + tooLongTitle + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMON_001"));
	}

	@Test
	void gettingAnotherUsersTodoReturns404WithTodo001() throws Exception {
		User owner = userRepository.saveAndFlush(new User("owner-a@example.com", null, "소유자", AuthProvider.LOCAL, null));
		User stranger = userRepository.saveAndFlush(new User("stranger@example.com", null, "타인", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "남의 할일", null, null));

		mockMvc.perform(get("/api/todos/" + todo.getId()).header("Authorization", "Bearer " + tokenFor(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TODO_001"));
	}

	@Test
	void updateWithoutStatusReturns400NotServerError() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("owner-update@example.com", null, "수정자", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "수정 전", null, null));

		mockMvc.perform(put("/api/todos/" + todo.getId())
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"수정 후\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMON_001"));
	}

	@Test
	void updateOmittingContentAndDueDateNullsThoseFields() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("owner-omit@example.com", null, "수정자", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(
				new Todo(owner, "수정 전", "{\"type\":\"doc\",\"content\":[]}", LocalDate.of(2026, 9, 1)));

		mockMvc.perform(put("/api/todos/" + todo.getId())
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"수정 후\",\"status\":\"DONE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("수정 후"))
				.andExpect(jsonPath("$.data.status").value("DONE"))
				.andExpect(jsonPath("$.data.content").value(nullValue()))
				.andExpect(jsonPath("$.data.dueDate").value(nullValue()));
	}

	@Test
	void updateWithInvalidStatusReturns400WithTodo002() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("owner-badstatus@example.com", null, "수정자", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "수정 전", null, null));

		mockMvc.perform(put("/api/todos/" + todo.getId())
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"수정 후\",\"status\":\"INVALID\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("TODO_002"));
	}
}
