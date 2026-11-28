package com.asbg.awsstudymcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties("app.auth.cognito")
public record CognitoJwtProperties(@NotBlank String issuerUri, @NotBlank String clientId) {
}
