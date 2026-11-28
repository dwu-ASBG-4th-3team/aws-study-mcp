package com.asbg.awsstudymcp.search;

import java.util.List;

/** 유사도 높은 순. 결과가 없으면 빈 목록. */
public record SearchResult(List<SearchHit> results) {

	public SearchResult {
		results = results == null ? List.of() : List.copyOf(results);
	}

	public static SearchResult empty() {
		return new SearchResult(List.of());
	}

}
