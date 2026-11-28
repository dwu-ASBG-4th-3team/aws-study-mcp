package com.asbg.awsstudymcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.asbg.awsstudymcp.idempotency.IdempotencyItem;
import com.asbg.awsstudymcp.message.MessageItem;
import com.asbg.awsstudymcp.qa.QaItem;
import com.asbg.awsstudymcp.session.SessionItem;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@EnableConfigurationProperties(DynamoDbProperties.class)
public class DynamoDbConfig {

	// 자격 증명은 SDK 기본 체인(ECS 태스크 역할, 로컬 프로필 등)을 사용한다.
	@Bean
	DynamoDbClient dynamoDbClient(DynamoDbProperties properties) {
		return DynamoDbClient.builder().region(Region.of(properties.region())).build();
	}

	@Bean
	DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
		return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
	}

	// 엔티티마다 스키마만 다르고 실제 테이블은 하나다.
	@Bean
	DynamoDbTable<QaItem> qaTable(DynamoDbEnhancedClient client, DynamoDbProperties properties) {
		return client.table(properties.tableName(), TableSchema.fromBean(QaItem.class));
	}

	@Bean
	DynamoDbTable<SessionItem> sessionTable(DynamoDbEnhancedClient client, DynamoDbProperties properties) {
		return client.table(properties.tableName(), TableSchema.fromBean(SessionItem.class));
	}

	@Bean
	DynamoDbTable<MessageItem> messageTable(DynamoDbEnhancedClient client, DynamoDbProperties properties) {
		return client.table(properties.tableName(), TableSchema.fromBean(MessageItem.class));
	}

	@Bean
	DynamoDbTable<IdempotencyItem> idempotencyTable(DynamoDbEnhancedClient client, DynamoDbProperties properties) {
		return client.table(properties.tableName(), TableSchema.fromBean(IdempotencyItem.class));
	}

}
