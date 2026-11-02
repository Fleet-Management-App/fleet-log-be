package com.fleetlog.dto

import java.util.UUID

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
