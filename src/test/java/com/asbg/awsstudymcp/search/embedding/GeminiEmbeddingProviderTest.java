package com.asbg.awsstudymcp.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

class GeminiEmbeddingProviderTest {

	private static final int DIM = 768;

	private final List<String> inputs = new ArrayList<>();
	private final List<EmbedContentConfig> configs = new ArrayList<>();

	@Test
	void 검색_질문에는_search_result_접두어를_붙인다() {
		GeminiEmbeddingProvider provider = provider(respondWith(DIM));

		List<Float> vector = provider.embedQuery("  S3와 EBS 차이  ");

		assertThat(inputs).containsExactly("task: search result | query: S3와 EBS 차이");
		assertThat(vector).hasSize(DIM);
	}

	@Test
	void 저장_QA에는_title_text_형식을_쓰고_제목이_없으면_none() {
		GeminiEmbeddingProvider provider = provider(respondWith(DIM));

		provider.embedDocument("Cloud Concepts", "리전과 가용 영역의 차이는?");
		provider.embedDocument(null, "IAM 역할이란?");

		assertThat(inputs).containsExactly("title: Cloud Concepts | text: 리전과 가용 영역의 차이는?",
				"title: none | text: IAM 역할이란?");
	}

	@Test
	void 출력_차원을_요청_설정에_넣는다() {
		GeminiEmbeddingProvider provider = provider(respondWith(DIM));

		provider.embedQuery("q");

		assertThat(configs.get(0).outputDimensionality()).contains(DIM);
		assertThat(provider.modelId()).isEqualTo("gemini-embedding-2");
		assertThat(provider.dimension()).isEqualTo(DIM);
	}

	@Test
	void 빈_텍스트는_호출하지_않고_재시도_불가로_실패한다() {
		GeminiEmbeddingProvider provider = provider(respondWith(DIM));

		assertThatThrownBy(() -> provider.embedQuery(" ")).isInstanceOfSatisfying(EmbeddingException.class,
				e -> assertThat(e.isRetryable()).isFalse());
		assertThat(inputs).isEmpty();
	}

	@Test
	void 차원이_다르면_재시도_불가로_실패한다() {
		GeminiEmbeddingProvider provider = provider(respondWith(1024));

		assertThatThrownBy(() -> provider.embedQuery("q")).isInstanceOfSatisfying(EmbeddingException.class,
				e -> assertThat(e.isRetryable()).isFalse());
	}

	@Test
	void 임베딩이_비어_있으면_실패한다() {
		GeminiEmbeddingProvider provider = provider((m, t, c) -> EmbedContentResponse.builder().build());

		assertThatThrownBy(() -> provider.embedQuery("q")).isInstanceOf(EmbeddingException.class);
	}

	@Test
	void 호출_한도와_서버_오류는_재시도_가능() {
		for (int code : new int[] { 429, 500, 503 }) {
			GeminiEmbeddingProvider provider = provider(failWith(new ApiException(code, "X", "secret detail")));

			assertThatThrownBy(() -> provider.embedQuery("q")).isInstanceOfSatisfying(EmbeddingException.class, e -> {
				assertThat(e.isRetryable()).isTrue();
				assertThat(e.getMessage()).doesNotContain("secret detail");
			});
		}
	}

	@Test
	void 잘못된_요청과_인증_오류는_재시도_불가() {
		for (int code : new int[] { 400, 401, 403, 404 }) {
			GeminiEmbeddingProvider provider = provider(failWith(new ApiException(code, "X", "m")));

			assertThatThrownBy(() -> provider.embedQuery("q")).isInstanceOfSatisfying(EmbeddingException.class,
					e -> assertThat(e.isRetryable()).isFalse());
		}
	}

	@Test
	void 네트워크_오류는_재시도_가능() {
		GeminiEmbeddingProvider provider = provider(failWith(new GenAiIOException("connection reset")));

		assertThatThrownBy(() -> provider.embedQuery("q")).isInstanceOfSatisfying(EmbeddingException.class,
				e -> assertThat(e.isRetryable()).isTrue());
	}

	private GeminiEmbeddingProvider provider(GeminiEmbeddingProvider.EmbedApi api) {
		return new GeminiEmbeddingProvider((model, text, config) -> {
			inputs.add(text);
			configs.add(config);
			return api.embed(model, text, config);
		}, "gemini-embedding-2", DIM);
	}

	private static GeminiEmbeddingProvider.EmbedApi respondWith(int size) {
		List<Float> values = Collections.nCopies(size, 0.01f);
		return (model, text, config) -> EmbedContentResponse.builder()
			.embeddings(ContentEmbedding.builder().values(values).build())
			.build();
	}

	private static GeminiEmbeddingProvider.EmbedApi failWith(RuntimeException e) {
		return (model, text, config) -> {
			throw e;
		};
	}

}
