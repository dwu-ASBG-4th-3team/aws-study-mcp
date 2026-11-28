# **ERD 상세 명세서**

- **연관관계 명세**
    
    
    | 부모 | 자식 | 관계 | 참조 속성 | 참조 조건 | ERD 선 |
    | --- | --- | --- | --- | --- | --- |
    | `USER` | `SESSION` | 1 : 0..N | `SESSION.userId` | 필수 | 점선 (비식별) |
    | `USER` | `MESSAGE` | 1 : 0..N | `MESSAGE.userId` | 필수 | 점선 |
    | `USER` | `QA` | 1 : 0..N | `QA.userId` | 필수 | 점선 |
    | `USER` | `FEEDBACK_HISTORY` | 1 : 0..N | `FEEDBACK_HISTORY.userId` | 필수 | 실선 (식별, PK 일부) |
    | `USER` | `IDEMPOTENCY` | 1 : 0..N | `IDEMPOTENCY.userId` | 필수 | 실선 (식별, PK 일부) |
    | `SESSION` | `MESSAGE` | 1 : 0..N | `MESSAGE.sessionId` | 필수 | 점선 |
    | `SESSION` | `QA` | 0..1 : 0..N | `QA.sessionId` | 선택 (출처 정보) | 점선 |
    | `QA` | `FEEDBACK_HISTORY` | 1 : 0..N | `FEEDBACK_HISTORY.qaId` | 필수 |  |
- 본 ERD는 DynamoDB Single Table 구조의 논리적 데이터 모델을 표현한다.
- PK·FK는 논리적 식별자 및 참조 관계를 의미한다.
- QA의 `sessionId`는 비강제 출처 참조이며, 세션 삭제 시에도 QA는 유지된다.
- IDEMPOTENCY의 `qaId`는 선택적 참조이며, `qaIds`는 복수 QA 저장 결과를 관리하기 위한 목록이다.
- 세부 삭제 및 데이터 정합성 정책은 ERD 상세 명세서를 따른다.

## **설계 결정 사항**

대화(MESSAGE)와 기록(QA)을 분리하고, 기록은 사용자가 "기록으로 저장"을 누를 때 마지막 저장 이후의 대화를 요약해 만든다.

| 항목 | 결정 내용 |
| --- | --- |
| SESSION 데이터 모델 | 기존 DynamoDB 테이블에 `SESSION#`, `MSG#` 레코드 추가 |
| 세션별 대화 조회 | 원본 테이블에서 `MSG#{sessionId}#` prefix로 조회, 별도 GSI 미사용 |
| 세션 목록 정렬 | 서버에서 `updatedAt` 기준 내림차순 정렬 |
| 세션 제목 | 기본값 `새로운 대화`(UI 문구와 통일), 최초 MESSAGE 저장 시 질문 앞 30자로 자동 변경, 사용자 수정 시 1~50자 |
| AI 대화 문맥 | 동일 세션의 최근 MESSAGE 최대 5개와 기존 의미 검색 결과 활용 |
| 기록 생성 | 사용자가 "기록으로 저장"을 누르면 마지막 기록 저장 이후의 대화를 AI가 요약해 QA 생성 |
| 참고 기록 수 | MESSAGE 및 QA의 생성 당시 참고한 과거 기록 개수를 저장하며, 이후 참고 기록이 삭제되어도 갱신하지 않음 |
| 삭제 | 기록 삭제와 세션 삭제는 서로 영향을 주지 않음 |
| 내부 AI 생성 기록 출처 | `INTERNAL_AI` 사용. 특정 모델이나 제공자에 종속되지 않도록 설계 |
| S3 분석 결과 | 사용자별 `latest.json` 저장, 하루 1회 갱신 |

## **1. 공통 기준**

- DynamoDB Single Table 구조를 사용한다.
- ERD의 엔티티는 논리적으로 구분한 것이며, 실제 DynamoDB 테이블은 1개로 구성한다.
- ERD의 PK·FK는 논리적 식별자 및 참조 관계를 의미하며, 실제 DynamoDB의 PK·SK·GSI 구조는 DynamoDB 데이터 모델 명세서를 따른다.
- ERDCloud의 SQL 데이터 타입은 시각화를 위한 표기이며, 실제 저장 시에는 DynamoDB 데이터 타입(S, N, L 등)을 사용한다.
- 사용자 식별자는 Cognito의 `sub`를 사용한다.
- 서버는 검증된 Cognito 인증 토큰에서 `sub`를 추출하여 `userId`로 사용하며, 클라이언트로부터 사용자 ID를 직접 전달받지 않는다.
- 모든 사용자 종속 엔티티는 논리적으로 USER를 직접 참조한다.
- 엔티티 간 참조 관계 및 사용자 소유권은 애플리케이션에서 검증한다. 단, QA의 출처 세션은 생성 시점에만 존재 여부 및 소유권을 검증한다.
- Q&A, 세션 및 메시지 식별자는 ULID를 사용하며, 논리적 식별자의 고유성을 보장한다.
- 모든 시각은 UTC, ISO 8601 형식(`2026-11-24T05:55:00.000Z`)으로 저장하고 전달한다. 단, TTL은 Unix Epoch Seconds를 사용한다.
- 정기 학습 분석 배치의 실행 시각은 KST 기준으로 정의하며, 스케줄러의 시간대 설정에 맞춰 실행 시각을 적용한다.
- 분석 배치의 실행 시각 및 분석 결과 생성 시각은 UTC, ISO 8601 형식으로 저장한다.
- KST 변환 및 화면 표시는 프론트엔드에서 처리한다.
- 학습 분석 결과는 DynamoDB에 저장하지 않고 S3에서 별도로 관리한다.
- 대화(MESSAGE)와 학습 기록(QA)은 독립적으로 관리한다.
- 세션 단건 삭제 기능을 개발 범위에 포함한다.
- 전체 학습 기록 일괄 삭제 및 기록 내보내기 기능은 본 프로젝트의 개발 범위에서 제외한다.

## **2. 엔티티 구성**

| 엔티티 | 논리명 | 용도 |
| --- | --- | --- |
| `USER` | 사용자 | Cognito 기반 사용자 식별 및 소유 관계 표현 |
| `SESSION` | 대화 세션 | 웹 AI 대화 세션 관리 |
| `MESSAGE` | 대화 메시지 | 세션별 질문·답변 대화 턴 저장 |
| `QA` | 학습 기록 | 사용자별 질문·답변 학습 기록 관리 |
| `FEEDBACK_HISTORY` | 피드백 이력 | QA별 이해도 평가 이력 관리 |
| `IDEMPOTENCY` | 멱등성 키 | 동일 요청에 대한 중복 저장 방지 |

## **3. 엔티티별 상세 명세**

### **3.1. USER (사용자)**

| 속성 논리명 | 속성명 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 사용자 ID | `userId` | String | PK, 필수 | Cognito `sub` |

**운영 규칙**

- 사용자 계정 및 인증 정보는 Cognito에서 관리한다.
- USER는 사용자와 다른 엔티티 간의 소유 관계를 표현하기 위한 논리적 엔티티이다.
- DynamoDB에 별도의 USER 레코드를 생성하지 않는다.
- 서버는 검증된 Cognito 인증 토큰의 `sub`를 사용자 식별자로 사용한다.
- SESSION, MESSAGE, QA, FEEDBACK_HISTORY, IDEMPOTENCY는 각각 USER의 `userId`를 직접 참조한다.
- 모든 사용자 종속 데이터의 조회·저장·수정·삭제는 인증된 사용자 ID를 기준으로 수행한다.

### **3.2. SESSION (대화 세션)**

