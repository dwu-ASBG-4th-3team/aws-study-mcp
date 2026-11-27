package com.asbg.awsstudymcp.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// UTC, ISO 8601, 밀리초 3자리 고정. 자릿수가 고정이어야 문자열 정렬이 시간순과 같다.
public final class Timestamps {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter
		.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
		.withZone(ZoneOffset.UTC);

	private Timestamps() {
	}

	public static String format(Instant instant) {
		return FORMAT.format(instant);
	}

}
