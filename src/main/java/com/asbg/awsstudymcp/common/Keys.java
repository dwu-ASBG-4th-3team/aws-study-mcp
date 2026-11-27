package com.asbg.awsstudymcp.common;

import java.time.Instant;

import com.asbg.awsstudymcp.qa.enums.Domain;


// DynamoDB 단일 테이블의 키 문자열은 이 클래스에서만 조립한다.
public final class Keys {

	public static final String PK = "PK";
	public static final String SK = "SK";
	public static final String GSI1 = "GSI1";
	public static final String GSI1PK = "GSI1PK";
	public static final String GSI1SK = "GSI1SK";
	public static final String QA_PREFIX = "QA#";

	private Keys() {
	}

	// sub는 검증된 인증 토큰에서 얻은 값만 넘긴다.
	public static String userPk(String sub) {
		return "USER#" + require(sub, "sub");
	}

	public static String qaSk(String qaId) {
		return QA_PREFIX + require(qaId, "qaId");
	}

	// 평가 시각까지 넣어 이력이 쌓이게 한다.
	public static String feedbackSk(String qaId, Instant createdAt) {
		return "FB#" + require(qaId, "qaId") + "#" + Timestamps.format(createdAt);
	}

	public static String idempotencySk(String key) {
		return "IDEM#" + require(key, "key");
	}

	public static String gsi1Pk(String sub, Domain domain) {
		if (domain == null) {
			throw new IllegalArgumentException("domain is required");
		}
		return userPk(sub) + "#DOM#" + domain.name();
	}

	public static String gsi1Sk(Instant createdAt) {
		return Timestamps.format(createdAt);
	}

	private static String require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}

}
