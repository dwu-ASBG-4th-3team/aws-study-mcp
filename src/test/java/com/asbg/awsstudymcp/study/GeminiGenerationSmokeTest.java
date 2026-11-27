package com.asbg.awsstudymcp.study;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GeminiGenerationSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_GEMINI_SMOKE", matches = "true")
    void generatesAnswerViaGemini() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertFalse(apiKey == null || apiKey.isBlank(), "GEMINI_API_KEY가 필요합니다.");

        var provider = new GeminiGenerationProvider(apiKey, "gemini-3.5-flash-lite");
        var service = new StudyService(provider);

        var response = service.generateAnswer(
                "AWS 초보자에게 한국어로 세 문장 이내로 설명해. "
                        + "answer 필드가 있는 JSON 객체를 반환해.",
                "AWS에서 S3와 EBS의 차이는 뭐야?");

        assertFalse(response.path("answer").asText().isBlank());
    }
}