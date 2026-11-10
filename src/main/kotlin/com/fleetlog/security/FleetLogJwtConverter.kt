package com.fleetlog.security

import com.fleetlog.repository.MembershipRepository
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Turns a verified JWT into [FleetLogAuthentication].
 *
 * The roles are read from the membership on every request rather than carried in the token, so a
 * role change or a removal from the organization applies at once, not when the token expires.
 * A token whose membership no longer exists is treated as having no active organization.
 */
@Component
class FleetLogJwtConverter(
	private val membershipRepository: MembershipRepository,
) : Converter<Jwt, AbstractAuthenticationToken> {

	override fun convert(jwt: Jwt): AbstractAuthenticationToken {
		val userId = UUID.fromString(jwt.subject)
		val tenantId = jwt.getClaimAsString(TokenService.TENANT_CLAIM)?.let(UUID::fromString)
		val roles = tenantId?.let { membershipRepository.findRoles(userId, it).toSet() }.orEmpty()
		val principal = FleetLogPrincipal(
			userId = userId,
			email = jwt.getClaimAsString(TokenService.EMAIL_CLAIM).orEmpty(),
			tenantId = if (roles.isNotEmpty()) tenantId else null,
			roles = roles,
		)
		return FleetLogAuthentication(principal, jwt)
	}

}
