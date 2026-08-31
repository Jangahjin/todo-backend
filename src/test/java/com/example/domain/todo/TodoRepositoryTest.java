package com.example.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.user.AuthProvider;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Task 009 검증 — PRD 13.3의 미해결 가정 2건을 여기서 확정한다.
 * 1) Todo 엔티티(JSONB content) 포함 상태에서 애플리케이션이 기동되고 왕복 저장이 손실 없이 된다 (PRD 8.4).
 * 2) @SQLRestriction의 사각지대로 지목된 단건 조회·count·keyword 검색 3경로 모두 삭제분을 가린다 (PRD 8.3).
 * 이 클래스 자체가 컨텍스트 로딩에 성공해야 실행되므로, 통과 자체가 "Todo 포함 기동 성공"의 증거이기도 하다.
 */
class TodoRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private TodoRepository todoRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void tiptapJsonRoundTripsWithoutLoss() {
		User user = userRepository.saveAndFlush(new User("tiptap@example.com", null, "홍길동", AuthProvider.LOCAL, null));
		String tiptapJson = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}]}";

		Todo saved = todoRepository.saveAndFlush(new Todo(user, "제목", tiptapJson, LocalDate.of(2026, 9, 1)));
		todoRepository.flush();

		Todo found = todoRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getContent()).isEqualTo(tiptapJson);
		assertThat(found.getStatus()).isEqualTo(TodoStatus.TODO);
	}

	@Test
	void findByIdAndUserExcludesDeletedTodo() {
		User user = userRepository.saveAndFlush(new User("owner1@example.com", null, "user1", AuthProvider.LOCAL, null));
		Todo todo = todoRepository.saveAndFlush(new Todo(user, "삭제될 할 일", null, null));
		Long todoId = todo.getId();

		todoRepository.delete(todo);
		todoRepository.flush();

		Optional<Todo> found = todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(todoId, user.getId());
		assertThat(found).isEmpty();
	}

	@Test
	void countQueryExcludesDeletedTodo() {
		User user = userRepository.saveAndFlush(new User("owner2@example.com", null, "user2", AuthProvider.LOCAL, null));
		todoRepository.saveAndFlush(new Todo(user, "활성", null, null));
		Todo deleted = todoRepository.saveAndFlush(new Todo(user, "삭제됨", null, null));
		todoRepository.delete(deleted);
		todoRepository.flush();

		long count = todoRepository.countByUser_Id(user.getId());

		assertThat(count).isEqualTo(1);
	}

	@Test
	void keywordSearchExcludesDeletedTodo() {
		User user = userRepository.saveAndFlush(new User("owner3@example.com", null, "user3", AuthProvider.LOCAL, null));
		todoRepository.saveAndFlush(new Todo(user, "장보기 목록", null, null));
		Todo deleted = todoRepository.saveAndFlush(new Todo(user, "장보기 취소분", null, null));
		todoRepository.delete(deleted);
		todoRepository.flush();

		List<Todo> found = todoRepository.findByUser_IdAndTitleContainingIgnoreCase(user.getId(), "장보기");

		assertThat(found).hasSize(1);
		assertThat(found.get(0).getTitle()).isEqualTo("장보기 목록");
	}

	@Test
	void todosTableIsCreatedInTestSchema() {
		// information_schema.tables는 스키마 무관하게 DB 전체를 본다 — table_name만으로 필터링하면
		// dev 스키마(todolistdb)에도 todos 테이블이 있을 때 2행이 나와 실패한다(실측 2026-08-31).
		// "테스트 스키마에 생성됐는가"를 검증하는 게 목적이므로 스키마도 함께 조건에 건다.
		String schema = jdbcTemplate.queryForObject(
				"SELECT table_schema FROM information_schema.tables "
						+ "WHERE table_name = 'todos' AND table_schema = 'todolistdb_test'",
				String.class);

		assertThat(schema).isEqualTo("todolistdb_test");
	}
}
