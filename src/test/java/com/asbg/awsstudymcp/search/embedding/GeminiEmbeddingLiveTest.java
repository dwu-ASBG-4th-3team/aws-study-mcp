package com.asbg.awsstudymcp.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.google.genai.Client;

/**
 * 실제 Gemini API를 호출한다. GEMINI_API_KEY가 있을 때만 실행되고 CI에서는 건너뛴다.
 * <pre>GEMINI_API_KEY=... ./gradlew test --rerun --tests '*GeminiEmbeddingLiveTest'</pre>
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiEmbeddingLiveTest {

	@Test
	void 비슷한_질문이_관계없는_질문보다_가깝다() {
		try (Client client = Client.builder().apiKey(System.getenv("GEMINI_API_KEY")).build()) {
			EmbeddingProvider provider = new GeminiEmbeddingProvider(client.models::embedContent,
					"gemini-embedding-2", 768);

			List<Float> stored = provider.embedDocument("Cloud Technology and Services",
					"S3와 EBS는 어떤 차이가 있나요?");
			List<Float> similar = provider.embedQuery("S3 버킷이랑 EBS 볼륨 차이 알려줘");
			List<Float> unrelated = provider.embedQuery("AWS 지원 플랜 중 Business 요금은?");

			assertThat(stored).hasSize(768);
			assertThat(norm(stored)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
			assertThat(cosine(stored, similar)).isGreaterThan(cosine(stored, unrelated));
		}
	}

	private static double cosine(List<Float> a, List<Float> b) {
		double dot = 0;
		for (int i = 0; i < a.size(); i++) {
			dot += a.get(i) * b.get(i);
		}
		return dot / (norm(a) * norm(b));
	}

	private static double norm(List<Float> v) {
		double sum = 0;
		for (float x : v) {
			sum += x * x;
		}
		return Math.sqrt(sum);
	}

}