| 속성 논리명 | 속성명 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 세션 ID | `sessionId` | String | PK, 필수 | ULID |
| 사용자 ID | `userId` | String | FK, 필수 | `USER.userId` 직접 참조 |
| 세션 제목 | `title` | String | 필수, 1~50자 | 기본값 `새로운 대화` |
| 마지막 기록 메시지 | `lastRecordedMessageId` | String | 선택 | 기록 요약에 포함된 마지막 MESSAGE의 ID. 값이 없으면 아직 기록한 적 없음 |
| 생성 일시 | `createdAt` | String | 필수 | UTC, ISO 8601 |
| 수정 일시 | `updatedAt` | String | 필수 | 마지막 MESSAGE 저장 또는 제목 수정 시각 |

**DynamoDB 키 구조**

| 항목 | 값 |
| --- | --- |
| PK | `USER#{sub}` |
| SK | `SESSION#{sessionId}` |
| GSI | 없음 |
| TTL | 미적용 |

**운영 규칙**

- 한 사용자는 여러 개의 대화 세션을 생성할 수 있다.
- 하나의 세션은 0개 이상의 MESSAGE를 포함할 수 있다.
- 세션 생성 시 서버에서 ULID를 생성한다.
- 세션 생성 시 인증된 사용자의 `sub`를 `userId`로 저장한다.
- 사용자 본인의 세션만 생성·조회·수정·삭제할 수 있다.
- `updatedAt`은 MESSAGE 저장 또는 제목 변경 시 갱신한다. 기록 저장은 SESSION의 `updatedAt`을 갱신하지 않는다.
- 단순 세션 조회 시에는 `updatedAt`을 갱신하지 않는다.
- `lastRecordedMessageId`는 기록 저장이 성공한 경우에만 갱신한다.
- 세션 최초 생성 시 `lastRecordedMessageId`는 저장하지 않는다.
- 세션에 TTL을 적용하지 않는다.
- 웹 AI 대화 세션과 MCP 통신 프로토콜의 세션은 별개로 관리한다.
- 세션 삭제: SESSION과 해당 세션의 MESSAGE를 모두 삭제(하드 삭제)한다. QA는 영향받지 않는다.
- 세션 삭제는 사용자 본인의 세션만 가능하다.
- 기록(QA) 삭제는 SESSION 및 MESSAGE에 영향을 주지 않는다.

**세션 제목 정책**

| 항목 | 처리 기준 |
| --- | --- |
| 세션 최초 생성 | `새로운 대화` |
| 최초 MESSAGE 저장 | 기본 제목 상태인 경우 최초 질문의 앞 30자를 제목으로 설정 |
| 사용자 직접 수정 | 허용 |
| 제목 길이 | 1~50자 |
| 공백 처리 | 앞뒤 공백 제거 |
| 빈 제목 | 허용하지 않음 |
| 이후 질문 저장 | 기존 제목 유지 |
| AI 제목 생성 | 별도 모델 호출 미사용 |

**세션 목록 조회 정책**

- 인증된 사용자의 PK에서 SK가 `SESSION#`으로 시작하는 레코드를 Query한다.
- 전체 조회 결과를 서버에서 `updatedAt` 기준 내림차순으로 정렬한다.
- 수정 시각이 동일한 경우 `sessionId` 내림차순으로 정렬한다.
- 세션 목록 정렬을 위한 별도 GSI는 생성하지 않는다.
- 오늘, 어제 등 날짜별 그룹화는 프론트엔드에서 KST 기준으로 처리한다.
- 세션별 대화 개수는 해당 세션의 MESSAGE 개수를 기준으로 계산하며, 별도 집계 속성으로 저장하지 않는다.
- 마지막 기록 저장 이후 새로운 MESSAGE가 존재하는지 확인할 수 있도록 세션 상세 조회 시 관련 정보를 제공한다.
- `lastRecordedMessageId`가 없는 경우 MESSAGE가 한 건 이상 존재하면 기록 저장이 가능하다.
- `lastRecordedMessageId`가 존재하는 경우 해당 ID 이후의 MESSAGE가 존재할 때만 기록 저장이 가능하다.

### **3.3. QA (질문·답변 학습 기록)**

QA는 세션 대화와 분리된 학습 기록이며, `sessionId`는 기록이 만들어진 출처 세션을 뜻하는 참고용 값이다.

| 속성 논리명 | 속성명 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| Q&A ID | `qaId` | String | PK, 필수 | ULID |
| 사용자 ID | `userId` | String | FK, 필수 | `USER.userId` 직접 참조 |
| 출처 세션 ID | `sessionId` | String | 선택, 비강제 참조 | 기록이 만들어진 출처 SESSION의 ID |
| 질문 | `question` | String | 필수 | 질문 본문, 최대 2,000자 |
| 답변 | `answer` | String | 필수 | 답변 본문, 최대 20,000자 |
| 태그 | `tags` | List of String | 선택, 기본값 `[]` | 질문 관련 개념 태그. 최대 10개, 각 50자, 빈 값 불가 |
| 대표 태그 | `primaryTag` | String | 선택 | 반복 질문 분석 기준 |
| 도메인 | `domain` | String | 필수 | CLF 도메인 코드 |
| 출처 유형 | `source` | String | 필수 | 질문·답변의 출처 유형 |
| 검증 상태 | `verification` | String | 필수 | 출처 기반 검증 상태 |
| 출처 URL | `sourceUrl` | String | 선택 | 참고 자료 URL |
| 참고 기록 수 | `referenceCount` | Number | 필수, 기본값 0 | 답변 생성 시 참고한 과거 학습 기록 개수. 생성 당시 기준 |
| 최신 피드백 | `latestFeedback` | String | 선택 | 가장 최근 이해도 평가 |
| 임베딩 상태 | `embeddingStatus` | String | 필수 | 기본값 `PENDING` |
| 임베딩 모델 | `embeddingModel` | String | 선택 | 인덱싱에 사용한 모델 ID (예: `gemini-embedding-2`). 인덱싱 완료 시 기록 |
| 인덱싱 일시 | `indexedAt` | String | 선택 | 벡터 저장 완료 시각. UTC, ISO 8601 |
| 임베딩 시도 횟수 | `embeddingAttempts` | Number | 선택 | 재처리 시 1 증가. 5회 초과 실패 시 자동 재처리 중단 |
| 임베딩 오류 | `embeddingError` | String | 선택 | 마지막 실패의 오류 코드만 저장(메시지 본문·원문 미저장) |
| 버전 | `version` | Number | 필수 | 낙관적 락 및 이벤트 순서 관리. 최초 저장 시 1, 수정할 때마다 1 증가 |
| 생성 일시 | `createdAt` | String | 필수 | UTC, ISO 8601 |
| 수정 일시 | `updatedAt` | String | 필수 | UTC, ISO 8601 |
| GSI1 파티션 키 | `GSI1PK` | String | 필수 | `USER#{sub}#DOM#{domain}` |
| GSI1 정렬 키 | `GSI1SK` | String | 필수 | `createdAt` |

**DynamoDB 키 구조**

| 항목 | 값 |
| --- | --- |
| PK | `USER#{sub}` |
| SK | `QA#{qaId}` |
| GSI1PK | `USER#{sub}#DOM#{domain}` |
| GSI1SK | `createdAt` |
| TTL | 미적용 |

**운영 규칙**

