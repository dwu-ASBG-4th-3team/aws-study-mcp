package com.asbg.awsstudymcp.study;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudyServiceTest {

    @Test
    void acceptsAnswerWithExactlyTwentyThousandCharacters() {
        String answer = "가".repeat(20_000);
        GenerationProvider provider =
                (instruction, prompt) -> "{\"answer\":\"" + answer + "\"}";
        StudyService service = new StudyService(provider);

        var response = service.generateAnswer("테스트 지시", "테스트 질문");

        assertEquals(answer, response.path("answer").asText());
    }

    @Test
    void rejectsAnswerWithMoreThanTwentyThousandCharacters() {
        String answer = "가".repeat(20_001);
        GenerationProvider provider =
                (instruction, prompt) -> "{\"answer\":\"" + answer + "\"}";
        StudyService service = new StudyService(provider);

        var error = assertThrows(IllegalStateException.class,
                () -> service.generateAnswer("테스트 지시", "테스트 질문"));

        assertEquals("AI 답변이 최대 20,000자를 초과했습니다.", error.getMessage());
    }
}