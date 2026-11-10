package com.fleetlog.security

import com.fleetlog.entity.Role
import java.util.UUID

/**
 * The caller of the current request. [tenantId] is null and [roles] empty when the user has no
 * active organization (e.g. right after sign-up); such a caller can only reach the account endpoints.
 * Roles are personas, not a ranking: a caller holds each one they were given, and nothing implied.
 */
data class FleetLogPrincipal(
	val userId: UUID,
	val email: String,
	val tenantId: UUID?,
	val roles: Set<Role>,
)
