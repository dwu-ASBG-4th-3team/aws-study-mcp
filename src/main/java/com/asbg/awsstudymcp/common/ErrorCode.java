package com.asbg.awsstudymcp.common;

public interface ErrorCode {

	int httpStatus();

	String code();

	String message();

}
