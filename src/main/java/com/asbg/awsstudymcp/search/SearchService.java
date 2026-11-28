package com.asbg.awsstudymcp.search;

/**
 * 사용자 본인의 QA 중 질문과 의미가 비슷한 기록을 찾는다 (MCP-004, REST-005, ask_study 공용).
 * 명세: docs/search-api.md
 *
 * <p>구현체는 userId를 모든 벡터 검색 필터에 강제하고, 결과를 DynamoDB에서 다시 읽어
 * 삭제됐거나 소유자가 다른 QA를 뺀다.
 *
 * @throws com.asbg.awsstudymcp.common.ApiException RATE_LIMITED(429), SEARCH_UNAVAILABLE(503),
 * INTERNAL_ERROR(500). 결과가 없으면 예외가 아니라 빈 결과를 돌려준다.
 */
public interface SearchService {

	/**
	 * @param userId 검증된 토큰의 sub. 사용자 입력에서 받지 않는다.
	 */
	SearchResult search(String userId, SearchQuery query);

}
