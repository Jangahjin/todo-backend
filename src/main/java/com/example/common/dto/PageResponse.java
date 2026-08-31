package com.example.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 조회 시 ApiResponse.data에 담기는 페이지네이션 래퍼 (API_SPEC 1.2). */
public record PageResponse<T>(
		List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.hasNext());
	}
}
