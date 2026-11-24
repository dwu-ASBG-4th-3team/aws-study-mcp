package com.asbg.awsstudymcp.qa;

import java.time.Instant;
import java.util.List;

public record QaRecord(
		String userId,
		String qaId,
		String question,
		String answer,
		String domain,
		List<String> tags,
		EmbeddingStatus embeddingStatus,
		Rating latestRating,
		Instant createdAt) {

	public QaRecord withLatestRating(Rating rating) {
		return new QaRecord(userId, qaId, question, answer, domain, tags, embeddingStatus, rating, createdAt);
	}

	public enum EmbeddingStatus {
		PENDING, PROCESSING, COMPLETED, FAILED
	}

	public enum Rating {
		UNDERSTOOD, CONFUSED, WRONG
	}

}
