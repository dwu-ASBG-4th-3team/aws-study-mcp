package com.asbg.awsstudymcp.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.asbg.awsstudymcp.auth.CurrentUserProvider;
import com.asbg.awsstudymcp.common.Ulid;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Source;
import com.asbg.awsstudymcp.qa.enums.Verification;
import com.asbg.awsstudymcp.support.LocalDynamoDb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// HTTP 요청부터 DynamoDB Local까지 확인한다. 사용자만 바꿔 끼운다.
@SpringBootTest(properties = "app.dynamodb.table-name=qa-api-test")
@ActiveProfiles("local")
class QaApiTest {

	@TestBean
	DynamoDbClient dynamoDbClient;

	@MockitoBean
	CurrentUserProvider currentUser;

	@Autowired
	DynamoDbTable<QaItem> table;

	@Autowired
	WebApplicationContext context;

	@Autowired
	JsonMapper jsonMapper;

	private MockMvc mvc;
	private String userId;

	static DynamoDbClient dynamoDbClient() {
		return LocalDynamoDb.client();
	}

	@BeforeEach
	void setUp() {
		if (!dynamoDbClient.listTables().tableNames().contains(table.tableName())) {
			LocalDynamoDb.createTable(table);
		}
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		userId = Ulid.next();
		when(currentUser.currentUserId()).thenReturn(userId);
	}

	@Test
	void listReturnsBaseResponse() throws Exception {
		String qaId = put("q");

		JsonNode body = call(get("/api/qa"), 200);

		assertSuccess(body);
		JsonNode result = body.get("result");
		assertThat(result.has("nextCursor")).isTrue();
		assertThat(result.get("nextCursor").isNull()).isTrue();
		JsonNode item = result.get("items").get(0);
		assertThat(item.propertyNames()).containsExactlyInAnyOrder("qaId", "question", "domain", "embeddingStatus",
				"latestFeedback", "createdAt");
		assertThat(item.get("qaId").asString()).isEqualTo(qaId);
		assertThat(item.get("latestFeedback").isNull()).isTrue();
		assertThat(item.get("createdAt").asString()).isEqualTo("2026-11-27T07:12:30.000Z");
	}

	@Test
	void listAcceptsDomainLimitAndCursor() throws Exception {
		put("first");
		put("second");

		JsonNode first = call(get("/api/qa").param("domain", "SECURITY").param("limit", "1"), 200).get("result");
		JsonNode second = call(get("/api/qa").param("domain", "SECURITY")
			.param("limit", "50")
			.param("cursor", first.get("nextCursor").asString()), 200).get("result");

		assertThat(first.get("items")).hasSize(1);
		assertThat(second.get("items")).hasSize(1);
		assertThat(second.get("nextCursor").isNull()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "domain=NETWORK", "domain=security", "limit=0", "limit=51", "limit=abc", "limit=1.5",
			"cursor=abc", "cursor=" })
	void listRejectsInvalidParameter(String parameter) throws Exception {
		String[] pair = parameter.split("=", 2);

		assertFailure(call(get("/api/qa").param(pair[0], pair[1]), 400), "COMMON_400");
	}

	@Test
	void detailReturnsAllFields() throws Exception {
		String qaId = put("IAM Role과 IAM Policy의 차이는?");

		JsonNode body = call(get("/api/qa/{qaId}", qaId), 200);

		assertSuccess(body);
		JsonNode result = body.get("result");
		assertThat(result.propertyNames()).containsExactlyInAnyOrder("qaId", "question", "answer", "domain", "tags",
				"source", "verification", "sourceUrl", "embeddingStatus", "latestFeedback", "createdAt", "updatedAt");
		assertThat(result.get("sourceUrl").isNull()).isTrue();
		assertThat(result.get("latestFeedback").isNull()).isTrue();
		assertThat(result.get("source").asString()).isEqualTo("EXTERNAL_AI");
		assertThat(result.get("verification").asString()).isEqualTo("UNVERIFIED");
		assertThat(result.get("tags").get(0).asString()).isEqualTo("iam");
		assertThat(result.get("updatedAt").asString()).isEqualTo("2026-11-27T07:12:30.000Z");
	}

