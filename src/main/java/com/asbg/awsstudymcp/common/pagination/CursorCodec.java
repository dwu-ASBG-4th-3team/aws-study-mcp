package com.asbg.awsstudymcp.common.pagination;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;

// 목록 API가 함께 쓰는 불투명 커서. Base64URL("위치값|...|체크섬")
//  - scope: 어떤 목록의 어떤 조회 조건인지 (예: "qa:ALL"). 다르면 다른 커서로 본다.
//  - 위치값: 다음 조회를 시작할 정렬 키. 파티션 키는 담지 않고 로그인 사용자로 다시 조립해야 다른 사용자의 데이터를 읽을 수 없다.
// 체크섬은 깨진 값과 다른 사용자·scope의 커서를 걸러낸다. 비밀 키가 없어 의도적인 위조까지 막지는 않는다.
public final class CursorCodec {

	private static final String DELIMITER = "|";
	private static final int MAX_LENGTH = 500;

	private CursorCodec() {
	}

	public static String encode(String userId, String scope, List<String> values) {
		if (values.stream().anyMatch(value -> value.isBlank() || value.contains(DELIMITER))) {
			throw new IllegalArgumentException("cursor values must not be blank or contain " + DELIMITER);
		}
		String body = String.join(DELIMITER, values);
		String raw = body + DELIMITER + checksum(userId, scope, body);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	// size는 encode에 넘긴 위치값의 개수. 올바르지 않은 커서는 400
	public static List<String> decode(String userId, String scope, String cursor, int size) {
		if (cursor.length() > MAX_LENGTH) {
			throw invalid();
		}
		String raw;
		try {
			raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException e) {
			throw invalid();
		}
		String[] parts = raw.split("\\" + DELIMITER, -1);
		if (parts.length != size + 1) {
			throw invalid();
		}
		List<String> values = Arrays.asList(parts).subList(0, size);
		// 같은 사용자·scope로 다시 만든 값과 같아야 한다.
		if (values.stream().anyMatch(String::isBlank) || !encode(userId, scope, values).equals(cursor)) {
			throw invalid();
		}
		return List.copyOf(values);
	}

	private static String checksum(String userId, String scope, String body) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(String.join(DELIMITER, userId, scope, body).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 8);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ApiException invalid() {
		return new ApiException(CommonErrorCode.VALIDATION_FAILED, "cursor가 올바르지 않습니다.");
	}

}
