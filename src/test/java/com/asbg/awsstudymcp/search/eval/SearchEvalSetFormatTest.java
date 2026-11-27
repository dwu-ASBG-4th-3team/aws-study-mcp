package com.asbg.awsstudymcp.search.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

// 질문셋 템플릿이 평가 코드에서 읽히는지 확인한다. Gemini를 호출하지 않는다.
class SearchEvalSetFormatTest {

	@Test
	void 템플릿을_읽을_수_있고_도메인_제목이_있다() {
		SearchEvaluationTest.EvalSet set = SearchEvaluationTest.read(Path.of("docs/search-eval-template.json"));

		assertThat(set.items()).isNotEmpty();
		assertThat(set.unrelated()).isNotEmpty();
		set.items().forEach(item -> {
			assertThat(item.variants()).isNotEmpty();
			assertThat(SearchEvaluationTest.DOMAIN_TITLES).containsKey(item.domain());
		});
	}

	@Test
	void cosine_유사도() {
		assertThat(SearchEvaluationTest.cosine(java.util.List.of(1f, 0f), java.util.List.of(1f, 0f))).isEqualTo(1.0);
		assertThat(SearchEvaluationTest.cosine(java.util.List.of(1f, 0f), java.util.List.of(0f, 1f))).isEqualTo(0.0);
	}

}
