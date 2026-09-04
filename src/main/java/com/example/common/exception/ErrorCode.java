package com.example.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API_SPEC 2장 에러코드 체계를 그대로 반영한다. */
@Getter
public enum ErrorCode {

	// 2.1 공통
	COMMON_001(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
	COMMON_002(HttpStatus.BAD_REQUEST, "잘못된 요청 파라미터입니다."),
	COMMON_500(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// 2.2 인증 — AUTH_001은 이메일 없음/비밀번호 불일치를 구분하지 않는다 (가입 이메일 열거 방지)
	AUTH_001(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
	AUTH_002(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	AUTH_003(HttpStatus.UNAUTHORIZED, "토큰이 없거나 형식이 잘못되었습니다."),
	AUTH_004(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
	AUTH_005(HttpStatus.UNAUTHORIZED, "위조된 토큰입니다."),
	AUTH_006(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
	AUTH_007(HttpStatus.BAD_REQUEST, "소셜 로그인 처리에 실패했습니다."),

	// 2.3 Todo — 소유권 위반도 403이 아닌 404(TODO_001)로 응답한다 (PRD 10장, 불변 규칙 11)
	TODO_001(HttpStatus.NOT_FOUND, "Todo를 찾을 수 없습니다."),
	TODO_002(HttpStatus.BAD_REQUEST, "잘못된 상태값입니다."),

	// 2.4 첨부파일 — 소유권 위반도 403이 아닌 404(FILE_003)로 응답한다 (불변 규칙 11, TODO_001과 동일한 이유)
	FILE_001(HttpStatus.BAD_REQUEST, "파일 크기가 허용 범위를 초과했습니다."),
	FILE_002(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),
	FILE_003(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."),
	FILE_004(HttpStatus.CONFLICT, "이미 처리된 첨부파일입니다."),
	FILE_005(HttpStatus.BAD_REQUEST, "업로드가 완료되지 않았습니다.");

	private final HttpStatus httpStatus;
	private final String defaultMessage;

	ErrorCode(HttpStatus httpStatus, String defaultMessage) {
		this.httpStatus = httpStatus;
		this.defaultMessage = defaultMessage;
	}
}
