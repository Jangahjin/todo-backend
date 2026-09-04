package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * app.storage.type=s3 일 때만 등록된다 (첨부파일 가이드 §9). 자격증명은 코드에 두지 않고
 * DefaultCredentialsProvider가 환경변수(로컬 시험) 또는 IAM Role(EC2 운영)에서 자동으로
 * 가져온다 — 두 경우 모두 코드 분기가 필요 없다 (PRD 12.1 자격증명 원칙).
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

	@Value("${app.storage.s3.region}")
	private String region;

	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
				.region(Region.of(region))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
				.region(Region.of(region))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}
}
