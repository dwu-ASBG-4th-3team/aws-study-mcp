package com.asbg.awsstudymcp.search.exception;

import com.asbg.awsstudymcp.common.ErrorCode;

public enum SearchErrorCode implements ErrorCode {

	// 임베딩(Gemini) 또는 S3 Vectors의 일시 오류. 잠시 뒤 재시도하면 된다.
	SEARCH_UNAVAILABLE(503, "검색을 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

	private final int httpStatus;
	private final String message;

	SearchErrorCode(int httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

	@Override
	public int httpStatus() {
		return httpStatus;
	}

	@Override
	public String code() {
		return name();
	}

	@Override
	public String message() {
		return message;
	}

}
