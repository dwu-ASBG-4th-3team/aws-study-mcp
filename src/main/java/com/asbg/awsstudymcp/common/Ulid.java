package com.asbg.awsstudymcp.common;

import java.security.SecureRandom;
import java.time.Clock;

// 문자열 정렬 순서가 생성 순서와 같아서 SK = QA#{ULID}만으로 최신순 조회가 된다.
// monotonic: 같은 밀리초(또는 시계가 뒤로 간 경우)에는 직전 값의 난수부를 1 증가시킨다.
// 단조 증가는 한 JVM 안에서만 보장된다.
public final class Ulid {

	private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
	private static final long MASK_40 = (1L << 40) - 1;
	private static final Generator DEFAULT = new Generator(Clock.systemUTC(), new SecureRandom());

	private Ulid() {
	}

	public static String next() {
		return DEFAULT.next();
	}

	static final class Generator {

		private final Clock clock;
		private final SecureRandom random;

		private long lastTime = -1;
		// 난수부 80비트를 상위 40비트와 하위 40비트로 나눠 보관한다.
		private long hi;
		private long lo;

		Generator(Clock clock, SecureRandom random) {
			this.clock = clock;
			this.random = random;
		}

		synchronized String next() {
			long now = clock.millis();
			if (now > lastTime) {
				lastTime = now;
				hi = random.nextLong() & MASK_40;
				lo = random.nextLong() & MASK_40;
			}
			else {
				increment();
			}
			return encode(lastTime, hi, lo);
		}

		private void increment() {
			lo = (lo + 1) & MASK_40;
			if (lo == 0) {
				hi = (hi + 1) & MASK_40;
				// 80비트가 넘치면 시각을 올려 순서를 유지한다.
				if (hi == 0) {
					lastTime++;
				}
			}
		}

	}

	private static String encode(long time, long hi, long lo) {
		char[] out = new char[26];
		write(out, 0, 10, time);
		write(out, 10, 8, hi);
		write(out, 18, 8, lo);
		return new String(out);
	}

	private static void write(char[] out, int offset, int length, long value) {
		for (int i = offset + length - 1; i >= offset; i--) {
			out[i] = ALPHABET[(int) (value & 31)];
			value >>>= 5;
		}
	}

}
