package com.example.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 경로 조작(../) 방어는 storageKey를 서버가 직접 생성해 현재 API로는 클라이언트가 주입할
 * 지점이 없어 E2E로 재현할 수 없다 (tiptap-s3-image-upload-prompt.md §4·§8). 대신 이 방어
 * 로직 자체를 단위 테스트로 검증한다. Spring 컨텍스트가 필요 없으므로 순수 POJO로 생성한다.
 */
class LocalStorageServiceTest {

	@TempDir
	Path tempDir;

	private LocalStorageService storageService;

	@BeforeEach
	void setUp() {
		AttachmentUrlTokenProvider tokenProvider = new AttachmentUrlTokenProvider(
				"local-storage-test-secret-key-32-bytes-minimum", 30L);
		storageService = new LocalStorageService(tempDir.toString(), "http://localhost:8080", tokenProvider);
	}

	@Test
	void resolveNormalKeyStaysUnderBaseDir() {
		// 파일을 실제로 만들지 않으므로 AssertJ의 Path#startsWith(toRealPath 사용)는 쓰지 않는다 —
		// java.nio.file.Path#startsWith는 경로 컴포넌트만 비교해 파일 시스템 접근이 필요 없다
		Path resolved = storageService.resolve("todos/1/2026/09/file.jpg");
		assertThat(resolved.startsWith(storageService.getBaseDir())).isTrue();
	}

	@Test
	void resolveRejectsParentDirectoryEscape() {
		assertThatThrownBy(() -> storageService.resolve("../../../../etc/passwd"))
				.isInstanceOf(CustomException.class);
	}

	@Test
	void resolveRejectsAbsolutePathEscapeDisguisedAsRelative() {
		assertThatThrownBy(() -> storageService.resolve("todos/../../outside.jpg"))
				.isInstanceOf(CustomException.class);
	}

	@Test
	void writeThenVerifyThenLoadRoundTripsSuccessfully() {
		String storageKey = "todos/1/2026/09/round-trip.jpg";
		byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

		storageService.writeUploadStream(storageKey, new ByteArrayInputStream(jpegHeader), 5_242_880L);

		assertThat(storageService.verifyUploaded(storageKey)).isEqualTo(jpegHeader.length);
		assertThat(storageService.readHeader(storageKey, 3)).containsExactly(0xFF, 0xD8, 0xFF);
		assertThat(storageService.loadAsResource(storageKey).exists()).isTrue();
	}

	@Test
	void writeRejectsFileExceedingMaxBytes() {
		String storageKey = "todos/1/2026/09/too-large.jpg";
		byte[] oversized = new byte[100];

		assertThatThrownBy(() -> storageService.writeUploadStream(storageKey, new ByteArrayInputStream(oversized), 10L))
				.isInstanceOf(CustomException.class);
		assertThat(storageService.resolve(storageKey)).doesNotExist();
	}
}
