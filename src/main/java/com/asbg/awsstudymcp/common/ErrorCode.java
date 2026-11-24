package com.asbg.awsstudymcp.common;

public enum ErrorCode {

	VALIDATION_FAILED(400),
	UNAUTHORIZED(401),
	FORBIDDEN(403),
	QA_NOT_FOUND(404),
	CONFLICT(409),
	RATE_LIMITED(429),
	INTERNAL_ERROR(500),
	EMBEDDING_FAILED(502),
	SEARCH_UNAVAILABLE(503);

	private final int httpStatus;

	ErrorCode(int httpStatus) {
		this.httpStatus = httpStatus;
	}

	public int httpStatus() {
		return httpStatus;
	}

}
