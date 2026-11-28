package com.asbg.awsstudymcp.qa.exception;

import com.asbg.awsstudymcp.common.ErrorCode;

public enum QaErrorCode implements ErrorCode {

	QA_NOT_FOUND(404, "QA_001", "기록을 찾을 수 없습니다.");

	private final int httpStatus;
	private final String code;
	private final String message;

	QaErrorCode(int httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	@Override
	public int httpStatus() {
		return httpStatus;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String message() {
		return message;
	}

}
