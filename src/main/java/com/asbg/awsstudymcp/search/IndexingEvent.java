package com.asbg.awsstudymcp.search;

import java.time.Instant;

import com.asbg.awsstudymcp.common.Ulid;

/**
 * 인덱싱 큐 메시지 (INT-001~003 공통). 질문·답변 원문은 싣지 않는다.
 * Lambda는 이 값으로 DynamoDB를 다시 읽고 그 상태에 벡터를 맞춘다.
 */
public record IndexingEvent(int schemaVersion, String eventId, Op op, String userId, String qaId,
		Instant requestedAt) {

	public static final int SCHEMA_VERSION = 1;

	public enum Op {

		UPSERT, UPDATE_META, DELETE

	}

	public IndexingEvent {
		if (op == null || userId == null || userId.isBlank() || qaId == null || qaId.isBlank()) {
			throw new IllegalArgumentException("op, userId, qaId are required");
		}
	}

	public static IndexingEvent of(Op op, String userId, String qaId, Instant now) {
		return new IndexingEvent(SCHEMA_VERSION, Ulid.next(), op, userId, qaId, now);
	}

}
