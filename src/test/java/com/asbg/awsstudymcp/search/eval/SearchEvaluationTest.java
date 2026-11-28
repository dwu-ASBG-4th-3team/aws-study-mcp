package com.asbg.awsstudymcp.search.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.asbg.awsstudymcp.search.embedding.EmbeddingException;
import com.asbg.awsstudymcp.search.embedding.EmbeddingProvider;
import com.asbg.awsstudymcp.search.embedding.GeminiEmbeddingProvider;
import com.google.genai.Client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * 평가 질문셋으로 임베딩 검색 품질을 잰다 (docs/search-poc.md 3절). 실제 Gemini를 호출한다.
 * S3 Vectors 없이 메모리에서 cosine 유사도로 순위를 매긴다.
 * <pre>
 * GEMINI_API_KEY=... SEARCH_EVAL_FILE=/path/to/questions.json \
 *   ./gradlew test --rerun --tests '*SearchEvaluationTest'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SEARCH_EVAL_FILE", matches = ".+")
class SearchEvaluationTest {

	private static final String MODEL = "gemini-embedding-2";
	private static final int DIMENSION = 768;
	private static final int MAX_ATTEMPTS = 3;

	// 인덱싱 Lambda와 같은 제목을 쓴다 (docs/indexing.md 3절).
	static final Map<String, String> DOMAIN_TITLES = Map.of("CONCEPTS", "Cloud Concepts", "SECURITY",
			"Security and Compliance", "TECH", "Cloud Technology and Services", "BILLING",
			"Billing, Pricing, and Support");

	@Test
	void 평가_질문셋으로_Recall과_유사도_분포를_잰다() throws Exception {
		EvalSet set = read(Path.of(System.getenv("SEARCH_EVAL_FILE")));

		try (Client client = Client.builder().apiKey(System.getenv("GEMINI_API_KEY")).build()) {
			EmbeddingProvider provider = new GeminiEmbeddingProvider(client.models::embedContent, MODEL, DIMENSION);
			Report report = evaluate(set, provider);
			String markdown = report.toMarkdown();
			System.out.println(markdown);
			write(markdown);
		}
	}

	static EvalSet read(Path file) {
		return JsonMapper.builder().build().readValue(file.toFile(), EvalSet.class);
	}

	private static Report evaluate(EvalSet set, EmbeddingProvider provider) throws InterruptedException {
		List<List<Float>> docs = new ArrayList<>();
		for (EvalItem item : set.items()) {
			docs.add(withRetry(() -> provider.embedDocument(DOMAIN_TITLES.get(item.domain()), item.question())));
		}

		Report report = new Report();
		for (int i = 0; i < set.items().size(); i++) {
			EvalItem item = set.items().get(i);
			for (String variant : item.variants()) {
				List<Float> query = withRetry(() -> provider.embedQuery(variant));
				List<Ranked> ranked = rank(query, docs);
				int position = indexOf(ranked, i);
				report.addVariant(item.id(), variant, position, ranked.get(position).similarity(),
						set.items().get(ranked.get(0).index()).id());
			}
		}
		for (String unrelated : set.unrelated()) {
			List<Float> query = withRetry(() -> provider.embedQuery(unrelated));
			Ranked top = rank(query, docs).get(0);
			report.addUnrelated(unrelated, top.similarity(), set.items().get(top.index()).id());
		}
		return report;
	}

	private static List<Ranked> rank(List<Float> query, List<List<Float>> docs) {
		List<Ranked> ranked = new ArrayList<>();
		for (int i = 0; i < docs.size(); i++) {
			ranked.add(new Ranked(i, cosine(query, docs.get(i))));
		}
		ranked.sort(Comparator.comparingDouble(Ranked::similarity).reversed());
		return ranked;
	}

	private static int indexOf(List<Ranked> ranked, int docIndex) {
		for (int p = 0; p < ranked.size(); p++) {
			if (ranked.get(p).index() == docIndex) {
				return p;
			}
		}
		throw new IllegalStateException("not ranked: " + docIndex);
	}

