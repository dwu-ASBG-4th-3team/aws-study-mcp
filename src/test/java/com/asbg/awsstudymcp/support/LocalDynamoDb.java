package com.asbg.awsstudymcp.support;

import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.asbg.awsstudymcp.common.Keys;
import com.asbg.awsstudymcp.qa.QaItem;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;

// 테스트용 메모리 기반 DynamoDB Local
public final class LocalDynamoDb {

	private LocalDynamoDb() {
	}

	public static DynamoDbClient client() {
		return DynamoDBEmbedded.create().dynamoDbClient();
	}

	public static void createTable(DynamoDbTable<QaItem> table) {
		table.createTable(CreateTableEnhancedRequest.builder()
			.globalSecondaryIndices(EnhancedGlobalSecondaryIndex.builder()
				.indexName(Keys.GSI1)
				.projection(p -> p.projectionType(ProjectionType.ALL))
				.build())
			.build());
	}

}
