package com.example.health;

import com.example.common.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 헬스체크용 엔드포인트. prod 프로파일은 ddl-auto=validate와 필수 환경변수 기본값 없음으로
 * 구성되어 있어, DB/설정이 잘못되면 Spring 컨텍스트 자체가 기동하지 않는다 — 이 엔드포인트가
 * 200을 반환한다는 사실 자체가 DataSource 초기화 성공을 의미하므로 별도 DB ping은 하지 않는다.
 */
@RestController
public class HealthController {

	@GetMapping("/api/health")
	public ResponseEntity<ApiResponse<Map<String, String>>> health() {
		return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP")));
	}
}
