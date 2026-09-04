package com.example.storage;

import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

/**
 * 로컬 디스크에 파일을 저장한다 (app.storage.type=local, dev 프로파일 기본값).
 * storageKey는 baseDir 하위 상대경로다 — 경로 조작(../) 방어를 위해 최종 경로를 정규화한 뒤
 * baseDir 하위인지 반드시 확인한다(가이드 §4). storageKey는 presign 단계에서 서버가 직접
 * 생성하므로 현재 API로는 클라이언트가 이 값을 주입할 지점이 없지만, 심층 방어로 유지한다.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

	private final Path baseDir;
	private final String backendUrl;
	private final AttachmentUrlTokenProvider tokenProvider;

	public LocalStorageService(
			@Value("${app.storage.local.base-dir}") String baseDir,
			@Value("${app.storage.local.base-url}") String backendUrl,
			AttachmentUrlTokenProvider tokenProvider) {
		this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
		this.backendUrl = backendUrl;
		this.tokenProvider = tokenProvider;
		createBaseDirIfMissing();
	}

	private void createBaseDirIfMissing() {
		try {
			Files.createDirectories(baseDir);
		} catch (IOException e) {
			throw new IllegalStateException("업로드 디렉토리를 생성할 수 없습니다: " + baseDir, e);
		}
	}

	@Override
	public StorageType getType() {
		return StorageType.LOCAL;
	}

	@Override
	public String createUploadUrl(Long attachmentId, String storageKey, String contentType) {
		return backendUrl + "/api/attachments/" + attachmentId + "/upload";
	}

	@Override
	public boolean requiresAuthHeaderForUpload() {
		return true; // 우리 서버의 인증된 엔드포인트이므로 Bearer 토큰이 필요하다
	}

	@Override
	public String createViewUrl(Long attachmentId, String storageKey) {
		return backendUrl + "/api/attachments/" + attachmentId + "/raw?token=" + tokenProvider.issue(attachmentId);
	}

	@Override
	public long verifyUploaded(String storageKey) {
		Path path = resolve(storageKey);
		if (!Files.exists(path)) {
			throw new CustomException(ErrorCode.FILE_005);
		}
		try {
			return Files.size(path);
		} catch (IOException e) {
			throw new CustomException(ErrorCode.FILE_005);
		}
	}

	@Override
	public byte[] readHeader(String storageKey, int length) {
		Path path = resolve(storageKey);
		try (InputStream in = Files.newInputStream(path)) {
			byte[] header = new byte[length];
			int read = in.readNBytes(header, 0, length);
			return read == length ? header : Arrays.copyOf(header, read);
		} catch (IOException e) {
			throw new CustomException(ErrorCode.FILE_005);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		} catch (IOException e) {
			// 정리 배치의 다음 회차가 재시도한다 — 파일 하나 실패로 배치 전체를 막지 않는다
		}
	}

	@Override
	public void writeUploadStream(String storageKey, InputStream input, long maxBytes) {
		Path path = resolve(storageKey);
		if (Files.exists(path)) {
			throw new CustomException(ErrorCode.FILE_004);
		}
		try {
			Files.createDirectories(path.getParent());
		} catch (IOException e) {
			throw new CustomException(ErrorCode.COMMON_500);
		}

		long written = 0;
		byte[] buffer = new byte[8192];
		try (input;
				var out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
			int read;
			while ((read = input.read(buffer)) != -1) {
				written += read;
				if (written > maxBytes) {
					throw new CustomException(ErrorCode.FILE_001);
				}
				out.write(buffer, 0, read);
			}
		} catch (IOException e) {
			deleteQuietly(path);
			throw new CustomException(ErrorCode.COMMON_500);
		} catch (CustomException e) {
			deleteQuietly(path);
			throw e;
		}
	}

	@Override
	public Resource loadAsResource(String storageKey) {
		Path path = resolve(storageKey);
		try {
			Resource resource = new UrlResource(path.toUri());
			if (!resource.exists() || !resource.isReadable()) {
				throw new CustomException(ErrorCode.FILE_003);
			}
			return resource;
		} catch (MalformedURLException e) {
			throw new CustomException(ErrorCode.FILE_003);
		}
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// 부분 파일 정리 실패는 다음 고아 정리 배치가 처리한다
		}
	}

	/** storageKey를 baseDir 하위 절대경로로 정규화하고, 벗어나면 예외를 던진다 (경로 조작 방어). */
	Path resolve(String storageKey) {
		Path resolved = baseDir.resolve(storageKey).normalize();
		if (!resolved.startsWith(baseDir)) {
			throw new CustomException(ErrorCode.COMMON_002, "잘못된 저장 경로입니다.");
		}
		return resolved;
	}

	Path getBaseDir() {
		return baseDir;
	}
}
