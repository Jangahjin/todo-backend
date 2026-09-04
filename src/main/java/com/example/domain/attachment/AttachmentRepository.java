package com.example.domain.attachment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	// 소유권 검증과 Soft Delete 필터를 조건절에서 함께 처리한다 (PRD 8.3과 동일 패턴)
	Optional<Attachment> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

	List<Attachment> findByIdInAndUser_IdAndDeletedAtIsNull(List<Long> ids, Long userId);

	List<Attachment> findByTodo_IdAndDeletedAtIsNull(Long todoId);

	// 업로드만 되고 Todo 저장으로 이어지지 않은 고아 TEMP 레코드 (가이드 §6 고아 파일 정리)
	List<Attachment> findByStatusAndCreatedAtBefore(AttachmentStatus status, LocalDateTime threshold);

	// @SQLRestriction은 deleted_at IS NULL만 보여주므로, 유예 기간이 지난 soft-deleted 레코드는
	// 네이티브 쿼리로 조회해야 한다 (PRD 8.3: 네이티브 쿼리에는 @SQLRestriction이 적용되지 않는다)
	@Query(value = "SELECT * FROM attachment WHERE deleted_at IS NOT NULL AND deleted_at < :threshold", nativeQuery = true)
	List<Attachment> findSoftDeletedBefore(@Param("threshold") LocalDateTime threshold);
}
