package com.asbg.awsstudymcp.qa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.Timestamps;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

@Repository
public class DynamoDbQaRepository implements QaRepository {

	private static final int MAX_ATTEMPTS = 3;
	private static final String CONDITION_FAILED = "ConditionalCheckFailed";
	private static final String TRANSACTION_CONFLICT = "TransactionConflict";
	private static final TableSchema<FeedbackHistoryItem> HISTORY_SCHEMA = TableSchema
		.fromBean(FeedbackHistoryItem.class);

	private final DynamoDbEnhancedClient enhancedClient;
	private final DynamoDbClient client;
	private final DynamoDbTable<QaItem> table;

	public DynamoDbQaRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient client,
			DynamoDbTable<QaItem> table) {
		this.enhancedClient = enhancedClient;
		this.client = client;
		this.table = table;
	}

	@Override
	public void save(QaItem record) {
		table.putItem(record);
	}

	@Override
	public Optional<QaItem> find(String userId, String qaId) {
		Key key = key(userId, qaId);
		return Optional.ofNullable(table.getItem(r -> r.key(key).consistentRead(true)));
	}

	@Override
	public QaRepository.Page findPage(String userId, Domain domain, Position after, int limit) {
		// 다음 항목이 있는지 알 수 있도록 하나 더 읽는다.
		int wanted = limit + 1;
		List<QaItem> items = domain == null ? queryAll(userId, after, wanted)
				: queryByDomain(userId, domain, after, wanted);
		if (items.size() <= limit) {
			return new QaRepository.Page(items, null);
		}
		List<QaItem> page = List.copyOf(items.subList(0, limit));
		QaItem last = page.getLast();
		return new QaRepository.Page(page, new Position(last.getQaId(), Timestamps.format(last.getCreatedAt())));
	}

	// SK가 QA#{ULID}라서 내림차순이 최신순이다.
	private List<QaItem> queryAll(String userId, Position after, int wanted) {
		QueryEnhancedRequest.Builder request = QueryEnhancedRequest.builder()
			.queryConditional(QueryConditional.sortBeginsWith(
					Key.builder().partitionValue(Keys.userPk(userId)).sortValue(Keys.QA_PREFIX).build()))
			.scanIndexForward(false)
			.consistentRead(true)
			.limit(wanted);
		if (after != null) {
			request.exclusiveStartKey(primaryKey(userId, after.qaId()));
		}
		// 한 번의 Query는 1MB까지만 읽으므로 모자라면 SDK가 이어서 읽는다.
		return table.query(request.build()).items().stream().limit(wanted).toList();
	}

	// GSI1은 최종적 일관성이라 원본 테이블에서 다시 읽는다. 삭제된 항목은 빠지고 최신 값이 반영된다.
	// 방금 저장한 항목은 인덱스에 반영되기 전까지 보이지 않을 수 있다.
	private List<QaItem> queryByDomain(String userId, Domain domain, Position after, int wanted) {
		String partition = Keys.gsi1Pk(userId, domain);
		QueryEnhancedRequest.Builder request = QueryEnhancedRequest.builder()
			.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(partition).build()))
			.scanIndexForward(false)
			.limit(wanted);
		if (after != null) {
			Map<String, AttributeValue> startKey = new HashMap<>(primaryKey(userId, after.qaId()));
			startKey.put(Keys.GSI1PK, AttributeValue.fromS(partition));
			startKey.put(Keys.GSI1SK, AttributeValue.fromS(after.createdAt()));
			request.exclusiveStartKey(startKey);
		}

		List<QaItem> result = new ArrayList<>();
		for (var page : table.index(Keys.GSI1).query(request.build())) {
			result.addAll(reload(page.items()));
			if (result.size() >= wanted) {
				return List.copyOf(result.subList(0, wanted));
			}
		}
		return result;
	}

	// 인덱스 순서를 유지한 채 원본 항목으로 바꾼다. 한 번에 최대 51개라 BatchGetItem 한도(100)를 넘지 않는다.
	private List<QaItem> reload(List<QaItem> indexed) {
		if (indexed.isEmpty()) {
			return List.of();
		}
		ReadBatch.Builder<QaItem> batch = ReadBatch.builder(QaItem.class).mappedTableResource(table);
		for (QaItem item : indexed) {
			Key key = Key.builder().partitionValue(item.getPk()).sortValue(item.getSk()).build();
			batch.addGetItem(r -> r.key(key).consistentRead(true));
		}
		Map<String, QaItem> found = enhancedClient.batchGetItem(r -> r.addReadBatch(batch.build()))
			.resultsForTable(table)
			.stream()
			.collect(Collectors.toMap(QaItem::getSk, Function.identity()));
		return indexed.stream().map(item -> found.get(item.getSk())).filter(Objects::nonNull).toList();
	}

	// 항목 전체를 다시 쓰지 않고 평가 관련 속성만 바꾼다. 인덱싱이 version을 올리지 않고 쓰는 값을 덮어쓰지 않기 위해서다.
	// 읽은 뒤 version이 그대로일 때만 쓴다. 그사이 수정됐으면 다시 시도하고, 삭제됐으면 빈 값을 돌려준다.
	@Override
	public Optional<QaItem> updateRating(String userId, String qaId, Rating rating, Instant now) {
		Instant ratedAt = now.truncatedTo(ChronoUnit.MILLIS);
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			Optional<QaItem> found = find(userId, qaId);
			if (found.isEmpty()) {
				return found;
			}
			QaItem record = found.get();
			long nextVersion = record.getVersion() + 1;
			FeedbackHistoryItem history = FeedbackHistoryItem.create(userId, qaId, rating, record.getDomain(),
					ratedAt);
			try {
				writeRating(record, rating, ratedAt, nextVersion, history);
				record.setLatestFeedback(rating);
				record.setUpdatedAt(ratedAt);
				record.setVersion(nextVersion);
				return Optional.of(record);
			}
			catch (TransactionCanceledException e) {
				// 사유는 요청한 순서(QA 갱신, 이력 저장)대로 온다.
				List<String> reasons = e.cancellationReasons().stream().map(CancellationReason::code).toList();
				if (!reasons.contains(CONDITION_FAILED) && !reasons.contains(TRANSACTION_CONFLICT)) {
					throw e;
				}
				// 같은 시각의 이력이 이미 있으면 덮어쓰지 않고 다음 밀리초를 쓴다.
				if (CONDITION_FAILED.equals(reasons.get(1))) {
					ratedAt = ratedAt.plusMillis(1);
				}
			}
		}
		throw new ApiException(CommonErrorCode.CONFLICT, "다른 요청이 같은 기록을 수정하고 있습니다. 다시 시도해 주세요.");
	}

	private void writeRating(QaItem record, Rating rating, Instant ratedAt, long nextVersion,
			FeedbackHistoryItem history) {
		Update update = Update.builder()
			.tableName(table.tableName())
			.key(primaryKey(record.getUserId(), record.getQaId()))
			.updateExpression("SET #feedback = :feedback, #updatedAt = :updatedAt, #version = :next")
			// 삭제된 항목에는 version이 없어 조건이 실패하므로 새로 만들어지지 않는다.
			.conditionExpression("#version = :expected")
			.expressionAttributeNames(
					Map.of("#feedback", "latestFeedback", "#updatedAt", "updatedAt", "#version", "version"))
			.expressionAttributeValues(Map.of(
					":feedback", AttributeValue.fromS(rating.name()),
					":updatedAt", AttributeValue.fromS(Timestamps.format(ratedAt)),
					":next", AttributeValue.fromN(String.valueOf(nextVersion)),
					":expected", AttributeValue.fromN(String.valueOf(record.getVersion()))))
			.build();
		Put put = Put.builder()
			.tableName(table.tableName())
			.item(HISTORY_SCHEMA.itemToMap(history, true))
			.conditionExpression("attribute_not_exists(#pk)")
			.expressionAttributeNames(Map.of("#pk", Keys.PK))
			.build();
		client.transactWriteItems(r -> r.transactItems(
				TransactWriteItem.builder().update(update).build(),
				TransactWriteItem.builder().put(put).build()));
	}

	// 삭제된 항목이 있으면 그 값을 돌려받는다.
	@Override
	public boolean delete(String userId, String qaId) {
		return table.deleteItem(key(userId, qaId)) != null;
	}

	private static Key key(String userId, String qaId) {
		return Key.builder().partitionValue(Keys.userPk(userId)).sortValue(Keys.qaSk(qaId)).build();
	}

	private static Map<String, AttributeValue> primaryKey(String userId, String qaId) {
		return Map.of(Keys.PK, AttributeValue.fromS(Keys.userPk(userId)), Keys.SK,
				AttributeValue.fromS(Keys.qaSk(qaId)));
	}

}
