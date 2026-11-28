package com.asbg.awsstudymcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.enums.Source;
import com.asbg.awsstudymcp.qa.enums.Verification;
import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.Ulid;
import com.asbg.awsstudymcp.idempotency.IdempotencyItem;
import com.asbg.awsstudymcp.message.MessageItem;
import com.asbg.awsstudymcp.qa.QaItem;
import com.asbg.awsstudymcp.session.SessionItem;
import com.asbg.awsstudymcp.support.LocalDynamoDb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

// 실제 설정(DynamoDbConfig)을 그대로 쓰고 클라이언트만 메모리 기반 DynamoDB Local로 바꾼다.
@SpringBootTest(properties = "app.dynamodb.table-name=entity-test")
@ActiveProfiles("local")
class DynamoDbEntityTest {

	private static final Instant CREATED_AT = Instant.parse("2026-11-24T05:55:00Z");

	@TestBean
	DynamoDbClient dynamoDbClient;

	@Autowired
	DynamoDbTable<QaItem> qaTable;

	@Autowired
	DynamoDbTable<IdempotencyItem> idempotencyTable;

	@Autowired
	DynamoDbTable<SessionItem> sessionTable;

	@Autowired
	DynamoDbTable<MessageItem> messageTable;

	static DynamoDbClient dynamoDbClient() {
		return LocalDynamoDb.client();
	}

	@BeforeEach
	void createTableOnce() {
		if (dynamoDbClient.listTables().tableNames().contains(qaTable.tableName())) {
			return;
		}
		LocalDynamoDb.createTable(qaTable);
	}

