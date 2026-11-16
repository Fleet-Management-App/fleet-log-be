package com.fleetlog.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class TripFuelDecisionRequest(
	@field:NotNull
	val approved: Boolean,

	@field:Size(max = 500)
	val rejectionReason: String? = null,
)
