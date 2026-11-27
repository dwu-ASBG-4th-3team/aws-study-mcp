package com.asbg.awsstudymcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.qa.QaRepository;
import com.asbg.awsstudymcp.qa.QaService;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import tools.jackson.databind.json.JsonMapper;

class QaToolsTest {

	private final QaRepository repository = mock(QaRepository.class);
	private final QaTools tools = new QaTools(new QaService(repository), () -> "user-a", JsonMapper.builder().build());

	@Test
	void awsErrorDetailIsNotExposed() {
		doThrow(ResourceNotFoundException.builder().message("table secret-table not found").build())
			.when(repository)
			.save(any());

		CallToolResult result = tools.saveQa("q", "a", null, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).singleElement()
			.isInstanceOfSatisfying(TextContent.class,
					content -> assertThat(content.text()).isEqualTo("[INTERNAL_ERROR] 서버 오류가 발생했습니다."));
	}

}
