package com.example.domain.todo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoRepository extends JpaRepository<Todo, Long> {

	// 소유권 검증과 Soft Delete 필터를 조건절에서 함께 처리한다 (PRD 8.3) — @SQLRestriction 동작 여부와 무관하게 안전
	Optional<Todo> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

	long countByUser_Id(Long userId);

	List<Todo> findByUser_IdAndTitleContainingIgnoreCase(Long userId, String keyword);

	// JPQL이라 @SQLRestriction이 그대로 적용된다(PRD 8.3) — 네이티브 쿼리 금지 규칙을 지킨다.
	// status·keywordPattern은 null이면 조건을 건너뛴다(전체 조회). 정렬은 서비스가 넘기는 Pageable로 강제한다.
	// ⚠️ keywordPattern('%...%')은 서비스에서 미리 조합해 넘긴다 — SQL에서 CONCAT(문자열, :keyword, 문자열)로
	// null 파라미터를 감싸면 PostgreSQL이 파라미터 타입을 bytea로 잘못 추론해 lower(bytea) 함수 없음 오류가 난다
	// (실측 2026-08-31). LIKE 왼쪽 피연산자(LOWER(t.title))가 타입을 명확히 하도록 단순 LIKE 비교만 남긴다.
	@Query("SELECT t FROM Todo t WHERE t.user.id = :userId "
			+ "AND (:status IS NULL OR t.status = :status) "
			+ "AND (:keywordPattern IS NULL OR LOWER(t.title) LIKE :keywordPattern)")
	Page<Todo> search(
			@Param("userId") Long userId,
			@Param("status") TodoStatus status,
			@Param("keywordPattern") String keywordPattern,
			Pageable pageable);
}
