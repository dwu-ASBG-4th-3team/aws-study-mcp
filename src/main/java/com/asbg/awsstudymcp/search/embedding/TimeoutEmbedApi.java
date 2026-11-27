package com.asbg.awsstudymcp.search.embedding;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

/**
 * 호출 전체에 제한시간을 건다. SDK의 HttpOptions.timeout은 연결·읽기·쓰기에 각각 적용돼서
 * 전체 호출은 그보다 길어질 수 있다. 시간이 지나면 기다리기를 멈추고 요청 취소를 시도한다.
 */
class TimeoutEmbedApi implements GeminiEmbeddingProvider.EmbedApi {

	private final AsyncEmbedApi async;
	private final long timeoutMillis;

	TimeoutEmbedApi(AsyncEmbedApi async, Duration timeout) {
		this.async = async;
		this.timeoutMillis = timeout.toMillis();
	}

	@Override
	public EmbedContentResponse embed(String model, String text, EmbedContentConfig config) {
		CompletableFuture<EmbedContentResponse> future = async.embed(model, text, config);
		try {
			return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e) {
			future.cancel(true);
			throw new EmbeddingException("Gemini 임베딩 시간 초과 (" + timeoutMillis + "ms)", true, e);
		}
		catch (InterruptedException e) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new EmbeddingException("Gemini 임베딩 대기 중 인터럽트", true, e);
		}
		catch (ExecutionException e) {
			// SDK 예외(ApiException 등)를 그대로 넘겨 GeminiEmbeddingProvider가 재시도 여부를 판단하게 한다.
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw new EmbeddingException("Gemini 임베딩 호출 실패", false, e.getCause());
		}
	}

	@FunctionalInterface
	interface AsyncEmbedApi {

		CompletableFuture<EmbedContentResponse> embed(String model, String text, EmbedContentConfig config);

	}

}
