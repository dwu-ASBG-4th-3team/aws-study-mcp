package com.asbg.awsstudymcp.qa;

import java.util.List;

import com.asbg.awsstudymcp.qa.dto.res.SimilarQuestion;

// 상세 조회의 "비슷한 과거 질문"을 찾는 자리. 검색 자체는 의미 검색 담당이다.
public interface SimilarQuestionFinder {

	// 본인의 QA 중 question과 비슷한 것을 유사도 높은 순으로 돌려준다. 실패하면 예외를 던진다.
	// 현재 qaId 제외, 최대 3개 제한, 실패 시 빈 목록 처리는 QaService가 한다.
	List<SimilarQuestion> find(String userId, String question);

}
