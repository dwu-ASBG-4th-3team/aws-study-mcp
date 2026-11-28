package com.asbg.awsstudymcp.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.enums.Source;
import com.asbg.awsstudymcp.qa.enums.Verification;
import com.asbg.awsstudymcp.qa.exception.QaErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.support.LocalDynamoDb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class QaServiceTest {

	private static DynamoDbTable<QaItem> table;
	private static DynamoDbQaRepository repository;

	private final QaService service = new QaService(repository);

	@BeforeAll
	static void startDynamoDb() {
		var dynamoDb = LocalDynamoDb.client();
		var client = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
		table = client.table("qa-service-test", TableSchema.fromBean(QaItem.class));
		LocalDynamoDb.createTable(table);
		repository = new DynamoDbQaRepository(client, dynamoDb, table);
	}

	@Test
	void saveStoresRecordAsPending() {
		var result = service.save("user-a", "ALB와 NLB 차이?", "L7 vs L4", Domain.TECH, List.of("elb"));

		assertThat(result.qaId()).hasSize(26);
		assertThat(result.embeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
		assertThat(repository.find("user-a", result.qaId())).isPresent();
	}

	@Test
	void saveWithoutDomainIsUnclassified() {
		var saved = service.save("user-a", "q", "a", null, null);

		assertThat(repository.find("user-a", saved.qaId())).get()
			.extracting(QaItem::getDomain)
			.isEqualTo(Domain.UNCLASSIFIED);
	}

	@Test
	void saveStripsTags() {
		var saved = service.save("user-a", "q", "a", Domain.SECURITY, List.of("  iam role ", "sts"));

		assertThat(repository.find("user-a", saved.qaId())).get()
			.extracting(QaItem::getTags)
			.isEqualTo(List.of("iam role", "sts"));
	}

	@Test
	void saveRejectsNullOrBlankTag() {
		var withNull = new ArrayList<String>();
		withNull.add("iam");
		withNull.add(null);

		assertValidationFailed(() -> service.save("user-a", "q", "a", Domain.SECURITY, withNull));
		assertValidationFailed(() -> service.save("user-a", "q", "a", Domain.SECURITY, List.of("iam", "  ")));
	}

	@Test
	void saveRejectsTooManyOrTooLongTags() {
		var tooMany = Collections.nCopies(QaService.MAX_TAGS + 1, "tag");
		var tooLong = "a".repeat(QaService.MAX_TAG_LENGTH + 1);

		assertValidationFailed(() -> service.save("user-a", "q", "a", Domain.SECURITY, tooMany));
		assertValidationFailed(() -> service.save("user-a", "q", "a", Domain.SECURITY, List.of(tooLong)));
	}

	private static void assertValidationFailed(ThrowingCallable call) {
		assertThatThrownBy(call)
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
	}

	@Test
	void saveAcceptsMaxLengthAndRejectsLonger() {
		var maxQuestion = "q".repeat(QaService.MAX_QUESTION_LENGTH);
		var maxAnswer = "a".repeat(QaService.MAX_ANSWER_LENGTH);

		assertThat(service.save("user-a", maxQuestion, maxAnswer, Domain.TECH, null).qaId()).isNotNull();
		assertValidationFailed(() -> service.save("user-a", maxQuestion + "q", "a", Domain.TECH, null));
		assertValidationFailed(() -> service.save("user-a", "q", maxAnswer + "a", Domain.TECH, null));
	}

	@Test
	void saveRejectsBlankQuestion() {
		assertThatThrownBy(() -> service.save("user-a", " ", "a", Domain.CONCEPTS, null))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
	}

	@Test
	void rateUpdatesLatestRating() {
		var saved = service.save("user-a", "q", "a", Domain.CONCEPTS, null);

		service.rate("user-a", saved.qaId(), Rating.CONFUSED);

		assertThat(repository.find("user-a", saved.qaId())).get()
			.extracting(QaItem::getLatestFeedback)
			.isEqualTo(Rating.CONFUSED);
	}

	@Test
	void otherUsersRecordIsNotFound() {
		var saved = service.save("user-a", "q", "a", Domain.CONCEPTS, null);

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
		var saved = service.save("user-a", "q", "a", Domain.CONCEPTS, null);
		service.delete("user-a", saved.qaId());

		assertThatThrownBy(() -> service.rate("user-a", saved.qaId(), Rating.UNDERSTOOD))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
		assertThat(repository.find("user-a", saved.qaId())).isEmpty();
	}

	@Test
	void deleteTwiceReturnsNotFound() {
		var saved = service.save("user-a", "q", "a", Domain.CONCEPTS, null);

		assertThat(service.delete("user-a", saved.qaId()).deleted()).isTrue();
		assertThatThrownBy(() -> service.delete("user-a", saved.qaId()))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
	}

	@Test
	void saveStoresMcpDefaults() {
		var saved = service.save("user-a", "q", "a", Domain.TECH, null);

		var record = repository.find("user-a", saved.qaId()).orElseThrow();
		assertThat(record.getSource()).isEqualTo(Source.EXTERNAL_AI);
		assertThat(record.getVerification()).isEqualTo(Verification.UNVERIFIED);
		assertThat(record.getVersion()).isEqualTo(1L);
		assertThat(record.getCreatedAt()).isEqualTo(saved.createdAt());
	}

	@Test
	void deleteRemovesItem() {
		var saved = service.save("user-a", "q", "a", Domain.TECH, List.of("elb"));

		service.delete("user-a", saved.qaId());

		assertThat(table.getItem(key("user-a", saved.qaId()))).isNull();
	}

	// 평가가 읽은 직후에 삭제가 끼어든 상황
	@Test
	void staleUpdateAfterDeleteDoesNotRecreateItem() {
		var saved = service.save("user-a", "q", "a", Domain.TECH, null);
		var stale = repository.find("user-a", saved.qaId()).orElseThrow();
		service.delete("user-a", saved.qaId());

		assertThatThrownBy(() -> table.updateItem(stale)).isInstanceOf(ConditionalCheckFailedException.class);
		assertThat(table.getItem(key("user-a", saved.qaId()))).isNull();
	}

	private static Key key(String userId, String qaId) {
		return Key.builder().partitionValue(Keys.userPk(userId)).sortValue(Keys.qaSk(qaId)).build();
	}

}
