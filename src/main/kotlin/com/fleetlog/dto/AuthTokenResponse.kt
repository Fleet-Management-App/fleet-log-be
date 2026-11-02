package com.fleetlog.dto

import java.time.Instant

data class AuthTokenResponse(
	val accessToken: String,
	val expiresAt: Instant,
)
