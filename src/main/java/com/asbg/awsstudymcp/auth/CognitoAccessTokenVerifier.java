package com.asbg.awsstudymcp.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;

/** Cognito Access Token을 검증하고 검증된 사용자 ID를 반환한다. */
public class CognitoAccessTokenVerifier {

	private final JwtDecoder decoder;
	private final String clientId;

	public CognitoAccessTokenVerifier(JwtDecoder decoder, String clientId) {
		this.decoder = decoder;
		this.clientId = clientId;
	}

	/** 요청의 Authorization 헤더 값 전체를 받는다. 실패하면 401 ApiException을 던진다. */
	public String extractSub(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
			throw unauthorized();
		}
		String token = authorizationHeader.substring(7).trim();
		if (token.isEmpty() || token.contains(" ")) {
			throw unauthorized();
		}
		try {
			Jwt jwt = decoder.decode(token);
			if (jwt.getExpiresAt() == null || !"access".equals(jwt.getClaimAsString("token_use"))
					|| !clientId.equals(jwt.getClaimAsString("client_id"))) {
				throw unauthorized();
			}
			String sub = jwt.getSubject();
			// Cognito client_credentials 토큰의 sub는 사용자 ID가 아니라 client_id이다.
			if (sub == null || sub.isBlank() || sub.equals(clientId)) {
				throw unauthorized();
			}
			return sub;
		}
		catch (JwtException e) {
			throw unauthorized();
		}
	}

	private static ApiException unauthorized() {
		return new ApiException(CommonErrorCode.UNAUTHORIZED);
	}

}
