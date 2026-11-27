package com.asbg.awsstudymcp.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;

class SearchQueryTest {

	@Test
	void topK를_생략하면_5이고_질문_앞뒤_공백을_지운다() {
		SearchQuery query = SearchQuery.of("  S3와 EBS 차이  ", null, Domain.TECH, null);

		assertThat(query.query()).isEqualTo("S3와 EBS 차이");
		assertThat(query.topK()).isEqualTo(5);
		assertThat(query.domain()).isEqualTo(Domain.TECH);
		assertThat(query.excludeSuspectedWrong()).isFalse();
	}

	@Test
	void 빈_질문과_너무_긴_질문은_거부한다() {
		assertInvalid(() -> SearchQuery.of(" ", null, null, null));
		assertInvalid(() -> SearchQuery.of(null, null, null, null));
		assertInvalid(() -> SearchQuery.of("a".repeat(1_001), null, null, null));
		assertThat(SearchQuery.of("a".repeat(1_000), null, null, null).query()).hasSize(1_000);
	}

	@Test
	void topK는_1에서_10까지() {
		assertInvalid(() -> SearchQuery.of("q", 0, null, null));
		assertInvalid(() -> SearchQuery.of("q", 11, null, null));
		assertThat(SearchQuery.of("q", 10, null, null).topK()).isEqualTo(10);
	}

	@Test
	void ask_study용은_오류_의심_QA를_뺀다() {
		SearchQuery query = SearchQuery.of("q", 3, null, null).excludingSuspectedWrong();

		assertThat(query.excludeSuspectedWrong()).isTrue();
		assertThat(query.topK()).isEqualTo(3);
		assertInvalid(() -> SearchQuery.of("q", 3, null, Rating.WRONG).excludingSuspectedWrong());
	}

	@Test
	void 답변_미리보기는_300자() {
		assertThat(SearchHit.preview("a".repeat(500))).hasSize(300);
		assertThat(SearchHit.preview("짧은 답")).isEqualTo("짧은 답");
		assertThat(SearchHit.preview(null)).isEmpty();
	}

	@Test
	void 결과_목록은_null이면_빈_목록() {
		assertThat(new SearchResult(null).results()).isEmpty();
		assertThat(new SearchHit("id", "q", "a", Domain.TECH, null, null, null, null).tags()).isEqualTo(List.of());
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
	}

}
