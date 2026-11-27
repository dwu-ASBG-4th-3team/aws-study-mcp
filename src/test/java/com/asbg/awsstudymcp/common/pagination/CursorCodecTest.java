package com.asbg.awsstudymcp.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;

class CursorCodecTest {

	@Test
	void decodesEncodedValues() {
		for (List<String> values : List.of(List.of("one"), List.of("one", "2026-11-27T07:12:30.123Z"))) {
			String cursor = CursorCodec.encode("user-a", "qa:ALL", values);

			assertThat(cursor).matches("[A-Za-z0-9_-]+");
			assertThat(CursorCodec.decode("user-a", "qa:ALL", cursor, values.size())).isEqualTo(values);
		}
	}

	@Test
	void rejectsOtherUserScopeOrSize() {
		String cursor = CursorCodec.encode("user-a", "qa:ALL", List.of("one"));

		assertInvalid(() -> CursorCodec.decode("user-b", "qa:ALL", cursor, 1));
		assertInvalid(() -> CursorCodec.decode("user-a", "session:ALL", cursor, 1));
		assertInvalid(() -> CursorCodec.decode("user-a", "qa:ALL", cursor, 2));
		assertInvalid(() -> CursorCodec.decode("user-a", "qa:ALL", "not a cursor", 1));
	}

	@Test
	void refusesValuesThatCannotBeDecoded() {
		assertThatThrownBy(() -> CursorCodec.encode("user-a", "qa:ALL", List.of("a|b")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CursorCodec.encode("user-a", "qa:ALL", List.of(" ")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static void assertInvalid(ThrowingCallable call) {
		assertThatThrownBy(call)
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
	}

}
