package com.asbg.awsstudymcp.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.common.Ulid;
import com.asbg.awsstudymcp.common.pagination.CursorPageRes;
import com.asbg.awsstudymcp.qa.dto.res.QaSummary;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.support.LocalDynamoDb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

class QaListTest {

	private static final Instant BASE = Instant.parse("2026-11-27T07:00:00Z");

	private static DynamoDbTable<QaItem> table;
	private static QaService service;

	@BeforeAll
	static void startDynamoDb() {
		var dynamoDb = LocalDynamoDb.client();
		var client = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
		table = client.table("qa-list-test", TableSchema.fromBean(QaItem.class));
		LocalDynamoDb.createTable(table);
		service = new QaService(new DynamoDbQaRepository(client, dynamoDb, table));
	}

	@Test
	void listsAllDomainsNewestFirst() {
		String user = Ulid.next();
		List<String> saved = new ArrayList<>();
		for (Domain domain : Domain.values()) {
			saved.add(put(user, domain, saved.size(), "a"));
		}

		CursorPageRes<QaSummary> response = service.list(user, null, null, null);

		assertThat(response.items()).extracting(QaSummary::qaId).containsExactlyElementsOf(saved.reversed());
		assertThat(response.nextCursor()).isNull();
		assertThat(response.items().getLast().createdAt()).isEqualTo("2026-11-27T07:00:00.000Z");
	}

	@ParameterizedTest
	@EnumSource(Domain.class)
	void filtersByDomainNewestFirst(Domain domain) {
		String user = Ulid.next();
		String older = put(user, domain, 0, "a");
		put(user, domain == Domain.TECH ? Domain.BILLING : Domain.TECH, 1, "a");
		String newer = put(user, domain, 2, "a");

		assertThat(service.list(user, domain, null, null).items()).extracting(QaSummary::qaId)
			.containsExactly(newer, older);
	}

	@ParameterizedTest
	@NullSource
	@EnumSource(value = Domain.class, names = "SECURITY")
	void emptyListHasNoCursor(Domain domain) {
		CursorPageRes<QaSummary> response = service.list(Ulid.next(), domain, null, null);

		assertThat(response.items()).isEmpty();
		assertThat(response.nextCursor()).isNull();
	}

	@ParameterizedTest
	@NullSource
	@EnumSource(value = Domain.class, names = "SECURITY")
	void cursorReadsEverythingWithoutDuplicates(Domain domain) {
		String user = Ulid.next();
		List<String> saved = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			saved.add(put(user, Domain.SECURITY, i, "a"));
		}

