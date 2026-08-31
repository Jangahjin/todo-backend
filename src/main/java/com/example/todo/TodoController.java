package com.example.todo;

import com.example.common.dto.ApiResponse;
import com.example.common.dto.PageResponse;
import com.example.domain.todo.TodoService;
import com.example.todo.dto.TodoCreateRequest;
import com.example.todo.dto.TodoResponse;
import com.example.todo.dto.TodoStatusUpdateRequest;
import com.example.todo.dto.TodoUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API_SPEC 4장. 모든 엔드포인트는 인증이 필요하다(SecurityConfig의 anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	// JwtAuthenticationFilter가 SecurityContext에 userId(Long)를 principal로 설정해둔다 (Task 011)
	@PostMapping
	public ResponseEntity<ApiResponse<TodoResponse>> create(
			@AuthenticationPrincipal Long userId, @Valid @RequestBody TodoCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(todoService.create(userId, request)));
	}

	@GetMapping("/{id}")
	public ApiResponse<TodoResponse> getOne(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		return ApiResponse.success(todoService.getOne(userId, id));
	}

	@GetMapping
	public ApiResponse<PageResponse<TodoResponse>> list(
			@AuthenticationPrincipal Long userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return ApiResponse.success(todoService.list(userId, page, size, status, keyword));
	}

	@PutMapping("/{id}")
	public ApiResponse<TodoResponse> update(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody TodoUpdateRequest request) {
		return ApiResponse.success(todoService.update(userId, id, request));
	}

	@PatchMapping("/{id}/status")
	public ApiResponse<TodoResponse> changeStatus(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody TodoStatusUpdateRequest request) {
		return ApiResponse.success(todoService.changeStatus(userId, id, request));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		todoService.delete(userId, id);
		return ApiResponse.success(null, "삭제되었습니다.");
	}
}
