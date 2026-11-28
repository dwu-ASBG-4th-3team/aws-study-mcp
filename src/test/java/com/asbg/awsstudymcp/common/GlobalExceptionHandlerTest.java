package com.asbg.awsstudymcp.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.asbg.awsstudymcp.study.GenerationException;

import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

class GlobalExceptionHandlerTest {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void awsErrorDetailIsNotExposed() throws Exception {
		assertError("aws", 500, "COMMON_500", "서버 오류가 발생했습니다.");
	}

	@Test
	void unexpectedErrorDetailIsNotExposed() throws Exception {
		assertError("bug", 500, "COMMON_500", "서버 오류가 발생했습니다.");
	}

	@Test
	void apiErrorsKeepStatusAndCode() throws Exception {
		assertError("unauthorized", 401, "COMMON_401", "인증이 필요합니다.");
		assertError("forbidden", 403, "COMMON_403", "권한이 없습니다.");
		assertError("conflict", 409, "COMMON_409", "이미 처리된 요청입니다.");
		assertError("limited", 429, "COMMON_429", "요청 한도를 초과했습니다.");
	}

	@Test
	void generationErrorsUseCommonStatusWithoutExposingDetails() throws Exception {
		assertError("ai-limited", 429, "COMMON_429", CommonErrorCode.RATE_LIMITED.message());
		assertError("ai-failed", 500, "COMMON_500", CommonErrorCode.INTERNAL_ERROR.message());
	}

	private void assertError(String path, int status, String code, String message) throws Exception {
		mvc.perform(get("/fail/{kind}", path))
			.andExpect(status().is(status))
			.andExpect(content().json("""
					{"isSuccess": false, "code": "%s", "message": "%s", "result": null}
					""".formatted(code, message), JsonCompareMode.STRICT));
	}

	@RestController
	static class FailingController {

		@GetMapping("/fail/{kind}")
		String fail(@PathVariable String kind) {
			throw switch (kind) {
				case "aws" -> ResourceNotFoundException.builder().message("table secret-table not found").build();
				case "unauthorized" -> new ApiException(CommonErrorCode.UNAUTHORIZED);
				case "forbidden" -> new ApiException(CommonErrorCode.FORBIDDEN);
				case "conflict" -> new ApiException(CommonErrorCode.CONFLICT);
				case "limited" -> new ApiException(CommonErrorCode.RATE_LIMITED);
				case "ai-limited" -> new GenerationException(GenerationException.Code.AI_RATE_LIMITED, "provider detail");
				case "ai-failed" -> new GenerationException(GenerationException.Code.AI_GENERATION_FAILED, "provider detail");
				default -> new IllegalStateException("secret detail");
			};
		}

	}

}
