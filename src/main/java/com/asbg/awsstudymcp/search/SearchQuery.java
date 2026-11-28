package com.asbg.awsstudymcp.search;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;

/**
 * 검색 조건. MCP·REST·ask_study가 같은 규칙을 쓰도록 검증을 {@link #of}에 모은다.
 * @param domain null이면 전체
 * @param feedback 최근 이해도 필터. null이면 전체
 * @param excludeSuspectedWrong true면 latestFeedback=WRONG인 QA를 뺀다 (ask_study용)
 */
public record SearchQuery(String query, int topK, Domain domain, Rating feedback, boolean excludeSuspectedWrong) {

	public static final int MAX_QUERY_LENGTH = 1_000;
	public static final int DEFAULT_TOP_K = 5;
	public static final int MAX_TOP_K = 10;

	public SearchQuery {
		if (query == null || query.isBlank()) {
			throw invalid("query는 비어 있을 수 없습니다.");
		}
		query = query.strip();
		if (query.length() > MAX_QUERY_LENGTH) {
			throw invalid("query는 " + MAX_QUERY_LENGTH + "자 이하여야 합니다.");
		}
		if (topK < 1 || topK > MAX_TOP_K) {
			throw invalid("topK는 1~" + MAX_TOP_K + " 사이여야 합니다.");
		}
		if (excludeSuspectedWrong && feedback == Rating.WRONG) {
			throw invalid("오류 의심 QA를 제외하면서 feedback=WRONG으로 검색할 수 없습니다.");
		}
	}

	/** 사용자 입력용. topK가 null이면 기본값 5. */
	public static SearchQuery of(String query, Integer topK, Domain domain, Rating feedback) {
		return new SearchQuery(query, topK == null ? DEFAULT_TOP_K : topK, domain, feedback, false);
	}

	/** ask_study 문맥 구성용: 오류 의심 QA를 뺀다. */
	public SearchQuery excludingSuspectedWrong() {
		return new SearchQuery(query, topK, domain, feedback, true);
	}

	private static ApiException invalid(String message) {
		return new ApiException(CommonErrorCode.VALIDATION_FAILED, message);
	}

}
