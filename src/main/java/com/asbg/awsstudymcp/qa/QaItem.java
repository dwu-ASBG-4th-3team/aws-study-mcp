package com.asbg.awsstudymcp.qa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.enums.Source;
import com.asbg.awsstudymcp.qa.enums.Verification;
import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.common.TimestampConverter;

import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class QaItem {

	private String pk;
	private String sk;
	private String qaId;
	private String userId;
	private String sessionId;
	private String question;
	private String answer;
	private List<String> tags = new ArrayList<>();
	private String primaryTag;
	private Domain domain;
	private Source source;
	private Verification verification;
	private String sourceUrl;
	private Integer referenceCount = 0;
	private Rating latestFeedback;
	private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;
	private Long version;
	private Instant createdAt;
	private Instant updatedAt;
	private String gsi1Pk;
	private String gsi1Sk;

	// Enhanced Client가 사용한다. 새 항목은 create()로 만든다.
	public QaItem() {
	}

	// 키와 GSI1 값을 채운다. qaId는 저장 직전에 생성해 넘긴다.
	public static QaItem create(String userId, String qaId, Domain domain, Instant createdAt) {
		QaItem item = new QaItem();
		item.pk = Keys.userPk(userId);
		item.sk = Keys.qaSk(qaId);
		item.userId = userId;
		item.qaId = qaId;
		item.domain = domain;
		item.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
		item.updatedAt = item.createdAt;
		item.gsi1Pk = Keys.gsi1Pk(userId, domain);
		item.gsi1Sk = Keys.gsi1Sk(createdAt);
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

	public String getQaId() {
		return qaId;
	}

	public void setQaId(String qaId) {
		this.qaId = qaId;
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

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public String getPrimaryTag() {
		return primaryTag;
	}

	public void setPrimaryTag(String primaryTag) {
		this.primaryTag = primaryTag;
	}

	public Domain getDomain() {
		return domain;
	}

	public void setDomain(Domain domain) {
		this.domain = domain;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public Verification getVerification() {
		return verification;
	}

	public void setVerification(Verification verification) {
		this.verification = verification;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public Integer getReferenceCount() {
		return referenceCount;
	}

	public void setReferenceCount(Integer referenceCount) {
		this.referenceCount = referenceCount;
	}

	public Rating getLatestFeedback() {
		return latestFeedback;
	}

	public void setLatestFeedback(Rating latestFeedback) {
		this.latestFeedback = latestFeedback;
	}

	public EmbeddingStatus getEmbeddingStatus() {
		return embeddingStatus;
	}

	public void setEmbeddingStatus(EmbeddingStatus embeddingStatus) {
		this.embeddingStatus = embeddingStatus;
	}

	// 낙관적 락: 저장할 때 SDK가 1씩 올리고, 저장된 값과 다르면 쓰기가 실패한다.
	@DynamoDbVersionAttribute
	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
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

	@DynamoDbSecondaryPartitionKey(indexNames = Keys.GSI1)
	@DynamoDbAttribute(Keys.GSI1PK)
	public String getGsi1Pk() {
		return gsi1Pk;
	}

	public void setGsi1Pk(String gsi1Pk) {
		this.gsi1Pk = gsi1Pk;
	}

	@DynamoDbSecondarySortKey(indexNames = Keys.GSI1)
	@DynamoDbAttribute(Keys.GSI1SK)
	public String getGsi1Sk() {
		return gsi1Sk;
	}

	public void setGsi1Sk(String gsi1Sk) {
		this.gsi1Sk = gsi1Sk;
	}

}
