package com.asbg.awsstudymcp.common;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// SDK 기본 변환은 밀리초 자릿수가 값에 따라 달라지므로 직접 변환한다.
public class TimestampConverter implements AttributeConverter<Instant> {

	@Override
	public AttributeValue transformFrom(Instant input) {
		return AttributeValue.fromS(Timestamps.format(input));
	}

	@Override
	public Instant transformTo(AttributeValue input) {
		return Instant.parse(input.s());
	}

	@Override
	public EnhancedType<Instant> type() {
		return EnhancedType.of(Instant.class);
	}

	@Override
	public AttributeValueType attributeValueType() {
		return AttributeValueType.S;
	}

}
