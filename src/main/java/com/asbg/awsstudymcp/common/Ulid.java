package com.asbg.awsstudymcp.common;

import java.security.SecureRandom;

// 문자열 정렬 순서가 생성 시각 순서와 같아서 SK = QA#{ULID}만으로 최신순 조회가 된다.
public final class Ulid {

	private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private Ulid() {
	}

	public static String next() {
		return of(System.currentTimeMillis());
	}

	static String of(long epochMillis) {
		byte[] random = new byte[10];
		RANDOM.nextBytes(random);

		char[] out = new char[26];
		long time = epochMillis;
		for (int i = 9; i >= 0; i--) {
			out[i] = ALPHABET[(int) (time & 31)];
			time >>>= 5;
		}

		long hi = 0;
		for (int i = 0; i < 5; i++) {
			hi = (hi << 8) | (random[i] & 0xFF);
		}
		long lo = 0;
		for (int i = 5; i < 10; i++) {
			lo = (lo << 8) | (random[i] & 0xFF);
		}
		for (int i = 17; i >= 10; i--) {
			out[i] = ALPHABET[(int) (hi & 31)];
			hi >>>= 5;
		}
		for (int i = 25; i >= 18; i--) {
			out[i] = ALPHABET[(int) (lo & 31)];
			lo >>>= 5;
		}
		return new String(out);
	}

}
