package com.fleetlog.dto

import com.fleetlog.entity.Role
import java.time.Instant
import java.util.UUID

/** An invitation addressed to the caller. */
data class IncomingInvitationResponse(
	val id: UUID,
	val organizationId: UUID,
	val organizationName: String,
	val role: Role,
	val invitedByName: String,
	val createdAt: Instant,
)
