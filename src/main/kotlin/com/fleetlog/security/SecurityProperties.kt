package com.fleetlog.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("fleetlog.security")
data class SecurityProperties(
	/** HS256 signing key; comes from the JWT_SECRET env var outside local development. */
	val jwtSecret: String,
	val tokenLifetime: Duration,
	val corsAllowedOrigins: List<String>,
) {

	init {
		// HS256 needs a key of at least 256 bits
		require(jwtSecret.toByteArray().size >= 32) { "fleetlog.security.jwt-secret must be at least 32 bytes" }
	}

}
