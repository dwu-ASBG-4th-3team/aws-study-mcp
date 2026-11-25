package com.asbg.awsstudymcp.qa.exception;

import com.asbg.awsstudymcp.common.ErrorCode;

public enum QaErrorCode implements ErrorCode {

	QA_NOT_FOUND(404, "해당 Q&A가 없습니다.");

	private final int httpStatus;
	private final String message;

	QaErrorCode(int httpStatus, String message) {
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