- QA의 `userId`는 SESSION을 통해 가져오지 않고, 인증된 Cognito 토큰의 `sub`를 사용한다.
- 일반 MCP Q&A는 세션에 소속되지 않아도 된다.
- 웹 AI 대화에서 생성된 MESSAGE는 자동으로 QA에 저장하지 않는다.
- 사용자가 "기록으로 저장"을 요청하면 마지막 기록 저장 이후의 MESSAGE를 AI가 요약해 QA를 생성한다.
- `sessionId`는 기록이 만들어진 출처 세션을 뜻하는 참고용 값이다. 생성 시점에만 소유권을 검증하고, 세션이 삭제되어도 값은 유지한다.
- `sessionId`가 없는 Q&A도 독립적으로 저장 및 조회할 수 있다.
- QA 생성 시 출처 세션이 지정된 경우 해당 세션이 존재하며 인증된 사용자 본인의 세션인지 검증한다.
- 저장 이후 출처 세션의 존재 여부는 QA 조회·수정·삭제의 필수 조건으로 사용하지 않는다.
- 대표 태그는 소문자 변환 및 공백 정규화 후 저장한다.
- 최초 저장 시 `embeddingStatus`는 `PENDING`으로 설정한다.
- G1 인덱싱 기능 도입 후 기존 활성 QA 중 `PENDING` 항목을 일괄 인덱싱한다.
- 백필 대상은 `embeddingStatus != COMPLETED` 또는 `embeddingModel != 현재 모델`인 QA다. 임베딩 모델을 바꿀 때는 새 벡터 인덱스에 백필한다.
- 최신 피드백은 QA에 덮어쓰고, 변경 이력은 FEEDBACK_HISTORY에 저장한다.
- `USER` 출처 또는 출처 URL의 호스트가 `docs.aws.amazon.com`인 경우 기존 정책에 따라 `VERIFIED`로 분류한다.
- 그 외에는 `UNVERIFIED`로 분류한다.
- `source = INTERNAL_AI`는 서비스 내부 AI가 생성한 학습 기록을 뜻한다.
- 서비스 내부 AI가 대화를 요약해 생성한 기록의 `verification`은 `UNVERIFIED`다(출처 URL이 없음).
- 질문, 답변, 태그는 앞뒤 공백을 제거한 후 길이를 검증한다.
- 이해도 평가로 `latestFeedback`을 갱신할 때도 `version`이 증가한다.
- 단건 삭제 시 QA 항목을 완전 삭제하고, G1부터 벡터 삭제 이벤트를 발행한다.
- 삭제된 Q&A는 일반 기록 조회, 의미 검색 및 학습 분석 대상에서 제외한다.
- 기록 삭제는 SESSION과 MESSAGE에 영향을 주지 않는다.
- `WRONG`(오류 의심) QA는 AI 답변의 참고 자료에서 제외한다. 비슷한 과거 질문 목록에는 표시할 수 있다.
- QA에 TTL을 적용하지 않는다.

**참고 기록 수(**`referenceCount`**) 관리 정책**

- `referenceCount`는 답변 생성 시 참고한 과거 학습 기록의 개수를 의미한다.
- 0 이상의 정수로 저장하며, 참고한 기록이 없는 경우 기본값은 0이다.
- AI가 의미 검색 결과를 참고하여 답변을 생성한 경우 실제 AI 입력 문맥에 포함된 QA를 기준으로 계산한다.
- 검색 결과 중 삭제된 QA, 오류 의심 QA 및 실제 입력 문맥에 포함되지 않은 QA는 참고 기록 수에서 제외한다.
- 동일한 QA를 중복으로 참고하더라도 한 건으로 계산한다.
- 웹 대화를 요약해 QA를 생성하는 경우 이번 기록 저장 요청의 요약 대상 MESSAGE에서 참고한 과거 QA ID를 기준으로 중복을 제거하여 계산한다.
- 참고 기록 수는 원본 대화의 답변 생성 당시 정보를 기준으로 산출하며, 이후 참고 기록이 삭제되거나 변경되더라도 영향을 받지 않는다.
- 동일 기록 저장 요청에서 생성된 QA에는 해당 요청의 참고 기록 수를 저장한다.
- `referenceCount`는 각 QA의 생성 당시를 기준으로 저장한다.
- 이후 참고한 학습 기록이나 출처 세션이 삭제되더라도 `referenceCount`는 갱신하지 않는다.
- 외부 AI 또는 사용자 직접 입력 기록에서 실제 참고 기록을 확인할 수 없는 경우 0으로 저장한다.

**기록으로 저장 정책**

기록(QA)은 "기록으로 저장" 요청에서 마지막 저장 이후의 대화를 요약해 만든다.

| 항목 | 처리 기준 |
| --- | --- |
| 요청 데이터 | `sessionId`, `idempotencyKey` |
| 요약 대상 | `lastRecordedMessageId` 이후의 MESSAGE |
| 최초 기록 저장 | 해당 세션의 MESSAGE 전체를 대상으로 함 |
| 새로운 대화 없음 | "저장할 새 대화 없음" 오류 반환 |
| 생성 기록 수 | 주제별 1개 이상의 QA, 요청당 최대 개수 제한 |
| 출처 유형 | `INTERNAL_AI` |
| 검증 상태 | `UNVERIFIED` |
| 출처 세션 | 해당 `sessionId` 저장 |
| 임베딩 상태 | `PENDING` |
| 참고 기록 수 | 이번 요약 대상 MESSAGE의 참고 QA ID를 중복 제거하여 산출하고, 동일 요청에서 생성된 모든 QA에 동일한 값 저장 |
| 멱등성 | 기존 `idempotencyKey` 정책 적용 |
| 세션 수정 시각 | `updatedAt` 미갱신 |
| 마지막 기록 위치 | 저장 성공 시 `lastRecordedMessageId` 갱신 |

"기록으로 저장"을 요청하면 다음 순서로 처리한다.

1. 사용자 인증 후 동일한 유효 멱등성 키로 처리된 요청이 있는지 확인한다. 동일 요청의 기존 처리 결과가 존재하면 QA 존재 여부 및 소유권을 확인하여 반환한다. 신규 요청인 경우 세션 존재 여부 및 소유권을 검증하고, SESSION을 강한 일관성으로 조회하여 `lastRecordedMessageId`를 확인한다.
2. `lastRecordedMessageId` 이후의 MESSAGE를 조회한다(값이 없으면 처음부터). 새 MESSAGE가 없으면 "저장할 새 대화 없음" 오류를 반환한다.
3. AI가 주제별로 1개 이상의 질문·답변으로 요약하고 `domain`, `tags`, `primaryTag`도 함께 생성한다. 서버가 Enum을 검증하고, 분류값 검증 실패 시 `UNCLASSIFIED`와 빈 `tags`로 처리하며 `primaryTag`는 저장하지 않는다.
4. 이번 기록 저장 요청의 요약 대상 MESSAGE에서 참고한 과거 QA ID를 기준으로 중복을 제거하여 `referenceCount`를 산출하고, 해당 요청에서 생성되는 모든 QA에 동일한 값을 적용한다.
5. 한 트랜잭션으로 처리한다: QA 저장(`source = INTERNAL_AI`, `verification = UNVERIFIED`, `embeddingStatus = PENDING`, 출처 `sessionId`) + IDEMPOTENCY 저장 + SESSION의 `lastRecordedMessageId`를 요약에 포함된 마지막 MESSAGE ID로 갱신(기존 값과 같을 때만).
6. 성공 후 생성된 각 QA에 대한 벡터 인덱싱 요청을 발행한다(G1 이후).
7. 생성된 학습 기록 정보를 웹 클라이언트에 반환한다.

**기록 저장 정합성 규칙**

- 기록으로 저장 한 번에 생성되는 QA는 최대 개수를 제한한다.
- 최대 생성 개수 및 입력 대화 크기는 DynamoDB 트랜잭션 처리 한도와 Gemini 모델 입력·출력 한도를 고려하여 설정한다.
- 기록 저장은 SESSION의 `updatedAt`을 갱신하지 않는다.
- 요약이나 트랜잭션이 실패하면 아무것도 저장하지 않고 `lastRecordedMessageId`도 유지한다.
- 요약 도중 새 대화가 들어와도 다음 저장 때 요약된다.
- 요약 작업 시작 시 이번 요청의 마지막 대상 MESSAGE ID를 확정하고, 해당 범위만 요약한다.
- 동시 요청은 `lastRecordedMessageId` 조건부 갱신으로 막고, 기록 저장 요청에는 기존 `idempotencyKey` 정책을 적용한다.
- 기록 저장 트랜잭션 실행 시 SESSION이 여전히 존재하는지 확인한다.
- 요약 도중 세션이 삭제된 경우 새로운 QA 및 IDEMPOTENCY 레코드를 저장하지 않는다.
- 한 번의 기록 저장 요청으로 여러 QA가 생성될 수 있으므로, 멱등성 레코드에 생성된 QA ID 목록을 저장한다.
- QA 저장 성공 이후 SQS 메시지 발행에 실패한 경우 저장된 QA를 유지하고 인덱싱 누락 복구 정책에 따라 재처리한다.
- 기록 저장 성공 이후 일부 QA가 삭제되어도 `lastRecordedMessageId`를 이전 값으로 되돌리지 않는다.
- 이미 기록으로 저장된 MESSAGE는 다음 기록 저장의 요약 대상에 중복 포함하지 않는다.

