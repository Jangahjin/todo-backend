package com.example.domain.todo;

import com.example.common.entity.BaseEntity;
import com.example.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * PRD 8.2 todos 테이블. content는 Tiptap JSON 문서를 그대로 담는 불투명(opaque) 문자열이다 (PRD 8.4).
 * user 연관관계는 목록 조회(페이지네이션) N+1을 막기 위해 LAZY를 명시한다 (PRD 8.3).
 */
@Entity
@Table(
		name = "todos",
		indexes = {
				@Index(name = "idx_todos_user_deleted", columnList = "user_id, deleted_at"),
				@Index(name = "idx_todos_user_status_deleted", columnList = "user_id, status, deleted_at")
		})
@Getter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE todos SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Todo extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY) // ✅ 기본값 EAGER를 명시적으로 덮어씀 (PRD 8.3)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 255)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TodoStatus status = TodoStatus.TODO;

	@Column(name = "due_date")
	private LocalDate dueDate;

	public Todo(User user, String title, String content, LocalDate dueDate) {
		this.user = user;
		this.title = title;
		this.content = content;
		this.dueDate = dueDate;
	}

	// PUT은 전체 교체다 — content·dueDate 생략 시 null로 덮어써도 된다 (title·status는 NOT NULL이라 호출부가 보장, API_SPEC 4.5)
	public void replace(String title, String content, LocalDate dueDate, TodoStatus status) {
		this.title = title;
		this.content = content;
		this.dueDate = dueDate;
		this.status = status;
	}

	// PATCH 상태 변경은 멱등이어야 한다 — 클라이언트가 지정한 목표 상태로 그대로 설정한다 (불변 규칙 13)
	public void changeStatus(TodoStatus status) {
		this.status = status;
	}
}
