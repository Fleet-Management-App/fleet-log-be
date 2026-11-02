package com.fleetlog.entity

import io.swagger.v3.oas.annotations.media.Schema

/** A user's role within one organization (SPEC-08). Roles are personas, not a ranking. */
// enumAsRef: one shared "Role" schema in the OpenAPI spec instead of a copy per DTO
@Schema(enumAsRef = true)
enum class Role {
	DRIVER,
	FLEET_MANAGER,
	ACCOUNTANT,
}
