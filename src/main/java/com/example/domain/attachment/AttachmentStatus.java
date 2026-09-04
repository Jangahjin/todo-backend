package com.example.domain.attachment;

/**
 * 업로드는 Todo 저장보다 먼저 일어난다 — presign 시점에는 아직 어떤 Todo에도 속하지 않으므로
 * TEMP로 시작하고, Todo 본문에 실제로 남아 있을 때만 LINKED로 전환된다 (가이드 §1).
 */
public enum AttachmentStatus {
	TEMP,
	LINKED
}
