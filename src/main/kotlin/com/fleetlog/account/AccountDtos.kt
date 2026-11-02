package com.fleetlog.account

import com.fleetlog.organization.IncomingInvitationResponse
import com.fleetlog.organization.MembershipResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class SignUpRequest(
	@field:NotBlank @field:Email @field:Size(max = 320)
	val email: String,
	@field:Size(min = 8, max = 100)
	val password: String,
	@field:NotBlank @field:Size(max = 200)
	val fullName: String,
)

data class LoginRequest(
	@field:NotBlank
	val email: String,
	@field:NotBlank
	val password: String,
)

data class SwitchOrganizationRequest(
	val organizationId: UUID,
)

/** Everything the FE needs right after login to decide where the user lands. */
data class MeResponse(
	val id: UUID,
	val email: String,
	val fullName: String,
	/** Null when the user has no organization yet (or the token's one is gone). */
	val activeOrganization: MembershipResponse?,
	val memberships: List<MembershipResponse>,
	val pendingInvitations: List<IncomingInvitationResponse>,
)
