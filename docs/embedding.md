# 임베딩 (Gemini)

의미 검색(`search_past_qa`)과 QA 인덱싱에서 쓰는 임베딩 설정입니다. 코드는 `search/embedding` 패키지에 있습니다.

## 설정

| 항목 | 값 | 환경변수 |
| --- | --- | --- |
| 모델 | `gemini-embedding-2` | `GEMINI_EMBEDDING_MODEL` |
| 출력 차원 | 768 (S3 Vectors 인덱스 `qa-index-gemini-v1`과 같아야 함) | `GEMINI_EMBEDDING_DIM` |
| API Key | Secrets Manager에서 태스크 환경변수로 주입 | `GEMINI_API_KEY` |
| 거리 | cosine (정규화는 모델이 처리) | - |

`GEMINI_API_KEY`가 비어 있으면 `EmbeddingProvider` 빈을 만들지 않습니다. 키 없이도 로컬 실행과 CI는 동작합니다.

## 입력 형식

gemini-embedding-2는 `task_type` 파라미터가 없어서 접두어로 용도를 지정합니다.

| 용도 | 메서드 | 모델 입력 |
| --- | --- | --- |
| 검색 질문 | `embedQuery(query)` | `task: search result \| query: {질문}` |
| 저장 QA | `embedDocument(title, text)` | `title: {도메인} \| text: {질문}` (제목이 없으면 `none`) |

## 주의

- 한 요청에 텍스트를 여러 개 넣으면 임베딩 하나로 합쳐집니다. 일괄 인덱싱도 QA마다 1건씩 호출합니다.
- 모델이나 차원을 바꾸면 기존 벡터와 호환되지 않아 전체 재임베딩이 필요합니다. QA에 `embeddingModel`을 함께 기록합니다.
- SDK 자체 재시도는 끕니다. `EmbeddingException.isRetryable()`이 true(429, 408, 5xx, 네트워크 오류)면 호출하는 쪽에서 재시도합니다. 인덱싱은 SQS 재처리, 검색 API는 `RETRY_LATER`로 응답합니다.
- ECS·Lambda에서 Gemini API(외부 HTTPS)를 호출하려면 NAT Gateway가 필요합니다.

## 실제 호출 확인

```sh
GEMINI_API_KEY=... ./gradlew test --tests '*GeminiEmbeddingLiveTest'
```

키가 없으면 이 테스트는 건너뜁니다.
