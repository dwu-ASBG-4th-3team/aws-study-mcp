package com.asbg.awsstudymcp.qa;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.asbg.awsstudymcp.qa.dto.res.FeedbackRes;
import com.asbg.awsstudymcp.qa.dto.res.QaDetailRes;
import com.asbg.awsstudymcp.qa.dto.res.QaSummary;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.enums.Source;
import com.asbg.awsstudymcp.qa.enums.Verification;
import com.asbg.awsstudymcp.qa.exception.QaErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.common.Timestamps;
import com.asbg.awsstudymcp.common.Ulid;
import com.asbg.awsstudymcp.common.pagination.CursorCodec;
import com.asbg.awsstudymcp.common.pagination.CursorPageRes;
import com.asbg.awsstudymcp.common.pagination.PageLimit;

@Service
public class QaService {

	static final int MAX_QUESTION_LENGTH = 2_000;
	static final int MAX_ANSWER_LENGTH = 20_000;
	static final int MAX_TAGS = 10;
	static final int MAX_TAG_LENGTH = 50;
	// 서버가 만드는 qaId는 26자다. 너무 긴 값은 DynamoDB 키 한도를 넘기 전에 걸러낸다.
	private static final int MAX_QA_ID_LENGTH = 100;

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

	public SaveResult save(String userId, String question, String answer, Domain domain, List<String> tags) {
		String q = requireText(question, "question", MAX_QUESTION_LENGTH);
		String a = requireText(answer, "answer", MAX_ANSWER_LENGTH);

		List<String> validTags = validateTags(tags);

		QaItem record = QaItem.create(userId, Ulid.next(),
				domain == null ? Domain.UNCLASSIFIED : domain, Instant.now(clock));
		record.setQuestion(q);
		record.setAnswer(a);
		record.setTags(validTags);
		// 이 저장 경로는 MCP save_qa뿐이고 출처 URL을 받지 않는다.
		record.setSource(Source.EXTERNAL_AI);
		record.setVerification(Verification.UNVERIFIED);
		repository.save(record);
		return new SaveResult(record.getQaId(), record.getEmbeddingStatus(), record.getCreatedAt());
	}

	public CursorPageRes<QaSummary> list(String userId, Domain domain, Integer limit, String cursor) {
		int size = PageLimit.resolve(limit);
		// domain이 다른 목록의 커서는 받지 않는다.
		String scope = "qa:" + (domain == null ? "ALL" : domain.name());
		QaRepository.Position after = cursor == null ? null : decodePosition(userId, scope, cursor);
		QaRepository.Page page = repository.findPage(userId, domain, after, size);
		String nextCursor = page.next() == null ? null
				: CursorCodec.encode(userId, scope, List.of(page.next().qaId(), page.next().createdAt()));
		return new CursorPageRes<>(page.items().stream().map(QaSummary::from).toList(), nextCursor);
	}

	public QaDetailRes get(String userId, String qaId) {
		requireQaId(qaId);
		QaItem record = repository.find(userId, qaId).orElseThrow(QaService::notFound);
		return QaDetailRes.from(record);
	}

	// REST와 MCP rate_answer가 함께 쓴다.
	public FeedbackRes rate(String userId, String qaId, Rating rating) {
		requireQaId(qaId);
		if (rating == null) {
			throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "rating은 UNDERSTOOD, CONFUSED, WRONG 중 하나여야 합니다.");
		}
		QaItem record = repository.updateRating(userId, qaId, rating, Instant.now(clock))
			.orElseThrow(QaService::notFound);
		return new FeedbackRes(qaId, record.getLatestFeedback(), Timestamps.format(record.getUpdatedAt()));
	}

	public DeleteResult delete(String userId, String qaId) {
		requireQaId(qaId);
		if (!repository.delete(userId, qaId)) {
			throw notFound();
		}
		return new DeleteResult(qaId, true);
	}

	private static QaRepository.Position decodePosition(String userId, String scope, String cursor) {
		List<String> values = CursorCodec.decode(userId, scope, cursor, 2);
		return new QaRepository.Position(values.get(0), values.get(1));
	}

	private static void requireQaId(String qaId) {
		requireText(qaId, "qaId");
		if (qaId.length() > MAX_QA_ID_LENGTH) {
			throw notFound();
		}
	}

	private static List<String> validateTags(List<String> tags) {
		if (tags == null) {
			return List.of();
		}
		if (tags.size() > MAX_TAGS) {
			throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "tags는 최대 " + MAX_TAGS + "개까지 입력할 수 있습니다.");
		}
		List<String> result = new ArrayList<>(tags.size());
		for (String tag : tags) {
			if (tag == null || tag.isBlank()) {
				throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "tags에 빈 값이 있습니다.");
			}
			String stripped = tag.strip();
			if (stripped.length() > MAX_TAG_LENGTH) {
				throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "태그는 " + MAX_TAG_LENGTH + "자 이하여야 합니다.");
			}
			result.add(stripped);
		}
		return List.copyOf(result);
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ApiException(CommonErrorCode.VALIDATION_FAILED, field + "은(는) 비어 있을 수 없습니다.");
		}
	}

	private static String requireText(String value, String field, int maxLength) {
		requireText(value, field);
		String stripped = value.strip();
		if (stripped.length() > maxLength) {
			throw new ApiException(CommonErrorCode.VALIDATION_FAILED, field + "은(는) " + maxLength + "자 이하여야 합니다.");
		}
		return stripped;
	}

	// 다른 사용자의 qaId인지 드러나지 않도록 404로 통일
	private static ApiException notFound() {
		return new ApiException(QaErrorCode.QA_NOT_FOUND);
	}

	public record SaveResult(String qaId, EmbeddingStatus embeddingStatus, Instant createdAt) {
	}

	public record DeleteResult(String qaId, boolean deleted) {
	}

}
