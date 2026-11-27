package com.asbg.awsstudymcp.qa;

import java.util.List;

import org.springframework.stereotype.Component;

import com.asbg.awsstudymcp.qa.dto.res.SimilarQuestion;

// 임시 구현: 검색이 아직 없어 항상 빈 목록을 돌려준다.
//
// TODO SearchService(feat/search-api-spec, docs/search-api.md)가 dev에 머지되면 이 클래스를 교체한다.
//  1. ObjectProvider<SearchService>를 주입받는다. 구현체가 없어도 앱이 기동해야 하므로 없으면 빈 목록을 돌려준다.
//  2. 검색어는 question의 앞 1,000자(SearchQuery.MAX_QUERY_LENGTH)로 자른다. 질문은 최대 2,000자다.
//  3. search(userId, SearchQuery.of(검색어, 4, null, null))를 호출한다.
//     - 자기 자신이 섞여 나올 수 있어 3개가 아니라 4개를 요청한다.
//     - 도메인·평가 필터는 걸지 않는다. WRONG 기록도 보여야 하므로 excludingSuspectedWrong()은 쓰지 않는다.
//  4. 결과의 SearchHit에서 qaId, question만 순서대로 SimilarQuestion으로 바꾼다.
//  5. 예외는 잡지 않는다. QaService가 경고 로그를 남기고 빈 목록으로 처리한다.
//  6. 1,000자 자르기와 topK=4를 확인하는 테스트를 추가한다.
@Component
class EmptySimilarQuestionFinder implements SimilarQuestionFinder {

	@Override
	public List<SimilarQuestion> find(String userId, String question) {
		return List.of();
	}

}
