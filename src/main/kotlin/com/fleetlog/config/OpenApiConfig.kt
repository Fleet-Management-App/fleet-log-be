package com.fleetlog.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

/** Swagger's Authorize button takes a bearer token: call /api/auth/login and paste the token. */
@Configuration
@OpenAPIDefinition(
	info = Info(title = "FleetLog API", version = "v1"),
	security = [SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)],
)
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {

	companion object {
		const val BEARER_AUTH = "bearerAuth"
	}

}
