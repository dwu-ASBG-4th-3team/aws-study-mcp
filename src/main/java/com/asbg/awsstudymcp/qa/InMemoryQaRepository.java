package com.asbg.awsstudymcp.qa;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryQaRepository implements QaRepository {

	private final Map<String, QaRecord> store = new ConcurrentHashMap<>();

	@Override
	public QaRecord save(QaRecord record) {
		store.put(key(record.userId(), record.qaId()), record);
		return record;
	}

	@Override
	public Optional<QaRecord> find(String userId, String qaId) {
		return Optional.ofNullable(store.get(key(userId, qaId)));
	}

	@Override
	public boolean delete(String userId, String qaId) {
		return store.remove(key(userId, qaId)) != null;
	}

	private static String key(String userId, String qaId) {
		return "USER#" + userId + "|QA#" + qaId;
	}

}
