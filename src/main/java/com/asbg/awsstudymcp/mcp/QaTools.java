package com.asbg.awsstudymcp.mcp;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.auth.CurrentUserProvider;
import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.qa.QaService;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import software.amazon.awssdk.core.exception.SdkException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class QaTools {

	private static final Logger log = LoggerFactory.getLogger(QaTools.class);

	private final QaService qaService;
	private final CurrentUserProvider currentUser;
	private final JsonMapper jsonMapper;

	public QaTools(QaService qaService, CurrentUserProvider currentUser, JsonMapper jsonMapper) {
		this.qaService = qaService;
		this.currentUser = currentUser;
		this.jsonMapper = jsonMapper;
	}

	@McpTool(name = "save_qa", description = """
			AWS 학습 중 나눈 질문과 답변을 사용자의 학습 노트에 저장한다.
			AWS 자격증(CLF) 관련 질문에 답한 직후, 사용자가 원하면 호출한다.
			저장 결과로 qaId를 돌려주며, 이후 rate_answer와 delete_qa에 이 qaId를 쓴다.
			domain은 가능하면 반드시 분류하고, 어느 영역인지 정말 판단하기 어려울 때만 생략한다.""")
	public CallToolResult saveQa(
			@McpToolParam(description = "사용자가 한 질문 원문") String question,
			@McpToolParam(description = "질문에 대한 답변 원문") String answer,
			@McpToolParam(description = """
					CLF 시험 도메인. CONCEPTS(클라우드 개념), SECURITY(보안·규정 준수), \
					TECH(클라우드 기술·서비스), BILLING(요금·결제·지원), UNCLASSIFIED(분류 불가). \
					생략하면 UNCLASSIFIED로 저장된다.""", required = false) Domain domain,
			@McpToolParam(description = "세부 주제 태그 (예: [\"iam\", \"vpc\"])", required = false) List<String> tags) {
		return call(() -> qaService.save(currentUser.currentUserId(), question, answer, domain, tags));
	}

	@McpTool(name = "rate_answer", description = """
			저장된 Q&A에 대한 사용자의 이해도를 기록한다.
			UNDERSTOOD(이해했어요), CONFUSED(아직 헷갈려요), WRONG(틀린 것 같아요) 중 하나를 고른다.""")
	public CallToolResult rateAnswer(
			@McpToolParam(description = "save_qa가 돌려준 qaId") String qaId,
			@McpToolParam(description = "UNDERSTOOD, CONFUSED, WRONG 중 하나") Rating rating) {
		return call(() -> {
			var rated = qaService.rate(currentUser.currentUserId(), qaId, rating);
			return new RateAnswerResult(rated.qaId(), rated.latestFeedback());
		});
	}

	@McpTool(name = "delete_qa", description = "사용자 본인의 Q&A 기록을 삭제한다. 삭제 전에 사용자에게 확인을 받는다.")
	public CallToolResult deleteQa(@McpToolParam(description = "삭제할 qaId") String qaId) {
		return call(() -> qaService.delete(currentUser.currentUserId(), qaId));
	}

	// 예외를 그대로 던지면 프레임워크가 오류 메시지를 두 번 붙여서 결과 객체를 직접 만든다.
	private CallToolResult call(Supplier<?> action) {
		try {
			return CallToolResult.builder().addTextContent(jsonMapper.writeValueAsString(action.get())).build();
		}
		catch (ApiException e) {
			return error(e);
		}
		// AWS 오류 원문에는 계정 ID, 테이블 이름 등이 들어가므로 로그에만 남긴다.
		catch (SdkException e) {
			log.error("AWS 호출 실패", e);
			return error(new ApiException(CommonErrorCode.INTERNAL_ERROR));
		}
	}

	private static CallToolResult error(ApiException e) {
		return CallToolResult.builder().isError(true).addTextContent(e.toToolMessage()).build();
	}

	record RateAnswerResult(String qaId, Rating rating) {
	}

}
