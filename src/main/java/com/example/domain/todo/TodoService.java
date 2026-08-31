package com.example.domain.todo;

import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.todo.dto.TodoCreateRequest;
import com.example.todo.dto.TodoResponse;
import com.example.todo.dto.TodoUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Controller에 비즈니스 로직을 두지 않는다 (CLAUDE.md 4장). */
@Service
@Transactional(readOnly = true)
public class TodoService {

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
