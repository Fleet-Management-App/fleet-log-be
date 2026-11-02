package com.fleetlog.dto

import com.fleetlog.entity.Role
import java.util.UUID

/** One of the caller's own organizations. */
data class MembershipResponse(
	val organizationId: UUID,
	val organizationName: String,
	val role: Role,
)
