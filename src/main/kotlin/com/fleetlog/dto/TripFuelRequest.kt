package com.fleetlog.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class TripFuelRequest(
	@field:NotNull @field:Positive
	val liters: BigDecimal,

	@field:NotNull @field:Positive
	val price: BigDecimal,

	/** Base64 or a URL to the already-uploaded receipt photo; stored as given. */
	val receiptPhoto: String? = null,
)
