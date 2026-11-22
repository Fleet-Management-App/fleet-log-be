package com.fleetlog.entity

import io.swagger.v3.oas.annotations.media.Schema

/** The fuel a vehicle runs on (SPEC-01). */
@Schema(enumAsRef = true)
enum class VehicleFuelType {
	PETROL,
	DIESEL,
	ELECTRIC,
	HYBRID,
	LPG,
}
