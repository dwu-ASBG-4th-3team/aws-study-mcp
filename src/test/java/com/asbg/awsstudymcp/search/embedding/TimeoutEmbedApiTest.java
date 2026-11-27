package com.asbg.awsstudymcp.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.google.genai.errors.ApiException;
import com.google.genai.types.EmbedContentResponse;

class TimeoutEmbedApiTest {

	@Test
	void 제한시간_안에_끝나면_응답을_돌려준다() {
		EmbedContentResponse response = EmbedContentResponse.builder().build();
		var api = new TimeoutEmbedApi((m, t, c) -> CompletableFuture.completedFuture(response), Duration.ofSeconds(1));

		assertThat(api.embed("model", "text", null)).isSameAs(response);
	}

	@Test
	void 제한시간이_지나면_요청을_취소하고_재시도_가능_오류() {
		CompletableFuture<EmbedContentResponse> never = new CompletableFuture<>();
		var api = new TimeoutEmbedApi((m, t, c) -> never, Duration.ofMillis(50));

		assertThatThrownBy(() -> api.embed("model", "text", null)).isInstanceOfSatisfying(EmbeddingException.class,
				e -> assertThat(e.isRetryable()).isTrue());
		assertThat(never).isCancelled();
	}

	@Test
	void SDK_예외는_그대로_넘겨_Provider가_판단한다() {
		ApiException error = new ApiException(429, "RESOURCE_EXHAUSTED", "quota");
		var api = new TimeoutEmbedApi((m, t, c) -> CompletableFuture.failedFuture(error), Duration.ofSeconds(1));

		assertThatThrownBy(() -> api.embed("model", "text", null)).isSameAs(error);
	}

}
