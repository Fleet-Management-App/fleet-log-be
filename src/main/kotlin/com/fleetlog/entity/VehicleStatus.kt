package com.fleetlog.entity

import io.swagger.v3.oas.annotations.media.Schema

/** A vehicle's availability for trip planning (SPEC-01, BR-01). */
@Schema(enumAsRef = true)
enum class VehicleStatus {
	ACTIVE,
	IN_SERVICE,
	SUSPENDED,
}
