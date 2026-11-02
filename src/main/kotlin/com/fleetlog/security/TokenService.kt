package com.fleetlog.security

import com.fleetlog.dto.AuthTokenResponse
import com.fleetlog.entity.AppUser
import com.fleetlog.entity.Membership
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant

/** Issues the API's JWTs. The active organization and the role in it travel as claims. */
@Service
class TokenService(
	private val jwtEncoder: JwtEncoder,
	private val properties: SecurityProperties,
) {

	fun issue(user: AppUser, activeMembership: Membership?): AuthTokenResponse {
		val now = Instant.now()
		val expiresAt = now.plus(properties.tokenLifetime)
		val claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(user.id.toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.claim(EMAIL_CLAIM, user.email)
			.apply {
				if (activeMembership != null) {
					claim(TENANT_CLAIM, activeMembership.organization.id.toString())
					claim(ROLE_CLAIM, activeMembership.role.name)
				}
			}
			.build()
		val header = JwsHeader.with(MacAlgorithm.HS256).build()
		val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
		return AuthTokenResponse(token, expiresAt)
	}

	companion object {
		const val ISSUER = "fleetlog"
		const val EMAIL_CLAIM = "email"
		const val TENANT_CLAIM = "tenant_id"
		const val ROLE_CLAIM = "role"
	}

}