**MCP 학습 기록 저장 정책**

- 기존 MCP `save_qa`를 통한 학습 기록 저장 방식은 유지한다.
- MCP `ask_study`를 통한 서버 응답형 AI 답변 생성 및 자동 저장은 기존 기획안에 따라 유지한다.
- MCP에서 생성·저장하는 독립 QA에는 `sessionId`를 필수로 요구하지 않는다.
- MCP `ask_study`를 통해 생성한 학습 기록은 `INTERNAL_AI`로 분류하고, 실제 참고한 과거 QA 수를 `referenceCount`에 기록한다.
- 웹 AI 대화에 적용되는 MESSAGE 저장 및 "기록으로 저장" 정책은 MCP 독립 Q&A 저장 방식과 구분한다.

**비동기 벡터 인덱싱 정책**

| 상태 | 설명 |
| --- | --- |
| `PENDING` | 벡터 인덱싱 처리 대기 또는 재처리 대기 |
| `PROCESSING` | 벡터 인덱싱 처리 중 |
| `COMPLETED` | 벡터 인덱싱 완료 |
| `FAILED` | 재시도 정책에 따른 최종 인덱싱 실패 |
- G0에서는 QA를 `PENDING` 상태로 저장한다.
- G1부터 QA 저장 성공 이후 SQS를 통해 인덱싱 요청을 발행한다.
- MESSAGE는 벡터 인덱싱 대상이 아니다.
- 인덱싱 메시지에는 사용자 ID, Q&A ID, 이벤트 유형 등 인덱싱에 필요한 식별 정보를 포함한다. 질문·답변 원문은 포함하지 않는다.
- 이벤트 유형은 `UPSERT`(저장·본문 수정), `UPDATE_META`(피드백·도메인 등 메타데이터 변경), `DELETE`(삭제)로 구분한다.
- Lambda는 임베딩 관련 속성(`embeddingStatus`, `embeddingModel`, `indexedAt`, `embeddingAttempts`, `embeddingError`)만 `UpdateItem`으로 갱신하며, 조건 `attribute_exists(PK)`를 적용한다. 항목 전체를 다시 저장하지 않고 `version`도 증가시키지 않는다.
- Lambda는 DynamoDB에서 대상 QA의 존재 여부, 원문 및 현재 버전을 확인한 후 임베딩을 생성한다.
- 벡터 저장 성공 이후 유효한 QA의 `embeddingStatus`를 `COMPLETED`로 갱신한다.
- 일시적 인덱싱 실패는 재시도 정책에 따라 처리한다.
- 최종 인덱싱 실패 시 유효한 QA의 `embeddingStatus`를 `FAILED`로 갱신한다.
- 실패한 인덱싱 요청을 재처리하는 경우 현재 QA의 존재 여부 및 버전을 다시 검증한다.
- 임베딩 모델 또는 제공자가 변경되더라도 QA 데이터 모델 및 인덱싱 상태 정의는 유지한다.
- 동일 벡터 인덱스에서는 동일한 임베딩 모델 및 출력 차원을 사용한다.
- S3 Vectors의 벡터 키는 `qaId`를 사용하며, 같은 키로 재저장하면 덮어쓴다.
- 단건 삭제와 비동기 인덱싱 간 데이터 정합성은 4.3의 정책을 따른다.

### **3.4. MESSAGE (대화 메시지)**

MESSAGE는 세션 대화의 질문·답변 한 턴을 저장하는 엔티티이며, 학습 기록(QA)과는 독립적으로 존재한다.

| 속성 논리명 | 속성명 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 메시지 ID | `messageId` | String | PK, 필수 | ULID |
| 사용자 ID | `userId` | String | FK, 필수 | `USER.userId` 직접 참조 |
| 세션 ID | `sessionId` | String | FK, 필수 | `SESSION.sessionId` 참조 |
| 질문 | `question` | String | 필수 | 사용자 질문 |
| 답변 | `answer` | String | 필수 | AI 답변 |
| 참고 기록 수 | `referenceCount` | Number | 필수, 기본값 0 | 답변 생성 시 참고한 과거 기록 개수(생성 당시 기준) |
| 참고 기록 ID 목록 | `referenceQaIds` | List of String | 선택, 기본값 `[]` | 답변 생성 시 실제 문맥에 포함한 과거 QA ID 목록 |
| 생성 일시 | `createdAt` | String | 필수 | UTC, ISO 8601 |

**DynamoDB 키 구조**

| 항목 | 값 |
| --- | --- |
| PK | `USER#{sub}` |
| SK | `MSG#{sessionId}#{messageId}` |
| GSI | 없음 |
| TTL | 미적용 |

**운영 규칙**

- 질문 한 개와 답변 한 개가 정상 완료된 턴 하나가 MESSAGE 한 건이다.
- 답변 생성이 실패하거나 저장 전에 사용자가 중지하면 MESSAGE를 저장하지 않고 SESSION의 `updatedAt`도 갱신하지 않는다.
- 이미 MESSAGE 저장 트랜잭션이 완료된 경우 이후의 사용자 중지 요청으로 저장을 취소하지 않는다.
- 세션 대화는 `MSG#{sessionId}#` prefix로 Query한다.
- MESSAGE ID는 ULID를 사용하며, 세션 내 저장 순서와 ULID 정렬 순서가 일치하도록 생성한다.
- MESSAGE 단건 삭제는 제공하지 않으며, 세션 삭제 시에만 함께 삭제한다.
- MESSAGE는 벡터 인덱싱, 학습 분석, 피드백 대상이 아니다.
- 웹 AI 질문 요청에는 `idempotencyKey`를 적용하지 않는다. 프론트에서 응답 대기 중 입력을 막는다.
- 서버는 동일 세션에서 MESSAGE의 저장 순서와 ULID 정렬 순서가 일치하도록 처리하며, 동시 질문 요청으로 인해 기록 저장 대상 MESSAGE가 누락되지 않도록 한다. 구체적인 동시성 제어 방식은 API 상세 명세서에서 정의한다.
- 모든 조회·저장·삭제는 인증된 사용자 ID 기준으로 수행한다.
- MESSAGE의 `userId`와 해당 SESSION의 `userId`는 일치해야 한다.
- 존재하지 않는 세션이나 다른 사용자의 세션에 MESSAGE를 저장할 수 없다.
- 새로운 MESSAGE 저장과 SESSION의 `updatedAt` 갱신은 트랜잭션으로 처리한다.
- 최초 MESSAGE 저장 시 세션 제목 자동 변경이 필요한 경우 해당 제목 갱신도 동일 트랜잭션에 포함한다.
- MESSAGE 저장 트랜잭션에서 SESSION의 존재 여부를 검증하며, 세션 삭제 이후에는 MESSAGE를 새로 저장할 수 없다.
- MESSAGE에는 `embeddingStatus`, `latestFeedback`, `rating`, `domain`, `tags` 등의 학습 기록 전용 속성을 저장하지 않는다.

**참고 기록 수 관리 정책**

