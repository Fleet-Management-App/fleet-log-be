package com.fleetlog.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class TripFuelRequest(
	@field:NotNull @field:Positive
	val liters: BigDecimal,

	@field:NotNull @field:Positive
	val price: BigDecimal,

	/** The receipt photo as a data URL (`data:image/...;base64,...`); stored as given. Required: a fuel entry is logged with its receipt (BR-06). */
	@field:NotBlank
	val receiptPhoto: String,
)
