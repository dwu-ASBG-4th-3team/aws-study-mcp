# 의미 검색 API 명세 (MCP-004, REST-005)

> 담당: ④ 의미 검색 · 대상: ① MCP 서버, ⑤ AI 응답(`ask_study`), ⑦ 웹
> 단계: G1 · 임베딩 `gemini-embedding-2` 768차원 (설정은 [embedding.md](embedding.md))
> 표시 ⚠️ = 팀 확인이 필요한 결정

## 1. 공통 규칙

| 항목 | 내용 |
| --- | --- |
| 검색 대상 | 로그인한 사용자 본인의 QA만. MESSAGE·SESSION은 인덱싱하지 않음 |
| 사용자 식별 | 입력으로 받지 않음. 서버가 토큰의 `sub`(=userId)로 모든 벡터 검색에 필터를 강제 |
| 원문 | S3 Vectors에는 원문을 두지 않음. 검색 후 DynamoDB에서 다시 읽어 응답 (DynamoDB가 Source of Truth) |
| 정렬 | 유사도 높은 순 |
| 유사도 수치 | 응답에 넣지 않음 (웹 "유사도 표시" 기능 제거에 맞춤). 대신 서버가 최소 유사도 미만 결과를 버림 |
| 결과 없음 | 오류가 아니라 빈 배열 |

## 2. 입력

MCP와 REST가 같은 값을 받고, 검증은 `SearchQuery.of(..)` 한 곳에서 합니다.

| 필드 | 타입 | 필수 | 기본 | 규칙 |
| --- | --- | --- | --- | --- |
| `query` | string | O | - | 앞뒤 공백 제거 후 1~1000자 |
| `topK` | int | X | 5 | 1~10 |
| `domain` | enum | X | 전체 | `CONCEPTS`, `SECURITY`, `TECH`, `BILLING`, `UNCLASSIFIED` |
| `feedback` | enum | X | 전체 | `UNDERSTOOD`, `CONFUSED`, `WRONG` (최근 이해도) |

## 3. 출력

```json
{
  "results": [
    {
      "qaId": "01JD...",
      "question": "S3와 EBS는 어떤 차이가 있나요?",
      "answerPreview": "S3는 객체 스토리지이고 EBS는 EC2에 붙는 블록 스토리지입니다. ...",
      "domain": "TECH",
      "tags": ["s3", "ebs"],
      "latestFeedback": "CONFUSED",
      "verification": "UNVERIFIED",
      "createdAt": "2026-11-27T07:12:30.123Z"
    }
  ]
}
```

| 필드 | 설명 |
| --- | --- |
| `question` | 질문 원문 |
| `answerPreview` | 답변 앞 300자. 전체 답변이 필요하면 `qaId`로 다시 조회 |
| `latestFeedback` | 사용자가 남긴 최근 이해도. 없으면 `null` |
| `verification` | `UNVERIFIED` / `VERIFIED` |
| `createdAt` | ISO-8601 UTC |

## 4. MCP-004 `search_past_qa`

- 도구 설명(안): "사용자가 예전에 저장한 AWS 학습 Q&A 중 지금 질문과 의미가 비슷한 기록을 찾는다. 같은 개념을 다시 물을 때, 예전에 헷갈려 한 내용을 떠올릴 때 호출한다."
- 파라미터: 2번 표와 같음
- 성공: `CallToolResult` 텍스트에 3번 JSON (`QaTools`와 같은 방식)
- 실패: `isError=true`, 텍스트 `[코드] 메시지` (`ApiException.toToolMessage()`)

## 5. REST-005 `POST /api/search`

- 인증: `Authorization: Bearer <Cognito JWT>`
- 요청 본문: 2번 필드를 JSON으로
- 응답: `200` + 3번 JSON
- GET 쿼리 문자열 대신 POST 본문을 씁니다. 질문 원문이 CloudFront·ALB 접근 로그와 브라우저 기록에 남지 않게 하려는 것입니다.
- ⚠️ 오류 응답 본문 형식은 REST 공통 규칙이 아직 없습니다. `{ "code": "...", "message": "..." }`를 제안합니다 (①과 확정).

## 6. 오류

| 상황 | 코드 | HTTP | 비고 |
| --- | --- | --- | --- |
| 입력 규칙 위반 | `VALIDATION_FAILED` | 400 | 메시지에 어떤 필드인지 표시 |
| 토큰 없음·만료 | `UNAUTHORIZED` | 401 | |
| Gemini 호출 한도(429) | `RATE_LIMITED` | 429 | 잠시 뒤 재시도 |
| Gemini 5xx·시간 초과·네트워크, S3 Vectors 일시 오류 | `SEARCH_UNAVAILABLE` | 503 | 잠시 뒤 재시도 |
| 설정 오류(키 없음·잘못된 키·차원 불일치) | `INTERNAL_ERROR` | 500 | 원인은 로그에만 남김 |

`EmbeddingException.isRetryable()`이 true면 429 또는 503, false면 500입니다. AWS·Gemini 오류 원문은 응답에 넣지 않습니다.

