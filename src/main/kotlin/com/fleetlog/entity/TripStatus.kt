package com.fleetlog.entity

import io.swagger.v3.oas.annotations.media.Schema

/** A trip's lifecycle state (SPEC-02). Plan approval is required; fuel approval is a separate, optional step. */
@Schema(enumAsRef = true)
enum class TripStatus {
	PLANNED,
	PLAN_APPROVED,
	PLAN_REJECTED,
	COMPLETED,
	CANCELLED,
}
