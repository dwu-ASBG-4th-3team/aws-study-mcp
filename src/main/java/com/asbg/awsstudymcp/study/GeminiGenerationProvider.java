package com.asbg.awsstudymcp.study;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

@Component
public class GeminiGenerationProvider implements GenerationProvider {

    private final RestClient client;
    private final String apiKey;
    private final String modelId;

    @Autowired
    public GeminiGenerationProvider(
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${GEMINI_GENERATION_MODEL:gemini-3.5-flash-lite}") String modelId) {
        this(createClient(), apiKey, modelId);
    }

    GeminiGenerationProvider(RestClient client, String apiKey, String modelId) {
        this.client = client;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    private static RestClient createClient() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(30_000);

        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String generate(String systemInstruction, String prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }

        var requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));

        JsonNode response;
        try {
            response = client.post()
                    .uri("/v1beta/models/{modelId}:generateContent", modelId)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 429) {
                throw new GenerationException(
                        GenerationException.Code.AI_RATE_LIMITED,
                        "현재 AI 호출 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
            }

            throw new GenerationException(
                    GenerationException.Code.AI_GENERATION_FAILED,
                    "현재 AI 답변을 생성할 수 없습니다.");
        } catch (ResourceAccessException error) {
            throw new GenerationException(
                    GenerationException.Code.AI_GENERATION_FAILED,
                    "현재 AI 서비스에 연결할 수 없습니다.");
        }

        if (response == null) {
            throw new IllegalStateException("Gemini 응답 본문이 비어 있습니다.");
        }

        JsonNode candidate = response.path("candidates").path(0);
        if (!"STOP".equals(candidate.path("finishReason").asText())) {
            throw new IllegalStateException("Gemini가 답변 생성을 정상적으로 완료하지 못했습니다.");
        }

        var result = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                result.append(part.path("text").asText(""));
            }
        }

        if (result.toString().isBlank()) {
            throw new IllegalStateException("Gemini가 생성한 텍스트가 없습니다.");
        }

        return result.toString();
    }
}