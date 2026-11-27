package com.asbg.awsstudymcp.qa;


import java.time.Instant;
import java.util.Optional;

import com.asbg.awsstudymcp.qa.enums.Rating;

public interface QaRepository {

	// 새 기록을 저장한다.
	void save(QaItem record);

	Optional<QaItem> find(String userId, String qaId);

	// 레코드가 있을 때만 갱신한다. 동시에 삭제된 Q&A가 되살아나지 않아야 한다.
	Optional<QaItem> updateRating(String userId, String qaId, Rating rating, Instant now);

	// 완전 삭제. 대상이 없으면 false
	boolean delete(String userId, String qaId);

}
