package com.example.support;

import com.example.demo.TodoBackendApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 테스트 공통 베이스 클래스.
 * com.example.support는 @SpringBootApplication이 있는 com.example.demo의 하위 패키지가
 * 아니라서 설정 클래스를 명시적으로 지정해야 한다(패키지 상위 탐색으로는 못 찾음).
 * 데이터 격리는 @Transactional 롤백 방식을 사용한다 — 각 테스트 메서드 종료 시 자동 롤백되어
 * todolistdb_test 스키마에 데이터가 남지 않는다.
 */
@SpringBootTest(classes = TodoBackendApplication.class)
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestSupport {
}
