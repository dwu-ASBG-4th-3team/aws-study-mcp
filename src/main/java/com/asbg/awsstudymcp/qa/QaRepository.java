package com.asbg.awsstudymcp.qa;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;

public interface QaRepository {

	// 새 기록을 저장한다.
	void save(QaItem record);

	Optional<QaItem> find(String userId, String qaId);

	// 최신순으로 limit개까지 읽는다. domain이 null이면 전체, after가 null이면 처음부터
	Page findPage(String userId, Domain domain, Position after, int limit);

	// 최신 평가 갱신과 평가 이력 저장을 한 번에 처리한다.
	// 레코드가 있을 때만 갱신한다. 동시에 삭제된 Q&A가 되살아나지 않아야 한다.
	Optional<QaItem> updateRating(String userId, String qaId, Rating rating, Instant now);

	// 완전 삭제. 대상이 없으면 false
	boolean delete(String userId, String qaId);

	// 목록에서 마지막으로 돌려준 항목의 위치. createdAt은 GSI1 정렬 키 문자열이다.
	record Position(String qaId, String createdAt) {
	}

	// 다음 항목이 없으면 next는 null
	record Page(List<QaItem> items, Position next) {
	}

}
