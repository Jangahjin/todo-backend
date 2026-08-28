package com.example.support.entity;

import com.example.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * BaseEntity의 Auditing·Soft Delete 동작을 검증하기 위한 테스트 전용 엔티티.
 * 실제 도메인 엔티티(User/Todo)는 Task 009에서 이 패턴을 그대로 적용한다.
 * UPDATE 문에 스키마를 명시하지 않는다 — JDBC URL의 currentSchema가 커넥션의
 * search_path로 이미 설정돼 있어(dev=todolistdb, test=todolistdb_test)
 * 비한정 테이블명이 환경마다 올바른 스키마로 자동 resolve된다.
 */
@Entity
@Table(name = "soft_delete_sample")
@Getter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE soft_delete_sample SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class SoftDeleteSampleEntity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	public SoftDeleteSampleEntity(String name) {
		this.name = name;
	}

	public void rename(String name) {
		this.name = name;
	}
}
