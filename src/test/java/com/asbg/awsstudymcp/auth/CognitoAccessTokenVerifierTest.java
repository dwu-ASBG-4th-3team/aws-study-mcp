package com.asbg.awsstudymcp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

class CognitoAccessTokenVerifierTest {

	private static final String CLIENT_ID = "app-client";
	private static HttpServer jwksServer;
	private static KeyPair keyPair;
	private static String issuer;
	private static CognitoAccessTokenVerifier verifier;

	@BeforeAll
	static void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keyPair = generator.generateKeyPair();
		RSAKey publicJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).keyID("test-key").build();
		byte[] jwks = new JWKSet(publicJwk).toString().getBytes(StandardCharsets.UTF_8);
		jwksServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		jwksServer.createContext("/pool/.well-known/jwks.json", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, jwks.length);
			try (var body = exchange.getResponseBody()) {
				body.write(jwks);
			}
		});
		jwksServer.start();
		issuer = "http://localhost:" + jwksServer.getAddress().getPort() + "/pool";
		CognitoJwtProperties properties = new CognitoJwtProperties(issuer, CLIENT_ID);
		CognitoJwtConfig config = new CognitoJwtConfig();
		verifier = config.cognitoAccessTokenVerifier(config.cognitoJwtDecoder(properties), properties);
	}

	@AfterAll
	static void tearDown() {
		jwksServer.stop(0);
	}

	@Test
	void validAccessTokenReturnsSub() throws Exception {
		assertThat(verifier.extractSub("Bearer " + token(issuer, "access", CLIENT_ID,
				"user-123", Instant.now().plusSeconds(600), keyPair))).isEqualTo("user-123");
	}

	@Test
	void rejectsMissingOrMalformedAuthorizationHeader() {
		assertUnauthorized(null);
		assertUnauthorized("");
		assertUnauthorized("Basic abc");
		assertUnauthorized("Bearer ");
		assertUnauthorized("Bearer abc def");
	}

	@Test
	void rejectsWrongTokenTypeClientIssuerAndExpiry() throws Exception {
		assertUnauthorized("Bearer " + token(issuer, "id", CLIENT_ID, "user-123",
				Instant.now().plusSeconds(600), keyPair));
		assertUnauthorized("Bearer " + token(issuer, "access", "another-client", "user-123",
				Instant.now().plusSeconds(600), keyPair));
		assertUnauthorized("Bearer " + token("https://wrong-issuer.example/pool", "access", CLIENT_ID,
				"user-123", Instant.now().plusSeconds(600), keyPair));
		assertUnauthorized("Bearer " + token(issuer, "access", CLIENT_ID, "user-123",
				Instant.now().minusSeconds(600), keyPair));
		assertUnauthorized("Bearer " + token(issuer, "access", CLIENT_ID, "",
				Instant.now().plusSeconds(600), keyPair));
		assertUnauthorized("Bearer " + token(issuer, "access", CLIENT_ID, CLIENT_ID,
				Instant.now().plusSeconds(600), keyPair));
		assertUnauthorized("Bearer " + token(issuer, "access", CLIENT_ID, "user-123", null, keyPair));
	}

	@Test
	void rejectsTokenWithUnknownSigningKey() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		assertUnauthorized("Bearer " + token(issuer, "access", CLIENT_ID, "user-123",
				Instant.now().plusSeconds(600), generator.generateKeyPair()));
	}

	private static String token(String tokenIssuer, String tokenUse, String clientId, String sub,
			Instant expiresAt, KeyPair signingKey) throws Exception {
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
			.issuer(tokenIssuer)
			.subject(sub)
			.claim("token_use", tokenUse)
			.claim("client_id", clientId)
			.issueTime(new Date());
		if (expiresAt != null) {
			claims.expirationTime(Date.from(expiresAt));
		}
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
				claims.build());
		jwt.sign(new RSASSASigner(signingKey.getPrivate()));
		return jwt.serialize();
	}

	private static void assertUnauthorized(String authorizationHeader) {
		assertThatThrownBy(() -> verifier.extractSub(authorizationHeader))
			.isInstanceOf(ApiException.class)
			.satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
	}

}
