package com.asbg.awsstudymcp.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.qa.enums.Domain;


class KeysTest {

	@Test
	void buildsKeysInSpecFormat() {
		assertThat(Keys.userPk("abc")).isEqualTo("USER#abc");
		assertThat(Keys.qaSk("01Q")).isEqualTo("QA#01Q");
		assertThat(Keys.idempotencySk("k1")).isEqualTo("IDEM#k1");
		assertThat(Keys.gsi1Pk("abc", Domain.TECH)).isEqualTo("USER#abc#DOM#TECH");
	}

	@Test
	void timestampAlwaysHasMilliseconds() {
		assertThat(Keys.gsi1Sk(Instant.parse("2026-11-24T05:55:00Z"))).isEqualTo("2026-11-24T05:55:00.000Z");
		assertThat(Keys.gsi1Sk(Instant.parse("2026-11-24T05:55:00.123456Z"))).isEqualTo("2026-11-24T05:55:00.123Z");
	}

	@Test
	void rejectsBlankParts() {
		assertThatThrownBy(() -> Keys.userPk(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Keys.qaSk(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Keys.gsi1Pk("abc", null)).isInstanceOf(IllegalArgumentException.class);
	}

}
