package com.asbg.awsstudymcp.common;

public enum CommonErrorCode implements ErrorCode {

	VALIDATION_FAILED(400, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(401, "인증이 필요합니다."),
	FORBIDDEN(403, "권한이 없습니다."),
	CONFLICT(409, "이미 처리된 요청입니다."),
	RATE_LIMITED(429, "요청 한도를 초과했습니다."),
	INTERNAL_ERROR(500, "서버 오류가 발생했습니다.");

	private final int httpStatus;
	private final String message;

	CommonErrorCode(int httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

	@Override
	public int httpStatus() {
		return httpStatus;
	}

	@Override
	public String code() {
		return "COMMON_" + httpStatus;
	}

	@Override
	public String message() {
		return message;
	}

}
