package com.asbg.awsstudymcp.search;

import java.time.Instant;
import java.util.List;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.enums.Verification;

/**
 * 검색 결과 한 건. 값은 모두 DynamoDB에서 다시 읽은 것이다.
 * 유사도 수치는 웹에서 표시하지 않기로 해서 넣지 않는다 (정렬 순서로만 반영).
 * @param answerPreview 답변 앞 {@value #ANSWER_PREVIEW_LENGTH}자 (코드 포인트 기준)
 * @param latestFeedback 없으면 null
 */
public record SearchHit(String qaId, String question, String answerPreview, Domain domain, List<String> tags,
		Rating latestFeedback, Verification verification, Instant createdAt) {

	public static final int ANSWER_PREVIEW_LENGTH = 300;

	public SearchHit {
		tags = tags == null ? List.of() : List.copyOf(tags);
	}

	public static String preview(String answer) {
		if (answer == null) {
			return "";
		}
		// 이모지 등 서로게이트 쌍이 잘리지 않도록 코드 포인트 기준으로 자른다.
		if (answer.codePointCount(0, answer.length()) <= ANSWER_PREVIEW_LENGTH) {
			return answer;
		}
		return answer.substring(0, answer.offsetByCodePoints(0, ANSWER_PREVIEW_LENGTH));
	}

}
