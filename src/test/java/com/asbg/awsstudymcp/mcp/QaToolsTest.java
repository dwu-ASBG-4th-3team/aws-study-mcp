package com.asbg.awsstudymcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.qa.QaItem;
import com.asbg.awsstudymcp.qa.QaRepository;
import com.asbg.awsstudymcp.qa.QaService;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import tools.jackson.databind.json.JsonMapper;

class QaToolsTest {

	private final QaRepository repository = mock(QaRepository.class);
	private final QaTools tools = new QaTools(
			new QaService(repository), () -> "user-a", JsonMapper.builder().build());

	@Test
	void awsErrorDetailIsNotExposed() {
		doThrow(ResourceNotFoundException.builder().message("table secret-table not found").build())
			.when(repository)
			.save(any());

		CallToolResult result = tools.saveQa("q", "a", null, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).singleElement()
			.isInstanceOfSatisfying(TextContent.class,
					content -> assertThat(content.text()).isEqualTo("[COMMON_500] 서버 오류가 발생했습니다."));
	}

	@Test
	void rateAnswerKeepsResultShape() {
		QaItem rated = QaItem.create("user-a", "qa-1", Domain.TECH, Instant.parse("2026-11-27T07:12:30.123Z"));
		rated.setLatestFeedback(Rating.CONFUSED);
		when(repository.updateRating(any(), any(), any(), any())).thenReturn(Optional.of(rated));

		CallToolResult result = tools.rateAnswer("qa-1", Rating.CONFUSED);

		assertText(result, "{\"qaId\":\"qa-1\",\"rating\":\"CONFUSED\"}");
	}

	@Test
	void missingRecordUsesNewErrorCode() {
		CallToolResult result = tools.deleteQa("qa-1");

		assertThat(result.isError()).isTrue();
		assertText(result, "[QA_001] 기록을 찾을 수 없습니다.");
	}

	private static void assertText(CallToolResult result, String expected) {
		assertThat(result.content()).singleElement()
			.isInstanceOfSatisfying(TextContent.class, content -> assertThat(content.text()).isEqualTo(expected));
	}

}
