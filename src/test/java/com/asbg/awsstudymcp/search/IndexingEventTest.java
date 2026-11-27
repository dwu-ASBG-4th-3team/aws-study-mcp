package com.asbg.awsstudymcp.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class IndexingEventTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void 명세와_같은_JSON으로_직렬화된다() {
		IndexingEvent event = IndexingEvent.of(IndexingEvent.Op.UPSERT, "user-1", "qa-1",
				Instant.parse("2026-11-27T07:12:30.123Z"));

		JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

		assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
		assertThat(json.get("eventId").asString()).hasSize(26);
		assertThat(json.get("op").asString()).isEqualTo("UPSERT");
		assertThat(json.get("userId").asString()).isEqualTo("user-1");
		assertThat(json.get("qaId").asString()).isEqualTo("qa-1");
		assertThat(json.get("requestedAt").asString()).isEqualTo("2026-11-27T07:12:30.123Z");
		assertThat(json.properties()).hasSize(6);
	}

	@Test
	void JSON에서_다시_읽을_수_있다() {
		IndexingEvent event = IndexingEvent.of(IndexingEvent.Op.DELETE, "user-1", "qa-1", Instant.now());

		IndexingEvent read = mapper.readValue(mapper.writeValueAsString(event), IndexingEvent.class);

		assertThat(read).isEqualTo(event);
	}

	@Test
	void 키가_없으면_만들_수_없다() {
		assertThatThrownBy(() -> IndexingEvent.of(IndexingEvent.Op.UPSERT, " ", "qa-1", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> IndexingEvent.of(null, "user-1", "qa-1", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
