package com.example.support;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Transactional 롤백 격리가 실제로 동작하는지 검증한다.
 * 첫 테스트에서 만든 스크래치 테이블/행이 두 번째 테스트에는 전혀 보이지 않아야 한다
 * (PostgreSQL은 DDL도 트랜잭션 대상이라 CREATE TABLE까지 롤백된다).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestIsolationVerificationTest extends IntegrationTestSupport {

	private static final String SCRATCH_TABLE = "test_isolation_scratch";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Order(1)
	void firstTest_createsScratchTableAndInsertsRow() {
		jdbcTemplate.execute("CREATE TABLE " + SCRATCH_TABLE + " (id INT)");
		jdbcTemplate.update("INSERT INTO " + SCRATCH_TABLE + " VALUES (1)");

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM " + SCRATCH_TABLE, Integer.class);

		assertThat(count).isEqualTo(1);
	}

	@Test
	@Order(2)
	void secondTest_previousScratchTableIsGone() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables "
						+ "WHERE table_schema = 'todolistdb_test' AND table_name = ?",
				Integer.class, SCRATCH_TABLE);

		assertThat(tableCount).isZero();
	}
}
