package com.fleetlog.organization

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateOrganizationRequest(
	@field:NotBlank @field:Size(max = 200)
	val name: String,
)

/** One of the caller's own organizations. */
data class MembershipResponse(
	val organizationId: UUID,
	val organizationName: String,
	val role: Role,
)

/** A person in the active organization, as the Fleet Manager sees them. */
data class MemberResponse(
	val userId: UUID,
	val fullName: String,
	val email: String,
	val role: Role,
	val joinedAt: Instant,
)

data class ChangeRoleRequest(
	val role: Role,
)

data class CreateInvitationRequest(
	@field:NotBlank @field:Email @field:Size(max = 320)
	val email: String,
	val role: Role,
)

/** An invitation sent by the active organization. */
data class InvitationResponse(
	val id: UUID,
	val email: String,
	val role: Role,
	val invitedByName: String,
	val createdAt: Instant,
)

/** An invitation addressed to the caller. */
data class IncomingInvitationResponse(
	val id: UUID,
	val organizationId: UUID,
	val organizationName: String,
	val role: Role,
	val invitedByName: String,
	val createdAt: Instant,
)
