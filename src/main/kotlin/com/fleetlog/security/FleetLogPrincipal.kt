package com.fleetlog.security

import com.fleetlog.entity.Role
import java.util.UUID

/**
 * The caller of the current request. [tenantId] and [role] are null when the user has no active
 * organization (e.g. right after sign-up); such a caller can only reach the account endpoints.
 */
data class FleetLogPrincipal(
	val userId: UUID,
	val email: String,
	val tenantId: UUID?,
	val role: Role?,
)
