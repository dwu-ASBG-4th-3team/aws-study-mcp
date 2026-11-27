package com.asbg.awsstudymcp.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class UlidTest {

	@Test
	void idsAreStrictlyIncreasing() {
		var ids = Stream.generate(Ulid::next).limit(10_000).toList();

		assertThat(ids).allSatisfy(id -> assertThat(id).hasSize(26)).isSorted().doesNotHaveDuplicates();
	}

	@Test
	void idsAreIncreasingWithinSameMillisecond() {
		var clock = Clock.fixed(Instant.parse("2026-11-24T05:55:00Z"), ZoneOffset.UTC);
		var generator = new Ulid.Generator(clock, new SecureRandom());

		var ids = Stream.generate(generator::next).limit(1_000).toList();

		assertThat(ids).isSorted().doesNotHaveDuplicates();
		assertThat(ids).extracting(id -> id.substring(0, 10)).containsOnly(ids.getFirst().substring(0, 10));
	}

	@Test
	void randomOverflowAdvancesTime() {
		var allOnes = new SecureRandom() {
			@Override
			public long nextLong() {
				return -1L;
			}
		};
		var generator = new Ulid.Generator(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), allOnes);

		assertThat(generator.next()).isEqualTo("0000000000ZZZZZZZZZZZZZZZZ");
		assertThat(generator.next()).isEqualTo("00000000010000000000000000");
	}

}
