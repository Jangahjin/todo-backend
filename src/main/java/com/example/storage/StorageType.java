package com.example.storage;

/** 첨부파일의 실제 저장 위치 (attachment 가이드 §1). Attachment 엔티티의 storage_type 컬럼에 저장된다. */
public enum StorageType {
	LOCAL,
	S3
}
