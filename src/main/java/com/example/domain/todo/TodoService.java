package com.example.domain.todo;

import com.example.common.dto.PageResponse;
import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.todo.dto.TodoCreateRequest;
import com.example.todo.dto.TodoResponse;
import com.example.todo.dto.TodoUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Controller에 비즈니스 로직을 두지 않는다 (CLAUDE.md 4장). */
@Service
@Transactional(readOnly = true)
public class TodoService {

	private static final int MAX_PAGE_SIZE = 100; // PRD 4.4 — 초과 시 100으로 절삭

	private final TodoRepository todoRepository;
	private final UserRepository userRepository;

	public TodoService(TodoRepository todoRepository, UserRepository userRepository) {
		this.todoRepository = todoRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public TodoResponse create(Long userId, TodoCreateRequest request) {
		// 인증된 사용자이므로 존재가 보장된다 — SELECT 없이 LAZY 프록시로 FK만 연결
		User user = userRepository.getReferenceById(userId);
		Todo todo = new Todo(user, request.title(), toJsonString(request.content()), request.dueDate());
		return TodoResponse.from(todoRepository.save(todo));
	}

	public TodoResponse getOne(Long userId, Long todoId) {
		return TodoResponse.from(findOwnedOrThrow(userId, todoId));
	}

	public PageResponse<TodoResponse> list(Long userId, int page, int size, String statusParam, String keyword) {
		int clampedSize = Math.min(size, MAX_PAGE_SIZE);
		TodoStatus status = parseOptionalStatusFilter(statusParam);
		String keywordPattern = toKeywordPattern(keyword);
		// 정렬은 클라이언트가 고르지 않는다 — MVP는 created_at DESC 고정 (ROADMAP Task 017)
		Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<Todo> result = todoRepository.search(userId, status, keywordPattern, pageable);
		return PageResponse.from(result.map(TodoResponse::from));
	}

	// '%'를 미리 조합해 넘긴다 — SQL의 CONCAT(문자열, :keyword, 문자열)에 null을 통과시키면
	// PostgreSQL이 파라미터 타입을 잘못 추론해 lower(bytea) 오류가 난다 (TodoRepository.search 참조)
	private String toKeywordPattern(String keyword) {
		return (keyword == null || keyword.isBlank()) ? null : "%" + keyword.toLowerCase() + "%";
	}

	private TodoStatus parseOptionalStatusFilter(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return TodoStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorCode.COMMON_002); // 목록 필터의 잘못된 값은 COMMON_002 (API_SPEC 4.2)
		}
	}

	@Transactional
	public TodoResponse update(Long userId, Long todoId, TodoUpdateRequest request) {
		Todo todo = findOwnedOrThrow(userId, todoId);
		todo.replace(request.title(), toJsonString(request.content()), request.dueDate(), parseStatus(request.status()));
		return TodoResponse.from(todo);
	}

	private Todo findOwnedOrThrow(Long userId, Long todoId) {
		// 소유권 검증 + Soft Delete 필터를 조건절에서 함께 처리 (PRD 8.3, 불변 규칙 11: 404로 통일)
		return todoRepository
				.findByIdAndUser_IdAndDeletedAtIsNull(todoId, userId)
				.orElseThrow(() -> new CustomException(ErrorCode.TODO_001));
	}

	private TodoStatus parseStatus(String status) {
		try {
			return TodoStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorCode.TODO_002);
		}
	}

	private String toJsonString(JsonNode content) {
		return content == null ? null : content.toString();
	}
}
