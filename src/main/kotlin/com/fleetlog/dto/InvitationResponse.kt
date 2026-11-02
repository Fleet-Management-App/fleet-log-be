package com.fleetlog.dto

import com.fleetlog.entity.Role
import java.time.Instant
import java.util.UUID

/** An invitation sent by the active organization. */
data class InvitationResponse(
	val id: UUID,
	val email: String,
	val role: Role,
	val invitedByName: String,
	val createdAt: Instant,
)
