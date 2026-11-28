package com.asbg.awsstudymcp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import software.amazon.awssdk.core.exception.SdkException;

// 모든 오류를 BaseResponse로 바꾼다. 예외 원문은 응답에 넣지 않는다.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApi(ApiException e) {
		return response(HttpStatusCode.valueOf(e.code().httpStatus()), new HttpHeaders(), e.code().code(),
				e.getMessage());
	}

	// AWS 오류 원문에는 계정 ID, 테이블 이름 등이 들어가므로 로그에만 남긴다.
	@ExceptionHandler(SdkException.class)
	public ResponseEntity<Object> handleAws(SdkException e) {
		log.error("AWS 호출 실패", e);
		return internalError();
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception e) {
		log.error("처리하지 못한 예외", e);
		return internalError();
	}

	// 잘못된 JSON·파라미터 타입, 없는 경로 등 Spring MVC 표준 예외가 모두 여기를 거친다.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("요청 처리 실패", e);
		}
		String message = switch (status.value()) {
			case 400 -> CommonErrorCode.VALIDATION_FAILED.message();
			case 404 -> "요청한 경로가 없습니다.";
			case 405 -> "지원하지 않는 요청 방식입니다.";
			case 415 -> "지원하지 않는 Content-Type입니다.";
			default -> status.is5xxServerError() ? CommonErrorCode.INTERNAL_ERROR.message() : "요청을 처리할 수 없습니다.";
		};
		return response(status, headers, "COMMON_" + status.value(), message);
	}

	private static ResponseEntity<Object> internalError() {
		CommonErrorCode code = CommonErrorCode.INTERNAL_ERROR;
		return response(HttpStatusCode.valueOf(code.httpStatus()), new HttpHeaders(), code.code(), code.message());
	}

	private static ResponseEntity<Object> response(HttpStatusCode status, HttpHeaders headers, String code,
			String message) {
		return ResponseEntity.status(status)
			.headers(headers)
			.contentType(MediaType.APPLICATION_JSON)
			.body(BaseResponse.failure(code, message));
	}

}
