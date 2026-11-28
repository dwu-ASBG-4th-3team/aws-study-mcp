package com.asbg.awsstudymcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties("app.dynamodb")
public record DynamoDbProperties(@NotBlank String tableName, @NotBlank String region) {
}
