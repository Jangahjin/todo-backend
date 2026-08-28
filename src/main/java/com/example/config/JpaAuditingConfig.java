package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 이 설정이 없으면 @CreatedDate/@LastModifiedDate가 조용히 동작하지 않아
 * created_at/updated_at이 null로 저장되고 NOT NULL 제약에서 실패한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
