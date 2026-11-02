package com.fleetlog.security

import com.fleetlog.organization.Role
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
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

class FleetLogAuthentication(
	private val principal: FleetLogPrincipal,
	private val jwt: Jwt,
) : AbstractAuthenticationToken(listOfNotNull(principal.role?.let { SimpleGrantedAuthority("ROLE_${it.name}") })) {

	init {
		isAuthenticated = true
	}

	override fun getPrincipal() = principal

	override fun getCredentials() = jwt.tokenValue

	override fun getName() = principal.userId.toString()

}
