package com.asbg.awsstudymcp.search.embedding;

/**
 * 임베딩 호출 실패. retryable이 true면 잠시 뒤 다시 시도할 수 있는 오류(호출 한도, 서버 오류, 네트워크)다.
 * 인덱싱 Lambda는 이 값으로 SQS 재시도 여부를, 검색 API는 RETRY_LATER 응답 여부를 정한다.
 */
public class EmbeddingException extends RuntimeException {

	private final boolean retryable;

	public EmbeddingException(String message, boolean retryable) {
		this(message, retryable, null);
	}

	public EmbeddingException(String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.retryable = retryable;
	}

	public boolean isRetryable() {
		return retryable;
	}

}
