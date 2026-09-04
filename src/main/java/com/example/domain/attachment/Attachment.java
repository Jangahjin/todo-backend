package com.example.domain.attachment;

import com.example.common.entity.BaseEntity;
import com.example.domain.todo.Todo;
import com.example.domain.user.User;
import com.example.storage.StorageType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * tiptap-s3-image-upload-prompt.md §1. todo는 업로드 시점에는 비어 있다가 Todo 저장 시
 * TodoService가 본문(Tiptap JSON)에서 attachmentId를 순회 수집해 연결한다 — HTML
 * data-attachment-id 파싱이 아니다. user는 삭제·조회 시 소유권 검증에 쓰인다.
 */
@Entity
@Table(
		name = "attachment",
		indexes = {
			@Index(name = "idx_attachment_todo", columnList = "todo_id"),
			@Index(name = "idx_attachment_status_created", columnList = "status, created_at")
		})
@Getter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE attachment SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Attachment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "todo_id")
	private Todo todo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "storage_type", nullable = false, length = 20)
	private StorageType storageType;

	@Column(name = "storage_key", nullable = false, unique = true, length = 512)
	private String storageKey;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AttachmentStatus status = AttachmentStatus.TEMP;

	public Attachment(
			User user, StorageType storageType, String storageKey, String originalFilename, String contentType, long fileSize) {
		this.user = user;
		this.storageType = storageType;
		this.storageKey = storageKey;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.fileSize = fileSize;
	}

	/** complete 단계에서 서버가 재확인한 실제 크기로 갱신한다 — presign 시점의 값은 클라이언트 신고값일 뿐이다. */
	public void updateFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public void link(Todo todo) {
		this.todo = todo;
		this.status = AttachmentStatus.LINKED;
	}
}
