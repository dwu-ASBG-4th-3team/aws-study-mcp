package com.asbg.awsstudymcp.common;

import com.fasterxml.jackson.annotation.JsonProperty;

// 모든 REST API의 공통 응답 형식. 실패하면 result는 null이다.
public record BaseResponse<T>(@JsonProperty("isSuccess") boolean isSuccess, String code, String message, T result) {

	private static final String SUCCESS_CODE = "COMMON_200";
	private static final String SUCCESS_MESSAGE = "요청에 성공했습니다.";

	public static <T> BaseResponse<T> success(T result) {
		return new BaseResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, result);
	}

	public static BaseResponse<Void> failure(String code, String message) {
		return new BaseResponse<>(false, code, message, null);
	}

}