- `referenceQaIds`에는 의미 검색 결과 중 실제 AI 답변 생성 문맥에 포함한 과거 QA의 ID를 저장한다.
- `referenceQaIds`는 `qaId`를 기준으로 중복을 제거한다.
- `referenceCount`는 중복 제거된 `referenceQaIds`의 개수로 저장한다.
- 참고 기록이 없는 경우 `referenceQaIds`는 빈 배열, `referenceCount`는 0으로 처리한다.
- 이전 MESSAGE 자체는 학습 기록(QA)이 아니므로 참고 기록 수에 포함하지 않는다.
- `referenceCount`와 `referenceQaIds`는 해당 답변 생성 당시를 기준으로 저장한다.
- 이후 참고 QA가 삭제되거나 피드백 상태가 변경되어도 두 속성을 갱신하지 않는다.
- `referenceQaIds`는 참고 기록 개수 산정 및 기록 요약에 사용하는 내부 데이터이며, 해당 QA의 존재 여부를 강제하는 FK로 사용하지 않는다.
- 참고 기록 ID를 이용하여 실제 QA를 조회하는 경우 존재 여부 및 사용자 소유권을 다시 검증한다.

**세션별 대화 조회 정책**

- 인증된 사용자의 PK에서 SK가 `MSG#{sessionId}#`으로 시작하는 레코드를 Query한다.
- 조회 전 해당 세션의 존재 여부 및 사용자 소유권을 검증한다.
- 세션별 대화 조회에는 별도의 GSI를 사용하지 않는다.
- 전체 세션 대화는 MESSAGE ID 기준 오름차순으로 조회하여 이전 대화부터 순서대로 반환한다.
- AI 입력 문맥에 사용할 최근 MESSAGE는 강한 일관성을 적용하여 내림차순으로 최대 5개 조회한 후 시간순으로 재정렬한다.
- `lastRecordedMessageId` 이후의 MESSAGE 조회에는 해당 ID를 기준으로 SK 범위 조회를 적용한다.
- MESSAGE 조회 시 QA의 존재 여부는 조회 조건으로 사용하지 않는다.
- 출처 세션에서 생성된 QA가 삭제되어도 MESSAGE는 정상적으로 조회할 수 있다.

**세션 기반 AI 대화 문맥 구성 정책**

| 항목 | 처리 기준 |
| --- | --- |
| 현재 질문 | 사용자가 입력한 질문 |
| 대화 문맥 | 동일 세션의 최근 MESSAGE 최대 5개 |
| 참고 자료 | 기존 `search_past_qa` 의미 검색 결과 |
| 사용자 격리 | 인증된 사용자 본인의 기록 및 대화만 사용 |
| 중복 참고 자료 | `qaId` 기준 중복 제거 |
| 삭제된 QA | 참고 자료에서 제외 |
| 오류 의심 QA | 참고 자료에서 제외 |
| 답변 저장 | 새로운 MESSAGE 레코드로 저장 |
| 세션 연결 | 기존 `sessionId` 사용 |
| 참고 기록 수 | 실제 참고한 과거 QA의 개수 저장 |
| 세션 수정 시각 | MESSAGE 저장 성공 시 `updatedAt` 갱신 |
| 학습 기록 자동 저장 | 수행하지 않음 |
| 벡터 인덱싱 | 수행하지 않음 |

**웹 AI 질문 처리 흐름**

웹 AI 질문은 MESSAGE만 저장하고, 기록(QA)은 "기록으로 저장" 요청에서 마지막 저장 이후의 대화를 요약해 만든다.

1. 인증과 세션 소유권 검증
2. 세션의 최근 MESSAGE 5개 조회(강한 일관성, 내림차순으로 읽어 시간순으로 정렬)
3. 의미 검색으로 관련 기록 조회(삭제·오류 의심 제외, `qaId` 기준 중복 제거)
4. 문맥 구성 후 Gemini API를 통한 답변 생성
5. 실제 참고한 과거 QA ID 및 참고 기록 개수 산출
6. MESSAGE 저장 + SESSION `updatedAt` 갱신 + 필요 시 최초 제목 변경을 한 트랜잭션으로 처리
7. 답변 반환(벡터 인덱싱 없음)

답변 생성에 실패하거나 저장 전에 사용자가 중지하면 MESSAGE를 저장하지 않는다. MESSAGE 저장 및 SESSION 갱신 트랜잭션이 실패한 경우에도 일부 데이터만 저장되지 않도록 처리한다.

### **3.5. FEEDBACK_HISTORY (피드백 이력)**

| 속성 논리명 | 속성명 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 사용자 ID | `userId` | String | 복합 PK, FK, 필수 | `USER.userId` 직접 참조 |
| Q&A ID | `qaId` | String | 복합 PK, FK, 필수 | `QA.qaId` 직접 참조 |
| 생성 일시 | `createdAt` | String | 복합 PK, 필수 | 피드백 등록 시각 |
| 평가 | `rating` | String | 필수 | 이해도 평가 값 |
| 도메인 | `domain` | String | 필수 | 평가 대상 QA의 도메인 |

**DynamoDB 키 구조**

| 항목 | 값 |
| --- | --- |
| PK | `USER#{sub}` |
| SK | `FB#{qaId}#{timestamp}` |
| TTL | 미적용 |

**운영 규칙**

- `userId`, `qaId`, `createdAt`의 조합으로 개별 피드백 이력을 식별한다.
- FEEDBACK_HISTORY의 `userId`는 논리적으로 USER를 직접 참조한다.
- 피드백 저장 시 인증된 사용자의 `sub`를 `userId`로 사용한다.
- 피드백을 등록하려는 QA가 인증된 사용자 본인의 기록인지 검증한다.
- FEEDBACK_HISTORY의 `userId`와 참조하는 QA의 `userId`는 반드시 일치해야 한다.
- 한 Q&A는 여러 개의 피드백 이력을 가질 수 있다.
- 피드백 등록 시 QA의 `latestFeedback`을 갱신하고 새로운 이력을 저장한다.
- 피드백 이력의 `domain`은 평가 당시 QA의 도메인 값을 복사하여 저장한다.
- 기존 피드백 이력은 덮어쓰지 않는다.
- 동일한 키의 이력이 이미 존재하는 경우 조건부 쓰기를 통해 덮어쓰기를 방지한다.
- 최신 피드백 갱신과 이력 저장은 트랜잭션으로 처리한다.
- 삭제된 QA에는 새로운 피드백을 등록할 수 없다.
- 이해도 피드백은 학습 기록(QA)에 대해서만 등록할 수 있다.
- MESSAGE는 피드백 대상이 아니며, MESSAGE에 대한 FEEDBACK_HISTORY를 생성하지 않는다.
- 학습 분석에서는 QA의 최신 피드백을 사용하며, 피드백 이력을 중복 집계하지 않는다.
- 단건 Q&A 삭제 시 피드백 이력을 유지한다. 따라서 원본 QA가 없는 이력이 남을 수 있다.
- 세션 삭제 시 기존 QA 및 피드백 이력을 유지한다.
- 피드백 이력에는 TTL을 적용하지 않는다.

### **3.6. IDEMPOTENCY (멱등성 키)**

| 속성 논리명 | 속성명 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 사용자 ID | `userId` | String | 복합 PK, FK, 필수 | `USER.userId` 직접 참조 |
| 멱등성 키 | `key` | String | 복합 PK, 필수 | 요청의 `idempotencyKey` |
| Q&A ID | `qaId` | String | FK, 선택 | 단일 QA 저장 요청의 결과 ID |
| Q&A ID 목록 | `qaIds` | List of String | 선택 | 기록으로 저장 요청으로 생성된 QA ID 목록 |
| 만료 시각 | `expiresAt` | Number | 필수, TTL | 생성 시각 + 24시간, Epoch Seconds |

**DynamoDB 키 구조**

| 항목 | 값 |
| --- | --- |
| PK | `USER#{sub}` |
| SK | `IDEM#{key}` |
| TTL 속성 | `expiresAt` |
| TTL 기간 | 24시간 |

**운영 규칙**

