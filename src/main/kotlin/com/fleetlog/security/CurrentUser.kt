package com.fleetlog.security

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The one place services get the caller and the tenant from. The tenant only ever comes from the
 * JWT, never from a request body or URL, so one organization cannot reach another one's data.
 */
@Component
class CurrentUser {

	fun principal(): FleetLogPrincipal =
		(SecurityContextHolder.getContext().authentication as? FleetLogAuthentication)?.principal
			?: throw AuthenticationCredentialsNotFoundException("Not authenticated")

	fun userId(): UUID = principal().userId

	/** The active organization; 403 when the caller has none. */
	fun tenantId(): UUID = principal().tenantId ?: throw AccessDeniedException("No active organization")

}
