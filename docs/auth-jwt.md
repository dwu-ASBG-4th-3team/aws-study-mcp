# Cognito Access Token 검증

운영 환경에서 `COGNITO_ISSUER_URI`와 `COGNITO_CLIENT_ID`를 설정한다.

```text
COGNITO_ISSUER_URI=https://cognito-idp.ap-northeast-2.amazonaws.com/ap-northeast-2_XXXXXXXXX
COGNITO_CLIENT_ID=<사용자 풀 앱 클라이언트 ID>
```

`CognitoAccessTokenVerifier.extractSub(authorizationHeader)`에 요청의 `Authorization` 헤더 값 전체를 전달한다. 검증에 성공하면 사용자 `sub`를 반환하고, 실패하면 `ApiException(COMMON_401)`을 던진다.

```java
String userId = verifier.extractSub(request.getHeader(HttpHeaders.AUTHORIZATION));
```

검증 항목은 Cognito JWKS의 RS256 서명, 발급자(`iss`), 만료 시각(`exp`), `token_use=access`, 설정한 앱 클라이언트의 `client_id`, 비어 있지 않은 사용자 `sub`이다. 사용자 ID가 아닌 앱 클라이언트 ID를 `sub`로 담은 `client_credentials` 토큰도 거부한다.

검증기는 `local` 이외의 프로필에서 빈으로 제공된다. ①·③은 각자의 HTTP/MCP 요청 처리 경로에서 이 검증기를 호출하고, 반환된 `sub`를 `CurrentUserProvider` 또는 서비스의 `userId`에 연결하면 된다. 현재 변경은 요청 경로에 인증 필터를 적용하지 않는다.