- `userId`, `key`의 조합으로 개별 멱등성 레코드를 식별한다.
- IDEMPOTENCY의 `userId`는 논리적으로 USER를 직접 참조한다.
- 멱등성 레코드 생성 시 인증된 사용자의 `sub`를 `userId`로 사용한다.
- 동일 사용자의 유효한 동일 멱등성 키에 대한 동일 요청은 중복 저장하지 않는다.
- 유효한 재시도 요청에는 기존 처리 결과를 반환한다.
- 동일한 질문·답변이라도 멱등성 키가 다르면 새로운 학습 기록으로 저장할 수 있다. 단, 웹 대화의 기록 저장은 `lastRecordedMessageId` 이후의 새로운 MESSAGE가 존재해야 한다.
- `qaId` 또는 `qaIds`가 존재하는 경우 참조하는 QA의 `userId`와 IDEMPOTENCY의 `userId`가 일치해야 한다.
- 단일 Q&A 저장 성공 시 생성된 `qaId`를 기록한다.
- "기록으로 저장" 요청 성공 시 생성된 모든 QA ID를 `qaIds`에 기록한다. 생성된 QA가 한 건인 경우에도 `qaIds`를 사용한다.
- 요청 유형에 따라 `qaId`와 `qaIds` 중 해당하는 속성만 저장한다.
- Q&A 저장과 멱등성 키 저장은 트랜잭션으로 처리한다.
- "기록으로 저장" 요청은 생성된 QA 저장, 멱등성 키 저장 및 SESSION의 `lastRecordedMessageId` 갱신을 동일 트랜잭션으로 처리한다.
- 동시 기록 저장 요청에 대해서는 멱등성 키 검사와 `lastRecordedMessageId` 조건부 갱신을 함께 적용한다.
- 참조하는 QA가 존재하지 않는 경우 기존 기록을 반환하거나 복구하지 않고 삭제된 기록에 대한 재요청 오류를 반환한다.
- 여러 QA를 생성한 요청에서 일부 또는 전체 QA가 삭제된 경우에도 삭제된 QA를 재생성하지 않는다.
- 세션 삭제 이후에도 기존 기록 저장 요청의 유효한 멱등성 키와 생성된 QA를 유지한다.
- 웹 AI 질문 요청 자체에는 `idempotencyKey`를 적용하지 않는다.
- TTL은 생성 시점으로부터 24시간으로 설정한다.
- TTL에 의한 물리 삭제가 지연될 수 있으므로 요청 처리 시 `expiresAt`을 확인한다.
- 만료된 멱등성 키는 유효한 중복 방지 대상으로 간주하지 않는다.
- 단건 Q&A 삭제 및 세션 삭제 시에도 기존 멱등성 키의 TTL 정책을 유지한다.
- TTL은 IDEMPOTENCY 레코드에만 적용한다.

## **4. 연관관계 명세**

| 부모 엔티티 | 자식 엔티티 | 관계 | 참조 속성 | 참조 조건 |
| --- | --- | --- | --- | --- |
| `USER` | `SESSION` | 1 : 0..N | `SESSION.userId` | 필수 |
| `USER` | `MESSAGE` | 1 : 0..N | `MESSAGE.userId` | 필수 |
| `USER` | `QA` | 1 : 0..N | `QA.userId` | 필수 |
| `USER` | `FEEDBACK_HISTORY` | 1 : 0..N | `FEEDBACK_HISTORY.userId` | 필수 |
| `USER` | `IDEMPOTENCY` | 1 : 0..N | `IDEMPOTENCY.userId` | 필수 |
| `SESSION` | `MESSAGE` | 1 : 0..N | `MESSAGE.sessionId` | 필수 |
| `SESSION` | `QA` | 0..1 : 0..N | `QA.sessionId` | 출처 정보, 비강제 참조 |
| `QA` | `FEEDBACK_HISTORY` | 1 : 0..N | `FEEDBACK_HISTORY.qaId` | 필수 |
| `QA` | `IDEMPOTENCY` | 0..1 : 0..N | `IDEMPOTENCY.qaId` | 단일 기록 저장 시 선택 |

`IDEMPOTENCY.qaIds`는 복수 기록 저장 요청의 결과 ID 목록이며, 별도의 관계 테이블을 생성하지 않는다.

### **4.1. 연관관계 및 식별자 규칙**

- 모든 사용자 종속 엔티티는 USER의 `userId`를 직접 참조한다.
- SESSION을 통해 MESSAGE 및 QA의 사용자 ID를 간접적으로 참조하지 않는다.
- QA를 통해 FEEDBACK_HISTORY 및 IDEMPOTENCY의 사용자 ID를 간접적으로 참조하지 않는다.
- 실제 사용자 ID는 Cognito 인증 토큰의 `sub`에서 추출한다.
- 모든 엔티티 간 참조 관계에서 사용자 소유권이 일치해야 한다.
- 하나의 MESSAGE는 반드시 하나의 SESSION에 소속되어야 한다.
- 생성 직후 대화가 없는 세션도 존재할 수 있다.
- 하나의 QA는 최대 하나의 출처 SESSION ID를 가질 수 있다.
- 출처 세션이 없는 QA도 독립적으로 존재할 수 있다.
- QA의 `sessionId`는 출처 정보를 표현하며, SESSION에 대한 강제 참조 관계로 사용하지 않는다.
- QA 생성 시에는 출처 세션의 존재 여부 및 소유권을 검증한다.
- 세션 삭제 후에도 QA의 `sessionId` 값은 유지하며 출처 정보로만 쓴다.
- SESSION의 `lastRecordedMessageId`는 기록 저장 위치를 나타내는 내부 참조 값이며, 별도의 인덱스 또는 관계 테이블을 생성하지 않는다.
- MESSAGE의 `referenceQaIds`는 답변 생성 당시의 참고 기록을 나타내는 내부 참조 값이다. 참고 QA에 대한 강제 FK 관계는 설정하지 않는다.
- 모든 PK·FK 관계는 논리적 관계이며, 실제 DynamoDB에서는 애플리케이션을 통해 참조 무결성을 검증한다.

### **4.2. 삭제 및 보존 기준**

기록(QA) 삭제는 대화를 건드리지 않고, 세션 삭제는 기록을 건드리지 않는다.

| 대상 | 기록(QA) 삭제 시 | 세션 삭제 시 |
| --- | --- | --- |
| USER | 유지 | 유지 |
| SESSION | 유지 | 하드 삭제 |
| MESSAGE | 유지 | 해당 세션의 MESSAGE 하드 삭제 |
| QA | 하드 삭제 | 유지 |
| FEEDBACK_HISTORY | 유지 | 유지 |
| IDEMPOTENCY | 기존 TTL 유지 | 기존 TTL 유지 |
| GSI1 | 항목 삭제로 자동 제외 | 해당 없음 |
| S3 Vectors | 해당 QA 벡터 삭제(G1 이후) | 해당 없음 |
| S3 분석 결과 | 다음 정기 분석 시 반영 | 해당 없음 |

**단건 Q&A 삭제 정책**

- 인증된 사용자 본인의 Q&A만 삭제할 수 있다.
- QA 항목을 완전 삭제하며, 삭제한 기록은 복구 기능을 제공하지 않는다.
- 존재하지 않는 항목은 삭제된 것으로 간주하며, 삭제된 `qaId`는 재사용하지 않는다.
- G1 이후에는 관련 벡터 삭제 이벤트를 발행한다.
- SESSION 및 MESSAGE는 유지한다.
- 피드백 이력은 유지한다.
- 멱등성 레코드는 기존 TTL 정책에 따라 관리한다.
- 삭제된 QA는 일반 기록 조회, 의미 검색 및 학습 분석 대상에서 제외한다.
- 삭제된 QA에는 새로운 피드백을 등록할 수 없다.
- 삭제된 QA는 새로운 AI 답변 생성 시 참고 자료에서 제외한다.
- 다음 정기 학습 분석 시 삭제된 QA를 집계 대상에서 제외한다.
- 해당 QA를 참고한 다른 MESSAGE 및 QA의 `referenceCount`는 갱신하지 않는다.
- 해당 QA가 세션 대화를 요약해 생성된 기록이더라도 SESSION의 `lastRecordedMessageId`는 변경하지 않는다.

**단건 세션 삭제 정책**

