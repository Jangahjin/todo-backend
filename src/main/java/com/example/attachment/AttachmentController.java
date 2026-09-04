package com.example.attachment;

import com.example.attachment.dto.AttachmentUrlResponse;
import com.example.attachment.dto.CompleteResponse;
import com.example.attachment.dto.PresignRequest;
import com.example.attachment.dto.PresignResponse;
import com.example.attachment.dto.ViewUrlsRequest;
import com.example.attachment.dto.ViewUrlsResponse;
import com.example.common.dto.ApiResponse;
import com.example.domain.attachment.AttachmentService;
import com.example.domain.attachment.AttachmentService.RawAttachment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 첨부파일 API (tiptap-s3-image-upload-prompt.md §6). presign/complete/urls/delete는
 * ApiResponse로 래핑되지만, raw(바이너리 스트림)와 S3 presigned PUT 응답은 그럴 수 없다 —
 * API_SPEC 1.1 "모든 응답은 예외 없이 래핑"의 명시적 예외다.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

	private final AttachmentService attachmentService;

	public AttachmentController(AttachmentService attachmentService) {
		this.attachmentService = attachmentService;
	}

	@PostMapping("/presign")
	public ResponseEntity<ApiResponse<PresignResponse>> presign(
			@AuthenticationPrincipal Long userId, @Valid @RequestBody PresignRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(attachmentService.presign(userId, request)));
	}

	// 로컬 전용 — S3 모드에서는 presign이 이 경로 대신 presigned PUT URL을 내려주므로 호출되지 않는다
	@PutMapping("/{id}/upload")
	public ResponseEntity<Void> upload(@AuthenticationPrincipal Long userId, @PathVariable Long id, HttpServletRequest request)
			throws IOException {
		attachmentService.receiveLocalUpload(userId, id, request.getInputStream());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{id}/complete")
	public ApiResponse<CompleteResponse> complete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		return ApiResponse.success(attachmentService.complete(userId, id));
	}

	@PostMapping("/urls")
	public ApiResponse<ViewUrlsResponse> urls(@AuthenticationPrincipal Long userId, @Valid @RequestBody ViewUrlsRequest request) {
		List<AttachmentUrlResponse> urls = attachmentService.getViewUrls(userId, request.ids());
		return ApiResponse.success(new ViewUrlsResponse(urls));
	}

	// permitAll (SecurityConfig) — <img> 태그가 직접 호출해 Authorization 헤더를 실을 수 없다.
	// 대신 쿼리의 서명 토큰으로 인가한다 (가이드 §4).
	@GetMapping("/{id}/raw")
	public ResponseEntity<Resource> raw(@PathVariable Long id, @RequestParam String token) {
		RawAttachment raw = attachmentService.loadRaw(id, token);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(raw.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
				// MIME 스니핑 방지 — inline 응답은 이 헤더 없이는 브라우저가 콘텐츠를 실행 가능한 것으로 오판할 수 있다
				.header("X-Content-Type-Options", "nosniff")
				.body(raw.resource());
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		attachmentService.delete(userId, id);
		return ApiResponse.success(null, "삭제되었습니다.");
	}
}
