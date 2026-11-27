package com.asbg.awsstudymcp.search.embedding;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Gemini 임베딩 설정. API Key는 환경변수(배포 시 Secrets Manager 주입)로만 받는다.
 * @param apiKey GEMINI_API_KEY
 * @param model 모델 ID. 바꾸면 전체 재임베딩이 필요하다.
 * @param dimension 출력 차원. S3 Vectors 인덱스 차원과 같아야 한다.
 * @param timeout 호출 한 번 전체의 제한시간 (시간 초과는 재시도 가능 오류)
 */
@Validated
@ConfigurationProperties("app.gemini.embedding")
public record GeminiEmbeddingProperties(
		@NotBlank String apiKey,
		@NotBlank @DefaultValue("gemini-embedding-2") String model,
		@Positive @DefaultValue("768") int dimension,
		@NotNull @DefaultValue("10s") Duration timeout) {

	// record 기본 toString이 API Key를 로그에 남기지 않도록 가린다.
	@Override
	public String toString() {
		return "GeminiEmbeddingProperties[model=" + model + ", dimension=" + dimension + ", timeout=" + timeout + "]";
	}

}
