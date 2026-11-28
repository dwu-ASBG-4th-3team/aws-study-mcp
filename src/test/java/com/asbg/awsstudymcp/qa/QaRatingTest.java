package com.asbg.awsstudymcp.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.Ulid;
import com.asbg.awsstudymcp.idempotency.IdempotencyItem;
import com.asbg.awsstudymcp.qa.dto.res.FeedbackRes;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.exception.QaErrorCode;
import com.asbg.awsstudymcp.support.LocalDynamoDb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

class QaRatingTest {

	private static final String TABLE = "qa-rating-test";
	private static final Instant NOW = Instant.parse("2026-11-27T07:12:30.123456Z");

	private static DynamoDbClient dynamoDb;
	private static DynamoDbEnhancedClient enhanced;
	private static DynamoDbTable<QaItem> table;

	@BeforeAll
	static void startDynamoDb() {
		dynamoDb = LocalDynamoDb.client();
		enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
		table = enhanced.table(TABLE, TableSchema.fromBean(QaItem.class));
		LocalDynamoDb.createTable(table);
	}

	@Test
	void ratingAccumulatesHistoryAndIncrementsVersion() {
		String user = Ulid.next();
		String qaId = put(user, Domain.SECURITY);

		service(dynamoDb, NOW).rate(user, qaId, Rating.CONFUSED);
		FeedbackRes response = service(dynamoDb, NOW.plusSeconds(1)).rate(user, qaId, Rating.UNDERSTOOD);

		assertThat(response).isEqualTo(new FeedbackRes(qaId, Rating.UNDERSTOOD, "2026-11-27T07:12:31.123Z"));
		Map<String, AttributeValue> qa = qa(user, qaId);
		assertThat(qa.get("version").n()).isEqualTo("3");
		assertThat(qa.get("latestFeedback").s()).isEqualTo("UNDERSTOOD");
		assertThat(qa.get("updatedAt").s()).isEqualTo("2026-11-27T07:12:31.123Z");

		List<Map<String, AttributeValue>> history = history(user, qaId);
		assertThat(history).extracting(item -> item.get("SK").s())
			.containsExactly("FB#" + qaId + "#2026-11-27T07:12:30.123Z", "FB#" + qaId + "#2026-11-27T07:12:31.123Z");
		assertThat(history.getFirst()).containsOnlyKeys("PK", "SK", "userId", "qaId", "createdAt", "rating", "domain");
		assertThat(history.getFirst().get("rating").s()).isEqualTo("CONFUSED");
		assertThat(history.getFirst().get("domain").s()).isEqualTo("SECURITY");
		assertThat(history.getFirst().get("userId").s()).isEqualTo(user);
		assertThat(history.getFirst().get("createdAt").s()).isEqualTo("2026-11-27T07:12:30.123Z");
	}

	@Test
	void historyWithSameTimestampIsNotOverwritten() {
		String user = Ulid.next();
		String qaId = put(user, Domain.TECH);
		QaService service = service(dynamoDb, NOW);

		service.rate(user, qaId, Rating.CONFUSED);
		FeedbackRes second = service.rate(user, qaId, Rating.WRONG);

		assertThat(second.updatedAt()).isEqualTo("2026-11-27T07:12:30.124Z");
		assertThat(history(user, qaId)).extracting(item -> item.get("rating").s())
			.containsExactly("CONFUSED", "WRONG");
	}

	// 인덱싱 Lambda처럼 version을 올리지 않고 쓴 값과, 매핑에 없는 속성이 남아 있어야 한다.
	@Test
	void ratingKeepsOtherAttributes() {
		String user = Ulid.next();
		String qaId = put(user, Domain.TECH);
		DynamoDbClient client = beforeFirstWrite(() -> update(user, qaId,
				"SET embeddingStatus = :v, embeddingModel = :m",
				Map.of(":v", AttributeValue.fromS("COMPLETED"), ":m", AttributeValue.fromS("model-1"))));
		Map<String, AttributeValue> before = qa(user, qaId);

		service(client, NOW).rate(user, qaId, Rating.CONFUSED);

		Map<String, AttributeValue> after = qa(user, qaId);
		assertThat(after.get("embeddingStatus").s()).isEqualTo("COMPLETED");
		assertThat(after.get("embeddingModel").s()).isEqualTo("model-1");
		assertThat(after).containsAllEntriesOf(Map.of("question", before.get("question"), "answer",
				before.get("answer"), "tags", before.get("tags"), "createdAt", before.get("createdAt"), "GSI1PK",
				before.get("GSI1PK"), "GSI1SK", before.get("GSI1SK")));
	}

	@Test
	void retriesWhenVersionChangedAfterRead() {
		String user = Ulid.next();
		String qaId = put(user, Domain.TECH);
		DynamoDbClient client = beforeFirstWrite(() -> update(user, qaId, "SET version = :v",
				Map.of(":v", AttributeValue.fromN("5"))));

		service(client, NOW).rate(user, qaId, Rating.CONFUSED);

		assertThat(qa(user, qaId).get("version").n()).isEqualTo("6");
		assertThat(history(user, qaId)).hasSize(1);
	}