	static double cosine(List<Float> a, List<Float> b) {
		double dot = 0;
		double na = 0;
		double nb = 0;
		for (int i = 0; i < a.size(); i++) {
			dot += a.get(i) * b.get(i);
			na += a.get(i) * a.get(i);
			nb += b.get(i) * b.get(i);
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}

	// 무료 등급의 분당 한도에 걸리면 기다렸다가 다시 시도한다.
	private static List<Float> withRetry(EmbedCall call) throws InterruptedException {
		for (int attempt = 1;; attempt++) {
			try {
				return call.run();
			}
			catch (EmbeddingException e) {
				if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
					throw e;
				}
				Thread.sleep(Duration.ofSeconds(20L * attempt));
			}
		}
	}

	private static void write(String markdown) throws IOException {
		Path out = Path.of("build", "reports", "search-eval.md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, markdown);
	}

	@FunctionalInterface
	private interface EmbedCall {

		List<Float> run();

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EvalSet(List<EvalItem> items, List<String> unrelated) {

		EvalSet {
			items = items == null ? List.of() : items;
			unrelated = unrelated == null ? List.of() : unrelated;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EvalItem(String id, String domain, String question, List<String> variants) {

		EvalItem {
			variants = variants == null ? List.of() : variants;
		}

	}

	private record Ranked(int index, double similarity) {
	}

	private static final class Report {

		private final List<String> variantRows = new ArrayList<>();
		private final List<String> unrelatedRows = new ArrayList<>();
		private int total;
		private int hit1;
		private int hit3;
		private double minCorrect = Double.MAX_VALUE;
		private double maxUnrelated = -1;

		void addVariant(String id, String variant, int position, double similarity, String topId) {
			total++;
			hit1 += position == 0 ? 1 : 0;
			hit3 += position < 3 ? 1 : 0;
			minCorrect = Math.min(minCorrect, similarity);
			variantRows.add("| %s | %s | %d | %.4f | %s |".formatted(id, variant, position + 1, similarity, topId));
		}

		void addUnrelated(String question, double similarity, String topId) {
			maxUnrelated = Math.max(maxUnrelated, similarity);
			unrelatedRows.add("| %s | %.4f | %s |".formatted(question, similarity, topId));
		}

		String toMarkdown() {
			StringBuilder sb = new StringBuilder();
			sb.append("# 검색 평가 결과 (").append(LocalDate.now()).append(", ").append(MODEL).append(" · ")
				.append(DIMENSION).append(")\n\n");
			sb.append("| Recall@1 | Recall@3 | 관계없는 질문 최고 | 변형 질문 최저 정답 |\n| --- | --- | --- | --- |\n");
			sb.append(String.format(Locale.ROOT, "| %.3f (%d/%d) | %.3f (%d/%d) | %.4f | %.4f |%n%n", ratio(hit1),
					hit1, total, ratio(hit3), hit3, total, maxUnrelated, total == 0 ? 0 : minCorrect));
			sb.append(maxUnrelated < minCorrect
					? String.format(Locale.ROOT, "최소 유사도 후보: %.4f ~ %.4f 사이%n%n", maxUnrelated, minCorrect)
					: "관계없는 질문이 정답보다 유사도가 높은 경우가 있어 최소 유사도 필터를 쓰지 않습니다 (0).\n\n");
			sb.append("## 변형 질문\n\n| 원본 | 변형 질문 | 정답 순위 | 정답 유사도 | 1위 |\n| --- | --- | --- | --- | --- |\n");
			variantRows.forEach(r -> sb.append(r).append('\n'));
			sb.append("\n## 관계없는 질문\n\n| 질문 | 최고 유사도 | 1위 |\n| --- | --- | --- |\n");
			unrelatedRows.forEach(r -> sb.append(r).append('\n'));
			return sb.toString();
		}

		private double ratio(int hits) {
			return total == 0 ? 0 : (double) hits / total;
		}

	}

}