## 7. 처리 흐름

1. 토큰에서 userId 확인 → 입력 검증
2. `EmbeddingProvider.embedQuery(query)` → 768차원 벡터
3. S3 Vectors `QueryVectors` (인덱스 `qa-index-gemini-v1`)
   - `topK = 요청 topK × 2` (최대 20). 5번 단계에서 빠지는 결과를 메우려고 더 가져옵니다.
   - 필터: `{"$and": [{"userId": {"$eq": <userId>}}, {"domain": {"$eq": ..}}, {"latestFeedback": {"$eq": ..}}]}` (선택 조건은 있을 때만)
   - `returnDistance=true`, `returnMetadata=false`
4. 유사도 `1 - distance`가 최소값(`app.search.min-similarity`) 미만이면 버림
5. DynamoDB `BatchGetItem` (PK `USER#<userId>`, SK `QA#<qaId>`)으로 원문 조회
   - 없는 항목(삭제된 QA, 인덱싱만 남은 벡터)은 제외
   - 조회한 `userId`가 요청자와 다르면 제외하고 경고 로그 (벡터 필터가 잘못됐다는 신호)
6. 유사도 순서를 유지한 채 앞에서 `topK`개 반환

## 8. 삭제·오류 의심 QA 처리 기준

| 대상 | 검색(`search_past_qa`, REST-005) | AI 참고(`ask_study`) |
| --- | --- | --- |
| 삭제된 QA | 제외 (DynamoDB에 없으면 버림) | 제외 |
| 인덱싱 전 QA (`PENDING`, `PROCESSING`) | 아직 안 나옴. 보통 수 초 안에 반영 | 제외 |
| 인덱싱 실패 QA (`FAILED`) | 안 나옴. 재처리 후 반영 | 제외 |
| 오류 의심 QA (`latestFeedback=WRONG`) | **포함**하고 `latestFeedback`으로 표시. 사용자가 다시 확인할 기록이기 때문 | **제외** (`excludeSuspectedWrong=true`). 틀린 답변이 새 답변에 섞이지 않게 함 |
| 헷갈림 (`CONFUSED`) | 포함 | 포함 |

- QA 삭제는 DynamoDB에서 완전 삭제입니다. 벡터 삭제는 비동기라서 잠시 벡터만 남을 수 있지만, 7-5번 단계에서 걸러져 결과에 나오지 않습니다.
- `latestFeedback`은 `rate_answer` 때마다 벡터 메타데이터에도 반영합니다 (INT 명세의 `UPDATE_META`). 반영 전에는 7-3번 필터가 예전 값으로 걸릴 수 있으므로, 7-5번에서 DynamoDB 값으로 한 번 더 확인합니다.

## 9. 벡터 메타데이터 (인덱스 `qa-index-gemini-v1`)

| 키 | 타입 | 필터 | 값 |
| --- | --- | --- | --- |
| (벡터 key) | string | - | `qaId` |
| `userId` | string | O | Cognito `sub` |
| `domain` | string | O | `Domain` enum 이름 |
| `latestFeedback` | string | O | `Rating` enum 이름. 없으면 키 생략 |
| `verification` | string | O | `Verification` enum 이름 |
| `createdAt` | number | O | epoch 초 |
| `questionPreview` | string | X | 질문 앞 200자 (디버깅용, 응답에는 DynamoDB 값 사용) |

인수인계 초안의 `feedbackStatus`, `verificationStatus`는 DynamoDB 필드 이름에 맞춰 `latestFeedback`, `verification`으로 바꿨습니다.

## 10. Service 인터페이스 (⑤ `ask_study`용)

```java
public interface SearchService {
    SearchResult search(String userId, SearchQuery query);
}

// MCP·REST: 사용자가 입력한 그대로
SearchQuery.of(query, topK, domain, feedback);

// ask_study: 오류 의심 QA 제외
SearchQuery.of(question, 3, null, null).excludingSuspectedWrong();
```

- 코드: `search/SearchService.java`, `search/SearchQuery.java`, `search/SearchHit.java`, `search/SearchResult.java`
- ⑤는 검색 결과의 `answerPreview`만으로 부족하면 `qaId`로 `QaRepository.find`를 호출해 전체 답변을 씁니다.
- 검색이 실패해도(`RATE_LIMITED`, `SEARCH_UNAVAILABLE`) `ask_study`는 참고 기록 없이 답변을 이어가는 것을 권장합니다.

## 11. 결정 필요 ⚠️

| 항목 | 제안 | 상대 |
| --- | --- | --- |
| 최소 유사도 값 | 평가 질문셋에서 관계없는 질문의 최고 유사도보다 조금 높게. 평가 전에는 0 (필터 없음) | ④ 평가 후 |
| REST 오류 본문 형식 | `{ code, message }` | ① |
| REST 경로 | `POST /api/search` | ①, ⑦ |
| `ask_study`에서 오류 의심 QA 제외 | 제외 | ⑤ |
| 검색 호출 제한 | Gemini 무료 등급 분당 한도 확인 후 사용자별 분당 제한 검토 | ②, ④ |
