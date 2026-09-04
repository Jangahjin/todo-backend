package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 이 설정이 없으면 AttachmentService.cleanupOrphans()의 @Scheduled가 조용히 등록되지 않는다
 * (가이드 §6 고아 파일 정리). 현재 EC2 단일 인스턴스 배포이므로 다중 인스턴스 중복 실행 문제는 없다 —
 * 배포 구성이 바뀌면 재검토한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
