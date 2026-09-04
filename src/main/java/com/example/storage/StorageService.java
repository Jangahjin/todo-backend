package com.example.storage;

import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * 로컬 디스크와 S3를 같은 인터페이스로 다룬다
 * (docs/guides/tiptap-s3-image-upload-prompt.md §2). AttachmentService는 이 인터페이스만
 * 의존하고 어떤 구현체가 실제로 동작하는지 알지 못한다.
 */
public interface StorageService {

	StorageType getType();

	/** presign 단계에서 발급하는 업로드 대상 URL. 로컬은 백엔드 엔드포인트, S3는 presigned PUT URL. */
	String createUploadUrl(Long attachmentId, String storageKey, String contentType);

	/**
	 * 이 업로드 URL에 Authorization 헤더를 실어야 하는지. S3 presigned URL에 Authorization을
	 * 붙이면 서명 불일치로 거부되므로 반드시 false를 반환해야 한다 (가이드 §3).
	 */
	boolean requiresAuthHeaderForUpload();

	/** 조회용 URL. 로컬은 서명 토큰이 붙은 자체 엔드포인트, S3는 presigned GET URL. */
	String createViewUrl(Long attachmentId, String storageKey);

	/**
	 * 업로드 완료 후 실제 파일 크기를 확인한다 (로컬: 파일 시스템, S3: HeadObject).
	 * presigned PUT은 크기를 강제하지 못하므로 이 호출이 크기 상한의 유일한 강제 지점이다 (가이드 §3).
	 */
	long verifyUploaded(String storageKey);

	/** 매직 바이트 검증을 위해 파일 앞부분 일부를 읽는다. */
	byte[] readHeader(String storageKey, int length);

	void delete(String storageKey);

	/**
	 * PUT /api/attachments/{id}/upload 로 들어온 스트림을 저장한다. 로컬 전용이다 — S3는
	 * 클라이언트가 presigned URL로 직접 업로드하므로 이 메서드가 호출되지 않는다.
	 */
	default void writeUploadStream(String storageKey, InputStream input, long maxBytes) {
		throw new UnsupportedOperationException("이 스토리지는 서버를 거치는 업로드를 지원하지 않습니다.");
	}

	/** GET /api/attachments/{id}/raw 응답용 리소스. 로컬 전용이다. */
	default Resource loadAsResource(String storageKey) {
		throw new UnsupportedOperationException("이 스토리지는 서버를 거치는 조회를 지원하지 않습니다.");
	}
}
