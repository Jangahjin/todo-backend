package com.example.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.jwt.JwtTokenProvider;
import com.example.domain.todo.Todo;
import com.example.domain.todo.TodoRepository;
import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Task 018 검증: 상태 변경(멱등)·Soft Delete가 API_SPEC 4.6~4.7 계약대로 동작하는지 확인한다. */
class TodoStatusAndDeleteControllerTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TodoRepository todoRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private String tokenFor(User user) {
		return jwtTokenProvider.createToken(user.getId(), user.getEmail());
	}

	@Test
	void changeStatusReturns200WithFullTodoResponse() throws Exception {
		User owner = userRepository.saveAndFlush(new User("status-owner@example.com", null, "유저", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "할일", null, null));

		mockMvc.perform(patch("/api/todos/" + todo.getId() + "/status")
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"DONE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(todo.getId()))
				.andExpect(jsonPath("$.data.status").value("DONE"))
				.andExpect(jsonPath("$.data.title").value("할일"))
				.andExpect(jsonPath("$.data.updatedAt").exists());
	}

	@Test
	void repeatingSameStatusChangeIsIdempotent() throws Exception {
		User owner = userRepository.saveAndFlush(new User("status-idem@example.com", null, "유저", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "할일", null, null));
		String token = tokenFor(owner);

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(patch("/api/todos/" + todo.getId() + "/status")
							.header("Authorization", "Bearer " + token)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"status\":\"DONE\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("DONE"));
		}
	}

	@Test
	void invalidStatusReturns400WithTodo002AndMissingStatusReturns400WithCommon001() throws Exception {
		User owner = userRepository.saveAndFlush(new User("status-invalid@example.com", null, "유저", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "할일", null, null));
		String token = tokenFor(owner);

		mockMvc.perform(patch("/api/todos/" + todo.getId() + "/status")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"INVALID\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("TODO_002"));

		mockMvc.perform(patch("/api/todos/" + todo.getId() + "/status")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMON_001"));
	}

	@Test
	void changingAnotherUsersTodoStatusReturns404WithTodo001() throws Exception {
		User owner = userRepository.saveAndFlush(new User("status-owner2@example.com", null, "소유자", AuthProvider.LOCAL, null));
		User stranger =
				userRepository.saveAndFlush(new User("status-stranger@example.com", null, "타인", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "남의 할일", null, null));

		mockMvc.perform(patch("/api/todos/" + todo.getId() + "/status")
						.header("Authorization", "Bearer " + tokenFor(stranger))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"DONE\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TODO_001"));
	}

	@Test
	void deleteIsSoftDeleteNotPhysicalRemoval() throws Exception {
		User owner = userRepository.saveAndFlush(new User("delete-owner@example.com", null, "유저", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "삭제될 할일", null, null));
		Long todoId = todo.getId();
		String token = tokenFor(owner);

		mockMvc.perform(delete("/api/todos/" + todoId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.message").value("삭제되었습니다."));

		// todoRepository.delete()가 큐에 넣은 @SQLDelete UPDATE는 Hibernate가 지연 flush한다.
		// JdbcTemplate은 Hibernate 세션을 거치지 않는 원시 JDBC라 flush 전 값을 볼 수 있어 명시적으로 밀어준다
		// (Task 008 SoftDeleteSampleEntityTest와 동일한 패턴).
		entityManager.flush();

		Boolean physicalRowExists = jdbcTemplate.queryForObject(
				"SELECT EXISTS(SELECT 1 FROM todos WHERE id = ?)", Boolean.class, todoId);
		LocalDateTime deletedAt =
				jdbcTemplate.queryForObject("SELECT deleted_at FROM todos WHERE id = ?", LocalDateTime.class, todoId);
		assertThat(physicalRowExists).isTrue();
		assertThat(deletedAt).isNotNull();
	}

	@Test
	void gettingDeletedTodoReturns404() throws Exception {
		User owner = userRepository.saveAndFlush(new User("delete-get@example.com", null, "유저", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "삭제될 할일", null, null));
		String token = tokenFor(owner);

		mockMvc.perform(delete("/api/todos/" + todo.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/todos/" + todo.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TODO_001"));
	}

	@Test
	void deletingAlreadyDeletedTodoReturns404() throws Exception {
		User owner = userRepository.saveAndFlush(new User("delete-twice@example.com", null, "유저", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(owner, "삭제될 할일", null, null));
		String token = tokenFor(owner);

		mockMvc.perform(delete("/api/todos/" + todo.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/todos/" + todo.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TODO_001"));
	}
}
