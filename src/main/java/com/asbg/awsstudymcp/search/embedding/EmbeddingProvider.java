package com.asbg.awsstudymcp.search.embedding;

import java.util.List;

/**
 * 텍스트를 검색용 벡터로 바꾼다. 검색 질문과 저장 QA는 모델 입력 형식이 달라서 메서드를 나눈다.
 * 모델이나 차원이 바뀌면 기존 벡터와 호환되지 않으므로 저장할 때 {@link #modelId()}를 함께 기록한다.
 */
public interface EmbeddingProvider {

	/** 사용자가 검색창에 입력한 질문을 임베딩한다. */
	List<Float> embedQuery(String query);

	/**
	 * 저장된 QA를 인덱싱용으로 임베딩한다. 한 번에 QA 하나만 받는다.
	 * @param title 문서 제목 역할을 하는 값(CLF 도메인 등). 없으면 null
	 * @param text 임베딩할 본문(QA 질문)
	 */
	List<Float> embedDocument(String title, String text);

	String modelId();

	int dimension();

}