	@Test
	void ratesRecord() throws Exception {
		String qaId = put("q");

		JsonNode body = call(feedback(qaId, "{\"rating\":\"UNDERSTOOD\"}"), 200);

		assertSuccess(body);
		JsonNode result = body.get("result");
		assertThat(result.propertyNames()).containsExactlyInAnyOrder("qaId", "latestFeedback", "updatedAt");
		assertThat(result.get("latestFeedback").asString()).isEqualTo("UNDERSTOOD");
		assertThat(result.get("updatedAt").asString()).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z");
		assertThat(call(get("/api/qa/{qaId}", qaId), 200).get("result").get("latestFeedback").asString())
			.isEqualTo("UNDERSTOOD");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"rating\":null}", "{\"rating\":\"\"}", "{\"rating\":\"GOOD\"}",
			"{\"rating\":\"understood\"}", "{\"rating\":0}", "{\"rating\":[]}", "{\"rating\":", "null", "" })
	void rejectsInvalidRatingBody(String json) throws Exception {
		String qaId = put("q");

		assertFailure(call(feedback(qaId, json), 400), "COMMON_400");
	}

	@Test
	void deletesRecordWith200() throws Exception {
		String qaId = put("q");

		JsonNode body = call(delete("/api/qa/{qaId}", qaId), 200);

		assertSuccess(body);
		assertThat(body.get("result").get("qaId").asString()).isEqualTo(qaId);
		assertThat(body.get("result").get("deleted").asBoolean()).isTrue();
		assertFailure(call(delete("/api/qa/{qaId}", qaId), 404), "QA_001");
		assertFailure(call(get("/api/qa/{qaId}", qaId), 404), "QA_001");
	}

	@Test
	void otherUsersRecordIsNotFound() throws Exception {
		String qaId = put("q");
		when(currentUser.currentUserId()).thenReturn(Ulid.next());

		assertFailure(call(get("/api/qa/{qaId}", qaId), 404), "QA_001");
		assertFailure(call(feedback(qaId, "{\"rating\":\"WRONG\"}"), 404), "QA_001");
		assertFailure(call(delete("/api/qa/{qaId}", qaId), 404), "QA_001");
		assertThat(call(get("/api/qa"), 200).get("result").get("items")).isEmpty();

		when(currentUser.currentUserId()).thenReturn(userId);
		assertThat(call(get("/api/qa/{qaId}", qaId), 200).get("result").get("latestFeedback").isNull()).isTrue();
	}

	@Test
	void frameworkErrorsUseBaseResponse() throws Exception {
		assertFailure(call(get("/api/unknown"), 404), "COMMON_404");
		assertFailure(call(post("/api/qa"), 405), "COMMON_405");
		assertFailure(call(patch("/api/qa/x/feedback").contentType(MediaType.TEXT_PLAIN).content("x"), 415),
				"COMMON_415");
		assertFailure(call(get("/api/qa").accept(MediaType.TEXT_XML), 406), "COMMON_406");
	}

	// 전역 예외 처리를 추가해도 MCP 엔드포인트는 그대로 동작해야 한다.
	@Test
	void mcpEndpointStillWorks() throws Exception {
		JsonNode body = call(post("/mcp").contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"), 200);

		assertThat(body.get("result").get("tools").toString()).contains("save_qa", "rate_answer", "delete_qa");
	}

	@Test
	void apiDocsDescribeQaApi() throws Exception {
		JsonNode docs = call(get("/v3/api-docs"), 200);

		assertThat(docs.get("info").get("title").asString()).isEqualTo("AWS Study MCP API");

		assertThat(docs.get("paths").propertyNames()).containsExactlyInAnyOrder("/api/qa", "/api/qa/{qaId}",
				"/api/qa/{qaId}/feedback");
		JsonNode response = docs.get("components").get("schemas").get("BaseResponseCursorPageResQaSummary");
		assertThat(response.get("properties").propertyNames()).containsExactlyInAnyOrder("isSuccess", "code",
				"message", "result");
		// QaControllerDoc에 적은 설명이 문서에 반영되어야 한다.
		JsonNode list = docs.get("paths").get("/api/qa").get("get");
		assertThat(list.get("summary").asString()).isEqualTo("목록 조회");
		assertThat(list.get("tags").get(0).asString()).isEqualTo("QA");
		list.get("parameters").forEach(parameter -> assertThat(parameter.has("description")).isTrue());
		assertThat(list.get("parameters")).hasSize(3);
	}

	private String put(String question) {
		QaItem item = QaItem.create(userId, Ulid.next(), Domain.SECURITY, Instant.parse("2026-11-27T07:12:30Z"));
		item.setQuestion(question);
		item.setAnswer("a");
		item.setTags(List.of("iam"));
		item.setSource(Source.EXTERNAL_AI);
		item.setVerification(Verification.UNVERIFIED);
		table.putItem(item);
		return item.getQaId();
	}

	private static MockHttpServletRequestBuilder feedback(String qaId, String json) {
		return patch("/api/qa/{qaId}/feedback", qaId).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private JsonNode call(MockHttpServletRequestBuilder request, int status) throws Exception {
		var response = mvc.perform(request).andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(status);
		return jsonMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
	}

	private static void assertSuccess(JsonNode body) {
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("isSuccess", "code", "message", "result");
		assertThat(body.get("isSuccess").asBoolean()).isTrue();
		assertThat(body.get("code").asString()).isEqualTo("COMMON_200");
		assertThat(body.get("message").asString()).isEqualTo("요청에 성공했습니다.");
	}

	private static void assertFailure(JsonNode body, String code) {
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("isSuccess", "code", "message", "result");
		assertThat(body.get("isSuccess").asBoolean()).isFalse();
		assertThat(body.get("code").asString()).isEqualTo(code);
		assertThat(body.get("message").asString()).isNotBlank();
		assertThat(body.get("result").isNull()).isTrue();
	}

}
