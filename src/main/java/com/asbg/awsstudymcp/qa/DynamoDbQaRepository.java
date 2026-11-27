package com.asbg.awsstudymcp.qa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.common.Keys;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class DynamoDbQaRepository implements QaRepository {

	private static final int MAX_ATTEMPTS = 3;

	private final DynamoDbTable<QaItem> table;

	public DynamoDbQaRepository(DynamoDbTable<QaItem> table) {
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

	// 읽은 뒤 version이 그대로일 때만 쓴다. 그사이 수정됐으면 다시 시도하고, 삭제됐으면 빈 값을 돌려준다.
	@Override
	public Optional<QaItem> updateRating(String userId, String qaId, Rating rating, Instant now) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			Optional<QaItem> found = find(userId, qaId);
			if (found.isEmpty()) {
				return found;
			}
			QaItem record = found.get();
			record.setLatestFeedback(rating);
			record.setUpdatedAt(now);
			try {
				// 반환값에는 올라간 version이 반영되어 있다.
				return Optional.of(table.updateItem(record));
			}
			catch (ConditionalCheckFailedException e) {
				// 다음 시도에서 최신 상태를 읽는다.
			}
		}
		throw new ApiException(CommonErrorCode.CONFLICT, "다른 요청이 같은 기록을 수정하고 있습니다. 다시 시도해 주세요.");
	}

	// 삭제된 항목이 있으면 그 값을 돌려받는다.
	@Override
	public boolean delete(String userId, String qaId) {
		return table.deleteItem(key(userId, qaId)) != null;
	}

	private static Key key(String userId, String qaId) {
		return Key.builder().partitionValue(Keys.userPk(userId)).sortValue(Keys.qaSk(qaId)).build();
	}

}
