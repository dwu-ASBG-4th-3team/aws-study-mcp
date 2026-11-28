package com.asbg.awsstudymcp.study;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class StudyService {

    private static final int MAX_ANSWER_LENGTH = 20_000;

    private final GenerationProvider generationProvider;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StudyService(GenerationProvider generationProvider) {
        this.generationProvider = generationProvider;
    }

    public JsonNode generateAnswer(String systemInstruction, String prompt) {
        String generatedJson = generationProvider.generate(systemInstruction, prompt);
        JsonNode response = jsonMapper.readTree(generatedJson);
        JsonNode answerNode = response.path("answer");

        if (!answerNode.isTextual() || answerNode.asText().isBlank()) {
            throw new IllegalStateException("Gemini 응답에 유효한 answer가 없습니다.");
        }

        String answer = answerNode.asText();
        int answerLength = answer.codePointCount(0, answer.length());

        if (answerLength > MAX_ANSWER_LENGTH) {
            throw new IllegalStateException("AI 답변이 최대 20,000자를 초과했습니다.");
        }

        return response;
    }
}