		// 나누어떨어지지 않는 경우, 딱 떨어지는 경우, 한 개씩
		for (int limit : new int[] { 3, 7, 1 }) {
			assertThat(readAll(user, domain, limit)).containsExactlyElementsOf(saved.reversed());
		}
	}

	// 항목이 커서 한 번의 Query(1MB)로 다 읽지 못하는 경우
	@ParameterizedTest
	@NullSource
	@EnumSource(value = Domain.class, names = "TECH")
	void fillsPageBeyondQuerySizeLimit(Domain domain) {
		String user = Ulid.next();
		String answer = "가".repeat(QaService.MAX_ANSWER_LENGTH);
		List<String> saved = new ArrayList<>();
		for (int i = 0; i < 55; i++) {
			saved.add(put(user, Domain.TECH, i, answer));
		}

		CursorPageRes<QaSummary> first = service.list(user, domain, 50, null);
		CursorPageRes<QaSummary> second = service.list(user, domain, 50, first.nextCursor());

		assertThat(first.items()).hasSize(50);
		assertThat(second.nextCursor()).isNull();
		assertThat(concat(first, second)).containsExactlyElementsOf(saved.reversed());
	}

	@Test
	void sameCreatedAtInDomainIsNotSkipped() {
		String user = Ulid.next();
		List<String> saved = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			saved.add(put(user, Domain.BILLING, 0, "a"));
		}

		assertThat(readAll(user, Domain.BILLING, 1)).containsExactlyInAnyOrderElementsOf(saved);
	}

	@Test
	void defaultLimitIsTwenty() {
		String user = Ulid.next();
		for (int i = 0; i < 21; i++) {
			put(user, Domain.TECH, i, "a");
		}

		CursorPageRes<QaSummary> response = service.list(user, null, null, null);

		assertThat(response.items()).hasSize(20);
		assertThat(response.nextCursor()).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0, 51 })
	void rejectsLimitOutOfRange(int limit) {
		assertInvalid(() -> service.list("user-a", null, limit, null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "!!!!", "QUxMfGF8YnxjCg" })
	void rejectsMalformedCursor(String cursor) {
		assertInvalid(() -> service.list("user-a", null, null, cursor));
	}

	@Test
	void rejectsTamperedCursor() {
		String user = Ulid.next();
		put(user, Domain.TECH, 0, "a");
		put(user, Domain.TECH, 1, "a");
		String cursor = service.list(user, null, 1, null).nextCursor();
		char replaced = cursor.charAt(12) == 'A' ? 'B' : 'A';
		String tampered = cursor.substring(0, 12) + replaced + cursor.substring(13);

		assertInvalid(() -> service.list(user, null, 1, tampered));
		assertInvalid(() -> service.list(user, null, 1, cursor + "A"));
		assertInvalid(() -> service.list(user, null, 1, "A".repeat(500)));
	}

	@Test
	void rejectsCursorFromOtherDomainOrUser() {
		String user = Ulid.next();
		put(user, Domain.TECH, 0, "a");
		put(user, Domain.TECH, 1, "a");
		String all = service.list(user, null, 1, null).nextCursor();
		String tech = service.list(user, Domain.TECH, 1, null).nextCursor();

		assertInvalid(() -> service.list(user, Domain.TECH, 1, all));
		assertInvalid(() -> service.list(user, null, 1, tech));
		assertInvalid(() -> service.list(user, Domain.SECURITY, 1, tech));
		assertInvalid(() -> service.list(Ulid.next(), null, 1, all));
		assertInvalid(() -> service.list(Ulid.next(), Domain.TECH, 1, tech));
	}

	@ParameterizedTest
	@NullSource
	@EnumSource(value = Domain.class, names = "TECH")
	void listsOnlyOwnRecords(Domain domain) {
		String mine = Ulid.next();
		String qaId = put(mine, Domain.TECH, 0, "a");
		put(Ulid.next(), Domain.TECH, 1, "a");

		assertThat(service.list(mine, domain, null, null).items()).extracting(QaSummary::qaId).containsExactly(qaId);
	}

	private static String put(String userId, Domain domain, int seconds, String answer) {
		QaItem item = QaItem.create(userId, Ulid.next(), domain, BASE.plusSeconds(seconds));
		item.setQuestion("q" + seconds);
		item.setAnswer(answer);
		table.putItem(item);
		return item.getQaId();
	}

	private static List<String> readAll(String userId, Domain domain, int limit) {
		List<String> result = new ArrayList<>();
		String cursor = null;
		do {
			CursorPageRes<QaSummary> page = service.list(userId, domain, limit, cursor);
			assertThat(page.items()).hasSizeBetween(1, limit);
			page.items().forEach(item -> result.add(item.qaId()));
			cursor = page.nextCursor();
		}
		while (cursor != null);
		return result;
	}

	@SafeVarargs
	private static List<String> concat(CursorPageRes<QaSummary>... pages) {
		List<String> result = new ArrayList<>();
		for (CursorPageRes<QaSummary> page : pages) {
			page.items().forEach(item -> result.add(item.qaId()));
		}
		return result;
	}

	private static void assertInvalid(ThrowingCallable call) {
		assertThatThrownBy(call)
			.isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
	}

}
