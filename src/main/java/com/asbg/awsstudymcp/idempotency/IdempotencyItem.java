package com.asbg.awsstudymcp.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.asbg.awsstudymcp.common.Keys;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// ERD 상세 명세서 3.6 IDEMPOTENCY. qaId와 qaIds 중 하나만 저장한다.
@DynamoDbBean
public class IdempotencyItem {

	public static final Duration TTL = Duration.ofHours(24);

	private String pk;
	private String sk;
	private String userId;
	private String key;
	private String qaId;
	private List<String> qaIds;
	// TTL 속성. Number(epoch seconds)
	private Long expiresAt;

	// Enhanced Client가 사용한다. 새 항목은 forSingle(), forBatch()로 만든다.
	public IdempotencyItem() {
	}

	// MCP 단건 저장
	public static IdempotencyItem forSingle(String userId, String key, String qaId, Instant createdAt) {
		if (qaId == null || qaId.isBlank()) {
			throw new IllegalArgumentException("qaId is required");
		}
		IdempotencyItem item = base(userId, key, createdAt);
		item.qaId = qaId;
		return item;
	}

	// 웹 "기록으로 저장". 생성된 QA가 한 건이어도 qaIds를 쓴다.
	public static IdempotencyItem forBatch(String userId, String key, List<String> qaIds, Instant createdAt) {
		if (qaIds == null || qaIds.isEmpty()) {
			throw new IllegalArgumentException("qaIds is required");
		}
		IdempotencyItem item = base(userId, key, createdAt);
		item.qaIds = List.copyOf(qaIds);
		return item;
	}

	private static IdempotencyItem base(String userId, String key, Instant createdAt) {
		IdempotencyItem item = new IdempotencyItem();
		item.pk = Keys.userPk(userId);
		item.sk = Keys.idempotencySk(key);
		item.userId = userId;
		item.key = key;
		item.expiresAt = createdAt.plus(TTL).getEpochSecond();
		return item;
	}

	@DynamoDbPartitionKey
	@DynamoDbAttribute(Keys.PK)
	public String getPk() {
		return pk;
	}

	public void setPk(String pk) {
		this.pk = pk;
	}

	@DynamoDbSortKey
	@DynamoDbAttribute(Keys.SK)
	public String getSk() {
		return sk;
	}

	public void setSk(String sk) {
		this.sk = sk;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getQaId() {
		return qaId;
	}

	public void setQaId(String qaId) {
		this.qaId = qaId;
	}

	public List<String> getQaIds() {
		return qaIds;
	}

	public void setQaIds(List<String> qaIds) {
		this.qaIds = qaIds;
	}

	public Long getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Long expiresAt) {
		this.expiresAt = expiresAt;
	}

}
