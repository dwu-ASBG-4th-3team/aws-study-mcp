package com.asbg.awsstudymcp.search;

/**
 * QA 변경을 벡터 인덱싱 큐(SQS)에 알린다. 명세: docs/indexing.md
 *
 * <p>DynamoDB 쓰기가 성공한 뒤에 호출한다. 발행이 실패해도 사용자 요청은 성공으로 응답하고
 * 경고 로그만 남긴다. 누락된 건은 재처리 작업이 PENDING 상태를 보고 다시 발행한다.
 * 구현체는 예외를 밖으로 던지지 않는다.
 */
public interface IndexingPublisher {

	/** INT-001: save_qa 후 */
	void upsert(String userId, String qaId);

	/** INT-002: rate_answer 후 */
	void updateMeta(String userId, String qaId);

	/** INT-003: delete_qa 후 */
	void delete(String userId, String qaId);

}
