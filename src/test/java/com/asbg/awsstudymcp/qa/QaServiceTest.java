package com.asbg.awsstudymcp.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.qa.exception.QaErrorCode;
import com.asbg.awsstudymcp.qa.QaRecord.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.QaRecord.Rating;

class QaServiceTest {

	private final InMemoryQaRepository repository = new InMemoryQaRepository();
	private final QaService service = new QaService(repository);

	@Test
	void saveStoresRecordAsPending() {
		var result = service.save("user-a", "ALB와 NLB 차이?", "L7 vs L4", "CLOUD_TECHNOLOGY_SERVICES", List.of("elb"));

		assertThat(result.qaId()).hasSize(26);
		assertThat(result.embeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
		assertThat(repository.find("user-a", result.qaId())).isPresent();
	}

	@Test
	void saveRejectsBlankQuestion() {
		assertThatThrownBy(() -> service.save("user-a", " ", "a", "CLOUD_CONCEPTS", null))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
	}

	@Test
	void rateUpdatesLatestRating() {
		var saved = service.save("user-a", "q", "a", "CLOUD_CONCEPTS", null);

		service.rate("user-a", saved.qaId(), Rating.CONFUSED);

		assertThat(repository.find("user-a", saved.qaId())).get()
			.extracting(QaRecord::latestRating)
			.isEqualTo(Rating.CONFUSED);
	}

	@Test
	void otherUsersRecordIsNotFound() {
		var saved = service.save("user-a", "q", "a", "CLOUD_CONCEPTS", null);

		assertThatThrownBy(() -> service.rate("user-b", saved.qaId(), Rating.UNDERSTOOD))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
		assertThatThrownBy(() -> service.delete("user-b", saved.qaId()))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
		assertThat(repository.find("user-a", saved.qaId())).isPresent();
	}

	@Test
	void rateAfterDeleteDoesNotRecreateRecord() {
		var saved = service.save("user-a", "q", "a", "CLOUD_CONCEPTS", null);
		service.delete("user-a", saved.qaId());

		assertThatThrownBy(() -> service.rate("user-a", saved.qaId(), Rating.UNDERSTOOD))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
		assertThat(repository.find("user-a", saved.qaId())).isEmpty();
	}

	@Test
	void deleteTwiceReturnsNotFound() {
		var saved = service.save("user-a", "q", "a", "CLOUD_CONCEPTS", null);

		assertThat(service.delete("user-a", saved.qaId()).deleted()).isTrue();
		assertThatThrownBy(() -> service.delete("user-a", saved.qaId()))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
	}

}
