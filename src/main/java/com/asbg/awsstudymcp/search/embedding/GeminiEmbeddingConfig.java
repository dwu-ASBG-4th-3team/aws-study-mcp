package com.asbg.awsstudymcp.search.embedding;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import okhttp3.OkHttpClient;

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
		return createClient(properties, HttpOptions.builder());
	}

	@Bean
	EmbeddingProvider embeddingProvider(Client geminiClient, GeminiEmbeddingProperties properties) {
		return new GeminiEmbeddingProvider(geminiClient.models::embedContent, properties.model(),
				properties.dimension());
	}

	/**
	 * 호출 전체 제한시간은 OkHttp callTimeout으로 건다. 시간이 지나면 OkHttp가 HTTP 요청 자체를 취소하고,
	 * 동기 호출이 GenAiIOException(재시도 가능)으로 끝난다. HttpOptions.timeout은 연결·읽기·쓰기에 각각
	 * 적용돼 전체 시간을 보장하지 못하므로 쓰지 않는다.
	 */
	static Client createClient(GeminiEmbeddingProperties properties, HttpOptions.Builder httpOptions) {
		OkHttpClient httpClient = new OkHttpClient.Builder()
			.callTimeout(properties.timeout())
			// 단계별 제한시간은 끄고 callTimeout 하나로 관리한다 (SDK 기본 클라이언트와 같은 설정).
			.connectTimeout(Duration.ZERO)
			.readTimeout(Duration.ZERO)
			.writeTimeout(Duration.ZERO)
			.build();
		return Client.builder()
			.apiKey(properties.apiKey())
			// 재시도는 호출하는 쪽(SQS 재처리, 검색 API의 RETRY_LATER)에서 정한다.
			.httpOptions(httpOptions.retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
			.clientOptions(ClientOptions.builder().customHttpClient(httpClient).build())
			.build();
	}

}