	@Test
	void savesAndReloadsQa() {
		String userId = Ulid.next();
		QaItem item = QaItem.create(userId, Ulid.next(), Domain.TECH, CREATED_AT);
		item.setSessionId(Ulid.next());
		item.setQuestion("ALB와 NLB 차이?");
		item.setAnswer("L7 vs L4");
		item.setTags(List.of("elb", "alb"));
		item.setPrimaryTag("elb");
		item.setSource(Source.USER);
		item.setVerification(Verification.VERIFIED);
		item.setSourceUrl("https://docs.aws.amazon.com/elasticloadbalancing/");
		item.setReferenceCount(2);
		item.setLatestFeedback(Rating.CONFUSED);

		qaTable.putItem(item);

		QaItem loaded = qaTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded.getVersion()).isEqualTo(1L);
		assertThat(loaded).usingRecursiveComparison().ignoringFields("version").isEqualTo(item);
	}

	@Test
	void storesQaAttributesInSpecFormat() {
		String userId = Ulid.next();
		String qaId = Ulid.next();
		QaItem item = newQa(userId, qaId);

		qaTable.putItem(item);

		Map<String, AttributeValue> raw = rawItem(Keys.userPk(userId), Keys.qaSk(qaId));
		assertThat(raw).containsOnlyKeys("PK", "SK", "qaId", "userId", "question", "answer", "tags", "domain",
				"source", "verification", "referenceCount", "embeddingStatus", "version", "createdAt", "updatedAt",
				"GSI1PK", "GSI1SK");
		assertThat(raw.get("PK").s()).isEqualTo("USER#" + userId);
		assertThat(raw.get("SK").s()).isEqualTo("QA#" + qaId);
		assertThat(raw.get("GSI1PK").s()).isEqualTo("USER#" + userId + "#DOM#SECURITY");
		assertThat(raw.get("GSI1SK").s()).isEqualTo("2026-11-24T05:55:00.000Z");
		assertThat(raw.get("createdAt").s()).isEqualTo("2026-11-24T05:55:00.000Z");
		assertThat(raw.get("tags").l()).isEmpty();
		assertThat(raw.get("referenceCount").n()).isEqualTo("0");
		assertThat(raw.get("embeddingStatus").s()).isEqualTo(EmbeddingStatus.PENDING.name());
	}

	@Test
	void queriesQaByDomainIndexNewestFirst() {
		String userId = Ulid.next();
		QaItem older = newQa(userId, Ulid.next());
		QaItem newer = QaItem.create(userId, Ulid.next(), Domain.SECURITY, CREATED_AT.plusSeconds(1));
		qaTable.putItem(older);
		qaTable.putItem(newer);
		idempotencyTable.putItem(IdempotencyItem.forSingle(userId, "k", older.getQaId(), CREATED_AT));

		var found = qaTable.index(Keys.GSI1)
			.query(r -> r.queryConditional(QueryConditional.keyEqualTo(
					Key.builder().partitionValue(Keys.gsi1Pk(userId, Domain.SECURITY)).build()))
				.scanIndexForward(false))
			.stream()
			.flatMap(page -> page.items().stream())
			.toList();

		assertThat(found).extracting(QaItem::getQaId).containsExactly(newer.getQaId(), older.getQaId());
	}

	@Test
	void rejectsStaleQaVersion() {
		QaItem item = newQa(Ulid.next(), Ulid.next());
		qaTable.putItem(item);

		// item의 version은 아직 null이므로 이미 저장된 항목을 덮어쓸 수 없다.
		assertThatThrownBy(() -> qaTable.putItem(item)).isInstanceOf(ConditionalCheckFailedException.class);
	}

	@Test
	void savesAndReloadsSingleIdempotency() {
		String userId = Ulid.next();
		IdempotencyItem item = IdempotencyItem.forSingle(userId, "request-1", "01QA", CREATED_AT);

		idempotencyTable.putItem(item);

		IdempotencyItem loaded = idempotencyTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded).usingRecursiveComparison().isEqualTo(item);

		Map<String, AttributeValue> raw = rawItem(Keys.userPk(userId), Keys.idempotencySk("request-1"));
		assertThat(raw).containsOnlyKeys("PK", "SK", "userId", "key", "qaId", "expiresAt");
		assertThat(raw.get("SK").s()).isEqualTo("IDEM#request-1");
		assertThat(raw.get("expiresAt").n())
			.isEqualTo(String.valueOf(CREATED_AT.getEpochSecond() + 24 * 60 * 60));
	}

	@Test
	void savesAndReloadsBatchIdempotency() {
		String userId = Ulid.next();
		IdempotencyItem item = IdempotencyItem.forBatch(userId, "request-2", List.of("01QA"), CREATED_AT);

		idempotencyTable.putItem(item);

		IdempotencyItem loaded = idempotencyTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded).usingRecursiveComparison().isEqualTo(item);
		assertThat(rawItem(item.getPk(), item.getSk()))
			.containsOnlyKeys("PK", "SK", "userId", "key", "qaIds", "expiresAt");
	}

	@Test
	void savesAndReloadsNewSessionWithoutLastRecordedMessageId() {
		String userId = Ulid.next();
		String sessionId = Ulid.next();
		SessionItem item = SessionItem.create(userId, sessionId, CREATED_AT);

		sessionTable.putItem(item);

		SessionItem loaded = sessionTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded).usingRecursiveComparison().isEqualTo(item);
		assertThat(loaded.getLastRecordedMessageId()).isNull();

		Map<String, AttributeValue> raw = rawItem(Keys.userPk(userId), Keys.sessionSk(sessionId));
		assertThat(raw).containsOnlyKeys("PK", "SK", "sessionId", "userId", "title", "createdAt", "updatedAt");
		assertThat(raw.get("PK").s()).isEqualTo("USER#" + userId);
		assertThat(raw.get("SK").s()).isEqualTo("SESSION#" + sessionId);
		assertThat(raw.get("title").s()).isEqualTo("새로운 대화");
		assertThat(raw.get("createdAt").s()).isEqualTo("2026-11-24T05:55:00.000Z");
		assertThat(raw.get("updatedAt").s()).isEqualTo("2026-11-24T05:55:00.000Z");
	}

	@Test
	void savesAndReloadsRecordedSession() {
		SessionItem item = SessionItem.create(Ulid.next(), Ulid.next(), CREATED_AT);
		item.setTitle("ALB와 NLB");
		item.setLastRecordedMessageId(Ulid.next());
		item.setUpdatedAt(CREATED_AT.plusSeconds(1));

		sessionTable.putItem(item);

		SessionItem loaded = sessionTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded).usingRecursiveComparison().isEqualTo(item);
		assertThat(rawItem(item.getPk(), item.getSk()).get("lastRecordedMessageId").s())
			.isEqualTo(item.getLastRecordedMessageId());
	}

	@Test
	void savesAndReloadsMessage() {
		String userId = Ulid.next();
		String sessionId = Ulid.next();
		String messageId = Ulid.next();
		MessageItem item = newMessage(userId, sessionId, messageId);
		item.setReferenceQaIds(List.of("01QA-B", "01QA-A"));
		item.setReferenceCount(2);

		messageTable.putItem(item);

		MessageItem loaded = messageTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded).usingRecursiveComparison().isEqualTo(item);

		Map<String, AttributeValue> raw = rawItem(Keys.userPk(userId), Keys.msgSk(sessionId, messageId));
		assertThat(raw).containsOnlyKeys("PK", "SK", "messageId", "userId", "sessionId", "question", "answer",
				"referenceCount", "referenceQaIds", "createdAt");
		assertThat(raw.get("SK").s()).isEqualTo("MSG#" + sessionId + "#" + messageId);
		assertThat(raw.get("referenceCount").n()).isEqualTo("2");
		assertThat(raw.get("referenceQaIds").l()).extracting(AttributeValue::s).containsExactly("01QA-B", "01QA-A");
		assertThat(raw.get("createdAt").s()).isEqualTo("2026-11-24T05:55:00.000Z");
	}

	@Test
	void storesMessageReferenceDefaults() {
		MessageItem item = newMessage(Ulid.next(), Ulid.next(), Ulid.next());

		messageTable.putItem(item);

		MessageItem loaded = messageTable.getItem(key(item.getPk(), item.getSk()));
		assertThat(loaded).usingRecursiveComparison().isEqualTo(item);

		Map<String, AttributeValue> raw = rawItem(item.getPk(), item.getSk());
		assertThat(raw.get("referenceCount").n()).isEqualTo("0");
		assertThat(raw.get("referenceQaIds").hasL()).isTrue();
		assertThat(raw.get("referenceQaIds").l()).isEmpty();
	}

	@Test
	void queriesMessagesOfOneSessionInMessageIdOrder() {
		String userId = Ulid.next();
		String sessionId = Ulid.next();
		String otherSessionId = Ulid.next();
		List<String> ids = List.of(Ulid.next(), Ulid.next(), Ulid.next());
		List<String> otherIds = List.of(Ulid.next(), Ulid.next());

		// 저장 순서가 조회 순서에 영향을 주지 않는지 보기 위해 섞어서 저장한다.
		messageTable.putItem(newMessage(userId, sessionId, ids.get(2)));
		messageTable.putItem(newMessage(userId, otherSessionId, otherIds.get(1)));
		messageTable.putItem(newMessage(userId, sessionId, ids.get(0)));
		messageTable.putItem(newMessage(userId, otherSessionId, otherIds.get(0)));
		messageTable.putItem(newMessage(userId, sessionId, ids.get(1)));
		// 같은 파티션의 다른 Item과 다른 사용자의 같은 sessionId는 조회되지 않아야 한다.
		sessionTable.putItem(SessionItem.create(userId, sessionId, CREATED_AT));
		messageTable.putItem(newMessage(Ulid.next(), sessionId, Ulid.next()));

		assertThat(messageIds(userId, sessionId, true)).containsExactlyElementsOf(ids);
		assertThat(messageIds(userId, sessionId, false)).containsExactly(ids.get(2), ids.get(1), ids.get(0));
		assertThat(messageIds(userId, otherSessionId, true)).containsExactlyElementsOf(otherIds);
	}

	private List<String> messageIds(String userId, String sessionId, boolean ascending) {
		Key prefix = key(Keys.userPk(userId), Keys.msgPrefix(sessionId));
		return messageTable
			.query(r -> r.queryConditional(QueryConditional.sortBeginsWith(prefix))
				.scanIndexForward(ascending)
				.consistentRead(true))
			.items()
			.stream()
			.map(MessageItem::getMessageId)
			.toList();
	}

	private static MessageItem newMessage(String userId, String sessionId, String messageId) {
		MessageItem item = MessageItem.create(userId, sessionId, messageId, CREATED_AT);
		item.setQuestion("q");
		item.setAnswer("a");
		return item;
	}

	private static QaItem newQa(String userId, String qaId) {
		QaItem item = QaItem.create(userId, qaId, Domain.SECURITY, CREATED_AT);
		item.setQuestion("q");
		item.setAnswer("a");
		item.setSource(Source.EXTERNAL_AI);
		item.setVerification(Verification.UNVERIFIED);
		return item;
	}

	private static Key key(String pk, String sk) {
		return Key.builder().partitionValue(pk).sortValue(sk).build();
	}

	private Map<String, AttributeValue> rawItem(String pk, String sk) {
		return dynamoDbClient.getItem(r -> r.tableName(qaTable.tableName())
			.key(Map.of(Keys.PK, AttributeValue.fromS(pk), Keys.SK, AttributeValue.fromS(sk)))
			.consistentRead(true))
			.item();
	}

}
