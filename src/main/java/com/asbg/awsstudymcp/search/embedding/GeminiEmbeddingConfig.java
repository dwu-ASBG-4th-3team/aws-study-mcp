package com.asbg.awsstudymcp.search.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * GEMINI_API_KEY가 있을 때만 임베딩 빈을 만든다. 키가 없는 로컬·CI에서도 서버는 뜬다.
 * 검색·인덱싱 코드가 이 빈을 필수로 주입받게 되면 조건을 없애고 키 누락 시 기동 실패로 바꾼다.
 */
@Configuration
@ConditionalOnExpression("!'${app.gemini.embedding.api-key:}'.isBlank()")
@EnableConfigurationProperties(GeminiEmbeddingProperties.class)
public class GeminiEmbeddingConfig {

	// close()는 Spring이 종료 시 자동으로 호출한다.
	@Bean
	Client geminiClient(GeminiEmbeddingProperties properties) {
		HttpOptions httpOptions = HttpOptions.builder()
			.timeout((int) properties.timeout().toMillis())
			// 재시도는 호출하는 쪽(SQS 재처리, 검색 API의 RETRY_LATER)에서 정한다.
			.retryOptions(HttpRetryOptions.builder().attempts(1).build())
			.build();
		return Client.builder().apiKey(properties.apiKey()).httpOptions(httpOptions).build();
	}

	@Bean
	EmbeddingProvider embeddingProvider(Client geminiClient, GeminiEmbeddingProperties properties) {
		return new GeminiEmbeddingProvider(geminiClient.models::embedContent, properties.model(),
				properties.dimension());
	}

}