- 인증된 사용자 본인의 세션만 삭제할 수 있다.
- SESSION과 해당 세션에 속한 모든 MESSAGE를 하드 삭제한다.
- 해당 세션에서 생성된 QA는 삭제하지 않는다.
- QA의 출처 `sessionId` 값은 유지한다.
- FEEDBACK_HISTORY 및 IDEMPOTENCY는 유지한다.
- 세션 삭제는 벡터 인덱싱 및 S3 Vectors 삭제 이벤트의 발생 조건이 아니다.
- 세션 삭제는 학습 분석 결과의 갱신 또는 삭제 조건이 아니다.
- 세션 삭제 시 진행 중인 웹 AI 질문 및 기록 요약 작업이 삭제된 SESSION 또는 MESSAGE를 다시 생성하지 않도록 처리한다.
- SESSION을 먼저 삭제하여 새로운 MESSAGE 저장 및 기록 저장 트랜잭션을 차단한 후 해당 세션의 MESSAGE를 강한 일관성으로 조회하여 삭제한다.
- MESSAGE 삭제 대상이 여러 페이지에 걸쳐 존재하는 경우 페이지네이션 및 일괄 삭제를 적용한다.
- 일괄 삭제 중 미처리 항목이 발생한 경우 재시도한다.
- 일부 MESSAGE 삭제에 실패한 경우 동일한 세션 삭제 요청의 재시도를 통해 잔여 MESSAGE를 삭제할 수 있도록 처리한다.
- SESSION이 이미 삭제되었더라도 인증된 사용자의 PK 및 요청된 세션 ID 범위에서 잔여 MESSAGE 삭제를 재시도할 수 있다.
- SESSION 및 해당 세션의 MESSAGE 삭제가 모두 완료된 경우에만 삭제 성공으로 처리한다.
- 삭제된 세션 ID는 재사용하지 않는다.
- 기록 삭제와 세션 삭제는 서로 영향을 주지 않는다.

※ 단건 Q&A 삭제 및 세션 삭제는 서비스에서 사용하는 현재 데이터에 대한 삭제를 의미하며, 기존 DynamoDB PITR 백업 및 Export 스냅샷까지 즉시 물리 삭제되는 것은 아니다.

### **4.3. 단건 삭제와 비동기 인덱싱 간 데이터 정합성 관리**

G1 이후 비동기 임베딩 및 벡터 인덱싱 작업이 수행되는 과정에서 이미 삭제된 Q&A의 벡터가 다시 생성되거나 검색 결과에 포함되는 것을 방지한다.

**관리 속성**

| 속성 | 용도 |
| --- | --- |
| `qaId` | 인덱싱 및 삭제 대상 QA 식별 |
| `userId` | 사용자별 데이터 접근 범위 식별 |
| `version` | 데이터 변경 및 오래된 인덱싱 이벤트 검증 |
| `embeddingStatus` | 벡터 인덱싱 처리 상태 관리 |
| `embeddingModel`, `indexedAt` | 인덱싱 완료 모델·시각 기록, 모델 변경 시 백필 대상 판별 |
| `embeddingAttempts`, `embeddingError` | 실패 재처리 횟수 제한 및 실패 원인 기록 |

**처리 기준**

- SQS 인덱싱 메시지에는 `userId`, `qaId` 및 이벤트 유형(`UPSERT`·`UPDATE_META`·`DELETE`)을 포함한다.
- ⚠️ 논의 필요: 메시지에 `version`을 넣어 오래된 이벤트를 건너뛸지, `version` 없이 Lambda가 항상 DynamoDB 최신 상태를 다시 읽어 벡터를 맞출지(indexing.md 방식) 확정이 필요하다. 아래 `version` 관련 항목은 확정 전까지 기존 기준으로 둔다.
- Lambda는 `UPSERT`·`UPDATE_META` 메시지를 처리하기 전 DynamoDB에서 대상 QA의 현재 상태를 조회한다.
- 대상 QA가 존재하지 않는 경우 임베딩 생성 및 벡터 저장을 수행하지 않는다.
- 메시지의 버전이 현재 QA 버전과 일치하지 않는 경우 오래된 인덱싱 요청으로 처리한다.
- 임베딩 생성 이후 벡터 저장 전 QA의 존재 여부 및 버전을 다시 확인한다.
- 벡터 저장 이후 인덱싱 상태를 갱신할 때 조건부 쓰기를 적용한다.
- 벡터 저장 과정에서 QA가 삭제되거나 버전이 변경된 경우 해당 작업의 벡터를 유효한 검색 데이터로 처리하지 않는다.
- 벡터 저장 직후 QA 상태를 재검증하고, 이미 삭제된 QA의 벡터가 생성된 경우 해당 벡터를 제거한다.
- DELETE 메시지 처리 시 메시지의 사용자 ID 범위에서 QA 항목이 없는 것을 확인한 후 해당 벡터를 삭제한다.
- 동일한 `UPSERT`·`UPDATE_META`·`DELETE` 메시지가 중복 수신되어도 데이터 정합성이 유지되어야 한다.
- S3 Vectors의 벡터 키는 `qaId`를 사용하며, 같은 키로 재저장하면 덮어쓴다.
- 벡터 삭제 실패 시 재시도 및 실패 복구 정책에 따라 처리한다.
- DLQ 메시지 재처리 시에도 대상 QA의 현재 존재 여부와 버전을 확인한다(DELETE 메시지는 존재 여부만 확인).
- S3 Vectors의 검색 결과는 DynamoDB 원문 재조회 및 존재 여부·사용자 소유권 검증을 거친 후 반환한다.
- 삭제된 QA의 벡터가 일시적으로 남아 있더라도 사용자에게 검색 결과로 반환하지 않는다.
- `WRONG`으로 평가된 QA는 비슷한 과거 질문 목록에는 표시할 수 있으나, AI 답변 생성 참고 자료에서는 제외한다.
- MESSAGE는 벡터 인덱싱 및 의미 검색 대상에 포함하지 않는다.
- 세션이 삭제되어도 해당 세션에서 생성된 활성 QA의 벡터는 유지한다.
- QA의 출처 세션 존재 여부는 벡터 인덱싱 및 의미 검색의 필수 조건으로 사용하지 않는다.
- "기록으로 저장" 요청으로 여러 QA가 생성된 경우 각 QA에 대해 독립적으로 인덱싱 요청을 발행한다.
- 인덱싱 요청 발행 누락 및 삭제 이벤트 처리 실패는 기존 실패 복구 정책에 따라 재처리한다.

### **4.4. S3 학습 분석 결과 연동 정책**

| 항목 | 처리 기준 |
| --- | --- |
| 저장 버킷 | `clfqa-dev-analytics-{고유식별자}` |
| 저장 경로 | `results/users/{userId}/latest.json` |
| 저장 형식 | JSON (UTF-8) |
| 갱신 주기 | 하루 1회 |
| 갱신 방식 | 분석 성공 시 기존 파일 덮어쓰기 |
| 단건 Q&A 삭제 | 다음 정기 분석 시 삭제된 QA 제외 |
| 세션 삭제 | 분석 결과에 영향 없음 |
| 분석 실패 | 마지막 정상 분석 결과 유지 |
| 분석 결과 미생성 | API에서 분석 결과 미생성 상태 반환 |
| 조회 권한 | 인증된 사용자 본인의 결과만 조회 |

**운영 규칙**

