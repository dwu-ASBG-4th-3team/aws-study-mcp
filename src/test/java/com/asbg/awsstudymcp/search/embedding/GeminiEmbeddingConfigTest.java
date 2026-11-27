package com.asbg.awsstudymcp.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeminiEmbeddingConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(GeminiEmbeddingConfig.class);

	@Test
	void 키가_없으면_임베딩_빈을_만들지_않는다() {
		runner.withPropertyValues("app.gemini.embedding.api-key=")
			.run(context -> assertThat(context).doesNotHaveBean(EmbeddingProvider.class));
	}

	@Test
	void 키가_있으면_기본값으로_빈을_만든다() {
		runner.withPropertyValues("app.gemini.embedding.api-key=dummy-key-for-test").run(context -> {
			EmbeddingProvider provider = context.getBean(EmbeddingProvider.class);
			assertThat(provider.modelId()).isEqualTo("gemini-embedding-2");
			assertThat(provider.dimension()).isEqualTo(768);
			assertThat(context.getBean(GeminiEmbeddingProperties.class).toString()).doesNotContain("dummy-key");
		});
	}

}
