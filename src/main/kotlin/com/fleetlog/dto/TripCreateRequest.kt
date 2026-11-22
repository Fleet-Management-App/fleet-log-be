package com.fleetlog.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class TripCreateRequest(
	@field:NotNull
	val vehicleId: UUID,

	@field:NotNull
	val dateTime: Instant,

	@field:NotBlank @field:Size(max = 200)
	val fromLocation: String,

	@field:NotBlank @field:Size(max = 200)
	val toLocation: String,

	@field:NotBlank @field:Size(max = 500)
	val purpose: String,
)
