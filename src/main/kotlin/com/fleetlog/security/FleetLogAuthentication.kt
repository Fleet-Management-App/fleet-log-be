package com.fleetlog.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

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
