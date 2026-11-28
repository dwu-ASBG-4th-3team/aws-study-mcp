package com.asbg.awsstudymcp.qa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.TimestampConverter;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// ERD 상세 명세서 3.5 FEEDBACK_HISTORY
@DynamoDbBean
public class FeedbackHistoryItem {

	private String pk;
	private String sk;
	private String userId;
	private String qaId;
	private Instant createdAt;
	private Rating rating;
	private Domain domain;

	// Enhanced Client가 사용한다. 새 항목은 create()로 만든다.
	public FeedbackHistoryItem() {
	}

	// domain은 평가 당시 QA의 값이다.
	public static FeedbackHistoryItem create(String userId, String qaId, Rating rating, Domain domain,
			Instant createdAt) {
		FeedbackHistoryItem item = new FeedbackHistoryItem();
		item.pk = Keys.userPk(userId);
		item.sk = Keys.feedbackSk(qaId, createdAt);
		item.userId = userId;
		item.qaId = qaId;
		item.rating = rating;
		item.domain = domain;
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

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getQaId() {
		return qaId;
	}

	public void setQaId(String qaId) {
		this.qaId = qaId;
	}

	@DynamoDbConvertedBy(TimestampConverter.class)
	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Rating getRating() {
		return rating;
	}

	public void setRating(Rating rating) {
		this.rating = rating;
	}

	public Domain getDomain() {
		return domain;
	}

	public void setDomain(Domain domain) {
		this.domain = domain;
	}

}
