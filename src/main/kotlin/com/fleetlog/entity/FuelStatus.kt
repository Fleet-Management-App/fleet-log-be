package com.fleetlog.entity

import io.swagger.v3.oas.annotations.media.Schema

/** Approval state of a trip's optional fuel entry (SPEC-02, BR-06) — independent of the trip's own status. */
@Schema(enumAsRef = true)
enum class FuelStatus {
	PENDING,
	APPROVED,
	REJECTED,
}
