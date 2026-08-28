package com.example.support.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Task 008 검증: BaseEntity의 Auditing과 Soft Delete(@SQLDelete + @SQLRestriction)가
 * 실제로 동작하는지 확인한다. 도메인 엔티티가 아직 없어(Task 009 예정) 테스트 전용
 * SoftDeleteSampleEntity로 패턴을 먼저 검증한다.
 */
class SoftDeleteSampleEntityTest extends IntegrationTestSupport {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void auditingFieldsAreFilledOnSave() {
		SoftDeleteSampleEntity entity = new SoftDeleteSampleEntity("first");
		entityManager.persist(entity);
		entityManager.flush();

		assertThat(entity.getCreatedAt()).isNotNull();
		assertThat(entity.getUpdatedAt()).isNotNull();
		assertThat(entity.getDeletedAt()).isNull();
	}

	@Test
	void updatedAtChangesOnModification_createdAtStaysSame() throws InterruptedException {
		SoftDeleteSampleEntity entity = new SoftDeleteSampleEntity("first");
		entityManager.persist(entity);
		entityManager.flush();

		LocalDateTime createdAtBefore = entity.getCreatedAt();
		LocalDateTime updatedAtBefore = entity.getUpdatedAt();

		Thread.sleep(10);
		entity.rename("renamed");
		entityManager.flush();

		assertThat(entity.getCreatedAt()).isEqualTo(createdAtBefore);
		assertThat(entity.getUpdatedAt()).isAfter(updatedAtBefore);
	}

	@Test
	void deleteSetsDeletedAtButKeepsPhysicalRow_andHidesFromEntityManagerFind() {
		SoftDeleteSampleEntity entity = new SoftDeleteSampleEntity("to-delete");
		entityManager.persist(entity);
		entityManager.flush();
		Long id = entity.getId();

		entityManager.remove(entity);
		entityManager.flush();
		entityManager.clear();

		// PRD 8.3의 미확정 가정 검증: @SQLRestriction이 EntityManager.find(ID 직접 로드)에도 적용되는지
		assertThat(entityManager.find(SoftDeleteSampleEntity.class, id)).isNull();

		// 네이티브 쿼리(JdbcTemplate)로 물리 행이 실제로 남아 있는지 직접 확인 — @SQLRestriction 영향을 받지 않는 경로
		Boolean physicalRowExists = jdbcTemplate.queryForObject(
				"SELECT EXISTS(SELECT 1 FROM soft_delete_sample WHERE id = ?)",
				Boolean.class, id);
		LocalDateTime deletedAt = jdbcTemplate.queryForObject(
				"SELECT deleted_at FROM soft_delete_sample WHERE id = ?",
				LocalDateTime.class, id);

		assertThat(physicalRowExists).isTrue();
		assertThat(deletedAt).isNotNull();
	}
}
