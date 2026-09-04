package com.example.domain.attachment;

import com.example.attachment.dto.AttachmentUrlResponse;
import com.example.attachment.dto.CompleteResponse;
import com.example.attachment.dto.PresignRequest;
import com.example.attachment.dto.PresignResponse;
import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import com.example.domain.todo.Todo;
import com.example.domain.user.User;
import com.example.domain.user.UserRepository;
import com.example.storage.AttachmentUrlTokenProvider;
import com.example.storage.StorageService;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 첨부파일 업로드·연결·조회·정리를 담당한다. StorageService만 의존하고 로컬/S3 구현체를
 * 구분하지 않는다 (가이드 §2). Controller에 비즈니스 로직을 두지 않는 기존 컨벤션을 따른다.
 */
@Service
@Transactional(readOnly = true)
public class AttachmentService {

	// contentType → 확장자 역매핑 (가이드 §1: 원본 파일명이 아니라 검증을 통과한 contentType에서 뽑는다).
	// SVG는 인라인 <script>를 담을 수 있어 저장형 XSS 벡터가 되므로 애초에 이 표에 없다 (가이드 §6)
	private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/gif", "gif",
			"image/webp", "webp");

	private static final Duration ORPHAN_TEMP_TTL = Duration.ofHours(24);
	private static final Duration SOFT_DELETE_GRACE_PERIOD = Duration.ofDays(7);

	private final AttachmentRepository attachmentRepository;
	private final UserRepository userRepository;
	private final StorageService storageService;
	private final AttachmentUrlTokenProvider tokenProvider;
	private final long maxFileSize;
	private final Set<String> allowedContentTypes;

	public AttachmentService(
			AttachmentRepository attachmentRepository,
			UserRepository userRepository,
			StorageService storageService,
			AttachmentUrlTokenProvider tokenProvider,
			@Value("${app.upload.max-file-size}") long maxFileSize,
			@Value("${app.upload.allowed-content-types}") String allowedContentTypesCsv) {
		this.attachmentRepository = attachmentRepository;
		this.userRepository = userRepository;
		this.storageService = storageService;
		this.tokenProvider = tokenProvider;
		this.maxFileSize = maxFileSize;
		this.allowedContentTypes = Set.copyOf(Arrays.asList(allowedContentTypesCsv.split(",")));
	}

	@Transactional
	public PresignResponse presign(Long userId, PresignRequest request) {
		String extension = extensionFor(request.contentType());
		validateDeclaredSize(request.fileSize());

		String storageKey = buildStorageKey(userId, extension);
		User user = userRepository.getReferenceById(userId);
		Attachment attachment =
				new Attachment(user, storageService.getType(), storageKey, request.filename(), request.contentType(), request.fileSize());
		attachmentRepository.save(attachment);

		String uploadUrl = storageService.createUploadUrl(attachment.getId(), storageKey, request.contentType());
		return new PresignResponse(attachment.getId(), uploadUrl, storageService.requiresAuthHeaderForUpload());
	}

	// PUT /api/attachments/{id}/upload — 로컬 전용 (S3는 클라이언트가 presigned URL로 직접 업로드한다)
	@Transactional
	public void receiveLocalUpload(Long userId, Long attachmentId, InputStream input) {
		Attachment attachment = findOwnedTempOrThrow(userId, attachmentId);
		storageService.writeUploadStream(attachment.getStorageKey(), input, maxFileSize);
	}

	@Transactional
	public CompleteResponse complete(Long userId, Long attachmentId) {
		Attachment attachment = findOwnedTempOrThrow(userId, attachmentId);

		long actualSize = storageService.verifyUploaded(attachment.getStorageKey());
		if (actualSize > maxFileSize) {
			storageService.delete(attachment.getStorageKey());
			attachmentRepository.delete(attachment);
			throw new CustomException(ErrorCode.FILE_001);
		}

		byte[] header = storageService.readHeader(attachment.getStorageKey(), 12);
		if (!matchesMagicBytes(attachment.getContentType(), header)) {
			storageService.delete(attachment.getStorageKey());
			attachmentRepository.delete(attachment);
			throw new CustomException(ErrorCode.FILE_002, "파일 내용이 선언된 형식과 일치하지 않습니다.");
		}

		attachment.updateFileSize(actualSize);
		String viewUrl = storageService.createViewUrl(attachment.getId(), attachment.getStorageKey());
		return new CompleteResponse(attachment.getId(), viewUrl);
	}

	public List<AttachmentUrlResponse> getViewUrls(Long userId, List<Long> ids) {
		return attachmentRepository.findByIdInAndUser_IdAndDeletedAtIsNull(ids, userId).stream()
				.map(a -> new AttachmentUrlResponse(a.getId(), storageService.createViewUrl(a.getId(), a.getStorageKey())))
				.toList();
	}

	// GET /api/attachments/{id}/raw?token=... — permitAll이라 서명 토큰으로 직접 인가한다 (가이드 §4)
	public RawAttachment loadRaw(Long attachmentId, String token) {
		if (!tokenProvider.isValid(token, attachmentId)) {
			throw new CustomException(ErrorCode.FILE_003);
		}
		// findById는 @SQLRestriction 대상이라 soft-deleted 레코드는 자동으로 제외된다
		Attachment attachment =
				attachmentRepository.findById(attachmentId).orElseThrow(() -> new CustomException(ErrorCode.FILE_003));
		Resource resource = storageService.loadAsResource(attachment.getStorageKey());
		return new RawAttachment(resource, attachment.getContentType());
	}

	@Transactional
	public void delete(Long userId, Long attachmentId) {
		Attachment attachment = findOwnedOrThrow(userId, attachmentId);
		attachmentRepository.delete(attachment); // soft delete — 실제 파일은 정리 배치가 유예 기간 후 삭제한다
	}

	/**
	 * Todo 생성/수정 시 TodoService가 호출한다. content(Tiptap JSON)에서 attachmentId를 순회
	 * 수집해 본문에 남아 있는 첨부만 LINKED로 전환하고, 사라진 첨부는 soft delete한다 (가이드 §6).
	 */
	@Transactional
	public void syncLinks(Long userId, Todo todo, JsonNode content) {
		Set<Long> currentIds = collectAttachmentIds(content);

		for (Attachment previouslyLinked : attachmentRepository.findByTodo_IdAndDeletedAtIsNull(todo.getId())) {
			if (!currentIds.contains(previouslyLinked.getId())) {
				attachmentRepository.delete(previouslyLinked); // 본문에서 사라진 첨부
			}
		}

		if (currentIds.isEmpty()) {
			return;
		}

		List<Attachment> toLink = attachmentRepository.findByIdInAndUser_IdAndDeletedAtIsNull(List.copyOf(currentIds), userId);
		if (toLink.size() != currentIds.size()) {
			// 존재하지 않거나 다른 사용자 소유인 ID가 섞여 있음
			throw new CustomException(ErrorCode.FILE_003);
		}
		for (Attachment attachment : toLink) {
			if (attachment.getTodo() != null && !attachment.getTodo().getId().equals(todo.getId())) {
				// 이미 다른 Todo에 LINKED된 첨부 재사용 금지 (가이드 §1 — 복제된 본문은 재업로드를 요구한다)
				throw new CustomException(ErrorCode.FILE_003);
			}
			attachment.link(todo);
		}
	}

	/** Todo가 Soft Delete될 때 TodoService가 호출한다 — 연결된 첨부를 고아로 남기지 않는다 (가이드 §6). */
	@Transactional
	public void deleteAllForTodo(Todo todo) {
		attachmentRepository.findByTodo_IdAndDeletedAtIsNull(todo.getId()).forEach(attachmentRepository::delete);
	}

	/**
	 * 하루 1회 두 종류의 고아를 물리 삭제한다: 업로드만 되고 Todo 저장으로 이어지지 않은 TEMP,
	 * 그리고 soft delete 후 유예 기간이 지난 레코드 (가이드 §6). deleteAllInBatch는 벌크 쿼리라
	 * @SQLDelete가 가로채는 엔티티 라이프사이클 이벤트를 거치지 않으므로 실제 DELETE가 실행된다.
	 */
	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public void cleanupOrphans() {
		LocalDateTime now = LocalDateTime.now();
		purge(attachmentRepository.findByStatusAndCreatedAtBefore(AttachmentStatus.TEMP, now.minus(ORPHAN_TEMP_TTL)));
		purge(attachmentRepository.findSoftDeletedBefore(now.minus(SOFT_DELETE_GRACE_PERIOD)));
	}

	private void purge(List<Attachment> attachments) {
		if (attachments.isEmpty()) {
			return;
		}
		attachments.forEach(a -> storageService.delete(a.getStorageKey()));
		attachmentRepository.deleteAllInBatch(attachments);
	}

	private Attachment findOwnedOrThrow(Long userId, Long attachmentId) {
		return attachmentRepository
				.findByIdAndUser_IdAndDeletedAtIsNull(attachmentId, userId)
				.orElseThrow(() -> new CustomException(ErrorCode.FILE_003));
	}

	private Attachment findOwnedTempOrThrow(Long userId, Long attachmentId) {
		Attachment attachment = findOwnedOrThrow(userId, attachmentId);
		if (attachment.getStatus() != AttachmentStatus.TEMP) {
			throw new CustomException(ErrorCode.FILE_004, "이미 처리된 첨부입니다.");
		}
		return attachment;
	}

	private void validateDeclaredSize(long fileSize) {
		if (fileSize <= 0 || fileSize > maxFileSize) {
			throw new CustomException(ErrorCode.FILE_001);
		}
	}

	private String extensionFor(String contentType) {
		if (!allowedContentTypes.contains(contentType)) {
			throw new CustomException(ErrorCode.FILE_002);
		}
		String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);
		if (extension == null) {
			// app.upload.allowed-content-types에는 있지만 확장자 매핑표에는 없는 설정 불일치 — 안전 실패
			throw new CustomException(ErrorCode.FILE_002);
		}
		return extension;
	}

	// 원본 파일명은 신뢰하지 않는다 — 화이트리스트를 통과한 contentType에서 확장자를 역매핑한다 (가이드 §1)
	private String buildStorageKey(Long userId, String extension) {
		LocalDate today = LocalDate.now();
		return "todos/%d/%04d/%02d/%s.%s"
				.formatted(userId, today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
	}

	// content는 Tiptap 문서({"type":"doc","content":[...]})다. HTML 파싱이 아니라 JSON 노드 트리를 순회한다
	private Set<Long> collectAttachmentIds(JsonNode content) {
		Set<Long> ids = new HashSet<>();
		if (content != null) {
			collectAttachmentIdsRecursive(content, ids);
		}
		return ids;
	}

	private void collectAttachmentIdsRecursive(JsonNode node, Set<Long> ids) {
		if ("image".equals(node.path("type").asString(null))) {
			JsonNode attachmentId = node.path("attrs").path("attachmentId");
			if (!attachmentId.isMissingNode() && !attachmentId.isNull()) {
				ids.add(attachmentId.asLong());
			}
		}
		for (JsonNode child : node.path("content")) {
			collectAttachmentIdsRecursive(child, ids);
		}
	}

	private boolean matchesMagicBytes(String contentType, byte[] header) {
		return switch (contentType) {
			case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
			case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47);
			case "image/gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38);
			case "image/webp" -> header.length >= 12
					&& startsWith(header, 0x52, 0x49, 0x46, 0x46) // "RIFF"
					&& header[8] == 0x57
					&& header[9] == 0x45
					&& header[10] == 0x42
					&& header[11] == 0x50; // "WEBP"
			default -> false;
		};
	}

	private boolean startsWith(byte[] header, int... expected) {
		if (header.length < expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((header[i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}

	/** 컨트롤러가 ResponseEntity를 조립하는 데 필요한 최소 정보만 담는다. */
	public record RawAttachment(Resource resource, String contentType) {
	}
}
