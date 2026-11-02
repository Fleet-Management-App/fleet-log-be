package com.fleetlog.dto

import com.fleetlog.entity.Role
import java.time.Instant
import java.util.UUID

/** A person in the active organization, as the Fleet Manager sees them. */
data class MemberResponse(
	val userId: UUID,
	val fullName: String,
	val email: String,
	val role: Role,
	val joinedAt: Instant,
)
