package com.asbg.awsstudymcp.qa;

import java.util.Optional;

public interface QaRepository {

	QaRecord save(QaRecord record);

	Optional<QaRecord> find(String userId, String qaId);

	// 레코드가 있을 때만 갱신한다. 확인과 쓰기가 한 번에 일어나야 동시에 삭제된 Q&A가 되살아나지 않는다.
	Optional<QaRecord> updateRating(String userId, String qaId, QaRecord.Rating rating);

	boolean delete(String userId, String qaId);

}
