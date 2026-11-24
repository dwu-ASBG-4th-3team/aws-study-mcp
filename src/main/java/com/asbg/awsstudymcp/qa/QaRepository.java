package com.asbg.awsstudymcp.qa;

import java.util.Optional;

public interface QaRepository {

	QaRecord save(QaRecord record);

	Optional<QaRecord> find(String userId, String qaId);

	boolean delete(String userId, String qaId);

}
