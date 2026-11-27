package com.asbg.awsstudymcp.session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.TimestampConverter;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class SessionItem {

	public static final String DEFAULT_TITLE = "새로운 대화";

	private String pk;
	private String sk;
	private String sessionId;
	private String userId;
	private String title = DEFAULT_TITLE;
	// null이면 속성을 저장하지 않는다. 아직 기록한 적 없음을 뜻한다.
	private String lastRecordedMessageId;
	private Instant createdAt;
	private Instant updatedAt;

	// Enhanced Client가 사용한다. 새 항목은 create()로 만든다.
	public SessionItem() {
	}

	// 키를 채운다. sessionId는 저장 직전에 생성해 넘긴다.
	public static SessionItem create(String userId, String sessionId, Instant createdAt) {
		SessionItem item = new SessionItem();
		item.pk = Keys.userPk(userId);
		item.sk = Keys.sessionSk(sessionId);
		item.userId = userId;
		item.sessionId = sessionId;
		item.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
		item.updatedAt = item.createdAt;
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

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getLastRecordedMessageId() {
		return lastRecordedMessageId;
	}

	public void setLastRecordedMessageId(String lastRecordedMessageId) {
		this.lastRecordedMessageId = lastRecordedMessageId;
	}

	@DynamoDbConvertedBy(TimestampConverter.class)
	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@DynamoDbConvertedBy(TimestampConverter.class)
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
