package com.example.storage;

import com.example.common.exception.CustomException;
import com.example.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3에 파일을 저장한다 (app.storage.type=s3, prod 프로파일 기본값). 클라이언트가 presigned
 * URL로 S3에 직접 PUT/GET하므로 writeUploadStream·loadAsResource(서버를 경유하는 로컬 전용
 * 엔드포인트)는 지원하지 않는다(첨부파일 가이드 §2·§9) — 호출되면 StorageService의 기본
 * 구현이 UnsupportedOperationException을 던진다.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;
	private final Duration urlExpiry;

	public S3StorageService(
			S3Client s3Client,
			S3Presigner s3Presigner,
			@Value("${app.storage.s3.bucket}") String bucket,
			@Value("${app.storage.url-expiry-minutes}") long urlExpiryMinutes) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.bucket = bucket;
		this.urlExpiry = Duration.ofMinutes(urlExpiryMinutes);
	}

	@Override
	public StorageType getType() {
		return StorageType.S3;
	}

	@Override
	public String createUploadUrl(Long attachmentId, String storageKey, String contentType) {
		// contentType을 서명에 포함시켜야 클라이언트가 실제로 보내는 Content-Type 헤더와 일치한다
		// (uploadFile은 스토리지 종류와 무관하게 항상 이 헤더를 보낸다) — 불일치 시 S3가 403을 반환한다.
		PutObjectRequest putRequest =
				PutObjectRequest.builder().bucket(bucket).key(storageKey).contentType(contentType).build();
		PutObjectPresignRequest presignRequest =
				PutObjectPresignRequest.builder().signatureDuration(urlExpiry).putObjectRequest(putRequest).build();
		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
		return presigned.url().toString();
	}

	@Override
	public boolean requiresAuthHeaderForUpload() {
		// presigned URL 자체가 서명이다 — Authorization 헤더를 더하면 서명 불일치로 S3가 403을 반환한다 (가이드 §3)
		return false;
	}

	@Override
	public String createViewUrl(Long attachmentId, String storageKey) {
		GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(storageKey).build();
		GetObjectPresignRequest presignRequest =
				GetObjectPresignRequest.builder().signatureDuration(urlExpiry).getObjectRequest(getRequest).build();
		PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
		return presigned.url().toString();
	}

	@Override
	public long verifyUploaded(String storageKey) {
		// presigned PUT은 크기를 강제하지 못하므로 HeadObject로 실제 크기를 재확인하는 이 호출이
		// 5MB 상한의 유일한 강제 지점이다(가이드 §3). 객체가 없으면 클라이언트가 아직 PUT을
		// 실행하지 않았거나 실패한 것 — complete를 너무 일찍 호출한 경우다.
		try {
			return s3Client
					.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build())
					.contentLength();
		} catch (SdkServiceException e) {
			throw new CustomException(ErrorCode.FILE_005);
		}
	}

	@Override
	public byte[] readHeader(String storageKey, int length) {
		GetObjectRequest request =
				GetObjectRequest.builder().bucket(bucket).key(storageKey).range("bytes=0-" + (length - 1)).build();
		try (InputStream in = s3Client.getObject(request, ResponseTransformer.toInputStream())) {
			byte[] header = new byte[length];
			int read = in.readNBytes(header, 0, length);
			return read == length ? header : Arrays.copyOf(header, read);
		} catch (IOException | SdkServiceException e) {
			throw new CustomException(ErrorCode.FILE_005);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
		} catch (SdkServiceException e) {
			// 정리 배치의 다음 회차가 재시도한다 — 객체 하나 실패로 배치 전체를 막지 않는다 (LocalStorageService와 동일 정책)
		}
	}
}
