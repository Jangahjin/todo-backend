package com.example.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.jwt.JwtTokenProvider;
import com.example.domain.todo.Todo;
import com.example.domain.todo.TodoRepository;
import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Task 017 검증: Todo 목록 페이지네이션·필터가 API_SPEC 4.2 계약대로 동작하는지 확인한다. */
class TodoListControllerTest extends IntegrationTestSupport {

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
	void paginatesTwentyFiveItemsAcrossThreePages() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-25@example.com", null, "목록유저", AuthProvider.LOCAL, null));
		for (int i = 0; i < 25; i++) {
			todoRepository.save(new Todo(owner, "할일 " + i, null, null));
		}
		todoRepository.flush();
		String token = tokenFor(owner);

		mockMvc.perform(get("/api/todos").param("page", "0").param("size", "10")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(25))
				.andExpect(jsonPath("$.data.totalPages").value(3))
				.andExpect(jsonPath("$.data.content.length()").value(10))
				.andExpect(jsonPath("$.data.hasNext").value(true));

		mockMvc.perform(get("/api/todos").param("page", "2").param("size", "10")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(5))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void oversizedSizeRequestIsClampedTo100() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-clamp@example.com", null, "목록유저", AuthProvider.LOCAL, null));
		todoRepository.saveAndFlush(new Todo(owner, "할일", null, null));

		mockMvc.perform(get("/api/todos").param("size", "200")
						.header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(100));
	}

	@Test
	void anotherUsersTodosDoNotLeakIntoTheList() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-owner@example.com", null, "소유자", AuthProvider.LOCAL, null));
		User stranger = userRepository.saveAndFlush(new User("list-stranger@example.com", null, "타인", AuthProvider.LOCAL, null));
		todoRepository.saveAndFlush(new Todo(owner, "내 할일", null, null));
		todoRepository.saveAndFlush(new Todo(stranger, "남의 할일", null, null));

		mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].title").value("내 할일"));
	}

	@Test
	void keywordSearchIsCaseInsensitive() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-keyword@example.com", null, "검색유저", AuthProvider.LOCAL, null));
		todoRepository.saveAndFlush(new Todo(owner, "jangbogi list", null, null));
		todoRepository.saveAndFlush(new Todo(owner, "other item", null, null));

		mockMvc.perform(get("/api/todos").param("keyword", "JANG")
						.header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].title").value("jangbogi list"));
	}

	@Test
	void softDeletedTodosAreExcludedFromListAndCount() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-deleted@example.com", null, "삭제유저", AuthProvider.LOCAL, null));
		todoRepository.saveAndFlush(new Todo(owner, "활성", null, null));
		Todo deleted = todoRepository.saveAndFlush(new Todo(owner, "삭제됨", null, null));
		todoRepository.delete(deleted);
		todoRepository.flush();

		mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content.length()").value(1));
	}

	@Test
	void emptyResultReturns200WithEmptyContentNotNotFound() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-empty@example.com", null, "빈유저", AuthProvider.LOCAL, null));

		mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void invalidStatusFilterReturns400WithCommon002() throws Exception {
		User owner = userRepository.saveAndFlush(new User("list-badstatus@example.com", null, "유저", AuthProvider.LOCAL, null));

		mockMvc.perform(get("/api/todos").param("status", "INVALID")
						.header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMON_002"));
	}
}
