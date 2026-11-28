package com.asbg.awsstudymcp.study;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GeminiGenerationProviderTest {

    @Test
    void mapsHttp429ToAiRateLimited() {
        verifyErrorMapping(
                HttpStatus.TOO_MANY_REQUESTS,
                GenerationException.Code.AI_RATE_LIMITED);
    }

    @Test
    void mapsHttp403ToAiGenerationFailed() {
        verifyErrorMapping(
                HttpStatus.FORBIDDEN,
                GenerationException.Code.AI_GENERATION_FAILED);
    }

    private void verifyErrorMapping(
            HttpStatus status, GenerationException.Code expectedCode) {
        var builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GeminiGenerationProvider(
                builder.build(), "test-key", "gemini-3.5-flash-lite");

        server.expect(requestTo(
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3.5-flash-lite:generateContent"))
                .andRespond(withStatus(status));

        var error = assertThrows(GenerationException.class,
                () -> provider.generate("테스트 지시", "테스트 질문"));

        assertEquals(expectedCode, error.getCode());
        server.verify();
    }
}