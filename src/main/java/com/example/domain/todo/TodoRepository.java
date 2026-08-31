package com.example.domain.todo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {

	// 소유권 검증과 Soft Delete 필터를 조건절에서 함께 처리한다 (PRD 8.3) — @SQLRestriction 동작 여부와 무관하게 안전
	Optional<Todo> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

	long countByUser_Id(Long userId);

	List<Todo> findByUser_IdAndTitleContainingIgnoreCase(Long userId, String keyword);
}
