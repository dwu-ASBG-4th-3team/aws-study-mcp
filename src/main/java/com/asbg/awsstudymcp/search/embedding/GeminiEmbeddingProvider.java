package com.asbg.awsstudymcp.search.embedding;

import java.util.List;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

/**
 * gemini-embedding-2 구현체.
 * <ul>
 * <li>이 모델은 task_type 파라미터가 없어서 입력 앞에 접두어를 붙여 용도를 알린다.</li>
 * <li>한 요청에 텍스트 여러 개를 넣으면 임베딩 하나로 합쳐지므로 항상 1건씩 호출한다.</li>
 * <li>출력 차원을 줄여도 모델이 재정규화하므로 별도 정규화는 하지 않는다.</li>
 * </ul>
 */
public class GeminiEmbeddingProvider implements EmbeddingProvider {

	private static final String NO_TITLE = "none";

	private final EmbedApi api;
	private final String model;
	private final int dimension;
	private final EmbedContentConfig config;

	public GeminiEmbeddingProvider(EmbedApi api, String model, int dimension) {
		this.api = api;
		this.model = model;
		this.dimension = dimension;
		this.config = EmbedContentConfig.builder().outputDimensionality(dimension).build();
	}

	@Override
	public List<Float> embedQuery(String query) {
		return embed("task: search result | query: " + requireText(query));
	}

	@Override
	public List<Float> embedDocument(String title, String text) {
		String safeTitle = (title == null || title.isBlank()) ? NO_TITLE : title.strip();
		return embed("title: " + safeTitle + " | text: " + requireText(text));
	}

	@Override
	public String modelId() {
		return model;
	}

	@Override
	public int dimension() {
		return dimension;
	}

	private List<Float> embed(String input) {
		EmbedContentResponse response;
		try {
			response = api.embed(model, input, config);
		}
		catch (ApiException e) {
			// 원문 메시지에 요청 정보가 들어갈 수 있어 상태 코드만 남긴다.
			throw new EmbeddingException("Gemini 임베딩 호출 실패: HTTP " + e.code() + " " + e.status(),
					isRetryable(e.code()), e);
		}
		catch (GenAiIOException e) {
			throw new EmbeddingException("Gemini 임베딩 네트워크 오류", true, e);
		}
		return extractValues(response);
	}

	private List<Float> extractValues(EmbedContentResponse response) {
		List<ContentEmbedding> embeddings = response == null ? List.of() : response.embeddings().orElse(List.of());
		if (embeddings.size() != 1) {
			throw new EmbeddingException("임베딩이 1개가 아님: " + embeddings.size() + "개", false);
		}
		List<Float> values = embeddings.get(0).values().orElse(List.of());
		if (values.size() != dimension) {
			throw new EmbeddingException("임베딩 차원 불일치: 기대 " + dimension + ", 실제 " + values.size(), false);
		}
		return List.copyOf(values);
	}

	// 429(호출 한도), 408(시간 초과), 5xx(서버 오류)는 잠시 뒤 재시도하면 성공할 수 있다.
	static boolean isRetryable(int httpStatus) {
		return httpStatus == 429 || httpStatus == 408 || httpStatus >= 500;
	}

	private static String requireText(String text) {
		if (text == null || text.isBlank()) {
			throw new EmbeddingException("임베딩할 텍스트가 비어 있음", false);
		}
		return text.strip();
	}

	/** SDK 호출 지점. 테스트에서 네트워크 없이 응답을 바꿔 끼우려고 분리했다. */
	@FunctionalInterface
	public interface EmbedApi {

		EmbedContentResponse embed(String model, String text, EmbedContentConfig config);

	}

}
