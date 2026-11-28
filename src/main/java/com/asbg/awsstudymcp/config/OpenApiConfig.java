package com.asbg.awsstudymcp.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

// Swagger UI에 표시하는 API 문서 정보
@Configuration
@OpenAPIDefinition(info = @Info(title = "AWS Study MCP API", version = "v0"))
public class OpenApiConfig {
}
