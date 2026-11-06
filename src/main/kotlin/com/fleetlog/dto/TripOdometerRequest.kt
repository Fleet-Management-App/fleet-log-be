package com.fleetlog.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero

data class TripOdometerRequest(
	@field:NotNull @field:PositiveOrZero
	val odometerStartKm: Int,

	@field:NotNull @field:PositiveOrZero
	val odometerEndKm: Int,
)