	@Test
	void reportsConflictWhenVersionKeepsChanging() {
		String user = Ulid.next();
		String qaId = put(user, Domain.TECH);
		DynamoDbClient client = mock(DynamoDbClient.class, delegatesTo(dynamoDb));
		doAnswer(invocation -> {
			update(user, qaId, "SET version = version + :v", Map.of(":v", AttributeValue.fromN("1")));
			return dynamoDb.transactWriteItems(invocation.<Consumer<TransactWriteItemsRequest.Builder>>getArgument(0));
		}).when(client).transactWriteItems(any(Consumer.class));

		assertThatThrownBy(() -> service(client, NOW).rate(user, qaId, Rating.CONFUSED))
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(CommonErrorCode.CONFLICT));
		assertThat(qa(user, qaId)).doesNotContainKey("latestFeedback");
		assertThat(history(user, qaId)).isEmpty();
	}

	@Test
	void deletedDuringRatingIsNotRecreated() {
		String user = Ulid.next();
		String qaId = put(user, Domain.TECH);
		DynamoDbClient client = beforeFirstWrite(
				() -> dynamoDb.deleteItem(r -> r.tableName(TABLE).key(key(user, qaId))));

		assertNotFound(() -> service(client, NOW).rate(user, qaId, Rating.CONFUSED));
		assertThat(qa(user, qaId)).isEmpty();
		assertThat(history(user, qaId)).isEmpty();
	}

	@Test
	void missingOrOtherUsersRecordLeavesNoHistory() {
		String user = Ulid.next();
		String other = Ulid.next();
		String qaId = put(user, Domain.TECH);

		assertNotFound(() -> service(dynamoDb, NOW).rate(other, qaId, Rating.CONFUSED));
		assertNotFound(() -> service(dynamoDb, NOW).rate(user, Ulid.next(), Rating.CONFUSED));
		assertNotFound(() -> service(dynamoDb, NOW).rate(user, "x".repeat(2_000), Rating.CONFUSED));

		assertThat(history(user, qaId)).isEmpty();
		assertThat(history(other, qaId)).isEmpty();
		assertThat(qa(user, qaId).get("version").n()).isEqualTo("1");
	}

	@Test
	void deleteKeepsHistoryAndIdempotency() {
		String user = Ulid.next();
		String qaId = put(user, Domain.TECH);
		var idempotency = enhanced.table(TABLE, TableSchema.fromBean(IdempotencyItem.class));
		idempotency.putItem(IdempotencyItem.forSingle(user, "key", qaId, NOW));
		QaService service = service(dynamoDb, NOW);
		service.rate(user, qaId, Rating.CONFUSED);

		service.delete(user, qaId);

		assertThat(qa(user, qaId)).isEmpty();
		assertThat(history(user, qaId)).hasSize(1);
		assertThat(dynamoDb.getItem(r -> r.tableName(TABLE)
			.key(Map.of(Keys.PK, AttributeValue.fromS(Keys.userPk(user)), Keys.SK,
					AttributeValue.fromS(Keys.idempotencySk("key")))))
			.item()).isNotEmpty();
		// 이력과 멱등성 레코드는 목록에 섞이지 않는다.
		assertThat(service.list(user, null, null, null).items()).isEmpty();
	}

	private static QaService service(DynamoDbClient client, Instant now) {
		return new QaService(new DynamoDbQaRepository(enhanced, client, table),
				Clock.fixed(now, ZoneOffset.UTC));
	}

	// 읽기와 쓰기 사이에 다른 작업이 끼어드는 상황
	@SuppressWarnings("unchecked")
	private static DynamoDbClient beforeFirstWrite(Runnable interference) {
		DynamoDbClient client = mock(DynamoDbClient.class, delegatesTo(dynamoDb));
		boolean[] done = { false };
		doAnswer(invocation -> {
			if (!done[0]) {
				done[0] = true;
				interference.run();
			}
			return dynamoDb.transactWriteItems(invocation.<Consumer<TransactWriteItemsRequest.Builder>>getArgument(0));
		}).when(client).transactWriteItems(any(Consumer.class));
		return client;
	}

	private static String put(String userId, Domain domain) {
		QaItem item = QaItem.create(userId, Ulid.next(), domain, NOW.minusSeconds(60));
		item.setQuestion("q");
		item.setAnswer("a");
		item.setTags(List.of("iam"));
		table.putItem(item);
		return item.getQaId();
	}

	private static Map<String, AttributeValue> key(String userId, String qaId) {
		return Map.of(Keys.PK, AttributeValue.fromS(Keys.userPk(userId)), Keys.SK,
				AttributeValue.fromS(Keys.qaSk(qaId)));
	}

	private static void update(String userId, String qaId, String expression, Map<String, AttributeValue> values) {
		dynamoDb.updateItem(r -> r.tableName(TABLE)
			.key(key(userId, qaId))
			.updateExpression(expression)
			.expressionAttributeValues(values));
	}

	private static Map<String, AttributeValue> qa(String userId, String qaId) {
		return dynamoDb.getItem(r -> r.tableName(TABLE).key(key(userId, qaId)).consistentRead(true)).item();
	}

	private static List<Map<String, AttributeValue>> history(String userId, String qaId) {
		return dynamoDb.query(r -> r.tableName(TABLE)
			.keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
			.expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(Keys.userPk(userId)), ":sk",
					AttributeValue.fromS("FB#" + qaId + "#")))
			.consistentRead(true))
			.items();
	}

	private static void assertNotFound(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo(QaErrorCode.QA_NOT_FOUND));
	}

}
