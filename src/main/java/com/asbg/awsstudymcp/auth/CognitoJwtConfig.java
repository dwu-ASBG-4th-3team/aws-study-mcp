package com.asbg.awsstudymcp.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@Profile("!local")
@EnableConfigurationProperties(CognitoJwtProperties.class)
public class CognitoJwtConfig {

	@Bean
	JwtDecoder cognitoJwtDecoder(CognitoJwtProperties properties) {
		String issuer = properties.issuerUri().replaceAll("/+$", "");
		// JWKS 주소를 직접 지정하면 Cognito에 연결하지 않고 서버가 기동할 수 있다.
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		return decoder;
	}

	@Bean
	CognitoAccessTokenVerifier cognitoAccessTokenVerifier(JwtDecoder cognitoJwtDecoder,
			CognitoJwtProperties properties) {
		return new CognitoAccessTokenVerifier(cognitoJwtDecoder, properties.clientId());
	}

}
