package com.asbg.awsstudymcp.qa;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.ErrorCode;
import com.asbg.awsstudymcp.common.Ulid;
import com.asbg.awsstudymcp.qa.QaRecord.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.QaRecord.Rating;

@Service
public class QaService {

	private final QaRepository repository;
	private final Clock clock;

	@Autowired
	public QaService(QaRepository repository) {
		this(repository, Clock.systemUTC());
	}

	QaService(QaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public SaveResult save(String userId, String question, String answer, String domain, List<String> tags) {
		requireText(question, "question");
		requireText(answer, "answer");
		requireText(domain, "domain");

		QaRecord record = new QaRecord(userId, Ulid.next(), question.strip(), answer.strip(), domain.strip(),
				tags == null ? List.of() : List.copyOf(tags), EmbeddingStatus.PENDING, null, Instant.now(clock));
		repository.save(record);
		return new SaveResult(record.qaId(), record.embeddingStatus(), record.createdAt());
	}

	public RateResult rate(String userId, String qaId, Rating rating) {
		requireText(qaId, "qaId");
		if (rating == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "rating은 UNDERSTOOD, CONFUSED, WRONG 중 하나여야 합니다.");
		}
		repository.updateRating(userId, qaId, rating).orElseThrow(QaService::notFound);
		return new RateResult(qaId, rating);
	}

	public DeleteResult delete(String userId, String qaId) {
		requireText(qaId, "qaId");
		if (!repository.delete(userId, qaId)) {
			throw notFound();
		}
		return new DeleteResult(qaId, true);
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, field + "은(는) 비어 있을 수 없습니다.");
		}
	}

	// 다른 사용자의 qaId인지 드러나지 않도록 404로 통일
	private static ApiException notFound() {
		return new ApiException(ErrorCode.QA_NOT_FOUND, "해당 Q&A가 없습니다.");
	}

	public record SaveResult(String qaId, EmbeddingStatus embeddingStatus, Instant createdAt) {
	}

	public record RateResult(String qaId, Rating rating) {
	}

	public record DeleteResult(String qaId, boolean deleted) {
	}

}