- 학습 분석 결과는 DynamoDB에 저장하지 않는다.
- DynamoDB PITR 기반 Export를 통해 분석 대상 데이터를 S3에 저장한다.
- Glue Data Catalog는 Export 데이터의 스키마 및 메타데이터를 관리한다.
- Athena는 Glue Data Catalog의 메타데이터를 참조하여 S3의 Export 데이터를 직접 조회한다.
- 별도의 Glue ETL Job은 사용하지 않는다.
- 학습 분석은 삭제되지 않은 QA 레코드를 대상으로 수행한다.
- SESSION, MESSAGE, FEEDBACK_HISTORY, IDEMPOTENCY는 학습 분석의 직접적인 집계 대상에서 제외한다.
- `EXTERNAL_AI`, `USER`, `INTERNAL_AI` 출처의 활성 QA를 학습 분석 대상에 포함한다.
- 웹 AI 대화만 진행하고 "기록으로 저장"을 수행하지 않은 경우 해당 MESSAGE는 학습 분석에 반영하지 않는다.
- "기록으로 저장" 요청으로 생성된 QA는 다음 정상 학습 분석 배치부터 집계 대상에 포함한다.
- 학습 분석에서는 QA의 `latestFeedback`을 사용하며, FEEDBACK_HISTORY를 중복 집계하지 않는다.
- `WRONG`(오류 의심) QA는 학습 분석의 헷갈림 비율 분자에 포함하지 않고 분모(전체 질문)에는 포함한다.
- 반복 질문 분석은 QA의 `primaryTag`를 기준으로 수행한다.
- 출처 세션이 삭제된 QA도 삭제되지 않은 활성 학습 기록인 경우 정상적으로 집계한다.
- 세션 삭제 자체는 학습 기록 수, 도메인별 질문 수 및 피드백 집계에 영향을 주지 않는다.
- 분석 결과는 사용자별 JSON으로 생성하여 지정된 S3 경로에 저장한다.
- 분석이 성공한 경우에만 기존 `latest.json`을 갱신한다.
- 분석에 실패한 경우 마지막 정상 분석 결과를 유지한다.
- 분석 결과에는 분석 시각을 포함한다.
- ECS REST API가 인증된 사용자 ID를 기준으로 S3의 사용자별 분석 결과를 조회한다.
- 분석 결과가 없는 경우 API는 분석 결과 미생성 상태를 반환하며, 웹에서는 이에 해당하는 초기 안내 화면을 표시한다.
- 신규 학습 기록, 피드백 변경 및 단건 Q&A 삭제는 다음 정상 분석 배치에서 반영한다.
- 분석 결과는 실시간 데이터가 아니며, Export 시점 및 정기 분석 주기에 따라 현재 학습 기록과 차이가 발생할 수 있다.
- 기존 Export 스냅샷 및 Athena 쿼리 결과 파일은 7일 Lifecycle 정책에 따라 정리한다.
- 사용자별 최종 분석 결과인 `latest.json`은 임시 분석 파일의 7일 Lifecycle 적용 대상에서 제외한다.

**도메인별 헷갈림 비율 계산 기준**

$\text{도메인별 헷갈림 비율}
=
\frac{\text{해당 도메인의 CONFUSED QA 수}}
{\text{해당 도메인의 전체 활성 QA 수}}
\times100$

| 최신 피드백 상태 | 분자 포함 | 분모 포함 |
| --- | --- | --- |
| `UNDERSTOOD` | X | O |
| `CONFUSED` | O | O |
| `WRONG` | X | O |
| 미평가(`null`) | X | O |
| 삭제된 QA | X | X |
| MESSAGE | X | X |

전체 활성 QA 수가 0인 경우 헷갈림 비율은 0으로 처리한다.

## **5. Enum 명세**

| Enum | 허용값 | 설명 |
| --- | --- | --- |
| `domain` | `CONCEPTS` | Cloud Concepts |
| `domain` | `SECURITY` | Security and Compliance |
| `domain` | `TECH` | Cloud Technology and Services |
| `domain` | `BILLING` | Billing, Pricing, and Support |
| `domain` | `UNCLASSIFIED` | 미분류 |
| `source` | `EXTERNAL_AI` | 외부 AI 생성 기록 |
| `source` | `USER` | 사용자 직접 입력 기록 |
| `source` | `INTERNAL_AI` | 서비스 내부 AI 모델 생성 기록 |
| `verification` | `UNVERIFIED` | 미검증 |
| `verification` | `VERIFIED` | 기존 출처 정책상 검증 분류 상태 |
| `latestFeedback`, `rating` | `UNDERSTOOD` | 이해함 |
| `latestFeedback`, `rating` | `CONFUSED` | 헷갈림 |
| `latestFeedback`, `rating` | `WRONG` | 오류 의심 |
| `embeddingStatus` | `PENDING` | 임베딩 처리 대기 |
| `embeddingStatus` | `PROCESSING` | 벡터 인덱싱 처리 중 |
| `embeddingStatus` | `COMPLETED` | 벡터 인덱싱 완료 |
| `embeddingStatus` | `FAILED` | 벡터 인덱싱 실패 |

**Enum 적용 규칙**

- 미평가 상태의 `latestFeedback`은 DynamoDB에 속성을 저장하지 않으며, API에서 `null`로 반환한다.
- `rating`은 실제 피드백 등록 시 사용하는 값으로, `UNDERSTOOD`, `CONFUSED`, `WRONG` 중 하나를 사용한다.
- `VERIFIED`는 기존 출처 분류 정책에 따른 상태이며, 답변 내용의 사실 정확성을 별도로 검증했다는 의미는 아니다.
- `INTERNAL_AI`는 특정 AI 모델 또는 제공자에 종속되지 않는 출처 유형이다.
- 서비스 내부 AI가 대화를 요약하여 생성한 QA와 MCP `ask_study`를 통해 생성한 QA는 `INTERNAL_AI`로 저장한다.
- 대화를 요약해 생성한 QA의 `verification`은 `UNVERIFIED`로 설정한다.
- AI 모델의 변경은 `source` Enum 변경 사유에 해당하지 않는다.
- MESSAGE는 실제 웹 AI 대화 내용을 저장하는 엔티티로, 학습 기록 전용 Enum을 적용하지 않는다.
- REST API 및 MCP Tool의 공통 데이터 필드명과 Enum 값은 본 ERD 상세 명세서의 정의를 기준으로 통일한다.

## **6. DynamoDB 키 및 인덱스 구조**

### **6.1. Item 유형별 키 구조**

| 종류 | PK | SK | 용도 |
| --- | --- | --- | --- |
| QA | `USER#{sub}` | `QA#{qaId}` | 질문·답변 학습 기록 |
| SESSION | `USER#{sub}` | `SESSION#{sessionId}` | 대화 세션 |
| MESSAGE | `USER#{sub}` | `MSG#{sessionId}#{messageId}` | 세션 대화 턴 |
| FEEDBACK_HISTORY | `USER#{sub}` | `FB#{qaId}#{timestamp}` | 피드백 이력 |
| IDEMPOTENCY | `USER#{sub}` | `IDEM#{key}` | 중복 저장 방지 |
- USER는 논리적 엔티티이며 별도의 DynamoDB Item으로 저장하지 않는다.
- 모든 사용자 종속 Item의 PK는 인증된 사용자 ID를 기반으로 구성한다.
- 각 엔티티의 SK Prefix를 이용하여 데이터 유형을 구분한다.
- TTL은 IDEMPOTENCY의 `expiresAt` 속성에만 적용한다.
- SESSION, MESSAGE, QA, FEEDBACK_HISTORY에는 TTL을 적용하지 않는다.

### **6.2. GSI 구조**

| 인덱스 | PK | SK | 용도 |
| --- | --- | --- | --- |
| GSI1 | `USER#{sub}#DOM#{domain}` | `createdAt` | 도메인별 최신 Q&A 조회 |
- GSI1은 기존 설계를 유지하며 QA에만 적용한다. 삭제된 QA는 항목이 없으므로 인덱스에서도 제외된다.
- GSI 조회 결과의 최신 상태 및 존재 여부 확인이 필요한 경우 원본 테이블의 데이터를 검증한다.
- 세션별 대화 조회는 원본 테이블의 `MSG#{sessionId}#` SK Prefix를 이용한다.
- 세션 목록 정렬용 별도 GSI는 생성하지 않는다.
- SESSION 및 MESSAGE는 별도의 GSI를 사용하지 않는다.
- 기존 Single Table 구조 및 on-demand 용량 모드, PITR, TTL 정책을 유지한다.
