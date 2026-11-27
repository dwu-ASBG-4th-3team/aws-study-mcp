package com.asbg.awsstudymcp.message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.TimestampConverter;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class MessageItem {

	private String pk;
	private String sk;
	private String messageId;
	private String userId;
	private String sessionId;
	private String question;
	private String answer;
	private Integer referenceCount = 0;
	private List<String> referenceQaIds = new ArrayList<>();
	private Instant createdAt;

	// Enhanced Client가 사용한다. 새 항목은 create()로 만든다.
	public MessageItem() {
	}

	// 키를 채운다. messageId는 저장 직전에 생성해 넘긴다.
	public static MessageItem create(String userId, String sessionId, String messageId, Instant createdAt) {
		MessageItem item = new MessageItem();
		item.pk = Keys.userPk(userId);
		item.sk = Keys.msgSk(sessionId, messageId);
		item.userId = userId;
		item.sessionId = sessionId;
		item.messageId = messageId;
		item.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
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

	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public Integer getReferenceCount() {
		return referenceCount;
	}

	public void setReferenceCount(Integer referenceCount) {
		this.referenceCount = referenceCount;
	}

	public List<String> getReferenceQaIds() {
		return referenceQaIds;
	}

	public void setReferenceQaIds(List<String> referenceQaIds) {
		this.referenceQaIds = referenceQaIds;
	}

	@DynamoDbConvertedBy(TimestampConverter.class)
	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
